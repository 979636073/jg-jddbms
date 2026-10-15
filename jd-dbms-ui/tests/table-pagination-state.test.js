const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')
const compiler = require('vue-template-compiler')
const file = path.join(__dirname, '../src/views/main/workspace/components/renderSearchResult/index.vue')
const source = compiler.parseComponent(fs.readFileSync(file, 'utf8'))
assert.deepStrictEqual(compiler.compile(source.template.content).errors, [])
const context = { module: { exports: {} }, sqlServer: {}, getToken: () => '', console: { log() {} } }
for (const name of ['SqlPreview', 'MonacoEditor', 'ScreeningResult', 'Filedialog', 'ViewGrantPanel', 'importDataDialog']) context[name] = {}
vm.runInNewContext(source.script.content.replace(/^import .*;?\s*$/gm, '').replace('export default', 'module.exports ='), context)
const component = context.module.exports
assert.strictEqual(component.data().valuePanelVisible, false, '表数据字段值面板默认收起')
const Vue = require('vue')
const summaryTemplate = source.template.content.match(/<div class="table_bottom_info">[\s\S]*?<\/div>/)[0]
const summaryRender = compiler.compileToFunctions(summaryTemplate)
const vnodeText = node => node.text || (node.children || []).map(vnodeText).join('')
const summary = new Vue({
  data: () => ({ queryResultData: { description: '执行成功', duration: 44, hasNextPage: true }, total: 2000 }),
  ...summaryRender
})
const summaryText = vnodeText(summary._render()).replace(/\s+/g, ' ')
assert(summaryText.includes('【结果】 执行成功') && summaryText.includes('【耗时】 44ms'))
assert(!summaryText.includes('【查询行数】') && !summaryText.includes('【提示】'),
  '底部状态栏不再重复显示查询行数和翻页提示')
summary.$destroy()
const pending = []
context.sqlServer.executeSql = params => new Promise(resolve => pending.push({ params, resolve }))
const errors = []
const state = {
  ...component.data(), ...component.methods,
  queryResultData: { params: { sql: 'SELECT * FROM T', skipCount: true }, pageNo: 1, pageSize: 100 },
  $forceUpdate() {}, $message: { error: text => errors.push(text) }
}
const response = (pageNo, extra = {}) => ({ success: true, data: [{ success: true, pageNo, pageSize: 100, fuzzyTotal: pageNo * 100 + 1,
  totalExact: false, hasNextPage: true, dataList: [[String(pageNo)]], duration: 1, ...extra }] })
async function run() {
  const second = state.handleCurrentChange(2)
  assert.strictEqual(pending[0].params.refresh, false, '普通翻页复用编辑元数据缓存')
  const third = state.handleCurrentChange(3)
  pending[1].resolve(response(3)); await third
  pending[0].resolve(response(2)); await second
  assert.strictEqual(state.queryResultData.pageNo, 3, '迟到的第2页不能覆盖第3页')
  assert.strictEqual(state.queryParams.pageNum, 3)
  const sorted = state.sortChange({ prop: 'ID', order: 'descending' }, false)
  assert.strictEqual(pending[2].params.pageNo, 1, '翻页后重新排序须回到第一页')
  pending[2].resolve(response(1)); await sorted
  const count = state.advancedQuery(false, true)
  assert.strictEqual(pending[3].params.skipCount, false)
  pending[3].resolve(response(1, { totalExact: true, fuzzyTotal: 10001 })); await count
  assert.strictEqual(state.total, 10001)
  assert.strictEqual(state.queryResultData.params.skipCount, false)
  const failed = state.handleCurrentChange(2)
  pending[4].resolve({ success: false, errorMessage: '连接中断' }); await failed
  assert.strictEqual(state.queryParams.pageNum, 1, '失败时应保留已显示数据页码')
  assert.strictEqual(state.isDialogLoading, false)
  assert.deepStrictEqual(errors, ['连接中断'])
  state.queryParams.pageNum = 4
  state.refresh()
  assert.strictEqual(pending[5].params.pageNo, 1, '刷新必须在发送请求前重置到第一页')
  assert.strictEqual(pending[5].params.refresh, true, '手动刷新必须重新获取编辑元数据')
  pending[5].resolve(response(1))
  await Promise.resolve()
  assert(source.template.content.includes("'sizes, prev, slot, next'"), '未知总数不能展示假总页数或末页跳转')
  console.log('表数据分页：乱序响应、排序重置、精确计数与失败回退通过')
}
run().catch(error => { console.error(error); process.exitCode = 1 })
