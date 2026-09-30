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
  return context.module.exports
}

async function main() {
  const deleteCalls = []
  const taskCalls = []
  const context = {
    module: { exports: {} },
    connectionServer: {}, sqlServer: {}, viewServer: {}, userServer: {}, tableSpaceServer: {},
    tableServer: {
      async deleteTableData(params) {
        deleteCalls.push(params)
        return { success: true }
      },
      async deleteTableDataTask(params) {
        taskCalls.push(params)
        return { success: true }
      },
    },
    SqlPreview: {}, CreateOption: {}, addTableSpaceVue: {}, addUser: {}, addTable: {}, addView: {}, ViewImport: {}, draggable: {},
    database: {}, uuidv4() { return 'node-id' }, copyText() {},
    window: { innerWidth: 1280, innerHeight: 720 },
  }
  const sideBar = loadComponent(path.join(__dirname, '../src/views/main/workspace/SideBar.vue'), context)

  const successMessages = []
  const emitted = []
  let refreshCount = 0
  const state = {
    treeTableSelections: { group: ['T_A', 'T_B'] },
    selectedTreeTableNames: sideBar.methods.selectedTreeTableNames,
    $confirm: async () => {},
    $delete(target, key) { delete target[key] },
    $message: {
      success(message) { successMessages.push(message) },
      warning() {},
      error(message) { throw new Error(message) },
    },
    refreshResourceNode() { refreshCount += 1 },
    $EventBus: { $emit(name) { emitted.push(name) } },
  }
  const group = {
    key: 'group',
    extraParams: { dataSourceId: 7, schemaName: 'APP', databaseName: '' },
  }
  await sideBar.methods.deleteSelectedTreeTables.call(state, group, {})
  assert.strictEqual(deleteCalls.length, 1)
  assert.strictEqual(JSON.stringify(deleteCalls[0].tableNames), JSON.stringify(['T_A', 'T_B']))
  assert.strictEqual(deleteCalls[0].schemaName, 'APP')
  assert.strictEqual(refreshCount, 1)
  assert.strictEqual(successMessages[0], '删除成功')
  assert.strictEqual(state.treeTableSelections.group, undefined)

  state.treeTableSelections.group = Array.from({ length: 21 }, (_, index) => `T_${index}`)
  await sideBar.methods.deleteSelectedTreeTables.call(state, group, {})
  assert.strictEqual(taskCalls.length, 1, '超过阈值的批量删除应转为后台任务')
  assert.strictEqual(deleteCalls.length, 1, '后台任务模式不应调用同步删除接口')
  assert.strictEqual(emitted[0], 'openTaskCenter')

  const menuState = {
    type: 'tables',
    dataInfo: { dataSource: { id: 7 } },
    schema: 'APP',
    copyschemaName: 'OLD',
    contextMenuVisible: false,
    contextMenu_top: 0,
    contextMenu_left: 0,
    context_current_node: null,
    context_current_item: {},
  }
  sideBar.methods.creatRightMenu.call(menuState, { name: 'T_A' }, {
    clientX: 1275,
    clientY: 715,
    preventDefault() {},
  }, { id: 'node' })
  assert.strictEqual(menuState.contextMenu_left, 1052, '右键菜单不应超出视口右边界')
  assert.strictEqual(menuState.contextMenu_top, 406, '右键菜单不应超出视口底边界')
  assert.strictEqual(menuState.contextMenuVisible, true)
  assert.strictEqual(menuState.context_current_item.name, 'T_A')

  console.log('数据源树批量删除、后台任务分流及右键菜单定位回归测试通过')
}

main().catch(error => {
  console.error(error)
  process.exitCode = 1
})
