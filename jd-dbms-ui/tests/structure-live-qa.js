// Opt-in local DM acceptance. Uses session jddbms and two disposable schemas only.
const path = require('path')
const { spawnSync } = require('child_process')
const binary = process.platform === 'win32'
  ? path.join(path.dirname(process.execPath), 'node_modules/agent-browser/bin/agent-browser-win32-x64.exe') : 'agent-browser'
const script = async () => {
  const root = document.querySelector('#app').__vue__;
  const token = root.$store.getters.token;
  const report = { status: 'PASS', checks: [], failures: [], schemas: [] };
  const suffix = Date.now().toString(36).toUpperCase();
  const source = 'QA_CMP_L_' + suffix, target = 'QA_CMP_R_' + suffix;
  const check = (value, name) => { if (!value) throw Error(name); report.checks.push(name); };
  const api = async (url, method = 'GET', body) => {
    const response = await fetch('/dev-api' + url, { method, headers: { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' }, body: body && JSON.stringify(body) });
    const result = await response.json();
    if (!result.success) throw Error(result.errorMessage || 'API request failed');
    return result.data;
  };
  const sql = async statement => {
    const result = await api('/api/rdb/dml/execute', 'POST', { dataSourceId: 226, consoleId: 1, sql: statement, confirmDangerousSql: true });
    if (!result.every(row => row.success)) throw Error(result.find(row => !row.success)?.message || 'Fixture SQL failed');
    return result[0];
  };
  const inventory = (schema, table = 'T') => api('/api/rdb/schema-inventory?dataSourceId=226&schemaName=' + schema + '&tableName=' + table);
  const find = vm => vm.$options.name === 'TableCompare' ? vm : vm.$children.map(find).find(Boolean);
  let vm;
  try {
    if (root.$route.path !== '/table-compare/index') await root.$router.push('/table-compare/index');
    await root.$nextTick();
    vm = find(root);
    if (!vm) throw Error('请先打开表结构对比页面');
    for (const schema of [source, target]) {
      await sql('CREATE SCHEMA "' + schema + '" AUTHORIZATION SYSDBA'); report.schemas.push(schema);
      await sql('CREATE TABLE "' + schema + '".QA_PARENT (ID INT,CODE INT,CONSTRAINT PK_REF PRIMARY KEY(ID,CODE),CONSTRAINT UQ_REF UNIQUE(CODE,ID))');
      await sql('CREATE TABLE "' + schema + '".T (ID INT,A INT,B INT,VAL VARCHAR(50),CONSTRAINT CK_T CHECK(A>0),CONSTRAINT FK_T FOREIGN KEY(A,B) REFERENCES "' + schema + '".QA_PARENT(ID,CODE))');
      await sql('CREATE INDEX "' + schema + '".IX_AB ON "' + schema + '".T(A ASC,B DESC)');
      await sql('CREATE INDEX "' + schema + '".IX_EXPR ON "' + schema + '".T(UPPER(VAL))');
    }
    const initial = await inventory(source);
    check(!Object.keys(initial.errors).length && !initial.truncated.length, 'DM 索引、表达式、约束清单完整读取');
    check(initial.objects.every(row => row.tableName === 'T'), '单表读取仅包含所选表');
    check(initial.objects.find(row => row.objectName === 'IX_AB')?.attributes['DESCEND[2]'] === 'DESC', '复合索引顺序和降序元数据');
    check(initial.objects.find(row => row.objectName === 'IX_EXPR')?.attributes['COLUMN_EXPRESSION[1]']?.includes('UPPER'), '函数索引读取实际表达式');
    check(initial.objects.find(row => row.objectName === 'FK_T')?.attributes['REFERENCE_COLUMN[2]'] === 'CODE', '复合外键逐列引用映射');
    vm.source.dataSourceId = 226; vm.source.namespaceName = source; vm.source.tableName = 'T';
    vm.target.dataSourceId = 226; vm.target.namespaceName = target; vm.target.tableName = 'T';
    const compare = async mode => { vm.compareMode = mode; await vm.compare(); await vm.$nextTick(); check(vm.hasCompared && !vm.inventoryIssues.length, mode + ' 对比接口与页面完成'); };
    await compare('table');
    check(vm.objectRows.length > 0 && vm.objectRows.every(row => row.status === 'same'), '跨模式等价定义及系统生成索引匹配一致');
    await sql('DROP INDEX "' + target + '".IX_AB');
    await sql('CREATE UNIQUE INDEX "' + target + '".IX_AB ON "' + target + '".T(B DESC,A ASC)');
    await sql('DROP INDEX "' + target + '".IX_EXPR');
    await sql('CREATE INDEX "' + target + '".IX_EXPR ON "' + target + '".T(LOWER(VAL))');
    await sql('ALTER TABLE "' + target + '".T DROP CONSTRAINT CK_T');
    await sql('ALTER TABLE "' + target + '".T ADD CONSTRAINT CK_T CHECK(A>10)');
    await sql('ALTER TABLE "' + target + '".T DROP CONSTRAINT FK_T');
    await sql('ALTER TABLE "' + target + '".T ADD CONSTRAINT FK_T FOREIGN KEY(A,B) REFERENCES "' + target + '".QA_PARENT(CODE,ID) ON DELETE CASCADE');
    await compare('table');
    const row = name => vm.objectRows.find(item => item.name === name);
    check(row('IX_AB')?.status === 'changed' && row('IX_AB').changeDetails.some(item => item.field.startsWith('UNIQUENESS')), '唯一性与复合索引顺序差异');
    check(row('IX_EXPR')?.changeDetails.some(item => item.field.startsWith('COLUMN_EXPRESSION')), '函数索引表达式差异');
    check(row('CK_T')?.changeDetails.some(item => item.field.startsWith('SEARCH_CONDITION')), '检查约束条件差异');
    check(row('FK_T')?.changeDetails.some(item => item.field.startsWith('REFERENCE_COLUMN') || item.field.startsWith('COLUMN_NAME')) && row('FK_T').changeDetails.some(item => item.field.startsWith('DELETE_RULE')), '外键逐列映射与删除规则差异');
    check(vm.resultRows.every(item => item.status === 'same'), '上述差异不依赖字段变化');
    await compare('database');
    const table = vm.databaseRows.find(item => item.sourceName === 'T');
    check(table.status === 'changed' && table.objectDifferenceCount >= 4 && table.counts.changed === 0, '整库汇总识别仅索引或约束变化');
    await sql('ALTER TABLE "' + target + '".T RENAME TO OTHER');
    vm.target.tableName = 'OTHER';
    await compare('table');
    check(row('IX_AB')?.source && row('IX_AB')?.target && row('IX_AB').status === 'changed', '不同表名的同名索引合并为一项差异');
    await vm.$nextTick();
    report.visibleChanged = vm.objectRows.filter(item => item.status === 'changed').length;
  } catch (error) { report.status = 'FAIL'; report.failures.push(error.message); }
  finally {
    for (const schema of report.schemas.slice().reverse()) {
      try { await sql('DROP SCHEMA "' + schema + '" CASCADE'); }
      catch (error) { report.status = 'FAIL'; report.failures.push('Cleanup ' + schema + ': ' + error.message); }
    }
    report.cleaned = !report.failures.some(message => message.startsWith('Cleanup'));
  }
  return report;
}
function evaluate(input) {
  const result = spawnSync(binary, ['--session', 'jddbms', 'eval', '--stdin'], { input, encoding: 'utf8', timeout: 30000, maxBuffer: 2 * 1024 * 1024 })
  if (result.status !== 0) throw Error('Structure browser command failed; inspect window.__structureQa before retrying')
  return JSON.parse(result.stdout)
}
async function main() {
  evaluate('window.__structureQa={status:"RUNNING"};(' + script.toString() + ')().then(report=>window.__structureQa=report);"started"')
  let report;
  const deadline = Date.now() + 120000
  do {
    await new Promise(resolve => setTimeout(resolve, 800))
    report = evaluate('window.__structureQa')
    if (Date.now() > deadline) throw Error('Acceptance timed out; inspect window.__structureQa before retrying')
  } while (report.status === 'RUNNING')
  console.log(JSON.stringify(report, null, 2))
  if (report.status !== 'PASS') process.exitCode = 1
}
main().catch(error => { console.error(error.message); process.exitCode = 1 })
