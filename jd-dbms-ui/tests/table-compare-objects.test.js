const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')
const root = path.join(__dirname, '..')
const context = { module: { exports: {} } }
vm.runInNewContext(fs.readFileSync(path.join(root, 'src/views/main/tableCompare/objects.js'), 'utf8')
  .replace(/export function /g, 'function ') + '\nmodule.exports = { buildObjectRows, applyObjectDifferences }', context)
const { buildObjectRows, applyObjectDifferences } = context.module.exports
const item = (kind, table, name, fingerprint, attributes = {}) => ({ kind, tableName: table,
  objectName: name, name: `${table}.${name}`, fingerprint, attributes })
const inventory = objects => ({ objects, errors: {}, truncated: [] })
const left = inventory([item('INDEX', 'A.B', 'IX', 'a', { 'COLUMN_NAME[1]': 'A', 'DESCEND[1]': 'ASC' })])
const right = inventory([item('INDEX', 'TARGET', 'IX', 'b', { 'COLUMN_NAME[1]': 'A', 'DESCEND[1]': 'DESC' })])

const previous = fs.readFileSync(path.join(root, 'src/views/main/tableCompare/index.vue'), 'utf8')
const previousContext = { module: { exports: {} }, buildObjectRows, applyObjectDifferences, connectionServer: {}, tableServer: {
  getSchemaInventory: request => Promise.resolve({ success: true, data: request.tableName === 'A.B' ? left : right })
}, FieldCompareResult: {}, WholeDatabaseResult: {}, SchemaObjectResult: {} }
vm.runInNewContext(previous.match(/<script>([\s\S]*?)<\/script>/)[1]
  .replace(/^import .*\s*$/gm, '').replace('export default', 'module.exports ='), previousContext)

;(async () => {
  const oldState = { databaseRows: [], compareMode: 'table', source: { tableName: 'A.B' }, target: { tableName: 'TARGET' },
    buildRequest(side) { return this[side] } }
  await previousContext.module.exports.methods.compareObjects.call(oldState)
  assert.strictEqual(oldState.objectRows.length, 1, '组件应将不同表名的同名对象合并比较')
  const rows = buildObjectRows(left, right, 'table', 'A.B', 'TARGET')
  assert.strictEqual(rows.length, 1)
  assert.strictEqual(rows[0].status, 'changed')
  assert.strictEqual(rows[0].changeDetails[0].label, '排序方向[1]')
  assert.strictEqual(rows[0].changeDetails[0].target, 'DESC')
  assert.strictEqual(buildObjectRows(left, left, 'table', 'A.B', 'A.B')[0].status, 'same')
  const generated = buildObjectRows(inventory([{ ...item('INDEX', 'T', 'SYS_1', 'same'), generated: true }]),
    inventory([{ ...item('INDEX', 'U', 'SYS_2', 'same'), generated: true }]), 'table', 'T', 'U')
  assert.strictEqual(generated.length, 1, '系统生成名称应按定义匹配')
  assert.strictEqual(generated[0].status, 'same')
  const duplicates = buildObjectRows(inventory([1, 2].map(n => ({ ...item('CONSTRAINT', 'T', 'SYS_' + n, 'same'), generated: true }))),
    inventory([{ ...item('CONSTRAINT', 'U', 'SYS_3', 'same'), generated: true }]), 'table', 'T', 'U')
  assert.strictEqual(duplicates.length, 2, '重复定义的系统对象不应被 Map 覆盖')
  assert.strictEqual(duplicates.filter(row => row.status === 'source-only').length, 1)
  const literalNull = buildObjectRows(inventory([item('INDEX', 'T', 'IX', 'a', { VALUE: null })]),
    inventory([item('INDEX', 'T', 'IX', 'b', { VALUE: 'NULL' })]), 'table', 'T', 'T')
  assert.strictEqual(literalNull[0].changeDetails.length, 1, '空值不能等同于文本 NULL')
  assert.strictEqual(buildObjectRows(left, inventory([]), 'table', 'A.B', 'TARGET')[0].status, 'source-only')
  assert.strictEqual(buildObjectRows(inventory([]), right, 'table', 'A.B', 'TARGET')[0].status, 'target-only')
  const foreign = item('INDEX', 'A.B.OTHER', 'IX', 'a')
  assert.strictEqual(buildObjectRows(inventory([foreign]), inventory([]), 'table', 'A.B', 'TARGET').length, 0)
  const precise = buildObjectRows(left, inventory([item('INDEX', 'TARGET', 'ix', 'a')]), 'table', 'A.B', 'TARGET')
  assert.strictEqual(precise.length, 2, '对象名仍区分大小写')
  for (const key of ['UNIQUENESS', 'COLUMN_NAME', 'COLUMN_EXPRESSION', 'REFERENCE_TABLE', 'REFERENCE_COLUMN', 'DELETE_RULE', 'SEARCH_CONDITION', 'STATUS']) {
    const changed = buildObjectRows(inventory([item('CONSTRAINT', 'T', 'C', 'a', { [key + '[1]']: null })]),
      inventory([item('CONSTRAINT', 'T', 'C', 'b', { [key + '[1]']: 'value' })]), 'table', 'T', 'T')[0]
    assert.strictEqual(changed.status, 'changed')
    assert.strictEqual(changed.changeDetails[0].source, 'NULL')
  }
  for (const failed of [{ errors: { INDEX: 'denied' } }, { truncated: ['CONSTRAINT'] }]) {
    const unknown = buildObjectRows({ ...inventory([]), ...failed }, inventory([]), 'database')
    assert(unknown.some(row => row.status === 'unknown'), '两侧空清单失败也必须显示无法判定')
    const tables = [{ sourceName: 'T', targetName: 'T', counts: { changed: 0, sourceOnly: 0, targetOnly: 0 } }]
    applyObjectDifferences(tables, unknown)
    assert.strictEqual(tables[0].status, 'unknown')
  }
  const tables = [{ sourceName: 'A.B', targetName: 'A.B', counts: { changed: 0, sourceOnly: 0, targetOnly: 0 } }]
  applyObjectDifferences(tables, rows)
  assert.strictEqual(tables[0].status, 'changed', '只有索引变化也必须计入整库表级差异')
  assert.strictEqual(tables[0].objectDifferenceCount, 1)
  applyObjectDifferences(tables, [], true)
  assert.strictEqual(tables[0].status, 'unknown', '整个对象接口失败不能宣称一致')
  console.log('结构对象匹配、属性差异、整库汇总与不完整状态回归通过')
})().catch(error => { console.error(error); process.exitCode = 1 })
