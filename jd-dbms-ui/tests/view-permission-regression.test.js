const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')
const compiler = require('vue-template-compiler')

const workspaceFile = path.join(__dirname, '../src/views/main/workspace/SideBar.vue')
const panelFile = path.join(__dirname, '../src/views/main/workspace/components/renderViewEditor/ViewGrantPanel.vue')

function readComponent(file) {
  return compiler.parseComponent(fs.readFileSync(file, 'utf8'))
}

function findNode(node, predicate) {
  if (!node) return null
  if (predicate(node)) return node
  for (const branch of (node.ifConditions || []).slice(1)) {
    const found = findNode(branch.block, predicate)
    if (found) return found
  }
  for (const child of node.children || []) {
    const found = findNode(child, predicate)
    if (found) return found
  }
  return null
}

async function main() {
  const sidebar = readComponent(workspaceFile)
  const template = compiler.compile(sidebar.template.content)
  assert.deepStrictEqual(template.errors, [])
  const viewName = findNode(template.ast, node => node.tag === 'span' && node.attrsMap.class === 'table_name')
  assert(viewName && viewName.events && viewName.events.click, '视图名称点击入口不存在')
  assert(!viewName.events.click.modifiers || !viewName.events.click.modifiers.stop,
    '视图名称不能阻断外层的打开视图事件')

  const panel = readComponent(panelFile)
  const source = panel.script.content
    .replace(/^import .*;?\s*$/gm, '')
    .replace('export default', 'module.exports =')
  const context = {
    module: { exports: {} },
    tableServer: {},
    viewServer: {
      grantViewSelect: async params => {
        assert.strictEqual(params.dataSourceId, 42)
        assert.strictEqual(params.tableName, 'TEST_MV')
        return { success: false, errorMessage: 'Grantor no granted privilege' }
      }
    },
    GrantList: {}
  }
  vm.runInNewContext(source, context, { filename: panelFile })
  const component = context.module.exports
  const messages = []
  let listReloaded = false
  const state = {
    queryResultData: { uniqueData: { dataSourceId: 42, schemaName: 'SYSDBA', tableName: 'TEST_MV' } },
    grantUser: 'JDDBMS',
    grantDialogVisible: true,
    grantSubmitting: false,
    grantParams: component.methods.grantParams,
    queryGrantList: () => { listReloaded = true },
    $message: { error: message => messages.push(message) }
  }
  await component.methods.grantViewSelect.call(state)
  assert.deepStrictEqual(messages, ['Grantor no granted privilege'])
  assert.strictEqual(state.grantDialogVisible, true)
  assert.strictEqual(state.grantSubmitting, false)
  assert.strictEqual(listReloaded, false)
  console.log('视图点击与权限不足反馈回归测试通过')
}

main().catch(error => {
  console.error(error)
  process.exitCode = 1
})
