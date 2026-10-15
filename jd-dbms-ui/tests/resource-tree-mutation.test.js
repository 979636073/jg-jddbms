const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')
const compiler = require('vue-template-compiler')

const root = path.join(__dirname, '../src')
const emitted = []
const mutationSource = fs.readFileSync(path.join(root, 'utils/resourceMutation.js'), 'utf8')
const mutationModule = { exports: {} }
vm.runInNewContext(mutationSource.replace(/^export /gm, '')
  + '\nmodule.exports = { groupsChangedBySql, mutationFromResponse, publishResourceMutation, notifyResourceTaskStatus }', {
  module: mutationModule,
  window: { dispatchEvent: event => emitted.push(event.detail) },
  CustomEvent: class { constructor(name, options) { this.name = name; this.detail = options.detail } },
})
const mutation = mutationModule.exports
const response = { success: true }
const config = (url, data) => ({ url, data: JSON.stringify({ dataSourceId: 11, schemaName: 'SYSDBA', ...data }) })

assert.deepStrictEqual(Array.from(mutation.mutationFromResponse(config('/api/rdb/table/createTable'), response).groups), ['tables'])
const copied = mutation.mutationFromResponse({
  url: '/api/rdb/table/copyTable?dataSourceId=11&schemaName=SYSDBA&copySchemaName=JDCPSC',
}, { success: true, data: [{ success: true }] })
assert.strictEqual(copied.schemaName, 'JDCPSC', '复制表须刷新目标模式')
assert.strictEqual(mutation.mutationFromResponse(config('/api/rdb/table/createTable'), { success: false }), null)
assert.strictEqual(mutation.mutationFromResponse(config('/api/rdb/table/modify/sql'), response), null, 'SQL 预览不应刷新资源树')
assert.deepStrictEqual(Array.from(mutation.mutationFromResponse(config('/api/rdb/dml/execute', {
  sql: 'CREATE TABLE "SYSDBA"."NEW_TABLE" (ID INT)',
}), { success: true, data: [{ success: true }] }).groups), ['tables'])
assert.strictEqual(mutation.mutationFromResponse(config('/api/rdb/dml/execute', {
  sql: "SELECT 'CREATE TABLE X'",
}), { success: true, data: [{ success: true }] }), null, '查询文本不能误判为建表')
assert.deepStrictEqual(Array.from(mutation.mutationFromResponse(config('/api/rdb/user/createUser'), response).groups), ['users', 'schemas'])
assert.deepStrictEqual(Array.from(mutation.mutationFromResponse(config('/api/rdb/user/createUser'), { code: 200 }).groups), ['users', 'schemas'])
assert.strictEqual(mutation.mutationFromResponse(config('/api/rdb/user/createUser'), { code: 500 }), null)
assert.deepStrictEqual(Array.from(mutation.mutationFromResponse(config('/api/rdb/user/dropUser'), {
  success: false, data: { items: [{ status: 'SUCCESS' }, { status: 'FAILED' }] },
}).groups), ['users', 'schemas'], '部分成功也需刷新')
assert.deepStrictEqual(Array.from(mutation.mutationFromResponse(config('/api/rdb/dml/execute_ddl', {
  sql: 'DROP SCHEMA "OLD_SCHEMA"',
}), { success: true, data: { success: true } }).groups), ['schemas'])
assert.deepStrictEqual(Array.from(mutation.groupsChangedBySql('COMMENT ON COLUMN JDCPSC.T.C IS NULL')), ['tables'])
assert.deepStrictEqual(Array.from(mutation.groupsChangedBySql('GRANT SELECT ON JDCPSC.T TO PIC_DEMO')), ['users', 'schemas'])
assert.deepStrictEqual(Array.from(mutation.groupsChangedBySql('REVOKE SELECT ON JDCPSC.T FROM PIC_DEMO')), ['users', 'schemas'])
assert.deepStrictEqual(Array.from(mutation.mutationFromResponse(config('/api/rdb/schema/delete_schema', {
  schemaName: 'OLD_SCHEMA',
}), response).groups), ['schemas'])

assert.strictEqual(mutation.mutationFromResponse(config('/api/rdb/table/dropTableTask'), {
  success: true, data: 42,
}), null, '后台任务提交时尚未完成')
mutation.notifyResourceTaskStatus({ id: 42, taskStatus: 'FINISH' })
assert.strictEqual(emitted.length, 1)
assert.deepStrictEqual(Array.from(emitted[0].groups), ['tables'])

const sidebarSource = fs.readFileSync(path.join(root, 'views/main/workspace/SideBar.vue'), 'utf8')
const descriptor = compiler.parseComponent(sidebarSource)
assert.deepStrictEqual(compiler.compile(descriptor.template.content).errors, [])
const sidebarModule = { exports: {} }
const connectionServer = {}
const tableServer = { getTreeTableList: async params => {
  assert.strictEqual(params.isRefreshCache, true, '资源树重新读取表时必须绕过后端缓存')
  return { success: true, data: { data: [] } }
} }
vm.runInNewContext(descriptor.script.content.replace(/^import .*;?\s*$/gm, '')
  .replace('export default', 'module.exports ='), {
  module: sidebarModule,
  connectionServer, sqlServer: {}, viewServer: {}, tableServer, userServer: {}, tableSpaceServer: {},
  SqlPreview: {}, CreateOption: {}, DataSourceSharingDialog: {}, TableTransferDialog: {}, SchemaSqlTransferDialog: {}, DmpTransferDialog: {}, addTableSpaceVue: {}, addSchema: {}, addUser: {}, addTable: {}, addView: {}, ViewImport: {}, draggable: {}, database: {},
})
const sidebar = sidebarModule.exports
const makeNode = (data, children = []) => {
  const node = { data, childNodes: children, expanded: true, loaded: true, loads: 0 }
  node.loadData = () => { node.loads++ }
  return node
}
const tables = makeNode({ treeNodeType: 'objectGroup', groupType: 'tables', extraParams: { schemaName: 'SYSDBA', databaseName: '' } })
const views = makeNode({ treeNodeType: 'objectGroup', groupType: 'views', extraParams: { schemaName: 'SYSDBA', databaseName: '' } })
const schema = makeNode({ treeNodeType: 'schema' }, [tables, views])
const schemas = makeNode({ treeNodeType: 'schemaCollection' }, [schema])
const users = makeNode({ treeNodeType: 'objectGroup', groupType: 'users', extraParams: { schemaName: 'SYSDBA', databaseName: '' } })
const rootNode = makeNode({ treeNodeType: 'datasource' }, [schemas, users])
const state = {
  resourceChangeQueue: { 11: { dataSourceId: 11, schemaName: 'SYSDBA', databaseName: '', groups: ['tables'] } },
  resourceChangeVersions: {}, resourceChangeTimer: 1,
  $refs: { resourceTree: { getNode: key => key === 'datasource-11' ? rootNode : null } },
  ...sidebar.methods,
}
state.flushResourceMutations()
assert.strictEqual(tables.loads, 1, '建表后应刷新已展开的数据表节点')
assert.strictEqual(views.loads, 0, '无关视图节点不应刷新')
assert.strictEqual(users.loads, 0, '无关用户节点不应刷新')
assert.strictEqual(tables.data.refreshOnce, true, '下一次读取须跳过旧缓存')
state.resourceChangeQueue = { 11: { dataSourceId: 11, schemaName: '', databaseName: '', groups: ['schemas', 'users'] } }
state.flushResourceMutations()
assert.strictEqual(schemas.loads, 1, '模式变更应刷新模式节点')
assert.strictEqual(users.loads, 1, '用户变更应刷新用户节点')

sidebar.methods.loadResourceObjects.call({
  ...sidebar.methods,
  resourceVersion: () => 1,
  $nextTick: () => {},
  createObjectNode: () => ({}),
  getTableListShared: sidebar.methods.getTableListShared,
}, {
  groupType: 'tables', dataSource: { id: 11 }, extraParams: { dataSourceId: 11, schemaName: 'SYSDBA' },
  refreshOnce: true,
}, () => {}).then(async () => {
  tableServer.getTreeTableList = async params => {
    const start = (params.pageNo - 1) * params.pageSize
    return { success: true, data: { data: Array.from({ length: Math.min(params.pageSize, 405 - start) }, (_, i) => ({ name: `T${start + i}` })), total: 405 } }
  }
  const groupData = {
    key: 'tables', label: '数据表', groupType: 'tables', dataSource: { id: 11 },
    extraParams: { dataSourceId: 11, schemaName: 'SYSDBA' },
  }
  const pageState = {
    ...sidebar.methods,
    resourceVersion: () => 0,
    $nextTick: () => {},
    createObjectNode: (group, item) => ({ treeNodeType: 'table', label: item.name }),
    getTableListShared: sidebar.methods.getTableListShared,
    $message: { error: message => { throw new Error(message) } },
  }
  let firstPage
  await sidebar.methods.loadResourceObjects.call(pageState, groupData, children => { firstPage = children })
  assert.strictEqual(groupData.objectChildren.length, 405, '必须加载全部表目录，不能截断在首 200 张')
  assert.strictEqual(firstPage.length, 405, '展开数据表应直接展示全部 405 张表')
  assert.strictEqual(firstPage[100].treeNodeType, 'table')
  const allTables = groupData.objectChildren
  assert.strictEqual(allTables.length, 405)
  assert.strictEqual(allTables[404].label, 'T404')
  const errors = []
  pageState.$message.error = message => errors.push(message)
  groupData.cachedChildren = firstPage
  tableServer.getTreeTableList = async () => ({ success: false, errorMessage: '权限不足' })
  let retainedChildren
  await sidebar.methods.loadResourceObjects.call(pageState, groupData, children => { retainedChildren = children })
  assert.strictEqual(retainedChildren, firstPage, '刷新失败时应保留已加载对象')
  assert.deepStrictEqual(errors, ['权限不足'])

  const requests = []
  let refreshed = null
  connectionServer.deleteSchema = async request => { requests.push(request); return { success: true } }
  const deleteState = {
    isProtectedSchema: sidebar.methods.isProtectedSchema,
    $prompt: async () => ({ value: 'USER_SCHEMA' }),
    $message: { warning: () => {}, success: () => {}, error: message => { throw new Error(message) } },
    refreshResourceNode: node => { refreshed = node },
  }
  const parent = { key: 'schemas' }
  await sidebar.methods.deleteSchemaFromTree.call(deleteState, {
    label: 'USER_SCHEMA', dataSource: { id: 11, user: 'SYSDBA' }, extraParams: { databaseName: '' },
  }, { parent })
  assert.strictEqual(requests.length, 1)
  assert.strictEqual(requests[0].schemaName, 'USER_SCHEMA')
  assert.strictEqual(refreshed, parent, '删除成功后刷新模式集合')
  await sidebar.methods.deleteSchemaFromTree.call(deleteState, {
    label: 'SYSDBA', dataSource: { id: 11, user: 'SYSDBA' }, extraParams: {},
  }, { parent })
  assert.strictEqual(requests.length, 1, '当前连接用户模式不能删除')
  console.log('资源树在数据库对象变更后按数据源与对象类型刷新回归测试通过')
}).catch(error => { console.error(error); process.exitCode = 1 })
