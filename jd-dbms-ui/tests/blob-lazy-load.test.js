const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')
const compiler = require('vue-template-compiler')

const file = path.join(__dirname, '../src/views/main/workspace/components/renderSearchResult/index.vue')
const component = compiler.parseComponent(fs.readFileSync(file, 'utf8'))
const requests = []
const context = {
  module: { exports: {} }, FormData,
  sqlServer: {}, viewServer: {},
  tableServer: {
    async getBlobData(params) { requests.push(params); return { success: true, data: 'AQID' } },
    async getTableDataBlob() { return { success: true, data: '' } }
  },
  SqlPreview: {}, MonacoEditor: {}, ScreeningResult: {}, Filedialog: {},
  ViewGrantPanel: {}, importDataDialog: {}
}
vm.runInNewContext(component.script.content.replace(/^import .*;?\s*$/gm, '')
  .replace('export default', 'module.exports ='), context)

async function main() {
  const methods = context.module.exports.methods
  const state = {
    queryResultData: {
      params: { dataSourceId: 42, schemaName: 'QA' },
      originalSql: 'SELECT ID, PAYLOAD AS DATA FROM T ORDER BY ID',
      headerList: [{ name: '行号' }, { name: 'ID' }, { name: 'DATA' }]
    },
    sqlInfo: [], $refs: { fileRef: { fromData: {} } },
    $message: { error(message) { throw new Error(message) } }
  }
  const row = { '行号': '202', ID: '202', DATA: 'A' }
  assert.strictEqual(requests.length, 0)
  await methods.openBinaryViewer.call(state, row, { name: 'DATA' })
  assert.strictEqual(requests.length, 1)
  assert.strictEqual(requests[0].dataSourceId, 42)
  assert.strictEqual(requests[0].sql, state.queryResultData.originalSql)
  assert.strictEqual(requests[0].rowIndex, 201)
  assert.strictEqual(requests[0].columnIndex, 2)
  assert.strictEqual(state.$refs.fileRef.blobStr, 'AQID')
  assert.strictEqual(state.$refs.fileRef.dialogVisible, true)
  assert.strictEqual(state.binaryPreviewLoading, false)

  state.queryResultData.uniqueData = { dataSourceId: 42, schemaName: 'QA', tableName: 'T' }
  await methods.openBinaryViewer.call(state, { ...row, ROWID: '123' }, { name: 'DATA' })
  assert.strictEqual(requests[1].rowId, '123')
  assert.strictEqual(requests[1].tableName, 'T')

  const consoleFile = path.join(__dirname, '../src/views/main/workspace/components/reanderSqlExecute/SqlResultTable.vue')
  const consoleComponent = compiler.parseComponent(fs.readFileSync(consoleFile, 'utf8'))
  const consoleContext = { ...context, module: { exports: {} }, FileDialog: {}, prepareSqlResult: value => value, copyText() {} }
  vm.runInNewContext(consoleComponent.script.content.replace(/^import .*;?\s*$/gm, '')
    .replace('export default', 'module.exports ='), consoleContext)
  const consoleMethods = consoleContext.module.exports.methods
  const consoleState = { ...state, ...consoleMethods, binaryLoading: false,
    rows: [['202', '202', 'A']], columns: state.queryResultData.headerList,
    queryResultData: { ...state.queryResultData, uniqueData: undefined } }
  assert.strictEqual(requests.length, 2, '渲染控制台不能读取 BLOB')
  await consoleState.showBinary(0, 2)
  assert.strictEqual(requests[2].rowIndex, 201)
  assert.strictEqual(requests[2].columnIndex, 2)
  assert.strictEqual(requests[2].sql, state.queryResultData.originalSql)
  assert.strictEqual(consoleState.$refs.fileRef.dialogVisible, true)
  consoleState.rows = [['203', '203', null]]
  await consoleState.showBinary(0, 2)
  assert.strictEqual(requests.length, 3, 'NULL 不触发文件读取')
  console.log('BLOB 控制台无 ROWID、分页定位与表数据按需读取回归通过')
}

main().catch(error => { console.error(error); process.exitCode = 1 })
