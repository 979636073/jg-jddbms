const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')

const file = path.join(__dirname, '../src/views/main/workspace/components/renderTableEditor/UsedByList.vue')
const script = fs.readFileSync(file, 'utf8').match(/<script>([\s\S]*?)<\/script>/)[1]
  .replace(/^import .*;?\s*$/gm, '').replace('export default', 'module.exports =')
const context = { module: { exports: {} } }
vm.runInNewContext(script, context)
const component = context.module.exports
const state = { ...component.data.call({ relationType: 'table' }), ...component.methods,
  $refs: { graphCanvas: { clientWidth: 900 } }, graphNodes: [{ id: 'parent' }],
  $nextTick(fn) { this.scheduled = fn }, }
assert.ok(!Object.prototype.hasOwnProperty.call(component.data.call({}), 'graph'), 'G6 实例不能被 Vue 深度观察')
let destroyed = 0
let resized = 0
state._relationGraph = { destroy() { destroyed++ }, changeSize(width, height) {
  assert.strictEqual(width, 900); assert.strictEqual(height, 520); resized++
} }
async function run() {
  component.watch.displayMode.call(state, 'tree')
  assert.strictEqual(destroyed, 0, '切回关系树不应销毁 ER 图')
  await state.initGraph()
  assert.strictEqual(resized, 1, '切回图应复用图实例，不重复导入和布局')
  component.watch.tableData.handler.call(state)
  assert.strictEqual(destroyed, 1, '数据变化必须丢弃旧图')
  assert.strictEqual(state._relationGraph, null)
  state.destroyGraph()
  assert.strictEqual(destroyed, 1)
  console.log('关联关系图实例复用、非响应式存储、数据变化失效回归通过')
}
run().catch(error => { console.error(error); process.exitCode = 1 })
