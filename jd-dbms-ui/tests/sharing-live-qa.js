// Opt-in local DM integration test. Requires an administrator logged into agent-browser session jddbms.
// Clones connection 226, creates temporary users/team, and removes its fixtures in finally.
const { spawnSync } = require('child_process')
const script = `
(async () => {
  const root = document.querySelector('#app').__vue_app__.config.globalProperties;
  const admin = root.$store.state.user.token;
  const checks = [], users = [];
  let connectionId, teamId;
  const suffix = Date.now().toString(36);
  const password = 'Qa!1' + Array.from(crypto.getRandomValues(new Uint32Array(4))).map(n => n.toString(36)).join('').slice(0, 12);
  const call = async (path, method = 'GET', body, token = admin) => {
    const response = await fetch('/dev-api' + path, {method, headers:{'Content-Type':'application/json', Authorization:'Bearer ' + token},
      body:body === undefined ? undefined : JSON.stringify(body)});
    return response.json();
  };
  const ok = result => result.success === true || result.code === 200;
  const ensure = (condition, message) => { if (!condition) throw Error(message); };
  const sharing = () => '/api/connection/datasource/sharing/' + connectionId;
  const sql = async statement => {
    const result = await call('/api/rdb/dml/execute', 'POST', {dataSourceId:226, consoleId:1, sql:statement, pageNo:1, pageSize:100, confirmDangerousSql:true});
    ensure(ok(result) && result.data.every(item => item.success), 'Fixture SQL failed: ' + (result.errorMessage || result.data?.[0]?.message));
    return result.data[0];
  };
  const grant = async (type, id, codes) => ensure(ok(await call(sharing() + '/' + type, 'POST',
    {[type === 'users' ? 'userId' : 'teamId']:id, permissionCodes:codes})), 'Grant failed');
  const revoke = async (type, id) => ensure(ok(await call(sharing() + '/' + type + '/' + id, 'DELETE')), 'Revoke failed');
  const read = user => call('/api/rdb/dml/execute_table', 'POST', {dataSourceId:connectionId, schemaName:'TEST-TEST',
    tableName:'BLOB_LARGE_TEST_20261015', consoleId:1, pageNo:1, pageSize:2}, user.token);
  const query = user => call('/api/rdb/dml/execute', 'POST', {dataSourceId:connectionId, consoleId:1,
    sql:'SELECT COUNT(*) FROM "TEST-TEST"."BLOB_LARGE_TEST_20261015"', pageNo:1, pageSize:2}, user.token);
  const preview = async user => {
    const result = await call(sharing() + '/effective/' + user.id);
    ensure(ok(result), 'Preview failed');
    return result.data.actions;
  };
  try {
    ensure(ok(await call('/getInfo')), 'Administrator login required');
    const roles = (await call('/system/role/list?pageNum=1&pageSize=100')).rows;
    for (const roleKeys of [['dbms_reader'], ['dbms_developer', 'dbms_reader']]) {
      const name = 'qa_share_' + suffix + '_' + users.length;
      const roleIds = roleKeys.map(key => roles.find(role => role.roleKey === key).roleId);
      ensure(ok(await call('/system/user', 'POST', {userName:name, nickName:name, password, status:'0', roleIds})), 'Create fixture user failed');
      const user = {id:null, name, token:null}; users.push(user);
      const rows = (await call('/system/user/list?userName=' + name)).rows;
      user.id = rows.find(row => row.userName === name).userId;
      const login = await call('/login', 'POST', {username:name, password});
      ensure(ok(login), 'Fixture login failed: ' + login.msg); user.token = login.token;
    }
    const reader = users[0], developer = users[1];
    const clone = await call('/api/connection/datasource/clone', 'POST', {id:226});
    ensure(ok(clone), 'Clone fixture connection failed'); connectionId = clone.data;
    ensure(!ok(await read(reader)), 'Unshared connection was readable');
    await grant('users', reader.id, ['DATA_READ']);
    ensure(ok(await read(reader)), 'Reader could not read an authorized table');
    ensure(!ok(await query(reader)), 'Reader executed arbitrary SQL');
    const write = await call('/api/rdb/dml/execute_update', 'POST', {dataSourceId:connectionId, consoleId:1,
      sql:'UPDATE "TEST-TEST"."BLOB_LARGE_TEST_20261015" SET PAYLOAD_BYTES = 0 WHERE ID = -999999'}, reader.token);
    ensure(!ok(write), 'Reader passed data-write authorization');
    for (const path of [sharing(), sharing() + '/effective/' + developer.id, sharing() + '/audit'])
      ensure(!ok(await call(path, 'GET', undefined, reader.token)), 'Non-manager inspected sharing state');
    ensure(!ok(await call('/system/user', 'PUT', {userId:reader.id, userName:reader.name, nickName:reader.name,
      status:'0', roleIds:[1]}, reader.token)), 'Reader assigned itself an admin role');
    ensure(!ok(await call('/api/rdb/schema/list?dataSourceId=226&requestType=2', 'GET', undefined, reader.token)), 'Cross-connection access allowed');
    checks.push('reader-read', 'reader-write-SQL-denied', 'non-manager-denied', 'cross-connection-denied', 'role-escalation-denied');

    await grant('users', developer.id, ['METADATA_READ']);
    ensure(!ok(await query(developer)), 'Platform role bypassed missing resource grant');
    await sql("INSERT INTO JDDBMS.TEAM (CODE, NAME, STATUS) VALUES ('qa_share_" + suffix + "', '授权回归临时团队', 'VALID')");
    const team = await sql("SELECT ID FROM JDDBMS.TEAM WHERE CODE = 'qa_share_" + suffix + "'");
    teamId = Number(team.dataList[0][1]);
    await sql('INSERT INTO JDDBMS.TEAM_USER (TEAM_ID, USER_ID) VALUES (' + teamId + ', ' + developer.id + ')');
    const search = await call(sharing() + '/teams?keyword=qa_share_' + suffix);
    ensure(ok(search) && search.data.some(row => row.teamId === teamId), 'Team search failed');
    await grant('teams', teamId, ['SQL_EXECUTE']);
    ensure(ok(await query(developer)), 'Multi-role team member could not query');
    let actions = await preview(developer);
    ensure(actions.find(row => row.code === 'SQL_EXECUTE').allowed, 'Preview differed from enforcement');
    await revoke('users', developer.id);
    ensure(ok(await query(developer)), 'Revoking direct grant removed remaining team grant');
    await sql("UPDATE JDDBMS.TEAM SET STATUS = 'INVALID' WHERE ID = " + teamId);
    ensure(!ok(await query(developer)), 'Disabled team retained access');
    ensure(!(await preview(developer)).find(row => row.code === 'SQL_EXECUTE').allowed, 'Disabled team remained in preview');
    await sql("UPDATE JDDBMS.TEAM SET STATUS = 'VALID' WHERE ID = " + teamId);
    await sql('DELETE FROM JDDBMS.TEAM_USER WHERE TEAM_ID = ' + teamId + ' AND USER_ID = ' + developer.id);
    ensure(!ok(await query(developer)), 'Removed team member retained access');
    await sql('INSERT INTO JDDBMS.TEAM_USER (TEAM_ID, USER_ID) VALUES (' + teamId + ', ' + developer.id + ')');
    ensure(ok(await query(developer)), 'Restored team membership did not work');
    await revoke('teams', teamId);
    ensure(!ok(await query(developer)), 'Revoked team retained access with the same token');
    checks.push('multi-role-team', 'grant-union', 'team-disable', 'member-removal', 'team-revoke-immediate');

    await grant('users', reader.id, ['SQL_EXECUTE', 'DATA_READ']);
    actions = await preview(reader);
    ensure(!actions.find(row => row.code === 'SQL_EXECUTE').allowed, 'Preview ignored platform restrictions');
    await revoke('users', reader.id);
    ensure(!ok(await read(reader)), 'Revoked user retained access with the same token');
    await grant('users', reader.id, ['DATA_READ']);
    ensure(ok(await read(reader)), 'Regrant failed');
    ensure(ok(await call('/system/user/changeStatus', 'PUT', {userId:reader.id, status:'1'})), 'Disable fixture user failed');
    ensure(!ok(await read(reader)), 'Disabled user kept a live session');
    ensure((await preview(reader)).every(row => !row.allowed), 'Disabled user preview allowed operations');
    const invalid = await call(sharing() + '/users', 'POST', {userId:developer.id, permissionCodes:['INVALID_ACTION']});
    ensure(!ok(invalid), 'Invalid action accepted');
    let audit;
    for (let attempt = 0; attempt < 10; attempt++) {
      audit = await call(sharing() + '/audit');
      if (ok(audit) && audit.data.some(row => !row.success)) break;
      await new Promise(resolve => setTimeout(resolve, 500));
    }
    ensure(ok(audit) && audit.data.some(row => row.success) && audit.data.some(row => !row.success), 'Success/failure audit missing');
    ensure(audit.data.every(row => row.url.startsWith(sharing() + '/')), 'Audit crossed connections');
    checks.push('user-revoke-immediate', 'user-disable-session', 'preview-role-intersection', 'success-failure-audit');
    return {status:'PASS', checks, auditRows:audit.data.length};
  } finally {
    const cleanupErrors = [];
    const cleanup = async (label, operation) => { try { const result = await operation(); if (result && !ok(result)) cleanupErrors.push(label); } catch (_) { cleanupErrors.push(label); } };
    if (connectionId) {
      for (const user of users) if (user.id) await cleanup('revoke user ' + user.id, () => call(sharing() + '/users/' + user.id, 'DELETE'));
      if (teamId) await cleanup('revoke team', () => call(sharing() + '/teams/' + teamId, 'DELETE'));
      await cleanup('delete fixture connection', () => call('/api/connection/datasource/' + connectionId, 'DELETE'));
    }
    if (teamId) {
      await cleanup('delete fixture members', async () => { await sql('DELETE FROM JDDBMS.TEAM_USER WHERE TEAM_ID = ' + teamId); });
      await cleanup('delete fixture team', async () => { await sql('DELETE FROM JDDBMS.TEAM WHERE ID = ' + teamId); });
    }
    for (const user of users) if (user.id) await cleanup('delete fixture user ' + user.id, () => call('/system/user/' + user.id, 'DELETE'));
    if (cleanupErrors.length) throw Error('Fixture cleanup failed: ' + cleanupErrors.join(', '));
  }
})()
`
const result = spawnSync(process.env.AGENT_BROWSER_BIN || 'agent-browser', ['--session', 'jddbms', 'eval', '--stdin'],
  { input: script, encoding: 'utf8', maxBuffer: 4 * 1024 * 1024, timeout: 120000, shell: !process.env.AGENT_BROWSER_BIN && process.platform === 'win32' })
process.stdout.write(result.stdout || '')
process.stderr.write(result.stderr || '')
if (result.error) throw result.error
if (result.status !== 0 || !/"status"\s*:\s*"PASS"/.test(result.stdout || '')) process.exitCode = result.status || 1
