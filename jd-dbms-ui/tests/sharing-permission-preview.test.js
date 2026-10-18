const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')
const compiler = require('./helpers/vue-compiler')

async function main() {
  const source = compiler.parseComponent(fs.readFileSync(path.join(__dirname,
    '../src/views/main/workspace/components/DataSourceSharingDialog.vue'), 'utf8'))
  assert.deepStrictEqual(compiler.compile(source.template.content).errors, [])
  const pending = []
  const connectionServer = {
    getEffectivePermissions: (id, userId) => new Promise(resolve => pending.push({ id, userId, resolve })),
    getSharingAudit: async () => ({ success: false, errorMessage: '没有管理权限' }),
    searchSharingUsers: async () => ({ success: true, data: [{ userId: 42, userName: 'reader' }] })
  }
  const context = { module: { exports: {} }, connectionServer }
  vm.runInNewContext(source.script.content.replace(/^import .*;?\s*$/gm, '')
    .replace('export default', 'module.exports ='), context)
  const component = context.module.exports
  const state = Object.assign(component.data(), { dataSourceId: 226, $message: { error: () => {} } })
  Object.entries(component.methods).forEach(([key, method]) => { state[key] = method.bind(state) })

  await state.loadPreview()
  assert.strictEqual(pending.length, 0, '未选择用户不发请求')
  state.previewUserId = 42
  const first = state.loadPreview()
  state.previewUserId = 43
  const second = state.loadPreview()
  pending[1].resolve({ success: true, data: { userId: 43, actions: [{ code: 'DATA_READ', allowed: true }] } })
  await second
  pending[0].resolve({ success: true, data: { userId: 42, actions: [] } })
  await first
  assert.strictEqual(state.preview.userId, 43, '慢响应不能覆盖当前用户')

  const refresh = state.loadPreview()
  pending[2].resolve({ success: true, data: { userId: 43, actions: [{ code: 'DATA_READ', allowed: false }] } })
  await refresh
  assert.strictEqual(state.preview.actions[0].allowed, false, '刷新显示撤权后的结果')

  const oldConnection = state.loadPreview()
  state.dataSourceId = 227
  state.resetInspection()
  pending[3].resolve({ success: true, data: { userId: 43, actions: [] } })
  await oldConnection
  assert.strictEqual(state.preview, null, '切换连接不保留旧权限')
  assert.strictEqual(state.previewLoading, false)

  state.auditRecords = [{ id: 1 }]
  await state.loadAudit()
  assert.strictEqual(state.auditRecords.length, 0)
  assert.strictEqual(state.auditError, '没有管理权限', '审计失败必须可见')
  assert.strictEqual(state.auditLoading, false)
  await state.searchPreviewUsers('reader')
  assert.strictEqual(state.previewCandidates[0].userId, 42)
  assert.strictEqual(state.sourceLabel('TEAM:9'), '团队 #9')
  assert.strictEqual(state.actionLabel('DATA_READ'), '读取数据')
  console.log('共享权限预览、撤权刷新、请求竞态和审计错误回归通过')
}

main().catch(error => { console.error(error); process.exitCode = 1 })
