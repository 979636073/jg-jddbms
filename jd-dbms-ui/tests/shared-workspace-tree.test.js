const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')
const compiler = require('vue-template-compiler')
const root = path.join(__dirname, '../src/views/main/workspace')

function load(name, mocks) {
  const source = fs.readFileSync(path.join(root, name), 'utf8')
  const component = compiler.parseComponent(source)
  assert.deepStrictEqual(compiler.compile(component.template.content).errors, [])
  const context = { module: { exports: {} }, ...mocks }
  vm.runInNewContext(component.script.content.replace(/^import .*;?\s*$/gm, '').replace('export default', 'module.exports ='), context)
  return { component: context.module.exports, source }
}

async function main() {
  const shell = load('index.vue', { NavBar: {}, JdTagsView: {}, linkServerDialog: {}, SideBar: {} })
  const content = load('content.vue', { ContainerBar: {} })
  assert.strictEqual((shell.source.match(/<SideBar\b/g) || []).length, 1)
  assert(!content.source.includes('<SideBar'), '每个右侧窗口不能再创建资源树')
  assert(shell.source.indexOf('<SideBar') < shell.source.indexOf('<keep-alive>'), '树应在右侧缓存容器之外')
  assert(!shell.source.includes(':isQuery='), '树功能不能随查询/表页签切换改变')
  assert.strictEqual(content.component.activated, undefined, '窗口激活不能重新加载对象')
  const store = { state: { workspaceData: { dataInfo: { dateSourceId: 7, userName: 'APP' } } } }
  const state = content.component.data.call({ $route: { params: { id: 'a' } }, $store: store })
  store.state.workspaceData.dataInfo = { dateSourceId: 9, userName: 'OTHER' }
  assert.strictEqual(state.dataInfo.dateSourceId, 7, '右侧概览也必须保留自身连接上下文')
  assert.strictEqual(content.component.computed.loading.call({ ...state, objectLoading: { a: true, b: false } }), true)

  let resolveQuery
  const dispatches = [], loads = [], requests = []
  const sidebar = load('SideBar.vue', {
    connectionServer: {}, viewServer: {}, tableServer: {}, userServer: {}, tableSpaceServer: {},
    SqlPreview: {}, CreateOption: {}, DataSourceSharingDialog: {}, TableTransferDialog: {}, SchemaSqlTransferDialog: {}, DmpTransferDialog: {}, addTableSpaceVue: {}, addSchema: {}, addUser: {}, addTable: {}, addView: {}, ViewImport: {}, draggable: {}, database: {},
    sqlServer: { executeSql(params) { requests.push(params); return new Promise(resolve => { resolveQuery = resolve }) } }
  }).component
  const tree = { ...sidebar.methods, dataInfo: { dataSource: { id: 9, type: 'DM' } }, schema: 'OTHER', type: 'views', pageId: 'b',
    activeClass: 6, nameTbale: 'KEPT', heightIndexs: [6], isQuery: false, $refs: {},
    $emit(name, ...args) { if (name === 'load') loads.push(args) },
    $message: { error(message) { throw Error(message) } },
    $store: { dispatch(name, payload) { dispatches.push({ name, payload }) }, state: { jdTagsView: { titleMap: [] } } }
  }
  const pending = sidebar.methods.handelClickItem.call(tree, { name: 'ORDERS', tableDetails: {} }, 0, {}, {
    pageId: 'a', dataSource: { id: 7, alias: 'Oracle', type: 'ORACLE' }, schema: 'APP', type: 'tables'
  })
  tree.pageId = 'c'
  tree.schema = 'NEW'
  tree.dataInfo = { dataSource: { id: 12, type: 'DM' } }
  resolveQuery({ success: true, data: [{ success: true, dataList: [], headerList: [] }] })
  await pending
  const loaded = dispatches.find(item => item.name === 'workspaceData/setDataCurrentData').payload
  assert.strictEqual(loaded.pageId, 'a', '异步结果必须写回发起请求的窗口')
  assert.strictEqual(loaded.uniqueData.dataSourceId, 7)
  assert.strictEqual(loaded.uniqueData.schemaName, 'APP')
  assert.strictEqual(requests[0].dataSourceId, 7)
  assert(loads.every(args => args[1] === 'a'), '加载状态也必须绑定目标窗口')
  assert.strictEqual(tree.activeClass, 6)
  assert.strictEqual(tree.nameTbale, 'KEPT', '加载右侧窗口不能改写共享树的选择')

  const opened = []
  const queryTree = { ...tree, pageId: 'q', schema: 'APP', dataInfo: { dataSource: { id: 7 } },
    $route: { path: '/workspace/query/q', query: {} },
    $store: { state: { workspaceData: { dataCurrentData: new Map() } } },
    $EventBus: { $emit(name, payload) { opened.push(payload) } },
    handelClickItem() { throw Error('打开表不能覆盖查询脚本') }
  }
  sidebar.methods.openTableInWindow.call(queryTree, { name: 'ORDERS' }, 0, {})
  assert.strictEqual(opened[0].tableName, 'ORDERS')

  assert(shell.source.includes('@openRouteObject="openRouteObject"'), '导航复用总览页签时须转发对象打开请求')
  const nav = load('NavBar.vue', {
    Hamburger: {}, TaskDrawer: {}, DmpToolManager: {}, mapGetters() { return {} },
    connectionServer: {}, sqlServer: {}, tableServer: {}, downloadFile() {}, getToken() {}, getUserProfile() {},
    uuidv4() { throw Error('复用总览页签不能新建窗口') }, TASK_STATUS: {}, listDmpTools() {},
  }).component
  const overviewRoute = { path: '/workspace/look/a', params: { id: 'a' }, query: { dataSourceId: 7, schemaName: 'APP', tableName: 'ORDERS' } }
  const restored = [], navigations = []
  nav.methods.sqlDetailTableData.call({
    $route: { path: '/workspace/query/q' },
    $store: { getters: { visitedViews2: [overviewRoute] }, state: { workspaceData: { dataCurrentData: new Map([['a', { uniqueData: false }]]) } } },
    $router: { push(route) { navigations.push(route); return Promise.resolve() } },
    $emit(name, route) { restored.push({ name, route }) },
  }, { dataSourceId: 7, schemaName: 'APP', tableName: 'ORDERS' })
  assert.strictEqual(restored.length, 0, '对象打开请求应等待页签路由切换完成')
  await Promise.resolve()
  assert.strictEqual(navigations[0].path, '/workspace/look/a')
  assert.strictEqual(restored[0].name, 'openRouteObject')
  assert.strictEqual(restored[0].route.params.id, 'a')

  const routeLoads = []
  const routeTree = {
    resourceTreeReady: Promise.resolve(), databaseTree: [{ dataSource: { id: 7 } }],
    $store: { state: { workspaceData: { dataCurrentData: new Map([['a', { uniqueData: false }]]) } } },
    isDataSourceConnected() { return true },
    handelClickItem(item, index, event, target) { routeLoads.push({ item, target }) },
  }
  await sidebar.methods.loadRouteObject.call(routeTree, restored[0].route)
  assert.strictEqual(routeLoads[0].item.name, 'ORDERS')
  assert.strictEqual(routeLoads[0].target.pageId, 'a')
  routeTree.$store.state.workspaceData.dataCurrentData.set('a', { uniqueData: { tableName: 'ORDERS' } })
  await sidebar.methods.loadRouteObject.call(routeTree, restored[0].route)
  assert.strictEqual(routeLoads.length, 1, '已有表数据的窗口不能重新加载')
  console.log('共享资源树、右侧独立缓存及异步上下文隔离回归通过')
}
main().catch(error => { console.error(error); process.exitCode = 1 })
