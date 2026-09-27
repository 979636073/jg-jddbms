const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')

const file = path.join(__dirname, '../src/views/main/workspace/components/reanderSqlExecute/index.vue')
const componentSource = fs.readFileSync(file, 'utf8')
const source = componentSource.match(/<script>([\s\S]*?)<\/script>/)[1]
  .replace(/^import .*;?\s*$/gm, '')
  .replace('export default', 'module.exports =')
const sqlServer = {}
const context = {
  module: { exports: {} },
  renderSearchResult: {}, MonacoEditor: {}, sqlServer, historyServer: {}, tableServer: {},
  downloadFile() {}, copyText() {}, draggable: {},
  window: { crypto: {} }, localStorage: { getItem() { return null }, setItem() {} }
}
vm.runInNewContext(source, context, { filename: file })
const component = context.module.exports

async function executeWith(results) {
  sqlServer.executeSql = () => Promise.resolve({ data: results })
  const state = {
    hasPendingTransaction: false,
    tableData: [],
    sqlLoading: true,
    currentExecutionId: 'test',
    cancelRequested: false,
    buildExecutionSummary() { return '执行汇总' },
    $emit() {}
  }
  component.methods.requestSqlExecution.call(state, {})
  await Promise.resolve()
  await Promise.resolve()
  return state
}

(async () => {
  const selectState = await executeWith([
    { success: true, sqlType: 'UPDATE', allMessage: '执行成功' },
    { success: false, sqlType: 'SELECT', message: '执行失败' },
    { success: true, sqlType: 'SELECT', allMessage: '查询成功' }
  ])
  assert.strictEqual(selectState.Allmessage, false, '存在成功 SELECT 时应直接展示结果集')
  assert.strictEqual(selectState.successCurrentIndex, 1, '结果下标应对应成功结果列表中的首个 SELECT')

  const dmlState = await executeWith([
    { success: true, sqlType: 'UPDATE', allMessage: '执行成功' }
  ])
  assert.strictEqual(dmlState.Allmessage, true, '仅有 DML/DDL 时应继续展示执行汇总')
  assert.strictEqual(dmlState.successCurrentIndex, null)

  console.log('SQL 查询结果默认展示回归测试通过')
})().catch(error => {
  console.error(error)
  process.exitCode = 1
})
