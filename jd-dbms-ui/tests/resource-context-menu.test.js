const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')

const file = path.join(__dirname, '../src/views/main/workspace/SideBar.vue')
const script = fs.readFileSync(file, 'utf8').match(/<script>([\s\S]*?)<\/script>/)[1]
  .replace(/^import .*;?\s*$/gm, '').replace('export default', 'module.exports =')
const listeners = []
const context = {
  module: { exports: {} },
  document: {
    addEventListener(...args) { listeners.push(['add', ...args]) },
    removeEventListener(...args) { listeners.push(['remove', ...args]) },
  },
  connectionServer: {}, sqlServer: {}, viewServer: {}, tableServer: {}, userServer: {}, tableSpaceServer: {},
  SqlPreview: {}, CreateOption: {}, DataSourceSharingDialog: {}, TableTransferDialog: {},
  SchemaSqlTransferDialog: {}, DmpTransferDialog: {}, addTableSpaceVue: {}, addSchema: {},
  addUser: {}, addTable: {}, addView: {}, ViewImport: {}, draggable: {}, database: {},
  uuidv4() {}, copyText() {},
}
vm.runInNewContext(script, context, { filename: file })
const component = context.module.exports
const state = { contextMenuVisible: true, closeMenu: component.methods.closeMenu }

component.watch.contextMenuVisible.call(state, true)
assert(listeners.some(([action, name, handler, capture]) =>
  action === 'add' && name === 'pointerdown' && handler === state.closeMenu && capture === true),
  '右键菜单应在捕获阶段监听外部点击，避免树节点阻止冒泡后菜单残留')

component.methods.closeMenu.call(state, { target: { closest: () => ({}) } })
assert.strictEqual(state.contextMenuVisible, true, '点击菜单自身不应抢先关闭，避免操作按钮失效')
component.methods.closeMenu.call(state, { target: { closest: () => null } })
assert.strictEqual(state.contextMenuVisible, false, '点击其他树节点应关闭菜单')

component.watch.contextMenuVisible.call(state, false)
assert(listeners.some(([action, name, handler, capture]) =>
  action === 'remove' && name === 'pointerdown' && handler === state.closeMenu && capture === true),
  '菜单关闭后应移除同一捕获监听器')

async function verifyViewDeletion() {
  let request
  context.viewServer.viewDelete = async value => {
    request = value
    return { success: true }
  }
  context.sqlServer.viewDelete = async value => {
    request = value
    return { success: true }
  }
  const parent = { loaded: true, loadData() {} }
  const messages = []
  const message = value => messages.push(value)
  message.success = value => messages.push(value)
  message.error = value => messages.push(value)
  const deleteState = {
    $confirm: async () => {},
    $message: message,
  }
  component.methods.deleteTable.call(deleteState, {
    label: 'QA_MV', treeNodeType: 'view',
    extraParams: { dataSourceId: 7, schemaName: 'QA', tableName: 'QA_MV', viewType: 'MATERIALIZED VIEW' },
  }, { parent })
  await new Promise(resolve => setImmediate(resolve))
  assert.strictEqual(request.name, 'QA_MV', '删除视图必须传后端要求的 name，而不是仅传 tableName')
  assert.strictEqual(request.viewType, 'MATERIALIZED VIEW', '删除物化视图必须保留类型')
  assert(messages.includes('删除成功'))
}

verifyViewDeletion().then(() => console.log('资源树菜单和视图删除回归测试通过'))
  .catch(error => { console.error(error); process.exitCode = 1 })
