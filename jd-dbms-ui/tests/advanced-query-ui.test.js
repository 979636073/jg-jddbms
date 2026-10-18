const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')
const compiler = require('./helpers/vue-compiler')

const file = path.join(__dirname, '../src/views/main/workspace/components/renderSearchResult/index.vue')
const componentSource = compiler.parseComponent(fs.readFileSync(file, 'utf8'))
const source = componentSource.script.content
  .replace(/^import .*;?\s*$/gm, '')
  .replace('export default', 'module.exports =')
const sqlServer = {}
const context = {
  module: { exports: {} },
  sqlServer, tableServer: {}, viewServer: {},
  SqlPreview: {}, MonacoEditor: {}, ScreeningResult: {}, Filedialog: {}, ViewGrantPanel: {}, importDataDialog: {},
  downloadFile() {}, copyText() {}, getToken() { return '' }, setTimeout
}
vm.runInNewContext(source, context, { filename: file })
const component = context.module.exports

const compiled = compiler.compile(componentSource.template.content)
assert.deepStrictEqual(compiled.errors, [])
assert(componentSource.template.content.includes('@click="clearAdvancedQuery"'))
assert(componentSource.template.content.includes('@click="closeAdvancedQuery"'))
assert(componentSource.template.content.includes('@click="confirmAdvancedQuery"'))
assert(!componentSource.template.content.includes('style="margin-bottom: -7px"'), '升降序箭头点击区域不得重叠')

const form = { columnTitle: 'ID', sql: '=', contentTest: '211' }
const clearState = {
  isColor: true,
  searchSql: '"ID" = \'211\'',
  pageSql: '"ID" = \'211\'',
  filterSql: '"ID" = \'211\'',
  searchDialogForm: form
}
component.methods.clearAdvancedQuery.call(clearState)
assert.strictEqual(clearState.isColor, false)
assert.strictEqual(clearState.searchSql, '')
assert.strictEqual(clearState.pageSql, '')
assert.deepStrictEqual(form, { columnTitle: '', sql: '', contentTest: '' })

const closeState = { searchDialogVisible: true }
component.methods.closeAdvancedQuery.call(closeState)
assert.strictEqual(closeState.searchDialogVisible, false)

let advancedQueryCalls = 0
const confirmState = {
  searchDialogVisible: true,
  isColor: false,
  queryParams: { pageNum: 3 },
  advancedQuery() { advancedQueryCalls += 1 }
}
component.methods.confirmAdvancedQuery.call(confirmState)
assert.strictEqual(confirmState.searchDialogVisible, false)
assert.strictEqual(confirmState.isColor, true)
assert.strictEqual(confirmState.queryParams.pageNum, 1)
assert.strictEqual(advancedQueryCalls, 1)

const warnings = []
const invalidConditionState = {
  searchDialogForm: { columnTitle: '', sql: '', contentTest: '' },
  searchSql: '',
  pageSql: '',
  filterSql: '',
  $message: { warning(message) { warnings.push(message) } }
}
component.methods.addSearchForm.call(invalidConditionState, 'And')
assert.deepStrictEqual(warnings, ['请选择表头字段'])
assert.strictEqual(invalidConditionState.searchSql, '', '缺少字段时不得生成无效条件')
invalidConditionState.searchDialogForm.columnTitle = 'ID'
component.methods.addSearchForm.call(invalidConditionState, 'And')
assert.deepStrictEqual(warnings, ['请选择表头字段', '请选择规则'])
invalidConditionState.searchDialogForm.sql = '='
component.methods.addSearchForm.call(invalidConditionState, 'And')
assert.deepStrictEqual(warnings, ['请选择表头字段', '请选择规则', '请输入内容'])
invalidConditionState.searchDialogForm.sql = 'IS NULL'
component.methods.addSearchForm.call(invalidConditionState, 'And')
assert.strictEqual(invalidConditionState.searchSql, '"ID" IS NULL', 'IS NULL 不需要输入比较值')

let request
sqlServer.viewTable = params => {
  request = params
  return { then() {} }
}
const queryState = {
  isDialogLoading: false,
  isCreate: true,
  isColor: true,
  pageSql: '"ID" = \'211\'',
  searchSql: '"ID" = \'211\'',
  filterSql: '"ID" = \'211\'',
  searchDialogForm: { columnTitle: 'ID', sql: '=', contentTest: '211' },
  queryParams: { pageNum: 1, pageSize: 100 },
  queryResultData: { params: { tableName: 'AA_FOREGN_TABLE', sql: 'SELECT t.*, t.rowid FROM "JDDBMS"."AA_FOREGN_TABLE" t' } },
  sortObj: {}
}
component.methods.advancedQuery.call(queryState)
assert.strictEqual(request.filterSql, '"ID" = \'211\'', '表格筛选应通过只读表查询传递条件')
assert.strictEqual(
  request.sql,
  'SELECT t.*, t.rowid FROM "JDDBMS"."AA_FOREGN_TABLE" t WHERE "ID" = \'211\'',
  '高级查询条件前必须保留空格'
)

queryState.sortObj = { order: 'ascending', prop: 'ID' }
component.methods.advancedQuery.call(queryState)
assert.strictEqual(request.isAsc, true, '升序箭头必须发送升序参数')
queryState.sortObj = { order: 'descending', prop: 'ID' }
component.methods.advancedQuery.call(queryState)
assert.strictEqual(request.isAsc, false, '降序箭头必须发送降序参数')

console.log('高级查询回归测试通过')
