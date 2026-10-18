const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')
const compiler = require('./helpers/vue-compiler')

const file = path.join(__dirname, '../src/views/main/workspace/SideBar.vue')
const descriptor = compiler.parseComponent(fs.readFileSync(file, 'utf8'))
assert.deepStrictEqual(compiler.compile(descriptor.template.content).errors, [])
assert(descriptor.template.content.includes('<vue-easy-tree'), '整树应使用参考项目同款虚拟滚动')
assert(!descriptor.script.content.includes('下一批'), '所有对象目录都不能出现下一批节点')
const context = {
  module: { exports: {} }, setTimeout, clearTimeout, connectionServer: {}, sqlServer: {}, viewServer: {},
  tableServer: {}, userServer: {}, tableSpaceServer: {},
  SqlPreview: {}, CreateOption: {}, DataSourceSharingDialog: {}, TableTransferDialog: {},
  SchemaSqlTransferDialog: {}, DmpTransferDialog: {}, addTableSpaceVue: {}, addSchema: {},
  addUser: {}, addTable: {}, addView: {}, ViewImport: {}, draggable: {}, database: {},
}
vm.runInNewContext(descriptor.script.content.replace(/^import .*;?\s*$/gm, '')
  .replace('export default', 'module.exports ='), context, { filename: file })
const methods = context.module.exports.methods
process.env.VUE_ENV = 'server'
const createTree = require('./helpers/resource-tree')

async function run() {
  const state = {
    ...methods, resourceVersion: () => 0, resourceFilter: '',
    $nextTick: () => {}, $message: { error: message => { throw new Error(message) } },
  }
  context.connectionServer.getSchemaList = async () => ({ data: Array.from({ length: 1011 },
    (_, i) => ({ name: 'SCHEMA_' + i })) })
  const collection = { key: 'schemas', label: '模式', treeNodeType: 'schemaCollection',
    dataSource: { id: 13, alias: 'QA' } }
  let children
  await state.loadResourceContainers(collection, items => { children = items })
  assert.strictEqual(children.length, 1011, '模式目录也应完整加载')

  const groups = state.createObjectGroups(collection.dataSource, { schemaName: 'SCHEMA_0' })
  let requests = 0
  let completeRequest
  context.viewServer.getViewList = () => {
    requests++
    return new Promise(resolve => { completeRequest = resolve })
  }
  const viewParams = { dataSourceId: 13, schemaName: 'SCHEMA_0', requestType: 2 }
  const treeRequest = state.getViewListShared({ ...viewParams, pageSize: 1000 })
  const listRequest = state.getViewListShared({ ...viewParams, pageSize: 200 })
  assert.strictEqual(treeRequest, listRequest, '树与右侧视图列表应共享同一并发目录请求')
  assert.strictEqual(requests, 1)
  completeRequest({ success: true, data: { data: [], total: 0 } })
  await treeRequest
  assert.strictEqual(state._viewListRequests.size, 0, '请求结束后不保留过期目录缓存')
  for (const group of groups) {
    const items = Array.from({ length: 1001 }, (_, i) => ({
      name: 'OBJECT_' + i, functionName: 'OBJECT_' + i, procedureName: 'OBJECT_' + i,
      triggerName: 'OBJECT_' + i, tableDetails: { type: 'VIEW' },
    }))
    if (group.groupType === 'materializedViews') items.forEach(item => { item.tableDetails.type = 'MATERIALIZED VIEW' })
    const api = async () => ({ success: true, data: { data: items, total: items.length } })
    context.tableServer.getTreeTableList = api
    context.viewServer.getViewList = api
    context.sqlServer.getFunctionList = api
    context.sqlServer.getProcedureList = api
    context.sqlServer.getTriggerList = api
    await state.loadResourceObjects(group, loaded => { children = loaded })
    assert.strictEqual(children.length, 1001, group.groupType + '应一次显示完整列表')
  }

  const tables = { key: 'tables', label: '数据表', treeNodeType: 'objectGroup', leaf: false }
  const leaf = { key: 'last', label: 'T_10001', treeNodeType: 'table', leaf: true }
  const schema = { key: 'schema', label: 'APP', leaf: false }
  const root = { key: 'datasource-13', label: 'QA', leaf: false }
  let delayedResolve
  let filterState
  const tree = createTree({
    nodeKey: 'key', lazy: true, props: { isLeaf: 'leaf', label: 'label' },
    filterNodeMethod: (value, data, node) => methods.resourceFilterNode.call(filterState, value, data, node),
    load(node, resolve) {
      if (node.level === 0) resolve([root])
      else if (node.key === root.key) resolve([schema])
      else if (node.key === schema.key) resolve([tables])
      else delayedResolve = resolve
    },
  })
  tree.getNode(root.key).expand()
  tree.getNode(schema.key).expand()
  tree.getNode(tables.key).expand()
  filterState = { ...methods, resourceFilter: 'T_10001', databaseTree: [root],
    $refs: { resourceTree: tree } }
  filterState.handleResourceFilter(filterState.resourceFilter)
  delayedResolve(Array.from({ length: 10001 }, (_, i) => i === 10000 ? leaf :
    { key: 't-' + i, label: 'T_' + i, treeNodeType: 'table', leaf: true }))
  filterState.applyResourceFilter(filterState.resourceFilter)
  assert(tree.getNode('last').visible, '请求期间输入的搜索词应立即应用到返回的对象')
  tree.setCurrentKey('last')
  const original = tree.getNode('last')
  tree.getNode(schema.key).collapse()
  filterState.handleResourceFilter('T_10001')
  assert(!tree.getNode(schema.key).expanded, '搜索不得展开用户收起的模式')
  assert(!tree.getNode('last').visible, '已加载但收起的模式下的对象也不能参与搜索')
  tree.getNode(root.key).collapse()
  filterState.applyResourceFilter('T_10001', false)
  assert(!tree.getNode(root.key).expanded, '接口返回重应用过滤时不能重新打开用户收起的目录')
  filterState.handleResourceFilter('not-found')
  assert(!original.visible)
  filterState.handleResourceFilter('')
  assert.strictEqual(tree.getNode('last'), original, '反复搜索不能销毁已经加载的节点')
  assert.strictEqual(tree.getCurrentKey(), 'last', '搜索保持原选中状态')
  assert.strictEqual(tree.getNode('tables').childNodes.length, 10001)
  const commentNode = { label: 'T_10001', comment: '图片附件测试' }
  assert(methods.resourceFilterNode('图片', commentNode, { data: commentNode }))
  assert(!methods.resourceFilterNode('不匹配', commentNode, { data: commentNode }))
  let searchRequests = 0
  context.tableServer.getTreeTableList = () => { searchRequests++; throw new Error('搜索不得调用接口') }
  context.connectionServer.getDatabaseList = () => { searchRequests++; throw new Error('搜索不得加载数据库') }
  const before = filterState.resourceNodes().map(node => [node.key, node.expanded])
  filterState.resourceFilter = '图片'
  filterState.handleResourceFilter('图片')
  await new Promise(resolve => setTimeout(resolve, 350))
  assert.strictEqual(searchRequests, 0, '搜索只能过滤已展开的节点，不得发起元数据请求')
  assert.deepStrictEqual(filterState.resourceNodes().map(node => [node.key, node.expanded]), before)
  filterState.handleResourceFilter('')
  tree.getNode(root.key).expand()
  tree.getNode(schema.key).expand()
  filterState.handleResourceFilter('T_10001')
  assert(tree.getNode('last').visible, '用户主动展开后已加载表可以再次参与搜索')
  assert(tree.getNode(schema.key).expanded)
  filterState.handleResourceFilter('')


  const node = state.createObjectNode(groups.find(group => group.groupType === 'materializedViews'),
    { name: 'MV_FALLBACK', type: 'MATERIALIZED VIEW' }, 0)
  assert.strictEqual(node.viewType, 'MATERIALIZED VIEW', '类型不能在创建树节点时丢失')
  console.log('整树完整加载、万节点搜索、迟到响应和节点状态回归通过')
}
run().catch(error => { console.error(error); process.exitCode = 1 })
