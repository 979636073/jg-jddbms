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
  SchemaObjectResult: {},
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
const compareState = {
  sameValue: compare.methods.sameValue,
  normalizedFieldValue: compare.methods.normalizedFieldValue,
  displayFieldValue: compare.methods.displayFieldValue
}
const rows = compare.methods.buildRows.call(compareState, sourceColumns, targetColumns)
assert.strictEqual(rows.length, 4)
assert.strictEqual(rows.find(row => row.columnName === 'ID').status, 'same')
assert.strictEqual(rows.find(row => row.columnName === 'NAME').status, 'changed')
assert(rows.find(row => row.columnName === 'NAME').changedFields.includes('columnSize'))
assert(rows.find(row => row.columnName === 'NAME').changedFields.includes('nullable'))
assert(rows.find(row => row.columnName === 'NAME').changeDetails.some(item => item.label === '是否可空' && item.source === '可为空' && item.target === '不可为空'))
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
assert.strictEqual(databaseRows.length, 4)
assert.strictEqual(databaseRows.find(row => row.sourceName === 'USERS').status, 'source-only', '大小写敏感模式应区分表名')
assert.strictEqual(databaseRows.find(row => row.targetName === 'users').status, 'target-only')
assert.strictEqual(databaseRows.find(row => row.sourceName === 'ORDERS').status, 'source-only')
assert.strictEqual(databaseRows.find(row => row.targetName === 'AUDIT_LOG').status, 'target-only')

const caseSensitiveFieldRows = compare.methods.buildRows.call(compareState,
  [{ name: 'USER_ID', dataType: 'NUMBER' }],
  [{ name: 'user_id', dataType: 'NUMBER' }]
)
assert.strictEqual(caseSensitiveFieldRows.length, 2)
assert.strictEqual(caseSensitiveFieldRows[0].status, 'source-only', '大小写敏感模式应区分字段名')
const orderRows = compare.methods.buildRows.call(compareState,
  [{ name: 'ID', dataType: 'NUMBER', ordinalPosition: 1 }],
  [{ name: 'ID', dataType: 'NUMBER', ordinalPosition: 2 }]
)
assert.strictEqual(orderRows[0].status, 'changed', '字段顺序变化应计入结构差异')
assert(orderRows[0].changedFields.includes('ordinalPosition'))

const equivalentMetadata = compare.methods.buildRows.call(compareState,
  [{ name: 'ID', dataType: 'VARCHAR', columnType: '', columnSize: 0, nullable: 1, primaryKey: 1, autoIncrement: false, ordinalPosition: 1 }],
  [{ name: 'ID', dataType: 'varchar', columnType: 'VARCHAR', columnSize: null, nullable: true, primaryKey: true, autoIncrement: 0, ordinalPosition: '1' }]
)
assert.strictEqual(equivalentMetadata[0].status, 'same', '等价元数据不应误报有变化')
const visibleLengthChange = compare.methods.buildRows.call(compareState,
  [{ name: 'ID', dataType: 'VARCHAR', columnType: 'VARCHAR', columnSize: 20 }],
  [{ name: 'ID', dataType: 'VARCHAR', columnType: 'VARCHAR', columnSize: 40 }]
)
assert.strictEqual(visibleLengthChange[0].status, 'changed')
assert(visibleLengthChange[0].changeDetails.some(item => item.label === '长度' && item.source === '20' && item.target === '40'))

const wholeResultFile = path.join(__dirname, '../src/views/main/tableCompare/components/WholeDatabaseResult.vue')
const wholeResult = loadComponent(wholeResultFile, { module: { exports: {} }, FieldCompareResult: {}, SchemaObjectResult: {} }).component
const wholeSummary = wholeResult.computed.summary.call({ rows: databaseRows })
assert.strictEqual(wholeSummary['source-only'], 2)
assert.strictEqual(wholeSummary['target-only'], 2)
const largeResult = Array.from({ length: 1000 }, (_, index) => ({ key: String(index) }))
const visiblePage = wholeResult.computed.pageRows.call({ filteredRows: largeResult, page: 2, pageSize: 50 })
assert.strictEqual(visiblePage.length, 50, '整库结果应按页渲染，避免低配终端一次渲染千行')
assert.strictEqual(visiblePage[0].key, '50')

const reportFile = path.join(__dirname, '../src/views/main/tableCompare/report.js')
const reportSource = fs.readFileSync(reportFile, 'utf8').replace('export function buildCompareReport', 'function buildCompareReport') + '\nmodule.exports = buildCompareReport'
const reportContext = { module: { exports: {} } }
vm.runInNewContext(reportSource, reportContext, { filename: reportFile })
const report = reportContext.module.exports({
  mode: 'table', sourcePath: '源库 / TEST', targetPath: '目标库 / TEST',
  fieldRows: visibleLengthChange, objectRows: [{ kind: 'VIEW', name: '<危险>', status: 'changed', source: { definition: 'SELECT 1' }, target: { definition: 'SELECT 2' } }]
})
assert(report.includes('长度') && report.includes('20') && report.includes('40'), 'PDF 报告应包含具体变化值')
assert(report.includes('&lt;危险&gt;') && !report.includes('<危险>'), '数据库元数据应转义后写入报告')
assert(report.includes('window.print()'), '报告应支持浏览器保存 PDF')
const objectReport = reportContext.module.exports({
  mode: 'database', tableRows: [{ sourceName: 'T', targetName: 'T', status: 'changed',
    counts: { changed: 0, sourceOnly: 0, targetOnly: 0 }, objectDifferenceCount: 1, fieldRows: [] }],
  objectRows: [{ kind: 'INDEX', name: 'IX', status: 'changed', changeDetails: [
    { label: '索引表达式[1]', source: '<UPPER>', target: 'LOWER(VAL)' }
  ] }]
})
assert(objectReport.includes('结构对象差异 1') && objectReport.includes('索引表达式[1]'), '报告应包含仅索引变化及属性明细')
assert(objectReport.includes('&lt;UPPER&gt;') && !objectReport.includes('<UPPER>'), '结构属性差异应转义')
if (process.env.PDF_FIXTURE_HTML) fs.writeFileSync(process.env.PDF_FIXTURE_HTML, report, 'utf8')

const tagsFile = path.join(__dirname, '../src/components/JdTagsView/index.vue')
const tagsSource = fs.readFileSync(tagsFile, 'utf8')
assert(tagsSource.includes('@contextmenu.prevent="openMenu(tag, $event)"'), '标签页应启用右键菜单')
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
const navListSource = navBarSource.slice(navBarSource.indexOf('navList:'), navBarSource.indexOf('computed:'))
assert(!navListSource.includes('导入文件') && !navListSource.includes('导出文件'),
  '连接数据库后顶部也不应出现旧导入导出入口')
assert(navListSource.includes('数据迁移') && navListSource.includes('表结构对比'), '保留其他顶部工具')
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
