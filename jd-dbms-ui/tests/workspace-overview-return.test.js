const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')

const file = path.join(__dirname, '../src/views/main/workspace/content.vue')
const script = fs.readFileSync(file, 'utf8').match(/<script>([\s\S]*?)<\/script>/)[1]
  .replace(/^import .*;?\s*$/gm, '')
  .replace('export default', 'module.exports =')
const context = { module: { exports: {} }, ContainerBar: {}, tableServer: {} }
vm.runInNewContext(script, context, { filename: file })

const sources = [{ id: 12, alias: '源库', type: 'DM' }, { id: 13, alias: '第二窗口', type: 'DM' }]
let request
context.tableServer.getTableList = async params => {
  request = params
  return { success: true, data: { total: 1, data: [{ name: 'USERS', tableDetails: { schema: 'SYSDBA', numRows: 8 } }] } }
}
const messages = []
const page = {
  $route: { query: { dataSourceId: 13, schemaName: 'OTHER' } },
  $store: { state: { workspace: { dataSourceList: sources } } },
  $message: { error: message => messages.push(message) },
  dataInfo: { dataSource: sources[1], userName: 'OTHER' },
  typeView: '', schema: '', detailDataSource: [], detailDataSourceTotal: 0,
  tableFilterName: 'old', overviewLoading: false, overviewRequestId: 0
}

;(async () => {
  await context.module.exports.methods.restoreOverview.call(page, { dataSourceId: 12, schemaName: 'SYSDBA' })
  assert.strictEqual(request.dataSourceId, 12, '返回时应使用原窗口数据源')
  assert.strictEqual(request.schemaName, 'SYSDBA', '返回时应使用原窗口模式')
  assert.strictEqual(page.typeView, 'tables')
  assert.strictEqual(page.dataInfo.dataSource.id, 12)
  assert.strictEqual(page.detailDataSource[0].name, 'USERS')
  assert.strictEqual(page.detailDataSourceTotal, 1)
  assert.strictEqual(page.overviewLoading, false)
  assert.deepStrictEqual(messages, [])
  console.log('第二窗口返回模式总览回归测试通过')
})().catch(error => { console.error(error); process.exitCode = 1 })
