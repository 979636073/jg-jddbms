const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')
const compiler = require('./helpers/vue-compiler')

const file = path.join(__dirname, '../src/views/main/workspace/SideBar.vue')
const descriptor = compiler.parseComponent(fs.readFileSync(file, 'utf8'))
assert.deepStrictEqual(compiler.compile(descriptor.template.content).errors, [])
assert(descriptor.template.content.includes("data.treeNodeType === 'objectGroup' && node.isLeaf"),
  '无子节点的对象分组应在箭头消失后保留同宽占位')

const context = {
  module: { exports: {} },
  connectionServer: {}, sqlServer: {}, viewServer: {}, tableServer: {}, userServer: {}, tableSpaceServer: {},
  SqlPreview: {}, CreateOption: {}, DataSourceSharingDialog: {}, TableTransferDialog: {},
  SchemaSqlTransferDialog: {}, DmpTransferDialog: {}, addTableSpaceVue: {}, addSchema: {},
  addUser: {}, addTable: {}, addView: {}, ViewImport: {}, draggable: {}, database: {},
}
vm.runInNewContext(descriptor.script.content.replace(/^import .*;?\s*$/gm, '')
  .replace('export default', 'module.exports ='), context, { filename: file })
const click = context.module.exports.methods.handleResourceNodeClick
const events = []
const state = {
  type: 'views', schema: 'OLD', detailDataSource: ['existing'],
  isDataSourceConnected: () => true,
  activateDataSourceContext() {},
  $emit: (...args) => events.push(args),
  $nextTick: () => Promise.resolve(),
  getTableDataList: () => events.push(['loadList']),
}
const node = { isLeaf: false, expanded: true, collapse() {} }
const dataSource = { id: 1 }

async function run() {
  const methods = context.module.exports.methods
  for (const treeNodeType of ['schema', 'database']) {
    let children
    await methods.loadResourceNode.call(methods, { level: 2, data: {
      treeNodeType, dataSource: { id: 1, supportSchema: false },
      extraParams: { databaseName: 'QA_DB', schemaName: 'QA_SCHEMA' },
    } }, value => { children = value })
    assert.deepStrictEqual(Array.from(children, child => child.label),
      ['数据表', '普通视图', '物化视图', '函数', '存储过程', '触发器', '模式总览'],
      '模式总览应位于触发器之后，其他对象分组顺序不变')
    assert.strictEqual(children[6].treeNodeType, 'schemaOverview')
    assert.strictEqual(children[6].leaf, true)
    assert.strictEqual(children[6].extraParams.schemaName, 'QA_SCHEMA')
  }
  process.env.VUE_ENV = 'server'
  const createTree = require('./helpers/resource-tree')

  const emptyTree = createTree({
    nodeKey: 'key', lazy: true,
    load(treeNode, resolve) {
      resolve(treeNode.level === 0
        ? [{ key: 'empty-group', treeNodeType: 'objectGroup', leaf: false }]
        : [])
    },
  })
  const emptyGroupNode = emptyTree.getNode('empty-group')
  emptyGroupNode.expand()
  assert.strictEqual(emptyGroupNode.isLeaf, true, '空分组加载后 Element UI 会将其识别为叶节点')

  await click.call(state, { treeNodeType: 'objectGroup', groupType: 'tables', dataSource,
    extraParams: { schemaName: 'TEST' } }, node)
  assert.strictEqual(state.type, 'views', '点数据表分组只展开树，不切换右侧视图')
  assert.strictEqual(state.schema, 'OLD')
  assert.deepStrictEqual(state.detailDataSource, ['existing'])
  assert.strictEqual(events.length, 0, '点数据表分组不应请求右侧列表')

  await click.call(state, { treeNodeType: 'objectGroup', groupType: 'views', dataSource,
    extraParams: { schemaName: 'TEST' } }, node)
  assert(events.some(([name]) => name === 'isType'), '其他对象分组仍保留原有列表行为')
  console.log('空分组缩进及数据表分组右侧不跳总览回归测试通过')
}

run().catch(error => { console.error(error); process.exitCode = 1 })
