// Opt-in local DM acceptance. Requires administrator session jddbms; creates only disposable fixtures.
const { spawnSync } = require('child_process')
const path = require('path')
const session = 'roles-qa'
const browserBinary = process.platform === 'win32'
  ? path.join(path.dirname(process.execPath), 'node_modules/agent-browser/bin/agent-browser-win32-x64.exe') : 'agent-browser'
function cli(args, input) {
  const opening = args[2] === 'open';
  const result = spawnSync(browserBinary, args, { input, stdio: [input === undefined ? 'ignore' : 'pipe', opening ? 'ignore' : 'pipe', opening ? 'ignore' : 'pipe'],
    encoding: 'utf8', maxBuffer: 8 * 1024 * 1024, timeout: 180000 })
  if (result.status !== 0) throw Error('Browser command failed: ' + args.slice(0, 3).join(' ') + ': ' + (result.stderr || '').split('\n')[0])
  return result.stdout?.trim() || ''
}
function evaluate(fn, data, browser = session) {
  return JSON.parse(cli(['--session', browser, 'eval', '--stdin'], '(' + fn.toString() + ')(' + JSON.stringify(data) + ')'))
}
const all = ['METADATA_READ', 'DATA_READ', 'DATA_WRITE', 'SCHEMA_CHANGE', 'SQL_EXECUTE', 'MONITOR_VIEW', 'MIGRATION_RUN', 'DATASOURCE_MANAGE']
const expected = {
  dbms_developer: all,
  dbms_reader: ['METADATA_READ', 'DATA_READ', 'MONITOR_VIEW'],
  dbms_datasource: ['METADATA_READ', 'DATASOURCE_MANAGE'],
  dbms_auditor: ['METADATA_READ', 'MONITOR_VIEW'],
  dbms_security: ['METADATA_READ', 'DATASOURCE_MANAGE'],
  yiban: []
}
let fixtures
const report = { status: 'PASS', checks: [], ui: [], failures: [] }
try {
  const adminToken = evaluate(() => document.querySelector('#app').__vue__.$store.getters.token, null, 'jddbms')
  cli(['--session', session, 'open', 'http://localhost:8080'])
  fixtures = evaluate(async ({ expected, all, adminToken: token }) => {
    const api = async (url, method = 'GET', body, auth = token) => (await fetch('/dev-api' + url,
      { method, headers: { Authorization: 'Bearer ' + auth, 'Content-Type': 'application/json' }, body: body && JSON.stringify(body) })).json();
    const ensure = (value, message) => { if (!value) throw Error(message); };
    const suffix = Date.now().toString(36).toUpperCase();
    const data = { users: [], auxUsers: [], roles: [], connections: [], tasks: [], tables: [], suffix, token };
    window.__roleFixtures = data;
    const sql = async statement => {
      const r = await api('/api/rdb/dml/execute', 'POST', { dataSourceId: 226, consoleId: 1, sql: statement, pageNo: 1, pageSize: 100, confirmDangerousSql: true });
      ensure(r.success && r.data.every(x => x.success), 'Fixture SQL failed'); return r.data[0];
    };
    const table = 'QA_ROLE_' + suffix;
    data.table = table; data.tables.push(table);
    await sql('CREATE TABLE "TEST-TEST"."' + table + '" (ID INT PRIMARY KEY, VAL VARCHAR(30))');
    await sql('INSERT INTO "TEST-TEST"."' + table + '" VALUES (1, \'one\')');
    const roles = (await api('/system/role/list?pageNum=1&pageSize=100')).rows;
    for (const key of Object.keys(expected)) {
      const name = 'qa_role_' + suffix.toLowerCase() + '_' + data.users.length;
      const password = 'Qa!1' + Array.from(crypto.getRandomValues(new Uint32Array(4))).map(n => n.toString(36)).join('').slice(0, 12);
      ensure((await api('/system/user', 'POST', { userName: name, nickName: name, password, status: '0', roleIds: [roles.find(r => r.roleKey === key).roleId] })).code === 200, 'User creation failed');
      const user = { key, name }; data.users.push(user);
      user.id = (await api('/system/user/list?userName=' + name)).rows.find(x => x.userName === name).userId;
      const login = await api('/login', 'POST', { username: name, password });
      ensure(login.code === 200, 'Fixture login failed: ' + login.msg); user.token = login.token;
      for (const kind of ['owned', 'shared']) {
        const clone = await api('/api/connection/datasource/clone', 'POST', { id: 226 });
        ensure(clone.success, 'Connection clone failed'); user[kind] = clone.data; data.connections.push(clone.data);
        if (kind === 'owned') await sql('UPDATE JDDBMS.DATA_SOURCE SET USER_ID = ' + user.id + ', ALIAS = \'QA_' + key + '\' WHERE ID = ' + clone.data);
        else ensure((await api('/api/connection/datasource/sharing/' + clone.data + '/users', 'POST', { userId: user.id, permissionCodes: all })).success, 'Grant failed');
      }
    }
    return data;
  }, { expected, all, adminToken })
  const apiReport = evaluate(async ({ fixtures: f, expected, all }) => {
    const out = { checks: [], failures: [] };
    const api = async (url, method = 'GET', body, token = f.token) => (await fetch('/dev-api' + url,
      { method, headers: { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' }, body: body && JSON.stringify(body) })).json();
    const ok = r => r.success === true || r.code === 200;
    const denied = r => r.errorCode === 'common.permissionDenied' || r.code === 403;
    const assert = (pass, name, r) => { out.checks.push(name); if (!pass) out.failures.push({ name, error: r?.errorMessage || r?.msg || r?.data?.[0]?.message || 'Unexpected permission decision' }); };
    const sql = async statement => {
      const r = await api('/api/rdb/dml/execute', 'POST', { dataSourceId: 226, consoleId: 1, sql: statement, pageNo: 1, pageSize: 100, confirmDangerousSql: true });
      if (!r.success || !r.data.every(x => x.success)) throw Error('Fixture SQL failed'); return r.data[0];
    };
    for (const user of [...f.users, { key: 'admin', token: f.token, owned: 226, shared: 226, id: 1 }]) {
      const allowed = user.key === 'admin' ? all : expected[user.key];
      const info = await api('/getInfo', 'GET', undefined, user.token);
      for (const action of all) assert(user.key === 'admin' || info.permissions.includes('dbms:' + ({METADATA_READ:'metadata:read', DATA_READ:'data:read', DATA_WRITE:'data:write', SCHEMA_CHANGE:'schema:change', SQL_EXECUTE:'sql:execute', MONITOR_VIEW:'monitor:view', MIGRATION_RUN:'migration:run', DATASOURCE_MANAGE:'datasource:manage'})[action]) === allowed.includes(action), user.key + '/role/' + action, info);
      for (const kind of ['owned', 'shared']) {
        const id = user[kind], base = { dataSourceId: id, schemaName: 'TEST-TEST', tableName: f.table, consoleId: 1, pageNo: 1, pageSize: 2 };
        const drop = 'QD_' + f.suffix + '_' + out.checks.length; f.tables.push(drop);
        await sql('CREATE TABLE "TEST-TEST"."' + drop + '" (ID INT)');
        const requests = {
          METADATA_READ: ['/api/rdb/table/column_list?dataSourceId=' + id + '&schemaName=TEST-TEST&tableName=' + f.table, 'GET'],
          DATA_READ: ['/api/rdb/dml/execute_table', 'POST', base],
          DATA_WRITE: ['/api/rdb/dml/execute_update', 'POST', { ...base, sql: 'UPDATE "TEST-TEST"."' + f.table + '" SET VAL=\'two\' WHERE ID=1' }],
          SCHEMA_CHANGE: ['/api/rdb/table/delete', 'POST', { ...base, tableName: drop }],
          SQL_EXECUTE: ['/api/rdb/dml/execute', 'POST', { ...base, sql: 'SELECT * FROM "TEST-TEST"."' + f.table + '"' }],
          MONITOR_VIEW: ['/api/rdb/monitor/snapshot?dataSourceId=' + id + '&schemaName=TEST-TEST&tableName=' + f.table, 'GET'],
          DATASOURCE_MANAGE: ['/api/connection/datasource/sharing/' + id, 'GET']
        };
        for (const [action, args] of Object.entries(requests)) {
          const r = await api(...args, ...(args.length === 2 ? [undefined, user.token] : [user.token]));
          const success = ok(r) && (!Array.isArray(r.data) || r.data.every(x => x.success !== false)) && r.data?.success !== false;
          assert(allowed.includes(action) ? success : denied(r), user.key + '/' + kind + '/' + action, r);
          if (action === 'DATA_READ' && success && !allowed.includes('DATA_WRITE')) assert(r.data.every(x => !x.canEdit), user.key + '/' + kind + '/read-only-result', r);
        }
        for (const tableName of ['TEST_VIEW3', 'TEST_VIEW']) {
          const r = await api('/api/rdb/dml/execute_table', 'POST', { ...base, tableName, isView: true }, user.token);
          assert(allowed.includes('DATA_READ') ? ok(r) && r.data?.every(x => x.success && x.dataList?.length > 0) : denied(r),
            user.key + '/' + kind + '/view/' + tableName, r);
        }
        const blobRequest = { dataSourceId: id, schemaName: 'TEST-TEST', blobColumn: 'PAYLOAD', readId: crypto.randomUUID(),
          sql: 'SELECT ID,PAYLOAD FROM "TEST-TEST".BLOB_LARGE_TEST_20261015 WHERE ID=1', resultColumns: ['ID', 'PAYLOAD'], resultValues: ['1', 'A'] };
        const blob = await api('/api/rdb/dml/blob/preview', 'POST', blobRequest, user.token);
        assert(allowed.includes('DATA_READ') ? ok(blob) && blob.data?.base64?.length > 0 : denied(blob),
          user.key + '/' + kind + '/blob-read', blob);
        const cancelled = await api('/api/rdb/dml/blob/cancel', 'POST', blobRequest, user.token);
        assert(allowed.includes('DATA_READ') ? ok(cancelled) && cancelled.data === false : denied(cancelled), user.key + '/' + kind + '/blob-cancel', cancelled);
        const downloaded = await fetch('/dev-api/api/rdb/dml/blob/download', {method:'POST', headers:{Authorization:'Bearer ' + user.token,'Content-Type':'application/json'}, body:JSON.stringify(blobRequest)});
        const downloadIsJson = (downloaded.headers.get('content-type') || '').includes('application/json');
        const downloadResult = downloadIsJson ? await downloaded.json() : { bytes: (await downloaded.arrayBuffer()).byteLength };
        assert(allowed.includes('DATA_READ') ? downloaded.ok && downloadResult.bytes > 0 : denied(downloadResult), user.key + '/' + kind + '/blob-download', downloadResult);
        const target = 'QM_' + f.suffix + '_' + out.checks.length; f.tables.push(target);
        const draft = await api('/api/migration/tasks', 'POST', { name: '角色验收 ' + target, sourceId: id, targetId: id,
          sourceSchema: 'TEST-TEST', targetSchema: 'TEST-TEST', tables: [{ source: f.table, target }] }, user.token);
        if (!allowed.includes('METADATA_READ')) assert(denied(draft), user.key + '/' + kind + '/migration-metadata-denied', draft);
        else if (!ok(draft)) assert(false, user.key + '/' + kind + '/migration-draft', draft);
        else {
          const task = draft.data.id; f.tasks.push({ id: task, token: user.token });
          const checked = await api('/api/migration/tasks/' + task + '/precheck', 'POST', {}, user.token);
          if (!ok(checked) || checked.data.status !== 'MIG_READY') assert(false, user.key + '/' + kind + '/migration-precheck', { errorMessage: checked.data?.message || checked.errorMessage });
          else {
            const started = await api('/api/migration/tasks/' + task + '/start', 'POST', { offlineConfirmed: true, fingerprint: checked.data.assessment.fingerprint }, user.token);
            assert(allowed.includes('MIGRATION_RUN') ? ok(started) : denied(started), user.key + '/' + kind + '/MIGRATION_RUN', started);
            if (allowed.includes('MIGRATION_RUN') && ok(started)) {
              let result;
              for (let i = 0; i < 100; i++) {
                result = await api('/api/migration/tasks/' + task, 'GET', undefined, user.token);
                if (!['MIG_RUNNING', 'MIG_CANCEL_REQUESTED'].includes(result.data?.status)) break;
                await new Promise(resolve => setTimeout(resolve, 200));
              }
              assert(result.data?.objects?.every(x => x.stage === 'COMPLETED'), user.key + '/' + kind + '/migration-finished', { errorMessage: result.data?.message });
              const rows = await sql('SELECT COUNT(*) FROM "TEST-TEST"."' + target + '"');
              assert(rows.dataList[0].slice(-1)[0] === '1', user.key + '/' + kind + '/migration-row-count', rows);
            }
          }
        }
        const preview = await api('/api/connection/datasource/sharing/' + id + '/effective/' + user.id);
        for (const action of all) assert(preview.data?.actions.find(x => x.code === action)?.allowed === allowed.includes(action), user.key + '/' + kind + '/preview/' + action, preview);
      }
      if (user.key !== 'admin') {
        const r = await api('/api/rdb/schema/list?dataSourceId=228&requestType=2', 'GET', undefined, user.token);
        assert(denied(r), user.key + '/other-private-denied', r);
        const sharing = '/api/connection/datasource/sharing/' + user.owned;
        const copy = await api('/api/connection/datasource/clone', 'POST', { id: user.owned }, user.token);
        assert(allowed.includes('DATASOURCE_MANAGE') ? ok(copy) : denied(copy), user.key + '/clone-connection', copy);
        if (ok(copy)) {
          f.connections.push(copy.data);
          assert(ok(await api('/api/connection/datasource/update', 'POST', { id: copy.data, alias: 'QA_edit_' + user.key }, user.token)), user.key + '/edit-connection');
          assert(ok(await api('/api/connection/datasource/' + copy.data, 'DELETE', undefined, user.token)), user.key + '/delete-connection');
          f.connections = f.connections.filter(x => x !== copy.data);
        }
        const grant = await api(sharing + '/users', 'POST', { userId: f.users[1].id, permissionCodes: ['DATA_READ'] }, user.token);
        assert(allowed.includes('DATASOURCE_MANAGE') ? ok(grant) : denied(grant), user.key + '/sharing-grant', grant);
        if (ok(grant)) {
          const revoke = await api(sharing + '/users/' + f.users[1].id, 'DELETE', undefined, user.token);
          assert(ok(revoke), user.key + '/sharing-revoke', revoke);
          assert(denied(await api('/api/rdb/dml/execute_table', 'POST', { dataSourceId: user.owned, schemaName: 'TEST-TEST', tableName: f.table, pageNo: 1, pageSize: 2 }, f.users[1].token)), user.key + '/sharing-revoke-immediate');
        }
        const name = 'qa_role_' + f.suffix.toLowerCase() + '_crud';
        const password = 'Qa!1' + crypto.randomUUID().replace(/-/g, '').slice(0, 12);
        const created = await api('/system/user', 'POST', { userName: name, nickName: name, password, status: '0', roleIds: [11] }, user.token);
        assert(user.key === 'dbms_security' ? ok(created) : denied(created), user.key + '/create-platform-user', created);
        if (ok(created)) {
          const id = (await api('/system/user/list?userName=' + name)).rows.find(x => x.userName === name).userId;
          f.auxUsers.push(id);
          assert(ok(await api('/system/user', 'PUT', { userId: id, userName: name, nickName: '角色回归', status: '0', roleIds: [10] }, user.token)), user.key + '/edit-user-and-role');
          const changed = 'Qa!2' + crypto.randomUUID().replace(/-/g, '').slice(0, 12);
          assert(ok(await api('/system/user/resetPwd', 'PUT', { userId: id, password: changed }, user.token)), user.key + '/reset-user-password');
          const login = await api('/login', 'POST', { username: name, password: changed });
          const info = login.token && await api('/getInfo', 'GET', undefined, login.token);
          assert(info?.permissions?.includes('dbms:sql:execute'), user.key + '/role-assignment-effective',
            { errorMessage: login.token ? JSON.stringify({ roles: info?.roles, permissions: info?.permissions }) : login.msg });
          assert(ok(await api('/system/user/changeStatus', 'PUT', { userId: id, status: '1' }, user.token)), user.key + '/disable-user');
          assert((await api('/getInfo', 'GET', undefined, login.token)).code === 401, user.key + '/disable-user-session-revoked');
          assert(ok(await api('/system/user/changeStatus', 'PUT', { userId: id, status: '0' }, user.token)), user.key + '/enable-user');
          assert(!!(await api('/login', 'POST', { username: name, password: changed })).token, user.key + '/enabled-user-login');
          assert(ok(await api('/system/user/' + id, 'DELETE', undefined, user.token)), user.key + '/delete-platform-user');
          f.auxUsers = f.auxUsers.filter(x => x !== id);
        }
        const roleKey = 'qa_role_' + f.suffix.toLowerCase();
        const roleBody = { roleName: roleKey, roleKey, roleSort: 99, status: '0', menuIds: [], menuCheckStrictly: true, deptCheckStrictly: true };
        const roleCreated = await api('/system/role', 'POST', roleBody, user.token);
        assert(user.key === 'dbms_security' ? ok(roleCreated) : denied(roleCreated), user.key + '/create-platform-role', roleCreated);
        if (ok(roleCreated)) {
          const id = (await api('/system/role/list?roleKey=' + roleKey)).rows.find(x => x.roleKey === roleKey).roleId;
          f.roles.push(id);
          assert(ok(await api('/system/role', 'PUT', { ...roleBody, roleId: id, roleName: '角色回归修改' }, user.token)), user.key + '/edit-platform-role');
          assert(ok(await api('/system/role/' + id, 'DELETE', undefined, user.token)), user.key + '/delete-platform-role');
          f.roles = f.roles.filter(x => x !== id);
        }
      }
      for (const [name, url, keys] of [
        ['users', '/system/user/list?pageNum=1&pageSize=1', ['admin', 'dbms_security']],
        ['roles', '/system/role/list?pageNum=1&pageSize=1', ['admin', 'dbms_security']],
        ['operation-log', '/monitor/operlog/list?pageNum=1&pageSize=1', ['admin', 'dbms_security', 'dbms_auditor']],
        ['login-log', '/monitor/logininfor/list?pageNum=1&pageSize=1', ['admin', 'dbms_security', 'dbms_auditor']]
      ]) { const r = await api(url, 'GET', undefined, user.token); assert(keys.includes(user.key) ? ok(r) : denied(r), user.key + '/' + name, r); }
    }
    window.__roleFixtures = f;
    out.tables = f.tables;
    out.tasks = f.tasks;
    out.auxUsers = f.auxUsers;
    out.roles = f.roles;
    out.connections = f.connections;
    return out;
  }, { fixtures, expected, all })
  report.checks.push(...apiReport.checks); report.failures.push(...apiReport.failures)
  fixtures.tables = apiReport.tables;
  fixtures.tasks = apiReport.tasks;
  fixtures.auxUsers = apiReport.auxUsers;
  fixtures.roles = apiReport.roles;
  fixtures.connections = apiReport.connections;
  // Each role uses the real application and its own authenticated browser state.
  for (const user of fixtures.users) {
    evaluate(({ token }) => { sessionStorage.clear(); localStorage.clear(); document.cookie = 'JDDBMS-Token=' + token + '; path=/'; return true }, user)
    cli(['--session', session, 'open', 'http://localhost:8080/workspace'])
    cli(['--session', session, 'wait', '--fn', "Boolean(document.querySelector('#app')?.__vue__?.$store.getters.name)"])
    const result = evaluate(async ({ user }) => {
      const root = document.querySelector('#app').__vue__;
      const components = []; const visit = vm => { components.push(vm); vm.$children.forEach(visit); }; visit(root);
      const sidebar = components.find(vm => vm.$options.name === 'leftTree');
      const header = components.find(vm => vm.$options.name === 'databaseHeader');
      const actual = { name: root.$store.getters.name, roles: root.$store.getters.roles, menus: header?.navList.filter(x => header.canSeeNav(x)).map(x => x.name), sidebar: !!sidebar };
      if (!sidebar) return actual;
      const wait = async test => { for (let i = 0; i < 100; i++) { if (test()) return true; await new Promise(resolve => setTimeout(resolve, 100)); } return false; };
      await sidebar.resourceTreeReady;
      const source = sidebar.databaseTree.find(x => x.dataSource.id === user.owned)?.dataSource;
      actual.manageOwned = sidebar.canManageDataSource(source);
      actual.manageShared = sidebar.canManageDataSource(sidebar.databaseTree.find(x => x.dataSource.id === user.shared)?.dataSource);
      actual.connectionVisible = !!source;
      if (!source) return actual;
      const label = Array.from(document.querySelectorAll('.resource-node-label')).find(el => el.textContent.trim() === 'QA_' + user.key);
      if (!label) { actual.error = 'Connection node missing'; return actual; }
      label.click();
      const connectButton = () => Array.from(document.querySelectorAll('.datasource-dialog button')).find(el => el.textContent.trim() === '连接' && !el.disabled);
      if (!await wait(connectButton)) { actual.error = 'Connect dialog not ready'; return actual; }
      connectButton().click();
      if (!await wait(() => sidebar.isDataSourceConnected(user.owned))) { actual.error = 'Connection failed'; return actual; }
      const tree = sidebar.$refs.resourceTree;
      await wait(() => tree.getNode(user.owned + '-schema-TEST-TEST'));
      const schema = tree.getNode(user.owned + '-schema-TEST-TEST');
      if (!schema) { actual.error = 'Schema node missing'; return actual; }
      await sidebar.expandResourceNode(schema);
      const tables = schema.childNodes.find(x => x.data.groupType === 'tables');
      await sidebar.expandResourceNode(tables);
      const targetNode = tables.childNodes.find(x => x.data.label === 'BLOB_LARGE_TEST_20261015');
      if (targetNode) tree.scrollToItem(targetNode.key);
      await root.$nextTick();
      await wait(() => Array.from(document.querySelectorAll('.resource-node-label')).some(el => el.textContent.trim() === 'BLOB_LARGE_TEST_20261015'));
      const table = Array.from(document.querySelectorAll('.resource-node-label')).find(el => el.textContent.trim() === 'BLOB_LARGE_TEST_20261015');
      if (!table) { actual.error = 'Table node missing'; return actual; }
      table.click();
      await wait(() => Array.from(root.$store.state.workspaceData.dataCurrentData.values()).some(x => x.dataList?.length) || document.querySelector('.el-message--error'));
      actual.messages = Array.from(document.querySelectorAll('.el-message')).map(el => el.textContent.trim());
      actual.tabs = Array.from(root.$store.state.workspaceData.dataCurrentData.values()).map(x => ({ title: x.title, rows: x.dataList?.length, table: x.uniqueData?.tableName }));
      return actual;
    }, { user })
    report.ui.push({ role: user.key, ...result });
    const name = user.key + '/workspace-table'; report.checks.push(name)
    if (expected[user.key].includes('DATA_READ') && !result.tabs?.some(x => x.rows > 0 && x.table === 'BLOB_LARGE_TEST_20261015')) report.failures.push({ name, result })
    if (expected[user.key].includes('METADATA_READ') && !expected[user.key].includes('DATA_READ') &&
      (result.error || result.tabs?.some(x => x.rows > 0) || !result.messages?.some(x => x.includes('权限')))) report.failures.push({ name, result })
    if (!expected[user.key].includes('METADATA_READ') && result.connectionVisible) report.failures.push({ name, result })
    const nav = user.key === 'dbms_developer' ? ['数据迁移', '表结构对比'] : user.key === 'dbms_reader' ? ['表结构对比'] : [];
    report.checks.push(user.key + '/UI/navigation');
    if (JSON.stringify(result.menus) !== JSON.stringify(nav)) report.failures.push({ name: user.key + '/UI/navigation', result })
    for (const key of ['manageOwned', 'manageShared']) {
      const name = user.key + '/UI/' + key; report.checks.push(name)
      if (result[key] !== expected[user.key].includes('DATASOURCE_MANAGE')) report.failures.push({ name, result })
    }
  }
} catch (error) {
  report.failures.push({ name: 'runner', error: error.message })
} finally {
  if (!fixtures) fixtures = evaluate(() => window.__roleFixtures || null)
  // The admin session remains untouched; fixture IDs never include existing connections or users.
  const cleanup = evaluate(async f => {
    if (!f) return { skipped: true };
    const token = document.querySelector('#app').__vue__.$store.getters.token;
    const api = async (url, method = 'GET', body) => (await fetch('/dev-api' + url, { method,
      headers: { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' }, body: body && JSON.stringify(body) })).json();
    const sql = statement => api('/api/rdb/dml/execute', 'POST', { dataSourceId: 226, consoleId: 1, sql: statement, pageNo: 1, pageSize: 100, confirmDangerousSql: true });
    const errors = [];
    for (const task of f.tasks) {
      const r = await (await fetch('/dev-api/api/migration/tasks/' + task.id, { method: 'DELETE', headers: { Authorization: 'Bearer ' + task.token } })).json();
      if (!r.success) errors.push('task/' + task.id);
    }
    for (const table of [...new Set(f.tables)]) {
      const r = await sql('DROP TABLE IF EXISTS "TEST-TEST"."' + table + '"');
      if (!r.success || !r.data.every(x => x.success)) errors.push('table/' + table);
    }
    for (const id of f.connections) if (!(await api('/api/connection/datasource/' + id, 'DELETE')).success) errors.push('connection/' + id);
    for (const user of f.users) if ((await api('/system/user/' + user.id, 'DELETE')).code !== 200) errors.push('user/' + user.id);
    for (const id of f.auxUsers) if ((await api('/system/user/' + id, 'DELETE')).code !== 200) errors.push('user/' + id);
    for (const id of f.roles) if ((await api('/system/role/' + id, 'DELETE')).code !== 200) errors.push('role/' + id);
    return { errors };
  }, fixtures, 'jddbms')
  if (cleanup.errors?.length) report.failures.push({ name: 'cleanup', errors: cleanup.errors })
  cli(['--session', session, 'close'])
}
report.status = report.failures.length ? 'FAIL' : 'PASS'
console.log(JSON.stringify(report, null, 2))
if (report.failures.length) process.exitCode = 1
