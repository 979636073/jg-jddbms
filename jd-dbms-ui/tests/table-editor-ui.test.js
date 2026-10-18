const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')
const compiler = require('./helpers/vue-compiler')

const file = path.join(__dirname, '../src/views/main/workspace/components/renderSearchResult/index.vue')
const componentSource = compiler.parseComponent(fs.readFileSync(file, 'utf8'))
const source = componentSource.script.content
  .replace(/^import .*;?\s*$/gm, '')
  .replace('export default', 'module.exports =')
const context = {
  module: { exports: {} },
  sqlServer: {}, tableServer: {}, viewServer: {},
  SqlPreview: {}, MonacoEditor: {}, ScreeningResult: {}, Filedialog: {}, ViewGrantPanel: {}, importDataDialog: {},
  downloadFile() {}, copyText() {}, getToken() { return '' }, setTimeout
}
vm.runInNewContext(source, context, { filename: file })
const component = context.module.exports

const compiled = compiler.compile(componentSource.template.content)
assert.deepStrictEqual(compiled.errors, [])
for (const marker of ['table-editor-workspace', 'value-inspector', 'pending-change-summary', '高级查询']) {
  assert(componentSource.template.content.includes(marker), `缺少 ${marker} 区域`)
}
assert(!componentSource.template.content.includes('table-editor-identity'), '不再显示库/模式/表名行')
assert(!componentSource.template.content.includes('table-editor-filter'), '不再显示独立 WHERE 条')
assert(!/<small>\{\{\s*header\.dataType\s*\}\}<\/small>/.test(componentSource.template.content), '结果集列头只显示列名')
assert(/\.el-table__body-wrapper\s*\{[^}]*flex: 1 1 0;[^}]*overflow: auto !important;/s.test(componentSource.styles[0].content), '结果集表体需要独立滚动')

const tableEditorFile = path.join(__dirname, '../src/views/main/workspace/components/renderTableEditor/index.vue')
const tableEditorSource = compiler.parseComponent(fs.readFileSync(tableEditorFile, 'utf8'))
const tableEditorTemplate = tableEditorSource.template.content
assert(tableEditorTemplate.includes('class="table-save-button"'), '表结构保存按钮应使用独立定位')
assert(/\.table-save-button\s*\{[^}]*right: 20px;[^}]*top: 17px;/s.test(tableEditorSource.styles[0].content), '表结构保存按钮应在右上角对齐')

const kind = component.methods.editorKind
assert.strictEqual(kind({ dataType: 'CLOB' }), 'text')
assert.strictEqual(kind({ dataType: 'NCLOB' }), 'text')
assert.strictEqual(kind({ dataType: 'CONTENT' }), 'text')
assert.strictEqual(kind({ dataType: 'BLOB' }), 'binary')
assert.strictEqual(kind({ dataType: 'BYTE' }), 'binary')
assert.strictEqual(kind({ dataType: 'TIMESTAMP(6)' }), 'datetime')
assert.strictEqual(kind({ dataType: 'TIME' }), 'time')
assert.strictEqual(kind({ dataType: 'NUMBER(12,2)' }), 'number')
assert.strictEqual(kind({ dataType: 'BIT' }), 'boolean')
assert.strictEqual(kind({ dataType: 'INTERVAL DAY TO SECOND' }), 'interval')
assert.strictEqual(kind({ dataType: 'ROWID' }), 'readonly')
assert.strictEqual(component.methods.datePickerType.call({ queryResultData: { uniqueData: { databaseType: 'ORACLE' } } }, 'DATE'), 'datetime')
assert.strictEqual(component.methods.datePickerType.call({ queryResultData: { uniqueData: { databaseType: 'DM' } } }, 'DATE'), 'date')
const dateEditor = {
  queryResultData: { uniqueData: { databaseType: 'DM' } },
  datePickerType: component.methods.datePickerType
}
const parsedDate = component.methods.parseDatePickerValue('2026-08-10 08:00:00')
assert.strictEqual(parsedDate.getFullYear(), 2026)
assert.strictEqual(parsedDate.getMonth(), 7)
assert.strictEqual(parsedDate.getHours(), 8)
assert.strictEqual(component.methods.formatDatePickerValue.call(dateEditor, parsedDate, 'DATETIME'), '2026-08-10 08:00:00')
parsedDate.setDate(12)
assert.strictEqual(component.methods.formatDatePickerValue.call(dateEditor, parsedDate, 'DATETIME'), '2026-08-12 08:00:00')
assert.strictEqual(component.methods.formatDatePickerValue.call(dateEditor, parsedDate, 'DATE'), '2026-08-12')
assert.strictEqual(component.methods.formatDatePickerValue.call(dateEditor, parsedDate, 'YEAR'), '2026')
assert(!componentSource.template.content.includes('value-format="datePickerFormat('), '日期选择器应接收 Date 对象，避免字符串导致日历失效')
assert(!component.methods.inputBlur.toString().includes('localStorage.setItem'), '单元格提交不得把含 BLOB 的整表快照写入 localStorage')

let refreshSql
context.sqlServer.executeSql = send => {
  refreshSql = send.sql
  return { then() {} }
}
const refreshState = {
  queryResultData: { tableName: '"QA_TESTER"."TEST"', uniqueData: { databaseType: 'DM', dataType: 'tables', dataSourceId: 1, schemaName: 'QA_TESTER' } },
  queryParams: { pageNum: 1, pageSize: 100, params: {} }
}
component.methods.getTableList.call(refreshState)
assert.strictEqual(refreshSql, 'SELECT t.*, t.rowid FROM "QA_TESTER"."TEST" t', '达梦表刷新需要带回 ROWID')
refreshState.queryResultData.uniqueData.dataType = 'views'
component.methods.getTableList.call(refreshState)
assert.strictEqual(refreshSql, 'SELECT * FROM "QA_TESTER"."TEST"', '视图刷新不得引用 ROWID')

const saveHeaders = [{ name: 'NAME', dataType: 'STRING' }, { name: 'NOTE', dataType: 'CONTENT' }, { name: 'IMAGE_DATA', dataType: 'BYTE' }]
const saveState = {
  queryResultData: { headerList: saveHeaders }, sqlInfo: [], editorKind: kind,
  sameCellValue: component.methods.sameCellValue
}
const pendingBlobRow = { '行号': 1 }
Object.defineProperty(pendingBlobRow, 'IMAGE_DATA', { get() { throw new Error('已进入 sqlInfo 的 BLOB 不应在渲染时重复比较') } })
const pendingBlobSummary = component.computed.pendingChangeSummary.call({
  sqlInfo: [{ type: 'UPDATE', rowId: 1 }],
  oldTableData: [{ '行号': 1, IMAGE_DATA: 'old' }],
  dataTable: [pendingBlobRow],
  queryResultData: { headerList: [{ name: 'IMAGE_DATA' }] },
  sameCellValue: component.methods.sameCellValue
})
assert.strictEqual(pendingBlobSummary.length, 1)
assert.strictEqual(pendingBlobSummary[0].rowId, '1')
assert.strictEqual(pendingBlobSummary[0].type, 'UPDATE')
saveState.sqlInfo = [{ type: 'UPDATE', oldDataList: ['旧', '长文本', null], dataList: ['新', '长文本', null] }]
assert.strictEqual(component.methods.requiresBlobSave.call(saveState), false, '普通字段更新不得因表包含大字段而进入大字段接口')
saveState.sqlInfo = [{ type: 'UPDATE', oldDataList: ['旧', '长文本', null], dataList: ['旧', '新长文本', null] }]
assert.strictEqual(component.methods.requiresBlobSave.call(saveState), true)

const binaryRow = { '行号': 3, ID: '7', IMAGE_DATA: 'A' }
const binaryHeaders = [{ name: 'ID', dataType: 'STRING' }, { name: 'IMAGE_DATA', dataType: 'BYTE' }]
const binaryEdit = {
  selectedRow: binaryRow,
  selectedHeader: binaryHeaders[1],
  queryResultData: { headerList: binaryHeaders },
  dataTable: [binaryRow],
  oldTableData: [{ ...binaryRow }],
  sqlInfo: [],
  valueEditorReadonly: false,
  valueEditorNull: false,
  valueEditorDraft: 'A',
  sameCellValue: component.methods.sameCellValue,
  cellValue: component.methods.cellValue,
  editorKind: kind,
  applyValueEditor() { component.methods.applyValueEditor.call(this) },
  inputBlur() { component.methods.syncPendingOperations.call(this) }
}
Object.defineProperty(binaryEdit, 'valueEditorChanged', {
  get() { return component.computed.valueEditorChanged.call(this) }
})
component.methods.clearBinaryValue.call(binaryEdit)
assert.strictEqual(binaryRow.IMAGE_DATA, null, '只应清空选中的 BLOB 字段')
assert.strictEqual(binaryRow.ID, '7', '清空 BLOB 不得删除整行')
assert.strictEqual(binaryEdit.sqlInfo.length, 1, '清空 BLOB 应产生一条待保存变更')
assert.strictEqual(binaryEdit.sqlInfo[0].type, 'UPDATE')
assert.strictEqual(binaryEdit.sqlInfo[0].dataList[1], null)
assert.strictEqual(component.methods.requiresBlobSave.call(binaryEdit), true, '清空 BLOB 应进入大字段保存接口')
binaryRow.IMAGE_DATA = 'A'
component.methods.syncPendingOperations.call(binaryEdit)
assert.strictEqual(binaryEdit.sqlInfo.length, 0, '还原 BLOB 后不应残留待保存变更')
assert(componentSource.template.content.includes('清空字段值'), '二进制编辑区应有单字段清空入口')
assert(componentSource.template.content.includes('上传 / 替换'), '二进制详情区应支持直接替换文件')
assert(componentSource.template.content.includes(':loading="binaryUploadLoading"'), 'BLOB 文件读取期间必须显示加载状态')
assert(componentSource.template.content.includes(':loading="blobSaveLoading"'), 'BLOB 保存期间必须显示加载状态')
for (const upload of componentSource.template.content.matchAll(/<el-upload[\s\S]*?<\/el-upload>/g)) {
  if (upload[0].includes('fileChange(')) {
    assert(!upload[0].includes(':limit="1"'), '重复替换不得被上传组件的单文件限制拦截')
  }
}
const binaryDialogSource = compiler.parseComponent(fs.readFileSync(path.join(__dirname, '../src/views/main/workspace/components/renderSearchResult/filedialog.vue'), 'utf8'))
assert(!binaryDialogSource.template.content.includes('<el-input'), '下载弹窗不应要求手工填写文件名和后缀')
assert(binaryDialogSource.template.content.includes('max-height: 420px'), '图片预览不得将下载按钮挤出弹窗')
assert(componentSource.template.content.includes("scope.row[header.name] === '' ? '空文件' : '文件'"), '零字节 BLOB 不应显示为 NULL')
assert.deepStrictEqual(compiler.compile(binaryDialogSource.template.content).errors, [])
let downloadCall
const dialogContext = {
  module: { exports: {} },
  process: { env: { VUE_APP_BASE_API: '' } },
  AbortController,
  crypto: require('crypto').webcrypto,
  downloadFile(url, params) { downloadCall = { url, params } }
}
vm.runInNewContext(binaryDialogSource.script.content.replace(/^import .*;?\s*$/gm, '').replace('export default', 'module.exports ='), dialogContext)
const dialog = dialogContext.module.exports
const downloadState = { blobStr: 'AA==', fromData: { name: 'QA_IMAGE_DATA_1', type: '.bin' }, $message: { success() {}, error() {} } }
const downloadPromise = dialog.methods.downLoad.call(downloadState)
assert.strictEqual(downloadCall.params.fileName, 'QA_IMAGE_DATA_1')
assert.strictEqual(downloadCall.params.prefix, '.bin')

const row = { '行号': 1, NOTE: '原值' }
let committed = false
const state = {
  readonly: false,
  typeView: 'tables',
  queryResultData: { headerList: [{ name: 'NOTE', dataType: 'CLOB' }] },
  selectedRow: null,
  selectedColumn: null,
  valueEditorDraft: null,
  valueEditorNull: false,
  valueEditorReadonly: false,
  valueEditorChanged: true,
  editorKind: kind,
  inputBlur() { committed = true }
}
component.methods.selectValueCell.call(state, row, { property: 'NOTE', index: 1 })
assert.strictEqual(state.valueEditorDraft, '原值')
assert.strictEqual(row.NOTE, '原值')
state.valueEditorDraft = '修改后的长文本'
state.selectedHeader = { name: 'NOTE', dataType: 'CLOB' }
component.methods.applyValueEditor.call(state)
assert.strictEqual(row.NOTE, '修改后的长文本')
assert.strictEqual(committed, true)

let pastePrevented = false
component.methods.pasteInfo.call({ readonly: false }, {
  target: { tagName: 'TEXTAREA' },
  preventDefault() { pastePrevented = true },
  stopPropagation() {}
})
assert.strictEqual(pastePrevented, false, '编辑器内粘贴必须保留浏览器默认行为')

const cells = [{ name: 'NAME', dataType: 'VARCHAR' }, { name: 'NOTE', dataType: 'CLOB' }]
const rows = [
  { '行号': 1, NAME: '甲', NOTE: '旧一' },
  { '行号': 2, NAME: '乙', NOTE: '旧二' }
]
let synced = 0
const pasteState = {
  isEditableTable: true,
  selectedRow: rows[0],
  selectedHeader: cells[0],
  headerList: cells,
  dataTable: rows,
  editorKind: kind,
  isDisableds() { return '' },
  syncPendingOperations() { synced++ },
  selectValueCell(row, column) { this.selectedRow = row; this.selectedHeader = cells.find(item => item.name === column.property) },
  $refs: { ref_table: { columns: [{ property: 'NAME' }, { property: 'NOTE' }] } },
  $message: { warning() {} }
}
component.methods.pasteGrid.call(pasteState, '新甲\t新一\n新乙\t新二')
assert.deepStrictEqual(rows.map(item => [item.NAME, item.NOTE]), [['新甲', '新一'], ['新乙', '新二']])
assert.strictEqual(rows.length, 2, '矩形粘贴不得隐式新增行')
assert.strictEqual(synced, 1)

const actions = []
const keyboardState = {
  isEditableTable: true,
  selectedRow: rows[0], selectedHeader: cells[0], selectedColumn: { property: 'NAME' },
  clickRow: null, editorKind: kind, isDisableds() { return '' },
  beginCellEdit(row, column, replacement) { actions.push(['begin', replacement]) },
  commitActiveEdit() { actions.push(['commit']) },
  cancelActiveEdit() { actions.push(['cancel']) },
  moveSelection(rowOffset, columnOffset) { actions.push(['move', rowOffset, columnOffset]) }
}
const keyEvent = (key, tagName = 'DIV') => ({ key, target: { tagName }, preventDefault() {}, stopPropagation() {}, ctrlKey: false, metaKey: false, altKey: false, shiftKey: false })
component.methods.handleGridKeydown.call(keyboardState, keyEvent('A'))
assert.deepStrictEqual(actions.pop(), ['begin', 'A'])
keyboardState.clickRow = 0
component.methods.handleGridKeydown.call(keyboardState, keyEvent('Enter', 'INPUT'))
assert.deepStrictEqual(actions.splice(0), [['commit'], ['move', 1, 0]])
keyboardState.clickRow = 0
component.methods.handleGridKeydown.call(keyboardState, keyEvent('Escape', 'INPUT'))
assert.deepStrictEqual(actions.splice(0), [['cancel']])
keyboardState.clickRow = 0
component.methods.handleGridKeydown.call(keyboardState, keyEvent('Tab', 'INPUT'))
assert.deepStrictEqual(actions.splice(0), [['commit'], ['move', 0, 1]])

let binaryCommitCalled = false
const binaryCommitState = {
  clickRow: 0,
  clickCell: 2,
  editingOriginalRow: {},
  editingHeader: { name: 'IMAGE_DATA', dataType: 'BLOB' },
  editorKind: kind,
  dataTable: [{ IMAGE_DATA: 'AQID' }],
  inputBlur() { binaryCommitCalled = true }
}
component.methods.commitActiveEdit.call(binaryCommitState)
assert.strictEqual(binaryCommitCalled, false, '已由文件选择完成的 BLOB 不应在保存前重复生成整行快照')
assert.strictEqual(binaryCommitState.clickRow, null)

context.FormData = class { append() {} }
context.tableServer.getBlobData = async () => ({ data: 'AA==' })
context.tableServer.getTableDataBlob = async () => ({ success: true, data: null })
const fileRef = { async open(request, name, localBase64) { this.request = request; this.name = name; this.blobStr = localBase64 } }
const binaryErrors = []
const binaryState = {
  queryResultData: { headerList: [{ name: 'IMAGE_DATA' }], uniqueData: { tableName: 'QA', schemaName: 'JDDBMS_QA', dataSourceId: 1 } },
  sqlInfo: [], oldTableData: [],
  sameCellValue: component.methods.sameCellValue,
  $refs: { fileRef },
  $message: { warning() {}, error(message) { binaryErrors.push(message) } },
  binaryPreviewLoading: false
}
component.methods.openBinaryViewer.call(binaryState, { ROWID: '1', '行号': 1, IMAGE_DATA: 'A' }, { name: 'IMAGE_DATA' })
  .then(async () => {
    await downloadPromise
    assert.strictEqual(downloadState.downloadLoading, false)
    assert.deepStrictEqual(binaryErrors, [], '未知文件类型不应触发空值 includes 异常')
    assert.strictEqual(fileRef.name, 'QA_IMAGE_DATA_1', '下载名应由表、字段和行标识自动生成')
    assert.strictEqual(fileRef.request.rowId, '1', '文件弹窗应收到原表的稳定定位信息')
    const uploads = []
    const uploadFeedback = []
    const uploadReadStates = []
    const uploadState = {
      binaryUploadLoading: false,
      binaryUploadFileName: '',
      $nextTick: async () => {},
      fileToBase64(file) {
        uploadReadStates.push([this.binaryUploadLoading, this.binaryUploadFileName])
        return Promise.resolve(`data:application/octet-stream;base64,${file.value}`)
      },
      syncPendingOperations() { uploads.push(this.row.IMAGE_DATA) },
      $message: {
        success(message) { uploadFeedback.push(message) },
        error(message) { throw new Error(message) }
      },
      row: { IMAGE_DATA: null }
    }
    await component.methods.fileChange.call(uploadState, { name: 'first.bin', value: 'AQID' }, uploadState.row, 'IMAGE_DATA')
    await component.methods.fileChange.call(uploadState, { name: 'empty.bin', value: '' }, uploadState.row, 'IMAGE_DATA')
    assert.deepStrictEqual(uploads, ['AQID', ''], '同一 BLOB 字段可连续替换且保留空文件')
    assert.deepStrictEqual(uploadReadStates, [[true, 'first.bin'], [true, 'empty.bin']], '读取开始前应立即进入加载状态并显示文件名')
    assert.strictEqual(uploadState.binaryUploadLoading, false, '读取完成后应恢复上传控件')
    assert.strictEqual(uploadFeedback.length, 2, '每次读取完成都应给出待保存反馈')

    let saveSawLoading = false
    context.tableServer.addBlobData = async () => {
      saveSawLoading = blobSaveState.blobSaveLoading
      return { success: true, data: true }
    }
    const blobSaveState = {
      blobSaveLoading: false,
      queryResultData: { headerList: binaryHeaders, tableName: 'QA', uniqueData: { dataSourceId: 1, schemaName: 'JDDBMS_QA', databaseType: 'DM' } },
      sqlInfo: [{ type: 'UPDATE' }],
      $nextTick: async () => {},
      $message: { success(message) { uploadFeedback.push(message) } },
      $notify: { error() { throw new Error('保存不应失败') } },
      advancedQuery() {},
      $refs: { ref_table: { clearSelection() {} } },
      clickRow: 1,
      clickCell: 1
    }
    await component.methods.executeBlobData.call(blobSaveState)
    assert.strictEqual(saveSawLoading, true, '发送 BLOB 请求前应先进入保存中状态')
    assert.strictEqual(blobSaveState.blobSaveLoading, false, 'BLOB 保存完成后应恢复保存按钮')

    let commitSawLoading = false
    let blobSaveCalled = false
    const saveEntryState = {
      blobSaveLoading: false,
      sqlInfo: [{ type: 'UPDATE' }],
      requiresBlobSave() { return true },
      $nextTick: async () => {},
      commitActiveEdit() { commitSawLoading = this.blobSaveLoading },
      syncPendingOperations() { throw new Error('已有 BLOB 变更时不应重复扫描整表') },
      async executeBlobData() { blobSaveCalled = true; this.blobSaveLoading = false },
      $notify: { error() { throw new Error('保存不应失败') } }
    }
    await component.methods.executeUpdateDataSql.call(saveEntryState)
    assert.strictEqual(commitSawLoading, true, 'BLOB 保存前置处理开始前应先渲染保存中状态')
    assert.strictEqual(blobSaveCalled, true)

    const requestSource = fs.readFileSync(path.join(__dirname, '../src/utils/request.js'), 'utf8')
    assert(!requestSource.includes('Object.keys(JSON.stringify(requestObj)).length'), '大请求不得按字符生成键数组计算体积')
    assert(requestSource.includes("String(requestObj.data || '').length"), '大请求体积应以字符串长度常量时间计算')
    context.tableServer.getTableDataBlob = async () => ({ success: true, data: '' })
    binaryState.sqlInfo = [{ type: 'CREATE', rowId: 2 }]
    await component.methods.openBinaryViewer.call(binaryState, { '行号': 2, IMAGE_DATA: '' }, { name: 'IMAGE_DATA' })
    assert.strictEqual(fileRef.blobStr, '', '未保存的空 BLOB 仍可下载')
    binaryState.sqlInfo = [{ type: 'UPDATE', rowId: 3, oldDataList: ['A'], dataList: ['AQID'] }]
    context.tableServer.getBlobData = async () => { throw new Error('待保存文件不应重新读取数据库旧值') }
    await component.methods.openBinaryViewer.call(binaryState, { '行号': 3, IMAGE_DATA: 'AQID' }, { name: 'IMAGE_DATA' })
    assert.strictEqual(fileRef.blobStr, 'AQID', '待保存的替换文件应立即可预览和下载')
    let downloadError
    dialogContext.downloadFile = async () => { throw new Error('下载失败（HTTP 500）') }
    await dialog.methods.downLoad.call({
      blobStr: '', fromData: { name: 'QA', type: '.bin' },
      handleClose() { throw new Error('下载失败时不应关闭弹窗') },
      $message: { error(message) { downloadError = message } }
    })
    assert.strictEqual(downloadError, '下载失败（HTTP 500）')

    const utilitySource = fs.readFileSync(path.join(__dirname, '../src/utils/file.js'), 'utf8')
      .replace(/^import .*;?\s*$/gm, '').replace(/export /g, '') + '\nmodule.exports = downloadFile'
    let savedName
    const anchor = { style: {}, click() { savedName = this.download } }
    const utilityContext = {
      module: { exports: {} }, getToken() { return 'test' },
      fetch: async () => ({ ok: true, headers: { get() { return null } }, blob: async () => ({}) }),
      URL: { createObjectURL() { return 'blob:test' }, revokeObjectURL() {} },
      document: { createElement() { return anchor }, body: { appendChild() {}, removeChild() {} } },
      setTimeout() {}
    }
    vm.runInNewContext(utilitySource, utilityContext)
    await utilityContext.module.exports('/exportFile', { fileName: 'QA_PAYLOAD_1', prefix: '.bin' })
    assert.strictEqual(savedName, 'QA_PAYLOAD_1.bin', '缺少响应头时应使用自动生成的文件名')
    utilityContext.fetch = async () => ({ ok: false, status: 500 })
    await assert.rejects(utilityContext.module.exports('/exportFile', {}), /HTTP 500/)
    utilityContext.fetch = async () => ({ ok: true, headers: { get(name) { return name === 'content-type' ? 'application/json' : null } } })
    await assert.rejects(utilityContext.module.exports('/exportFile', {}), /服务器未返回文件/)
    console.log('表数据编辑区结构、类型分流及编辑行为回归测试通过')
  })
  .catch(error => { console.error(error); process.exitCode = 1 })
