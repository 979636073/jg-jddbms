const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')

const sideBarFile = path.join(__dirname, '../src/views/main/workspace/SideBar.vue')
const sideBarSource = fs.readFileSync(sideBarFile, 'utf8')
const script = sideBarSource.match(/<script>([\s\S]*?)<\/script>/)[1]
  .replace(/^import .*;?\s*$/gm, '')
  .replace('export default', 'module.exports =')
const context = {
  module: { exports: {} },
  connectionServer: {}, sqlServer: {}, viewServer: {}, tableServer: {}, userServer: {}, tableSpaceServer: {},
  SqlPreview: {}, CreateOption: {}, DataSourceSharingDialog: {}, TableTransferDialog: {},
  SchemaSqlTransferDialog: {}, DmpTransferDialog: {}, addTableSpaceVue: {}, addSchema: {},
  addUser: {}, addTable: {}, addView: {}, ViewImport: {}, draggable: {}, database: {},
  uuidv4() { return 'node-id' }, copyText() {},
}
vm.runInNewContext(script, context, { filename: sideBarFile })
const sideBar = context.module.exports

async function run() {
  let calls = 0
  let finish
  context.tableServer.getTableList = () => {
    calls++
    return new Promise(resolve => { finish = resolve })
  }
  const state = { _tableListRequests: new Map() }
  const params = { dataSourceId: 7, schemaName: 'APP', pageNo: 1, pageSize: 200, requestType: 2 }
  const first = sideBar.methods.getTableListShared.call(state, params)
  const second = sideBar.methods.getTableListShared.call(state, { ...params, isRefreshCache: false })
  assert.strictEqual(calls, 1, '树和总览同时加载时应复用同一请求')
  finish({ success: true, data: { data: [] } })
  await Promise.all([first, second])
  const afterFinish = sideBar.methods.getTableListShared.call(state, params)
  assert.strictEqual(calls, 2, '完成后重新刷新仍应发起请求')
  finish({ success: true, data: { data: [] } })
  await afterFinish

  assert(sideBarSource.includes('skipCount: true'), '打开表首屏不能等待全表计数')
  const overviewSource = fs.readFileSync(path.join(__dirname, '../src/views/main/workspace/components/mainListView/tableViewList.vue'), 'utf8')
  assert(overviewSource.includes('skipCount: true'), '从总览打开表也不能等待全表计数')

  const editorFile = path.join(__dirname, '../src/views/main/workspace/components/renderTableEditor/index.vue')
  const editorScript = fs.readFileSync(editorFile, 'utf8').match(/<script>([\s\S]*?)<\/script>/)[1]
    .replace(/^import .*;?\s*$/gm, '').replace('export default', 'module.exports =')
  const editorContext = { module: { exports: {} }, tableServer: {} }
  for (const name of ['BaseInfo', 'ColumnList', 'IndexList', 'ConstraintList', 'GrantList', 'ScriptInfo', 'TriggerList', 'UsedByList', 'SqlPreview', 'CreateSql', 'renderSearchResult', 'MonacoEditor']) editorContext[name] = {}
  vm.runInNewContext(editorScript, editorContext, { filename: editorFile })
  const editor = editorContext.module.exports
  let metaCalls = 0
  const savedConfig = { uniqueData: { dataSourceId: 7 }, basicForm: {}, databaseSupportField: { columnTypes: ['cached'] } }
  const editorState = {
    queryResultData: savedConfig, currentConfig: null, configChange() {},
    initData() { return editor.methods.initData.call(this) },
    handleClick() { return Promise.resolve() },
    getDatabaseFieldTypeList() { metaCalls++ }
  }
  editor.watch.changeFlag.handler.call(editorState)
  await Promise.resolve()
  assert.strictEqual(metaCalls, 0, '切回表页时应复用已加载的字段类型元数据')
  assert.strictEqual(editorState.databaseSupportField, savedConfig.databaseSupportField)
}

run().catch(error => { console.error(error); process.exitCode = 1 })
