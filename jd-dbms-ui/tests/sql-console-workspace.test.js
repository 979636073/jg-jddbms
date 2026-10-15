const assert = require('assert')
const fs = require('fs')
const vm = require('vm')
const compiler = require('vue-template-compiler')
const path = require('path')
const root = path.join(__dirname, '../src')
function load(relative, mocks) {
  const descriptor = compiler.parseComponent(fs.readFileSync(path.join(root, relative), 'utf8'))
  assert.deepStrictEqual(compiler.compile(descriptor.template.content).errors, [])
  const context = { module: { exports: {} }, ...mocks }
  vm.runInNewContext(descriptor.script.content.replace(/^import .*;?\s*$/gm, '').replace('export default', 'module.exports ='), context)
  return context.module.exports
}
const scriptContext = {}
vm.runInNewContext(fs.readFileSync(path.join(root, 'utils/sqlScript.js'), 'utf8').replace(/export /g, ''), scriptContext)
const { splitSqlStatements, sqlAtCursor } = scriptContext
const resultContext = {}
vm.runInNewContext(fs.readFileSync(path.join(root, 'utils/sqlResult.js'), 'utf8').replace(/export /g, ''), resultContext)
const script = "SELECT 'a;b' FROM dual;\nDECLARE n NUMBER := 1; BEGIN IF n=1 THEN NULL; END IF; END;\n/\nSELECT 2 FROM dual;"
assert.strictEqual(splitSqlStatements(script).length, 3)
assert.strictEqual(sqlAtCursor(script, 4), "SELECT 'a;b' FROM dual")
assert(sqlAtCursor(script, script.indexOf('IF')).startsWith('DECLARE'))
assert.strictEqual(sqlAtCursor(script, script.length), 'SELECT 2 FROM dual')

async function main() {
  let keyHandler, keyCapture;
  const requests = []
  let requestFailure = false
  const results = [{ success: true, sqlType: 'SELECT', originalSql: 'SELECT 1 FROM dual', sessionId: 'query', sign: false },
    { success: true, originalSql: 'INSERT INTO T VALUES(1)', sessionId: 'transaction', sign: true }]
  const component = load('views/main/workspace/components/reanderSqlExecute/index.vue', {
    SqlResultTable: {}, prepareSqlResult: resultContext.prepareSqlResult, MonacoEditor: {}, draggable: {}, tableServer: {}, historyServer: {}, connectionServer: {},
    sqlServer: { executeSql: async params => { requests.push(params); if (requestFailure) throw new Error('Network Error'); return { success: true, data: results } } },
    localStorage: { getItem: () => '[]', setItem() {} }, window: { crypto: { randomUUID: () => 'execution-id' } },
    document: { addEventListener(event, handler, capture) { keyHandler = handler; keyCapture = capture },
      removeEventListener(event, handler, capture) { assert.strictEqual(capture, true) } },
  })
  const state = { ...component.data(), ...component.methods,
    currentConfig: { id: 77, uniqueData: { dataSourceId: 8, schemaName: 'APP', databaseName: 'orcl', databaseType: 'ORACLE' } },
    $refs: { MonacoEditor: { getSelectValue: () => '', getCurrentStatement: () => 'SELECT 1 FROM dual', getValue: () => 'SELECT 1 FROM dual; INSERT INTO T VALUES(1);' } },
    $emit(event, key, value) { this.currentConfig[key] = value },
  }
  let executions = 0;
  const keyboardState = { ...state, loadRecentSqlScripts() {}, executeSQL() { executions++ }, _inactive: true };
  component.mounted.call(keyboardState);
  const key = { key: 'Enter', ctrlKey: true, preventDefault() {}, stopPropagation() {} };
  keyHandler(key);
  assert.strictEqual(executions, 0, '隐藏窗口不能响应执行快捷键');
  keyboardState._inactive = false;
  keyHandler(key);
  assert.strictEqual(executions, 1);
  assert.strictEqual(keyCapture, true, 'Ctrl+Enter 必须在 Monaco 吞掉冒泡事件前处理');
  component.beforeDestroy.call(keyboardState);
  assert.strictEqual(component.computed.hasPendingTransaction.call({ disableCommit: true, disableRollBack: true, successList: [] }), true,
    '清除查询结果不能抹掉待提交事务的保护状态')
  component.methods.executeSQL.call(state, 'run')
  await new Promise(resolve => setImmediate(resolve))
  assert.strictEqual(requests[0].sql, 'SELECT 1 FROM dual')
  assert.strictEqual(requests[0].databaseName, 'orcl')
  assert.strictEqual(requests[0].skipCount, false, '控制台必须默认精确计数')
  assert.strictEqual(state.sessionId, 'transaction', '批量结果必须取实际最后的会话')
  assert.strictEqual(state.disableCommit, true)
  assert.strictEqual(results[0].params.sql, results[0].originalSql, '分页不能重放整个批次')
  assert.strictEqual(results[0].params.sessionId, 'query', '结果翻页必须保留原事务会话')
  assert.strictEqual(results[1].params.sql, results[1].originalSql)
  state.sqlLoading = false
  component.methods.executeSQL.call(state, 'run', false, true)
  await new Promise(resolve => setImmediate(resolve))
  assert(requests[1].sql.includes('INSERT'))
  state.successList = results
  state.successCurrentIndex = 0
  const paged = { ...results[0], pageNo: 2 }
  component.methods.updateResult.call(state, paged)
  assert.strictEqual(state.currentConfig.sqlData[0].pageNo, 2)
  assert.strictEqual(state.currentConfig.sqlData[1], results[1], '翻页必须保留其他结果')
  state.searchSql = 'ID > 3'
  state.closeDialog = () => {}
  state.$message = { error: message => { throw new Error(message) } }
  await state.saveFilterData()
  assert.strictEqual(requests[2].skipCount, false, '过滤后的结果也要精确计数')
  assert.strictEqual(requests[2].sessionId, 'query')
  assert.strictEqual(requests[2].databaseName, 'orcl')
  assert.strictEqual(state.currentConfig.sqlData[1], results[1], '过滤一个结果集不能丢掉批次中的其他结果')
  requestFailure = true
  state.sqlLoading = true
  component.methods.requestSqlExecution.call(state, requests[1])
  await new Promise(resolve => setImmediate(resolve))
  assert.strictEqual(state.lastOperationStatus, '请求失败，执行状态未确认', '请求异常不能恢复成就绪')
  assert.strictEqual(state.resultTab, 'error')
  assert(state.currentConfig.sqlData[0].message.includes('Network Error'))
  assert.strictEqual(state.sessionId, 'transaction', '请求异常不能清除已知事务会话')
  assert.strictEqual(state.disableCommit, true)

  const apiRequests = []
  const apiContext = { module: { exports: {} }, request: options => { apiRequests.push(options); return Promise.resolve() }, splitSqlStatements }
  vm.runInNewContext(fs.readFileSync(path.join(root, 'api/main/sql.js'), 'utf8').replace(/^import .*\s*$/gm, '').replace('export default', 'module.exports ='), apiContext)
  apiContext.module.exports.executeSql({ sql: 'SELECT 1 FROM dual; SELECT 2 FROM dual', queryTimeoutSeconds: 120 })
  assert.strictEqual(apiRequests[0].timeout, 255000, '批量传输超时应覆盖每条 SQL 的超时与返回余量')
  apiContext.module.exports.executeSql({ sql: 'SELECT 1 FROM dual' })
  assert.strictEqual(apiRequests[1].timeout, 60000, '未指定 SQL 超时的原有调用保持不变')
  const originalSql = "SELECT '&gt;' FROM T WHERE ID >= 1 AND ID <> 2"
  apiContext.module.exports.executeSql({ sql: originalSql })
  assert.strictEqual(apiRequests[2].data.sql, originalSql, 'SQL 请求不得把比较运算符转换为 HTML 实体')

  let provider, tokenizer
  const metadataRequests = []
  const editorComponent = load('components/MonacoEditor/index.vue', {
    monaco: { languages: { register() {}, setLanguageConfiguration() {}, setMonarchTokensProvider(language, item) { tokenizer = item }, registerCompletionItemProvider(language, item) { provider = item; return { dispose() {} } }, CompletionItemKind: {} } }, conf: {},
    language: { keywords: ['SELECT'], operators: [], builtinFunctions: [], builtinVariables: [], tokenizer: { strings: [] } }, sqlAtCursor,
    tableServer: { getTableList: async params => { metadataRequests.push(params); return { success: true, data: { data: [{ name: 'T' }] } } },
      getAllFieldByTable: async params => { metadataRequests.push(params); return { success: true, data: [{ name: 'REAL_COLUMN' }] } } },
  })
  assert(tokenizer.tokenizer.strings[0][0].test("q'["), 'q 引用需要独立着色，不能污染后续语句')
  assert(tokenizer.tokenizer.oracleBracketString[0][0].test("]'"))
  const model = { getValue: () => 'SELECT t. FROM t t', getOffsetAt: () => 9, getWordUntilPosition: () => ({ startColumn: 10, endColumn: 10 }) }
  const editorState = { ...editorComponent.methods, editor: { getModel: () => model }, queryContext: { dataSourceId: 8, schemaName: 'APP', databaseType: 'ORACLE' },
    columnList: [], databaseList: ['APP'], tableCache: {}, columnCache: {} }
  editorState.initSuggestions()
  assert.strictEqual((await provider.provideCompletionItems({}, {}, {}, {})).suggestions.length, 0, '不能为其他窗口的模型补齐')
  const completed = await provider.provideCompletionItems(model, { lineNumber: 1 }, {}, { isCancellationRequested: false })
  assert(completed.suggestions.some(item => item.label === 'REAL_COLUMN'), '别名后必须补齐真实字段')
  assert.strictEqual(metadataRequests[0].tableName, 'T', 'Oracle 未加引号的小写表名应按数据库规则折叠')
  await editorState.getParamSuggest('APP', 'T')
  assert.strictEqual(metadataRequests.length, 1, '同一上下文应缓存元数据')
  editorState.queryContext = { dataSourceId: 9, schemaName: 'APP' }
  await editorState.getParamSuggest('APP', 'T')
  assert.strictEqual(metadataRequests[1].dataSourceId, 9, '不同连接不能串用元数据缓存')

  const sideBar = load('views/main/workspace/SideBar.vue', { connectionServer: {}, sqlServer: {}, viewServer: {}, tableServer: {}, userServer: {}, tableSpaceServer: {},
    SqlPreview: {}, CreateOption: {}, DataSourceSharingDialog: {}, TableTransferDialog: {}, SchemaSqlTransferDialog: {}, DmpTransferDialog: {}, addTableSpaceVue: {}, addSchema: {}, addUser: {}, addTable: {}, addView: {}, ViewImport: {}, draggable: {}, database: {} })
  assert.strictEqual(sideBar.activated, undefined, '共享树不能随右侧窗口激活重置状态')
  console.log('SQL 控制台与窗口缓存回归通过')
}
main().catch(error => { console.error(error); process.exitCode = 1 })
