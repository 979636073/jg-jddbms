const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')

function loadComponent(file, context) {
  const source = fs.readFileSync(file, 'utf8')
  const script = source.match(/<script>([\s\S]*?)<\/script>/)[1]
    .replace(/^import .*;?\s*$/gm, '')
    .replace('export default', 'module.exports =')
  vm.runInNewContext(script, context, { filename: file })
  return { component: context.module.exports, source }
}

function assertScriptParses(file) {
  const source = fs.readFileSync(file, 'utf8')
  const script = source.match(/<script>([\s\S]*?)<\/script>/)[1]
    .replace(/^import .*;?\s*$/gm, '')
    .replace('export default', 'return')
  assert.doesNotThrow(() => new Function(script), `${path.basename(file)} 脚本语法应有效`)
}

const navBarFile = path.join(__dirname, '../src/views/main/workspace/NavBar.vue')
let uuidIndex = 0
const navBarContext = {
  module: { exports: {} },
  Hamburger: {}, TaskDrawer: {}, DmpToolManager: {},
  mapGetters() { return {} },
  connectionServer: {}, sqlServer: {}, tableServer: {},
  downloadFile() {}, getToken() { return '' }, getUserProfile() {},
  uuidv4() { uuidIndex += 1; return `page-${uuidIndex}` },
  TASK_STATUS: {},
  listDmpTools() { return Promise.resolve({ data: [] }) },
}
const navBar = loadComponent(navBarFile, navBarContext).component

function createNavState(visitedViews = [], currentData = new Map()) {
  const pushes = []
  const errors = []
  return {
    state: {
      dataInfo: { dataSource: { id: 7, alias: '生产库' } },
      $route: { path: '/workspace/look/current' },
      $store: {
        getters: { visitedViews2: visitedViews },
        state: { workspaceData: { dataCurrentData: currentData } },
      },
      $router: {
        push(target) { pushes.push(target); return Promise.resolve() },
      },
      $message: { error(message) { errors.push(message) } },
    },
    pushes,
    errors,
  }
}

const firstOpen = createNavState()
navBar.methods.sqlDetailTableData.call(firstOpen.state, { schemaName: 'APP', tableName: 'ORDERS' })
assert.strictEqual(firstOpen.pushes.length, 1)
assert.strictEqual(firstOpen.pushes[0].path, '/workspace/look/page-1')
assert.strictEqual(firstOpen.pushes[0].query.dataSourceId, 7)
assert.strictEqual(firstOpen.pushes[0].query.schemaName, 'APP')
assert.strictEqual(firstOpen.pushes[0].query.tableName, 'ORDERS')

const existingView = {
  path: '/workspace/look/existing',
  params: { id: 'existing' },
  query: { dataSourceId: '7', schemaName: 'APP', tableName: 'ORDERS' },
}
const reuseOpen = createNavState([existingView])
navBar.methods.sqlDetailTableData.call(reuseOpen.state, { schemaName: 'APP', tableName: 'ORDERS' })
assert.strictEqual(reuseOpen.pushes.length, 1)
assert.strictEqual(reuseOpen.pushes[0].path, existingView.path, '相同表应聚焦已有页签')

const legacyData = new Map([['legacy', {
  uniqueData: { dataSourceId: 7, schemaName: 'APP', tableName: 'CUSTOMERS' },
}]])
const legacyView = { path: '/workspace/look/legacy', params: { id: 'legacy' }, query: {} }
const legacyOpen = createNavState([legacyView], legacyData)
navBar.methods.sqlDetailTableData.call(legacyOpen.state, { schemaName: 'APP', tableName: 'CUSTOMERS' })
assert.strictEqual(legacyOpen.pushes[0].path, legacyView.path, '旧页签应通过 pageId 数据识别并复用')

const forceOpen = createNavState([existingView])
navBar.methods.sqlDetailTableData.call(forceOpen.state, { schemaName: 'APP', tableName: 'ORDERS', forceNew: true })
assert.strictEqual(forceOpen.pushes[0].path, '/workspace/look/page-2', '右键新建窗口应允许重复打开')

const invalidOpen = createNavState()
navBar.methods.sqlDetailTableData.call(invalidOpen.state, { tableName: 'ORDERS' })
assert.strictEqual(invalidOpen.pushes.length, 0)
assert.strictEqual(invalidOpen.errors.length, 1)

const sideBarFile = path.join(__dirname, '../src/views/main/workspace/SideBar.vue')
const sideBarSource = fs.readFileSync(sideBarFile, 'utf8')
assertScriptParses(sideBarFile)
assert(sideBarSource.includes("@click.stop=\"type == 'tables' ? openTableInWindow(item, index, $event) : handelClickItem(item, index, $event)\""), '普通表整行点击应进入多页签打开流程')
assert(sideBarSource.includes('this.handelClickItem({\n          name: this.$route.query.tableName'), '路由目标表应直接加载，不依赖首批表列表命中')
assert(!sideBarSource.includes('this.filterName = this.$route.query.tableName'), '路由目标表不应写入搜索框或过滤列表')
assert(sideBarSource.includes('searchKey: routeTableName'), '首批列表未命中时应精确取回路由目标表')
assert(sideBarSource.includes('(!routeSchema || routeSchema === this.schema)'), '只应在路由模式与当前模式一致时恢复目标表')
assert(sideBarSource.includes('ref="objectListScrollbar"'), '表列表应提供滚动定位引用')
assert(sideBarSource.includes(':data-object-index="index"'), '表行应提供直接定位标识')
assert(!sideBarSource.includes('item.name == this.$route.query.tableName'), '表列表映射阶段不应重复发起目标表查询')
assert(sideBarSource.includes('forceNew: true'), '右键新建窗口应保留强制重复打开语义')

const sideBarContext = {
  module: { exports: {} },
  connectionServer: {}, sqlServer: {}, viewServer: {}, tableServer: {}, userServer: {}, tableSpaceServer: {},
  SqlPreview: {}, CreateOption: {}, addTableSpaceVue: {}, addUser: {}, addTable: {}, addView: {}, ViewImport: {}, draggable: {},
  database: {}, uuidv4() { return 'side-page' }, copyText() {},
}
const sideBar = loadComponent(sideBarFile, sideBarContext).component
const scrollCalls = []
const activeElement = { offsetTop: 520, offsetHeight: 25 }
const scrollState = {
  activeClass: 8,
  $refs: {
    objectListScrollbar: {
      wrap: {
        clientHeight: 225,
        scrollTop: 0,
        querySelector(selector) {
          scrollCalls.push(selector)
          return activeElement
        },
      },
    },
  },
  $nextTick(callback) { callback() },
}
sideBar.methods.scrollToActiveTable.call(scrollState)
assert.deepStrictEqual(scrollCalls, ['[data-object-index="8"]'])
assert.strictEqual(scrollState.$refs.objectListScrollbar.wrap.scrollTop, 420, '目标表应滚动到列表可见区域中央')

const blankCalls = { replace: [], load: 0, emit: 0 }
const blankState = {
  isQuery: false,
  pageId: 'blank',
  schema: 'APP',
  activeClass: -1,
  nameTbale: '',
  dataInfo: { dataSource: { id: 7, alias: '生产库' } },
  $route: { path: '/workspace/look/blank', query: {} },
  $store: { state: { workspaceData: { dataCurrentData: new Map([['blank', { uniqueData: false }]]) } } },
  $router: { replace(target) { blankCalls.replace.push(target) } },
  $EventBus: { $emit() { blankCalls.emit += 1 } },
  handelClickItem() { blankCalls.load += 1 },
}
sideBar.methods.openTableInWindow.call(blankState, { name: 'ORDERS' }, 2, {})
assert.strictEqual(blankCalls.replace.length, 1, '空白窗口应直接承载首次打开的表')
assert.strictEqual(blankCalls.load, 1)
assert.strictEqual(blankCalls.emit, 0)

const populatedCalls = []
const populatedState = {
  ...blankState,
  activeClass: 2,
  nameTbale: 'CUSTOMERS',
  $route: { path: '/workspace/look/current', query: { tableName: 'CUSTOMERS' } },
  $EventBus: { $emit(name, payload) { populatedCalls.push({ name, payload }) } },
}
sideBar.methods.openTableInWindow.call(populatedState, { name: 'ORDERS' }, 2, {})
assert.strictEqual(populatedCalls.length, 1, '已显示表的窗口点击不同表时应请求新页签')
assert.strictEqual(populatedCalls[0].payload.tableName, 'ORDERS')

const switchedSchemaCalls = { replace: [], load: 0, emit: 0 }
const switchedSchemaState = {
  ...blankState,
  schema: 'ARCHIVE',
  $route: { path: '/workspace/look/current', query: { schemaName: 'APP', tableName: 'CUSTOMERS' } },
  $store: { state: { workspaceData: { dataCurrentData: new Map([['blank', { uniqueData: { schemaName: 'APP', tableName: 'CUSTOMERS' } }]]) } } },
  $router: { replace(target) { switchedSchemaCalls.replace.push(target) } },
  $EventBus: { $emit() { switchedSchemaCalls.emit += 1 } },
  handelClickItem() { switchedSchemaCalls.load += 1 },
}
sideBar.methods.openTableInWindow.call(switchedSchemaState, { name: 'ORDERS' }, 4, {})
assert.strictEqual(switchedSchemaCalls.replace.length, 1, '切换模式且未选表时应复用当前页签')
assert.strictEqual(switchedSchemaCalls.replace[0].query.schemaName, 'ARCHIVE')
assert.strictEqual(switchedSchemaCalls.load, 1)
assert.strictEqual(switchedSchemaCalls.emit, 0)

const tagsFile = path.join(__dirname, '../src/components/JdTagsView/index.vue')
const tagsSource = fs.readFileSync(tagsFile, 'utf8')
assertScriptParses(tagsFile)
assert(tagsSource.includes(':title="getViewTooltip(tag)"'), '表页签应提供完整对象路径提示')
assert(tagsSource.includes('dirtyPageIds[pageId]'), '关闭存在待保存数据的页签前应提示')
assert(tagsSource.includes('`${resolvedTitle} *`'), '存在待保存数据时页签标题应显示状态标记')

assertScriptParses(path.join(__dirname, '../src/views/main/workspace/components/renderSearchResult/index.vue'))

console.log('多表独立页签、重复打开规则及关闭保护回归测试通过')
