const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')

function loadComponent(file, context) {
  const source = fs.readFileSync(file, 'utf8')
  const script = source.match(/<script>([\s\S]*?)<\/script>/)[1]
    .replace(/^import .*;?\s*$/gm, '')
    .replace('export default', 'module.exports =')
  vm.runInNewContext(script, context, { filename: file })
  return { component: context.module.exports, source }
}

const compareFile = path.join(__dirname, '../src/views/main/tableCompare/index.vue')
const compareContext = {
  module: { exports: {} },
  connectionServer: {},
  tableServer: {},
  FieldCompareResult: {},
  WholeDatabaseResult: {},
  setTimeout,
  clearTimeout
}
const { component: compare, source: compareSource } = loadComponent(compareFile, compareContext)

assert(compareSource.includes(':remote-method="searchSourceTables"'), '源表应支持远程搜索')
assert(compareSource.includes(':remote-method="searchTargetTables"'), '目标表应支持远程搜索')
assert(compareSource.includes('@click="swapSides"'), '应支持交换源表和目标表')
assert(compareSource.includes('<el-radio-button label="database">整库对比</el-radio-button>'), '应提供整库对比模式')
assert(compareSource.includes('tableServer.getAllTableList'), '整库对比应读取两侧完整表清单')
assert(compareSource.includes('runWithConcurrency(matchedRows, 4'), '字段读取应限制为四路并发')
assert(compareSource.includes(':percentage="wholeProgressPercentage"'), '整库对比应展示处理进度')

const sourceColumns = [
  { name: 'ID', dataType: 'NUMBER', columnType: 'NUMBER(10)', columnSize: 10, nullable: 0, primaryKey: true },
  { name: 'NAME', dataType: 'VARCHAR', columnType: 'VARCHAR(40)', columnSize: 40, nullable: 1, comment: '姓名' },
  { name: 'CREATED_AT', dataType: 'DATE', columnType: 'DATE', nullable: 0 }
]
const targetColumns = [
  { name: 'ID', dataType: 'NUMBER', columnType: 'NUMBER(10)', columnSize: 10, nullable: 0, primaryKey: true },
  { name: 'NAME', dataType: 'VARCHAR', columnType: 'VARCHAR(80)', columnSize: 80, nullable: 0, comment: '用户姓名' },
  { name: 'UPDATED_AT', dataType: 'DATE', columnType: 'DATE', nullable: 0 }
]
const compareState = { sameValue: compare.methods.sameValue }
const rows = compare.methods.buildRows.call(compareState, sourceColumns, targetColumns)
assert.strictEqual(rows.length, 4)
assert.strictEqual(rows.find(row => row.columnName === 'ID').status, 'same')
assert.strictEqual(rows.find(row => row.columnName === 'NAME').status, 'changed')
assert(rows.find(row => row.columnName === 'NAME').changedFields.includes('columnSize'))
assert(rows.find(row => row.columnName === 'NAME').changedFields.includes('nullable'))
assert.strictEqual(rows.find(row => row.columnName === 'CREATED_AT').status, 'source-only')
assert.strictEqual(rows.find(row => row.columnName === 'UPDATED_AT').status, 'target-only')

const fieldResultFile = path.join(__dirname, '../src/views/main/tableCompare/components/FieldCompareResult.vue')
const { component: fieldResult, source: fieldResultSource } = loadComponent(fieldResultFile, { module: { exports: {} } })
assert(fieldResultSource.includes('<el-radio-button label="changed">'), '字段结果应支持差异筛选')
assert(fieldResultSource.includes("propertyClass('type')"), '字段属性差异应提供独立高亮')
const summary = fieldResult.computed.summary.call({ rows })
assert.deepStrictEqual(JSON.parse(JSON.stringify(summary)), {
  changed: 1,
  'source-only': 1,
  'target-only': 1,
  same: 1
})
const changedRows = fieldResult.computed.filteredRows.call({ filter: 'changed', rows })
assert.strictEqual(changedRows.length, 1)
assert.strictEqual(changedRows[0].columnName, 'NAME')

const databaseRows = compare.methods.buildDatabaseRows.call({},
  [{ name: 'USERS' }, { name: 'ORDERS' }],
  [{ name: 'users' }, { name: 'AUDIT_LOG' }]
)
assert.strictEqual(databaseRows.length, 3)
assert.strictEqual(databaseRows.find(row => row.sourceName === 'USERS').targetName, 'users', '整库表名匹配应忽略大小写')
assert.strictEqual(databaseRows.find(row => row.sourceName === 'ORDERS').status, 'source-only')
assert.strictEqual(databaseRows.find(row => row.targetName === 'AUDIT_LOG').status, 'target-only')

const caseInsensitiveFieldRows = compare.methods.buildRows.call(compareState,
  [{ name: 'USER_ID', dataType: 'NUMBER' }],
  [{ name: 'user_id', dataType: 'NUMBER' }],
  true
)
assert.strictEqual(caseInsensitiveFieldRows.length, 1)
assert.strictEqual(caseInsensitiveFieldRows[0].status, 'same', '整库字段名匹配应忽略大小写')

const wholeResultFile = path.join(__dirname, '../src/views/main/tableCompare/components/WholeDatabaseResult.vue')
const wholeResult = loadComponent(wholeResultFile, { module: { exports: {} } }).component
const wholeSummary = wholeResult.computed.summary.call({ rows: databaseRows })
assert.strictEqual(wholeSummary['source-only'], 1)
assert.strictEqual(wholeSummary['target-only'], 1)

const tagsFile = path.join(__dirname, '../src/components/JdTagsView/index.vue')
const tagsSource = fs.readFileSync(tagsFile, 'utf8')
assert(tagsSource.includes('@contextmenu.prevent.native="openMenu(tag, $event)"'), '标签页应启用右键菜单')
assert(tagsSource.includes('刷新页面'))
assert(tagsSource.includes('关闭当前'))
assert(tagsSource.includes('关闭其他'))
assert(tagsSource.includes('全部关闭'))

const routerFile = path.join(__dirname, '../src/router/index.js')
const routerSource = fs.readFileSync(routerFile, 'utf8')
assert(/path: '\/task-center',[\s\S]*?hidden: true/.test(routerSource), '任务中心应从侧边导航隐藏')
assert(/path: '\/relation-graph',[\s\S]*?hidden: true/.test(routerSource), '关联关系图应从侧边导航隐藏')

const navBarFile = path.join(__dirname, '../src/views/main/workspace/NavBar.vue')
const navBarSource = fs.readFileSync(navBarFile, 'utf8')
assert(/\.nav_list \{[\s\S]*?position: absolute;[\s\S]*?left: 50%;[\s\S]*?transform: translateX\(-50%\);/.test(navBarSource), '首页操作区应独立居中')

;(async () => {
  let activeWorkers = 0
  let maxActiveWorkers = 0
  await compare.methods.runWithConcurrency.call({}, Array.from({ length: 10 }), 4, async () => {
    activeWorkers += 1
    maxActiveWorkers = Math.max(maxActiveWorkers, activeWorkers)
    await new Promise(resolve => setTimeout(resolve, 5))
    activeWorkers -= 1
  })
  assert.strictEqual(maxActiveWorkers, 4, '整库字段读取并发数不得超过四路')
  console.log('单表及整库结构对比、标签页菜单、导航精简和顶部布局回归测试通过')
})().catch(error => {
  console.error(error)
  process.exitCode = 1
})
