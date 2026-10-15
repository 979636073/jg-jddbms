const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')
const compiler = require('vue-template-compiler')

async function main() {
  const source = compiler.parseComponent(fs.readFileSync(path.join(__dirname,
    '../src/views/main/databaseMonitor/index.vue'), 'utf8'))
  assert.deepStrictEqual(compiler.compile(source.template.content).errors, [])
  const pending = []
  const context = { module: { exports: {} }, getMonitorState: id => new Promise(resolve => pending.push({ id, resolve })),
    getDatabaseSnapshot: async id => ({ success: true, data: { dataSourceId: id } }), clearInterval,
    window: { removeEventListener() {} } }
  vm.runInNewContext(source.script.content.replace(/^import .*;?\s*$/gm, '')
    .replace('export default', 'module.exports ='), context)
  const component = context.module.exports
  const state = Object.assign(component.data(), { dataSourceId: 1, $nextTick() {} })
  Object.entries(component.methods).forEach(([key, method]) => { state[key] = method.bind(state) })
  const result = id => ({ success: true, data: { config: { dataSourceId: id, revision: 'r' + id, activeThreshold: 30 }, canManage: id === 2, history: [], alerts: [] } })
  const old = state.refresh()
  state.dataSourceId = 2
  const current = state.refresh()
  pending[1].resolve(result(2))
  await current
  pending[0].resolve(result(1))
  await old
  assert.strictEqual(state.config.dataSourceId, 2, '旧连接慢响应不能覆盖新连接')
  assert.strictEqual(state.snapshot.dataSourceId, 2)
  assert.strictEqual(state.canManage, true)
  state.config.activeThreshold = 50
  const refresh = state.refresh()
  pending[2].resolve(result(2))
  await refresh
  assert.strictEqual(state.config.activeThreshold, 50, '定时刷新不覆盖尚未保存的阈值')
  const offline = state.refresh()
  pending[3].resolve({ success: false, errorMessage: '无监控权限' })
  await offline
  assert.strictEqual(state.loadError, '无监控权限')
  const destroyed = state.refresh()
  component.beforeDestroy.call(state)
  pending[4].resolve(result(1))
  await destroyed
  assert.strictEqual(state.config.dataSourceId, 2, '销毁后不写入异步响应')
  console.log('监控连接切换竞态、阈值草稿、权限错误及销毁清理回归通过')
}
main().catch(error => { console.error(error); process.exitCode = 1 })
