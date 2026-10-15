const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')

function loadComponent(name, dependencies) {
  const file = path.join(__dirname, '../src/views/main/workspace', name)
  const script = fs.readFileSync(file, 'utf8').match(/<script>([\s\S]*?)<\/script>/)[1]
    .replace(/^import .*;?\s*$/gm, '').replace('export default', 'module.exports =')
  const context = { module: { exports: {} }, ...dependencies }
  vm.runInNewContext(script, context, { filename: file })
  return context.module.exports
}

const sideBar = loadComponent('SideBar.vue', {
  SqlPreview: {}, CreateOption: {}, DataSourceSharingDialog: {}, TableTransferDialog: {},
  SchemaSqlTransferDialog: {}, DmpTransferDialog: {}, addTableSpaceVue: {}, addSchema: {},
  addUser: {}, addTable: {}, addView: {}, ViewImport: {}, draggable: {},
})
const navBar = loadComponent('NavBar.vue', {
  Hamburger: {}, TaskDrawer: {}, DmpToolManager: {}, mapGetters: () => ({}),
})

async function run() {
  const configs = new Map()
  const route = { path: '/workspace/look/first', params: { id: 'first' },
    query: { dataSourceId: 3, schemaName: 'TEST', tableName: 'OLD' } }
  let opened = 0
  let loads = 0
  let updatedRoute
  const state = {
    pageId: 'first', schema: 'TEST', $route: route,
    dataInfo: { dataSource: { id: 3, alias: 'QA' } },
    $store: { state: { workspaceData: { dataCurrentData: configs } },
      dispatch(action, value) { updatedRoute = { action, value }; return Promise.resolve() } },
    $router: { replace(value) { route.query = value.query; return Promise.resolve(route) } },
    $EventBus: { $emit() { opened++ } },
    handelClickItem() { loads++; return Promise.resolve() },
  }
  await sideBar.methods.openTableInWindow.call(state, { name: 'T_A' }, 0, {})
  assert.strictEqual(loads, 1, '刷新恢复的空窗口不能因为路由保留表名而跳过首次加载')
  assert.strictEqual(opened, 0, '首次打开应复用当前空窗口')
  assert.strictEqual(updatedRoute.action, 'jdTagsView/updateVisitedView')
  assert.strictEqual(updatedRoute.value.query.tableName, 'T_A', '页签必须同步新的表路由，避免再次点击重复开窗')
  configs.set('first', { uniqueData: { schemaName: 'TEST', tableName: 'T_A' } })
  await sideBar.methods.openTableInWindow.call(state, { name: 'T_B' }, 1, {})
  assert.strictEqual(opened, 1, '已有表窗口打开不同表仍走现有页签规则')
  assert.strictEqual(loads, 1)

  const restored = { path: '/workspace/look/first', params: { id: 'first' }, query: route.query }
  let restoredLoads = 0
  let navigation = 0
  const nav = {
    $route: { path: '/workspace/look/second' }, dataInfo: {},
    $store: { getters: { visitedViews2: [restored] }, state: { workspaceData: { dataCurrentData: configs } } },
    $router: { push() { navigation++; return Promise.resolve() } },
    $emit(event, value) { assert.strictEqual(event, 'openRouteObject'); assert.strictEqual(value.params.id, 'first'); restoredLoads++ },
  }
  configs.delete('first')
  navBar.methods.sqlDetailTableData.call(nav, route.query)
  await Promise.resolve()
  assert.strictEqual(navigation, 1)
  assert.strictEqual(restoredLoads, 1, '切回刷新恢复但没有配置的旧页签必须补上对象加载')
  nav.$route.path = restored.path
  navBar.methods.sqlDetailTableData.call(nav, route.query)
  assert.strictEqual(restoredLoads, 2, '当前空页签也应能补加载')
  configs.set('first', { uniqueData: false })
  navBar.methods.sqlDetailTableData.call(nav, route.query)
  assert.strictEqual(restoredLoads, 3, '模式总览返回表仍应加载')
  configs.set('first', { uniqueData: { tableName: 'T_A' } })
  navBar.methods.sqlDetailTableData.call(nav, route.query)
  assert.strictEqual(restoredLoads, 3, '已经加载的窗口不应重复请求')
  console.log('首次点表、恢复空页签和已加载窗口复用回归通过')
}

run().catch(error => { console.error(error); process.exitCode = 1 })
