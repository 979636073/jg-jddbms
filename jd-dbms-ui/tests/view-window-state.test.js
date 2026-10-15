const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')
const compiler = require('vue-template-compiler')
const file = path.join(__dirname, '../src/views/main/workspace/components/renderViewEditor/index.vue')
const source = compiler.parseComponent(fs.readFileSync(file, 'utf8'))
const context = { module: { exports: {} }, viewServer: {}, tableServer: {} }
for (const name of ['SqlPreview', 'MonacoEditor', 'MonacoSqlEditor', 'ColumnViewList', 'DataInfoView', 'DespUses', 'DespUsedBy', 'ViewGrantPanel', 'renderSearchResult']) context[name] = {}
vm.runInNewContext(source.script.content.replace(/^import .*;?\s*$/gm, '').replace('export default', 'module.exports ='), context)
const component = context.module.exports
const stateFor = config => ({
  ...component.data(), ...component.methods, queryResultData: config,
  $refs: {}, $nextTick() {}, $message: { error: text => { throw Error(text) } }
})
async function run() {
  const containerSource = compiler.parseComponent(fs.readFileSync(path.join(__dirname, '../src/views/main/workspace/ContainerBar.vue'), 'utf8'))
  const containerContext = { module: { exports: {} }, TableViewList: {}, ViewList: {}, UserList: {}, TableSpace: {} }
  vm.runInNewContext(containerSource.script.content.replace(/^import .*;?\s*$/gm, '').replace('export default', 'module.exports ='), containerContext)
  const container = containerContext.module.exports
  const route = { params: { id: 'view-page' } }
  const ownView = { type: 'editView', title: 'QA_MV' }
  const otherSql = { type: 'createSql', title: '查询' }
  const state = { ...container.data.call({ $route: route }), $route: route,
    dataCurrentDataMap: new Map([['view-page', ownView], ['sql-page', otherSql]]) }
  Object.defineProperty(state, 'pageId', { get: () => container.computed.pageId.call(state) })
  container.mounted.call(state)
  route.params.id = 'sql-page'
  assert.strictEqual(state.pageId, 'view-page', '已缓存窗口不能跟随全局路由切换归属')
  container.watch.changeFlag.call(state)
  assert.strictEqual(state.currentConfig, ownView, '其他窗口更新不能把隐藏视图替换为 SQL 控制台')
  const refreshedView = { ...ownView, title: '刷新后的视图' }
  state.dataCurrentDataMap.set('view-page', refreshedView)
  container.watch.changeFlag.call(state)
  assert.strictEqual(state.currentConfig, refreshedView, '所属窗口主动刷新仍应生效')
  let refreshCalls = 0
  context.viewServer.refreshMaterialized = async () => ({ success: true })
  const materialized = stateFor({})
  materialized.materializedView = true
  materialized.$message.success = () => {}
  materialized.initData = async force => assert.strictEqual(force, true)
  materialized.$refs.renderSearchResult = { refresh: () => { refreshCalls++ } }
  await materialized.refreshMaterializedView()
  assert.strictEqual(refreshCalls, 1, '顶部物化视图刷新后必须重新加载表格数据')
  assert(source.template.content.includes('ref="renderSearchResult"'), '表格引用必须绑定到真实组件')
  console.log('窗口配置归属隔离与物化视图顶部数据刷新回归通过')
}
run().catch(error => { console.error(error); process.exitCode = 1 })
