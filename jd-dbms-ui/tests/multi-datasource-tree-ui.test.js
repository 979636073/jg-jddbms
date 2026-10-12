const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')
const compiler = require('vue-template-compiler')

function loadComponent(file, context) {
  const source = fs.readFileSync(file, 'utf8')
  const script = source.match(/<script>([\s\S]*?)<\/script>/)[1]
    .replace(/^import .*;?\s*$/gm, '')
    .replace('export default', 'module.exports =')
  vm.runInNewContext(script, context, { filename: file })
  return { component: context.module.exports, source }
}

const sideBarFile = path.join(__dirname, '../src/views/main/workspace/SideBar.vue')
const context = {
  module: { exports: {} },
  connectionServer: {}, sqlServer: {}, viewServer: {}, tableServer: {}, userServer: {}, tableSpaceServer: {},
  SqlPreview: {}, CreateOption: {}, DataSourceSharingDialog: {}, TableTransferDialog: {}, SchemaSqlTransferDialog: {}, DmpTransferDialog: {}, addTableSpaceVue: {}, addSchema: {}, addUser: {}, addTable: {}, addView: {}, ViewImport: {}, draggable: {},
  database: {}, uuidv4() { return 'node-id' }, copyText() {},
  sessionStorage: { setItem() {} },
  document: { title: '' },
}
const { component: sideBar, source: sideBarSource } = loadComponent(sideBarFile, context)
const descriptor = compiler.parseComponent(sideBarSource)
const template = compiler.compile(descriptor.template.content)
assert.deepStrictEqual(template.errors, [], '多数据源树模板应可编译')
assert(sideBarSource.includes('placeholder="搜索过滤库/表/用户..."'), '左树应提供统一资源搜索入口')
assert(sideBarSource.includes(':load="loadResourceNode"'), '左树应按层级懒加载')
assert(sideBarSource.includes('@node-contextmenu="handleResourceContextMenu"'), '表节点应保留数据模板、复制和新窗口菜单')
assert(sideBarSource.includes('connection-status'), '数据源根节点应显示连接状态')
assert(sideBarSource.includes('@click.stop="openDataSourceManager(data.dataSource)"'), '数据源根节点应保留编辑入口')
assert(sideBarSource.includes('this.dataSourceDisplayName(dataSource)'), '删除连接时应用连接名标识目标')
assert(sideBarSource.includes('class="resource-table-checkbox"'), '表节点应提供批量选择入口')
assert(sideBarSource.includes('deleteSelectedTreeTables(data, node)'), '表分组应提供批量删除入口')
assert(sideBarSource.includes('.el-tree-node__expand-icon.is-leaf'), '表等叶子节点不应显示展开箭头')
assert(sideBarSource.includes('class="resource-context-menu"'), '表右键菜单应使用资源树专用菜单')
assert(sideBarSource.includes("executeResourceContextAction('ddl')"), '表右键菜单应保留查看 DDL')
assert(sideBarSource.includes("executeResourceContextAction('delete')"), '表右键菜单应提供删除表')
assert(!sideBarSource.includes('scrollIntoView'), '资源树定位不得滚动整个页面')

const dataSource = {
  id: 7,
  alias: '生产库',
  user: 'APP',
  type: 'ORACLE',
  status: true,
  supportDatabase: false,
  supportSchema: true,
}
const helperState = {
  isDataSourceConnected() { return false },
  createExtraParams: sideBar.methods.createExtraParams,
  dataSourceDisplayName: sideBar.methods.dataSourceDisplayName,
}
const root = sideBar.methods.createDataSourceNode.call(helperState, dataSource)
assert.strictEqual(root.key, 'datasource-7')
assert.strictEqual(root.label, '生产库', '数据源树根节点应优先展示连接名')
assert.strictEqual(root.extraParams.dataSourceId, 7)
const hierarchy = sideBar.methods.createHierarchyNode.call(helperState, dataSource)
assert.strictEqual(hierarchy.label, '模式')

const commits = []
const sessionValues = []
context.sessionStorage.setItem = (key, value) => sessionValues.push({ key, value })
const activateState = {
  dataInfo: {}, schema: '', activeClass: 3, heightIndexs: [3], nameTbale: 'OLD', seleCtrlKet: ['OLD'],
  $store: { commit(type, payload) { commits.push({ type, payload }) } },
}
sideBar.methods.activateDataSourceContext.call(activateState, dataSource, { schemaName: 'APP' })
assert.deepStrictEqual(commits.map(item => item.type), [
  'workspaceData/SET_DATA_BASE_INFO',
  'workspaceData/SET_DATA_INFO',
  'SET_CURRENTCONNECTIONDETAILS',
])
assert.strictEqual(sessionValues.length, 1)
assert.strictEqual(activateState.activeClass, -1, '切换数据源时应清理当前表选择，但不能创建外层页面')

const navBarSource = fs.readFileSync(path.join(__dirname, '../src/views/main/workspace/NavBar.vue'), 'utf8')
const navList = navBarSource.match(/navList:\s*\[([\s\S]*?)\n\s*\],/)[1]
for (const name of ['新建窗口', '新建查询', '新建连接']) {
  assert(!navList.includes(`name: "${name}"`), `顶部导航不应再显示${name}`)
}
assert(navList.includes('route: "/data-migration/index"'), '顶部导航应提供数据迁移入口')
assert(navList.includes('route: "/table-compare/index"'), '顶部导航应提供表结构对比入口')
assert(navBarSource.includes('target="_blank"'), '顶部功能入口应在同一浏览器打开新标签页')
assert(!navBarSource.includes('window.open('), '顶部功能入口不应弹出独立浏览器窗口')
const routerSource = fs.readFileSync(path.join(__dirname, '../src/router/index.js'), 'utf8')
for (const path of ['/data-migration', '/table-compare']) {
  assert(routerSource.includes(`path: '${path}',\n    component: Layout,\n    hidden: true,`), `${path} 不应再显示在后台管理菜单`)
}
assert(routerSource.includes("standaloneTool: true"), '独立工具页应启用无侧栏布局')
const layoutSource = fs.readFileSync(path.join(__dirname, '../src/layout/index.vue'), 'utf8')
assert(layoutSource.includes('<sidebar v-if="!sidebar.hide&&!isStandaloneTool"'), '独立工具页不应显示左侧树')

const dialogSource = fs.readFileSync(path.join(__dirname, '../src/views/main/workspace/components/linkServerDialog/index.vue'), 'utf8')
assert(dialogSource.includes('this.$emit("saved", res.data)'), '保存连接后应通知当前工作区刷新左树')
assert(dialogSource.includes('label="连接名" prop="connectionName"'), '连接表单应提供连接名字段')
assert(dialogSource.includes('alias: this.formData.connectionName'), '保存数据源时应提交连接名')
assert(dialogSource.includes('required: true, message: "请输入连接名"'), '连接名必须校验必填')
assert(!dialogSource.includes('localStorage.removeItem("saveVisitedViews2")'), '保存连接不应清空现有工作区页签')
assert(!dialogSource.includes('this.$store.dispatch("jdTagsView/delAllViews"'), '保存连接不应关闭现有工作区页签')

const publicHtml = fs.readFileSync(path.join(__dirname, '../public/index.html'), 'utf8')
assert(publicHtml.includes('<title>数据库管理软件</title>'), '浏览器标签名应为数据库管理软件')
const productionEnv = fs.readFileSync(path.join(__dirname, '../.env.production'), 'utf8')
assert(productionEnv.includes('VUE_APP_TITLE = 数据库管理软件'), '运行时浏览器标签名应为数据库管理软件')

const selectionState = {
  treeTableSelections: {},
  selectedTreeTableNames: sideBar.methods.selectedTreeTableNames,
  $set(target, key, value) { target[key] = value },
}
sideBar.methods.toggleTreeTableSelection.call(selectionState, { groupKey: 'group-1', label: 'T_A' }, true)
assert.strictEqual(JSON.stringify(selectionState.treeTableSelections['group-1']), JSON.stringify(['T_A']))
sideBar.methods.toggleTreeTableSelection.call(selectionState, { groupKey: 'group-1', label: 'T_B' }, true)
assert.strictEqual(JSON.stringify(selectionState.treeTableSelections['group-1']), JSON.stringify(['T_A', 'T_B']))
sideBar.methods.toggleTreeTableSelection.call(selectionState, { groupKey: 'group-1', label: 'T_A' }, false)
assert.strictEqual(JSON.stringify(selectionState.treeTableSelections['group-1']), JSON.stringify(['T_B']))

console.log('统一多数据源资源树及当前工作区连接管理回归测试通过')
