const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')
const compiler = require('./helpers/vue-compiler')
const fileContext = { module: { exports: {} }, atob, Response, ReadableStream,
  process: { env: { VUE_APP_BASE_API: '/dev-api' } }, getToken: () => 'test-token' }
vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../src/utils/file.js'), 'utf8')
  .replace(/^import .*;?\s*$/gm, '').replace(/export /g, '') +
  '\nmodule.exports = {downloadBinaryFile, binaryFileType}', fileContext)
const { downloadBinaryFile, binaryFileType } = fileContext.module.exports
const png = Buffer.from('\x89PNG\r\n\x1a\n', 'latin1').toString('base64')
const jpg = Buffer.from('\xff\xd8\xff', 'latin1').toString('base64')
const source = compiler.parseComponent(fs.readFileSync(path.join(__dirname,
  '../src/views/main/workspace/components/renderSearchResult/filedialog.vue'), 'utf8'))
const pending = [], cancellations = []
const context = { module: { exports: {} }, AbortController, binaryFileType,
  tableServer: { previewBlob: request => new Promise(resolve => pending.push({ request, resolve })),
    cancelBlob: request => { cancellations.push(request); return Promise.resolve({ success: true }) } } }
vm.runInNewContext(source.script.content.replace(/^import .*;?\s*$/gm, '').replace('export default', 'module.exports ='), context)
const component = context.module.exports

async function main() {
  assert.strictEqual(binaryFileType(png), '.png')
  assert.strictEqual(binaryFileType(jpg), '.jpg')
  assert.strictEqual(binaryFileType(''), '.bin')
  const chunks = [new Uint8Array([1, 2]), new Uint8Array([3, 4, 5])]
  let writes, closed, aborted, progress
  const fileHandle = { async createWritable() { return {
    async write(chunk) { writes.push(...chunk) }, async close() { closed++ }, async abort() { aborted++ }
  } } }
  const reset = () => { writes = []; closed = 0; aborted = 0; progress = [] }
  const response = length => new Response(new ReadableStream({ start(controller) {
    chunks.forEach(chunk => controller.enqueue(chunk)); controller.close()
  } }), { headers: { 'Content-Length': String(length) } })
  reset()
  fileContext.fetch = async (url, options) => {
    assert.strictEqual(url, '/dev-api/api/rdb/dml/blob/download')
    assert.strictEqual(JSON.parse(options.body).readId, 'read-1')
    return response(5)
  }
  assert.strictEqual(await downloadBinaryFile({ readId: 'read-1' }, { fileHandle, onProgress: count => progress.push(count) }), 5)
  assert.deepStrictEqual(writes, [1, 2, 3, 4, 5])
  assert.deepStrictEqual(progress, [2, 5])
  assert.strictEqual(closed, 1)
  assert.strictEqual(aborted, 0)
  reset()
  fileContext.fetch = async () => response(8)
  await assert.rejects(downloadBinaryFile({}, { fileHandle }), /传输未完成/)
  assert.strictEqual(closed, 0)
  assert.strictEqual(aborted, 1, '截断文件不得被保存为下载完成')
  for (const fixture of [new Response(null, { status: 204 }), new Response(JSON.stringify({ errorMessage: '没有读取权限' }),
    { headers: { 'Content-Type': 'application/json' } })]) {
    reset(); fileContext.fetch = async () => fixture
    await assert.rejects(downloadBinaryFile({}, { fileHandle }), /NULL|没有读取权限/)
    assert.strictEqual(aborted, 1)
    assert.strictEqual(closed, 0)
  }
  reset()
  const controller = new AbortController()
  fileContext.fetch = async (_, { signal }) => {
    assert.strictEqual(signal, controller.signal)
    throw new DOMException('Aborted', 'AbortError')
  }
  await assert.rejects(downloadBinaryFile({}, { fileHandle, signal: controller.signal }), { name: 'AbortError' })
  assert.strictEqual(aborted, 1)

  const dialog = { ...component.data(), ...component.methods }
  const request = { dataSourceId: 1, blobColumn: 'PAYLOAD' }
  const first = dialog.open(request, 'first')
  assert(dialog.dialogVisible && dialog.loading, '网络返回前必须展示读取状态和取消按钮')
  const signal = dialog._readController.signal
  dialog.handleClose()
  assert(signal.aborted)
  assert.strictEqual(cancellations.length, 1)
  const second = dialog.open(request, 'second')
  pending[1].resolve({ success: true, data: { tooLarge: true, prefix: png, byteLength: 16 * 1024 * 1024 } })
  await second
  pending[0].resolve({ success: true, data: { base64: jpg } })
  await first
  assert.strictEqual(dialog.fromData.name, 'second', '取消的迟到响应不得覆盖新弹窗')
  assert(dialog.tooLarge && !dialog.isShow && !dialog.loading)
  assert.strictEqual(dialog.blobStr, '')
  for (const preview of [{ nullValue: true }, { base64: '' }, { base64: png }]) {
    const opening = dialog.open(request, 'next')
    pending[pending.length - 1].resolve({ success: true, data: preview })
    await opening
    assert.strictEqual(dialog.nullValue, !!preview.nullValue)
    assert.strictEqual(dialog.isShow, preview.base64 === png)
  }
  console.log('BLOB 分块写盘、截断/失败中止、取消、迟到响应及预览边界回归通过')
}
main().catch(error => { console.error(error); process.exitCode = 1 })
