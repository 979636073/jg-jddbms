const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')
const compiler = require('vue-template-compiler')

const file = path.join(__dirname, '../src/views/main/workspace/SideBar.vue')
const descriptor = compiler.parseComponent(fs.readFileSync(file, 'utf8'))
assert.deepStrictEqual(compiler.compile(descriptor.template.content).errors, [])
const context = {
  module: { exports: {} },
  connectionServer: {
    getSchemaList: async () => ({ data: Array.from({ length: 1011 }, (_, index) => ({
      name: `CODEX_S_${String(index + 1).padStart(4, '0')}`,
    })) }),
  },
  sqlServer: {}, viewServer: {}, tableServer: {}, userServer: {}, tableSpaceServer: {},
  SqlPreview: {}, CreateOption: {}, DataSourceSharingDialog: {}, TableTransferDialog: {}, SchemaSqlTransferDialog: {}, DmpTransferDialog: {}, addTableSpaceVue: {}, addSchema: {}, addUser: {}, addTable: {},
  addView: {}, ViewImport: {}, draggable: {}, database: {},
}
vm.runInNewContext(descriptor.script.content.replace(/^import .*;?\s*$/gm, '')
  .replace('export default', 'module.exports ='), context, { filename: file })
const methods = context.module.exports.methods
const state = {
  resourceVersion: () => 0,
  resourceFilter: '',
  createExtraParams: methods.createExtraParams,
  containerPageChildren: methods.containerPageChildren,
  $message: { error: message => { throw new Error(message) } },
}
const data = {
  key: 'datasource-13-schemas', label: '模式', treeNodeType: 'schemaCollection',
  dataSource: { id: 13, alias: '测试库' },
}

methods.loadResourceContainers.call(state, data, children => {
  assert.strictEqual(children.length, 101, '初次展开只渲染 100 个模式和翻页入口')
  assert.strictEqual(children[0].label, 'CODEX_S_0001')
  assert.strictEqual(children[100].treeNodeType, 'containerPage')
  assert.strictEqual(children[100].offset, 100)
  const second = methods.containerPageChildren(data, '', 100)
  assert.strictEqual(second[0].label, '上一批模式')
  assert.strictEqual(second[1].label, 'CODEX_S_0101')
  const search = methods.containerPageChildren(data, 'CODEX_S_0999', 0)
  assert.strictEqual(search.length, 1, '未展开的末尾模式也应可搜索')
  assert.strictEqual(search[0].label, 'CODEX_S_0999')
  const end = methods.containerPageChildren(data, '', 1000)
  assert.strictEqual(end[1].label, 'CODEX_S_1001')
  assert.strictEqual(end.length, 12, '末页应包含上一批入口和剩余 11 个模式')
  console.log('千模式资源树分页与跨页搜索测试通过')
}).catch(error => { console.error(error); process.exit(1) })

const viewGroup = {
  key: 'views', groupType: 'views',
  objectChildren: Array.from({ length: 1001 }, (_, index) => ({ label: `V_${String(index + 1).padStart(4, '0')}` })),
}
const firstViews = methods.objectGroupPageChildren(viewGroup, '', 0)
assert.strictEqual(firstViews.length, 101)
assert.strictEqual(firstViews[100].label, '下一批视图（100 / 1001）')
const lastViews = methods.objectGroupPageChildren(viewGroup, '', 1000)
assert.strictEqual(lastViews.length, 2)
assert.strictEqual(lastViews[1].label, 'V_1001')
assert.strictEqual(methods.objectGroupPageChildren(viewGroup, 'V_0999', 0)[0].label, 'V_0999')
