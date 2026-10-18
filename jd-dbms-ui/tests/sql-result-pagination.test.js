const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')
const compiler = require('./helpers/vue-compiler')
const Vue = require('@vue/compat')
const root = path.join(__dirname, '../src')
const prepareContext = {}
vm.runInNewContext(fs.readFileSync(path.join(root, 'utils/sqlResult.js'), 'utf8').replace(/export /g, ''), prepareContext)
const source = compiler.parseComponent(fs.readFileSync(path.join(root, 'views/main/workspace/components/reanderSqlExecute/SqlResultTable.vue'), 'utf8'))
assert.deepStrictEqual(compiler.compile(source.template.content).errors, [])
const pending = []
const resizeListeners = new Map()
const context = { module: { exports: {} }, sqlServer: { executeSql: params => new Promise(resolve => pending.push({ params, resolve })) },
  FileDialog: {}, prepareSqlResult: prepareContext.prepareSqlResult, copyText() {}, requestAnimationFrame() {}, cancelAnimationFrame() {},
  window: { addEventListener: (name, listener) => resizeListeners.set(name, listener), removeEventListener: name => resizeListeners.delete(name) } }
vm.runInNewContext(source.script.content.replace(/^import .*;?\s*$/gm, '').replace('export default', 'module.exports ='), context)
const component = context.module.exports
const fixture = (pageNo = 1, pageSize = 200) => ({ success: true, pageNo, pageSize, totalExact: true, fuzzyTotal: '10001',
  params: { sql: 'SELECT ID, VALUE FROM T', sessionId: 'session', queryTemplate: true, skipCount: true },
  headerList: [{ name: '行号', dataType: 'CHAT2DB_ROW_NUMBER' }, { name: 'ID' }, { name: 'VALUE' }], dataList: [['1', '1', 'test']] })
const errors = []
const state = { ...component.data(), ...component.methods, queryResultData: fixture(),
  $emit(event, result) { this.queryResultData = result }, $message: { error: text => errors.push(text) } }
async function run() {
  const second = state.fetchPage(2, 200)
  const third = state.fetchPage(3, 200)
  assert.strictEqual(pending[0].params.skipCount, false)
  assert.strictEqual(pending[0].params.isLog, false)
  assert.strictEqual(pending[0].params.sessionId, 'session')
  pending[1].resolve({ success: true, data: [fixture(3)] }); await third
  pending[0].resolve({ success: true, data: [fixture(2)] }); await second
  assert.strictEqual(state.queryResultData.pageNo, 3, '迟到响应不能覆盖当前页')
  assert(Object.isFrozen(state.queryResultData.dataList), '只读行数组不能被 Vue 深度观察')
  assert(Object.isFrozen(state.queryResultData.headerList))
  const changedSize = state.changeSize(1000)
  assert.strictEqual(pending[2].params.pageNo, 1)
  assert.strictEqual(pending[2].params.pageSize, 1000)
  pending[2].resolve({ success: true, data: [fixture(1, 1000)] }); await changedSize
  const sorted = state.changeSort({ name: 'ID' }, false)
  assert.strictEqual(pending[3].params.pageNo, 1)
  assert.strictEqual(pending[3].params.isAsc, false)
  assert.strictEqual(pending[3].params.sql, 'SELECT ID, VALUE FROM T')
  pending[3].resolve({ success: true, data: [fixture()] }); await sorted
  const failed = state.fetchPage(2, 200)
  pending[4].resolve({ success: false, errorMessage: '连接中断' }); await failed
  assert.strictEqual(state.pageNo, 1)
  assert.strictEqual(state.loading, false)
  assert.deepStrictEqual(errors, ['连接中断'])
  const switched = state.fetchPage(2, 200)
  const nextResult = fixture(7)
  state.queryResultData = nextResult
  pending[5].resolve({ success: true, data: [fixture(2)] }); await switched
  assert.strictEqual(state.queryResultData, nextResult, '切换结果集后不能应用旧请求')
  assert.strictEqual(component.computed.total.call({ totalExact: true, queryResultData: { fuzzyTotal: '0' } }), 0)

  // 压力数据只用于测试渲染边界，不伪装成实库结果。
  const wide = { ...fixture(), headerList: Array.from({ length: 300 }, (_, index) => ({ name: 'C' + index })),
    dataList: Array.from({ length: 1000 }, () => Array(300).fill('wide')) }
  prepareContext.prepareSqlResult(wide)
  const screen = new Vue({ ...component, propsData: { queryResultData: wide } })
  screen.viewportWidth = 900
  screen.viewportHeight = 420
  screen.scrollTop = 20000
  screen.scrollLeft = 16000
  assert(screen.visibleRows.length <= 23, '千行结果只渲染视口和缓冲行')
  const maxVisibleColumns = Math.ceil((screen.viewportWidth + 2 * 160) / 80) + 1
  assert(screen.visibleColumns.length <= maxVisibleColumns, '自适应窄列仍只渲染视口和缓冲列')
  assert(screen.visibleRows[0].index > 600)
  assert(screen.visibleColumns[0].index > 90)
  assert(!screen.rows.__ob__, '冻结的结果数组不得生成响应式单元格')
  const template = compiler.compileToFunctions(source.template.content)
  const rendered = template.render.call(screen, screen, [])
  const countCells = node => (node.props?.role === 'gridcell' ? 1 : 0) +
    (Array.isArray(node.children) ? node.children : []).reduce((count, child) => count + countCells(child), 0)
  assert(countCells(rendered) > 0, 'Vue 3 渲染器应实际生成结果单元格')
  assert(countCells(rendered) <= screen.visibleRows.length * maxVisibleColumns, '30万单元格结果的 DOM 必须保持有界')
  assert.strictEqual(screen.displayValue('x'.repeat(100000)).length, 200, '大字段不能直接铺满单元格 DOM')
  screen.$destroy()
  const widths = new Vue({ ...component, propsData: { queryResultData: { ...fixture(),
    headerList: [{ name: '行号', dataType: 'CHAT2DB_ROW_NUMBER' }, { name: 'ID' }, { name: 'DESCRIPTION' }, { name: 'PAYLOAD', dataType: 'BLOB' }],
    dataList: [['1', '1', '这是一段较长的中文字段内容，用来检查列宽是否适配显示', 'BLOB']] } } })
  assert(widths.columns[2].width > widths.columns[1].width, '长内容应比短 ID 列更宽')
  assert.strictEqual(widths.columns[0].width, 64)
  const originalWidth = widths.columns[2].width
  const originalGridWidth = widths.gridWidth
  widths.startResize({ button: 0, clientX: 100 }, widths.columns[2])
  resizeListeners.get('pointermove')({ clientX: 220 })
  assert.strictEqual(widths.columns[2].width, originalWidth + 120, '拖动应调整列宽')
  assert.strictEqual(widths.gridWidth, originalGridWidth + 120)
  assert.strictEqual(widths.columns[3].left, widths.columns[2].left + widths.columns[2].width, '后续列和表体应同步移动')
  resizeListeners.get('pointermove')({ clientX: -1000 })
  assert.strictEqual(widths.columns[2].width, 64, '手动缩窄不能低于最小列宽')
  resizeListeners.get('pointerup')()
  assert.strictEqual(resizeListeners.size, 0, '拖拽结束必须移除全局监听')
  widths.fitColumn(widths.columns[2])
  assert.strictEqual(widths.columns[2].width, originalWidth, '双击应恢复内容自适应宽度')
  const watchState = { columnWidths: { 2: 300 }, $refs: {}, $nextTick() {} }
  component.watch.queryResultData.handler.call(watchState, fixture(2), fixture(1))
  assert.strictEqual(watchState.columnWidths[2], 300, '翻页应保留手动列宽')
  component.watch.queryResultData.handler.call(watchState, { ...fixture(), params: { sql: 'SELECT * FROM OTHER' } }, fixture())
  assert.strictEqual(Object.keys(watchState.columnWidths).length, 0, '更换查询时应按新结果重新适配')
  widths.startResize({ button: 0, clientX: 100 }, widths.columns[2])
  widths.$destroy()
  assert.strictEqual(resizeListeners.size, 0, '销毁结果表必须清理拖拽监听')
  console.log('SQL 结果：精确分页、乱序/失败回退、结果隔离、双向虚拟渲染通过')
}
run().catch(error => { console.error(error); process.exitCode = 1 })
