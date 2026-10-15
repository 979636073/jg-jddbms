// Opt-in real DM acceptance; uses disposable connection clone and administrator session jddbms.
const path = require('path')
const { spawnSync } = require('child_process')
const binary = process.platform === 'win32'
  ? path.join(path.dirname(process.execPath), 'node_modules/agent-browser/bin/agent-browser-win32-x64.exe') : 'agent-browser'
const script = async keep => {
  const token = document.querySelector('#app').__vue__.$store.getters.token;
  const report = { status: 'PASS', checks: [], failures: [], fixtureId: null };
  const check = (value, name) => { if (!value) throw Error(name); report.checks.push(name); };
  const api = async (url, method = 'GET', body) => {
    const response = await fetch('/dev-api' + url, { method, headers: { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' }, body: body && JSON.stringify(body) });
    const result = await response.json();
    if (!result.success) throw Error(result.errorMessage || 'API request failed');
    return result.data;
  };
  const wait = async predicate => {
    const end = Date.now() + 45000;
    do {
      const state = await api('/api/rdb/monitor/state?dataSourceId=' + report.fixtureId);
      if (predicate(state)) return state;
      await new Promise(resolve => setTimeout(resolve, 700));
    } while (Date.now() < end);
    throw Error('后台采集等待超时');
  };
  const configure = (enabled, active = 1, space = 1) => api('/api/rdb/monitor/config', 'PUT', {
    dataSourceId: report.fixtureId, enabled, activeThreshold: active, spaceThreshold: space
  });
  const sql = async statement => {
    const rows = await api('/api/rdb/dml/execute', 'POST', { dataSourceId: 226, consoleId: 1, sql: statement, confirmDangerousSql: true });
    if (!rows.every(row => row.success)) throw Error('Fixture metadata cleanup failed');
  };
  try {
    report.fixtureId = await api('/api/connection/datasource/clone', 'POST', { id: 226 });
    await api('/api/connection/datasource/update', 'POST', { id: report.fixtureId, alias: 'QA_MONITOR_' + Date.now() });
    const original = await api('/api/connection/datasource/' + report.fixtureId);
    const initial = await api('/api/rdb/monitor/state?dataSourceId=' + report.fixtureId);
    check(!initial.config.enabled && initial.history.length === 0, '新连接默认不采集');
    check(initial.canManage, '管理员具备配置权限');
    await configure(true);
    const first = await wait(state => state.history.length >= 1);
    check(first.history[0].error == null, '真实 DM 后台采集完整');
    check(first.history[0].spacePercent > 1, '表空间使用率来自实测');
    check(first.alerts.some(row => row.key === 'space' && !row.endedAt), '阈值触发持久化告警');
    const next = await wait(state => state.history.length >= 2);
    check(next.history[0].capturedAt > first.history[0].capturedAt, '未打开监控页面仍持续采集');
    check(next.alerts.filter(row => row.key === 'space' && !row.endedAt).length === 1, '连续命中不重复创建告警');
    const baseline = next.history[0].capturedAt;
    await configure(true, 100000, 100);
    const recovered = await wait(state => state.history[0]?.capturedAt > baseline && state.alerts.every(row => row.endedAt));
    check(recovered.alerts.some(row => row.key === 'space' && row.endedAt), '调整阈值后实测恢复并记录恢复时间');
    // The normal update endpoint validates connections, so inject the outage only into this disposable clone.
    await sql("UPDATE JDDBMS.DATA_SOURCE SET URL='jdbc:dm://127.0.0.1:1' WHERE ID=" + Number(report.fixtureId));
    await configure(true, 100000, 100);
    const failed = await wait(state => state.history[0]?.error);
    check(failed.history[0].activeCount == null && failed.history[0].spacePercent == null, '连接故障显示未知指标');
    check(failed.alerts.some(row => row.key === 'collection' && !row.endedAt), '连接故障记录采集异常告警');
    check(!JSON.stringify(failed).includes('jdbc:dm:'), '持久化错误不包含连接串');
    await sql("UPDATE JDDBMS.DATA_SOURCE SET URL='" + original.url.replace(/'/g, "''") + "' WHERE ID=" + Number(report.fixtureId));
    await configure(true, 100000, 100);
    const healthy = await wait(state => !state.history[0]?.error && state.alerts.every(row => row.endedAt));
    check(healthy.history[0].sessionCount > 0, '连接恢复后重新采集并恢复异常告警');
    await configure(false, 100000, 100);
    const stopped = await api('/api/rdb/monitor/state?dataSourceId=' + report.fixtureId);
    await new Promise(resolve => setTimeout(resolve, 32000));
    const after = await api('/api/rdb/monitor/state?dataSourceId=' + report.fixtureId);
    check(!after.config.enabled && after.history.length === stopped.history.length, '关闭后台采集后记录数保持不变');
    if (keep) report.retainedForRestartCheck = true;
  } catch (error) { report.status = 'FAIL'; report.failures.push(error.message); }
  finally {
    if (report.fixtureId && (!keep || report.status === 'FAIL')) {
      try {
        await configure(false);
        for (const table of ['DB_MONITOR_CONFIG', 'DB_MONITOR_SAMPLE', 'DB_MONITOR_ALERT']) {
          await sql('DELETE FROM JDDBMS.' + table + ' WHERE DATA_SOURCE_ID=' + Number(report.fixtureId));
        }
        await api('/api/connection/datasource/' + report.fixtureId, 'DELETE');
        report.cleaned = true;
      } catch (error) { report.status = 'FAIL'; report.failures.push('Cleanup: ' + error.message); }
    }
  }
  return report;
}
function evaluate(input) {
  const result = spawnSync(binary, ['--session', 'jddbms', 'eval', '--stdin'], {
    input, encoding: 'utf8', timeout: 30000, maxBuffer: 2 * 1024 * 1024
  })
  if (result.status !== 0) throw Error('Monitoring browser command failed')
  return JSON.parse(result.stdout)
}
async function main() {
  // Long collection waits run in the page; each CLI evaluation stays below its RPC timeout.
  evaluate('window.__monitorQa = {status:"RUNNING"}; (' + script.toString() + ')('
    + process.argv.includes('--keep-for-restart') + ').then(report => {window.__monitorQa = report}); "started"')
  const deadline = Date.now() + 240000
  let report
  do {
    await new Promise(resolve => setTimeout(resolve, 1000))
    report = evaluate('window.__monitorQa')
    if (Date.now() > deadline) throw Error('Monitoring acceptance timed out; inspect window.__monitorQa before retrying')
  } while (report.status === 'RUNNING')
  console.log(JSON.stringify(report, null, 2))
  if (report.status !== 'PASS') process.exitCode = 1
}
main().catch(error => { console.error(error.message); process.exitCode = 1 })
