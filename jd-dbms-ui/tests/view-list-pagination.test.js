const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')
const compiler = require('vue-template-compiler')

const file = path.join(__dirname, '../src/views/main/workspace/components/ViewAllView/index.vue')
const descriptor = compiler.parseComponent(fs.readFileSync(file, 'utf8'))
assert.deepStrictEqual(compiler.compile(descriptor.template.content).errors, [])
const context = { module: { exports: {} }, viewServer: {}, uuidv4: () => 'id', downloadFile: () => {}, getToken: () => '' }
vm.runInNewContext(descriptor.script.content.replace(/^import .*;?\s*$/gm, '')
  .replace('export default', 'module.exports ='), context, { filename: file })
const applyViewPage = context.module.exports.methods.applyViewPage
const state = {
  allViews: Array.from({ length: 1000 }, (_, index) => ({ name: `V_${String(index + 1).padStart(4, '0')}` })),
  searchKey: '', queryParams: { pageNo: 1, pageSize: 100 }, tableData: [], tableDataTotal: 0,
}
applyViewPage.call(state)
assert.strictEqual(state.tableData.length, 100)
assert.strictEqual(state.tableDataTotal, 1000)
state.queryParams.pageNo = 10
applyViewPage.call(state)
assert.strictEqual(state.tableData[0].name, 'V_0901')
assert.strictEqual(state.tableData[99].name, 'V_1000')
state.searchKey = 'V_0999'
state.queryParams.pageNo = 1
applyViewPage.call(state)
assert.strictEqual(state.tableDataTotal, 1)
assert.strictEqual(state.tableData[0].name, 'V_0999')
console.log('千视图本地分页与搜索测试通过')
