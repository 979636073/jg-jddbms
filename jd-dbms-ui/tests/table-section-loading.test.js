const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')
const { compile } = require('@vue/compiler-dom')
const Vue = require('vue')

const file = path.join(__dirname, '../src/views/main/workspace/components/renderTableEditor/index.vue')
const source = fs.readFileSync(file, 'utf8')
const script = source.match(/<script>([\s\S]*?)<\/script>/)[1]
  .replace(/^import .*;?\s*$/gm, '').replace('export default', 'module.exports =')
const calls = []
let fail = false
let pending
let sqlParams
let previewSql = ';'
const context = { module: { exports: {} }, uuidv4: () => 'row-key', tableServer: {
  getTableSection(params, section) {
    calls.push(section)
    if (fail) return Promise.resolve({ success: false })
    if (pending) return pending
    return Promise.resolve({ success: true, data: {
      columns: { columnList: [{ name: 'ID', primaryKey: true }] },
      indexes: { indexList: [{ name: 'PK_TEST', columnList: [{ columnName: 'ID' }] }] },
      constraints: { constraints: [{ name: 'PK_TEST' }] },
      basic: { name: 'TEST', comment: 'keep comment', columnList: null, indexList: null },
      ddl: { querySql: 'CREATE TABLE TEST (ID INT)' }
    }[section] })
  },
  getDatabaseFieldTypeList() { calls.push('types'); return Promise.resolve({ data: {} }) },
  getModifyTableSql(params) { sqlParams = params; return Promise.resolve({ success: true, data: [{ sql: previewSql }] }) }
} }
for (const name of ['BaseInfo', 'ColumnList', 'IndexList', 'ConstraintList', 'GrantList', 'ScriptInfo', 'TriggerList', 'UsedByList', 'SqlPreview', 'CreateSql', 'renderSearchResult', 'MonacoEditor']) context[name] = {}
vm.runInNewContext(script, context, { filename: file })
const component = context.module.exports
function create() {
  const state = { ...component.data(), queryResultData: { id: 'window-1', uniqueData: { dataSourceId: 1, schemaName: 'APP', tableName: 'TEST' } },
    $set: (object, key, value) => { object[key] = value }, $delete: (object, key) => { delete object[key] }, $nextTick: callback => callback(),
    $refs: {}, $message: { error() {}, warning() {} }
  }
  for (const [name, method] of Object.entries(component.methods)) state[name] = method.bind(state)
  return state
}

async function run() {
  const firstVisit = create()
  await firstVisit.initData()
  const renderTabs = new Function('Vue', compile(source.match(/<el-tabs\b[^>]*>/)[0] + '</el-tabs>', {
    mode: 'function', prefixIdentifiers: true, bindingMetadata: { 'el-tabs': 'setup-const' }
  }).code)(Vue)
  for (const [tab, section] of [['second', 'columns'], ['third', 'indexes'], ['forth', 'constraints'], ['seventh', 'ddl'], ['first', 'basic']]) {
    const listeners = renderTabs(firstVisit, [], null, { 'el-tabs': {} }).props
    const before = calls.length
    // Element Plus emits tabClick before updating v-model, then emits tabChange.
    if (listeners.onTabClick) await listeners.onTabClick({ props: { name: tab } })
    listeners['onUpdate:modelValue'](tab)
    if (listeners.onTabChange) await listeners.onTabChange(tab)
    assert.deepStrictEqual(calls.slice(before).filter(name => name !== 'types'), [section], '首次切换必须加载目标页签，不能读取上一个页签')
    assert(firstVisit.loadedSections[tab], '首次点击后应完成加载，无需刷新或切回')
  }
  calls.length = 0
  const state = create()
  await state.initData()
  assert.deepStrictEqual(calls, [], '打开数据页不得请求结构或字段类型')
  for (const [tab, section] of [['third', 'indexes'], ['forth', 'constraints'], ['seventh', 'ddl'], ['first', 'basic'], ['second', 'columns']]) {
    const before = calls.length
    await state.loadTableSection(tab)
    assert.deepStrictEqual(calls.slice(before), [section], '每个页签只请求自身内容')
    await state.loadTableSection(tab)
    assert.strictEqual(calls.length, before + 1, '重复点击复用缓存，包括空列表')
  }
  state.activeName = 'sixth'
  const beforeData = calls.length
  await state.handleClick()
  assert.strictEqual(calls.length, beforeData, '回到数据页不重新查询')
  state.activeName = 'third'
  await state.getTableDetails(true)
  assert.strictEqual(calls[calls.length - 1], 'indexes', '刷新只请求当前结构页签')
  state.configChange()
  await state.initData()
  assert(state.loadedSections.third, '窗口切回保留页签缓存')
  state.activeName = 'forth'
  const beforeRefresh = calls.length
  await state.getTableDetails(true)
  assert.deepStrictEqual(calls.slice(beforeRefresh), ['constraints'], '约束刷新不得连带查询其他页签')
  assert(!state.loadedSections.third && !state.loadedSections.second && !state.loadedSections.seventh, '约束变更后关联结构页签下次点击重新读取')

  const edited = create()
  await edited.initData()
  await edited.loadTableSection('second')
  edited.columnList[0].name = 'EDITED_ID'
  await edited.submit()
  assert.strictEqual(sqlParams.newTable.columnList[0].name, 'EDITED_ID', '补齐其他元数据不能覆盖已编辑列')
  assert.strictEqual(sqlParams.oldTable.columnList[0].name, 'ID', '保存使用原列快照')
  assert.strictEqual(sqlParams.oldTable.indexList.length, 1, '基本信息不能覆盖已经读取的索引基线')
  assert.strictEqual(sqlParams.newTable.comment, 'keep comment', '未查看基本信息不能把表注释清空')
  previewSql = ''
  await edited.submit()
  // 没有 sqlPreview ref；空变更若打开预览会抛错，不能弹出空 SQL 对话框。

  const retry = create()
  await retry.initData()
  fail = true
  assert.strictEqual(await retry.loadTableSection('second'), false)
  assert(!retry.loadedSections.second, '失败不能标记加载完成')
  fail = false
  let finish
  pending = new Promise(resolve => { finish = resolve })
  const beforePending = calls.length
  const first = retry.loadTableSection('second')
  const second = retry.loadTableSection('second')
  assert.strictEqual(calls.length, beforePending + 1, '重复点击合并进行中的请求')
  retry.currentConfig.uniqueData.tableName = 'OTHER_TABLE'
  finish({ success: true, data: { columnList: [{ name: 'STALE' }] } })
  await Promise.all([first, second])
  assert.strictEqual(retry.columnList.length, 0, '同一窗口切换表后，旧表响应不能覆盖新表')
  pending = null
  assert(source.includes('name="second" lazy'), '非数据页签组件按需挂载')
}
run().catch(error => { console.error(error); process.exitCode = 1 })
