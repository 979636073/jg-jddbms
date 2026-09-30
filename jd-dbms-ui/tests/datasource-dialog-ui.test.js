const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')
const compiler = require('vue-template-compiler')

function loadComponent(file, context) {
  const source = fs.readFileSync(path.join(__dirname, '../src/', file), 'utf8')
  const descriptor = compiler.parseComponent(source)
  assert.deepStrictEqual(compiler.compile(descriptor.template.content).errors, [])
  const script = descriptor.script.content.replace(/^import .*;?\s*$/gm, '').replace('export default', 'module.exports =')
  context.module = { exports: {} }
  vm.runInNewContext(script, context)
  return { component: context.module.exports, source }
}

async function main() {
  const calls = []
  let response = { success: true }
  const dataSource = { id: 7, alias: '生产库', user: 'APP', type: 'ORACLE', host: 'db.local', port: '1528', serviceName: 'APPDB', driverConfig: { jdbcDriver: 'driver' } }
  const api = {
    async getDetails(id) { calls.push(['details', id]); return { success: true, data: dataSource } },
    async getDriverList() { calls.push(['drivers']); return { success: true, data: { defaultDriverConfig: { jdbcDriver: 'driver' } } } },
    async connect(params) { calls.push(['connect', params]); return response },
    async test(params) { calls.push(['test', params]); return response },
    async save(params) { calls.push(['save', params]); return { success: true, data: { dataSource } } },
    async update(params) { calls.push(['update', params]); return response },
  }
  const { component: dialog, source } = loadComponent('views/main/workspace/components/linkServerDialog/index.vue', { connectionServer: api })
  assert(!source.includes('<el-table'), '弹窗不再重复展示数据源列表')
  assert(source.includes(':disabled="isReadonly || connecting || loading"'), '连接信息应整体只读')
  const events = []
  const errors = []
  const state = {
    mode: 'create', visibleDialog: true, selectedDataSourceId: null,
    $refs: { formData: { clearValidate() {}, validate: async fn => fn(true) } },
    $nextTick: fn => fn && fn(),
    $message: { success() {}, error: message => errors.push(message) },
    $emit: (...args) => events.push(args),
  }
  Object.assign(state, dialog.data.call(state), dialog.methods)
  Object.defineProperty(state, 'isReadonly', { get: () => dialog.computed.isReadonly.call(state) })
  await state.initializeForm()
  assert.strictEqual(calls.length, 0, '新增模式不能加载既有数据源或连接数据库')
  for (const key of ['id', 'connectionName', 'username', 'password', 'host', 'port', 'type', 'input']) assert.strictEqual(state.formData[key], '', `${key} 应初始为空`)

  state.mode = 'connect'; state.selectedDataSourceId = 7
  await state.initializeForm()
  assert.strictEqual(state.formData.port, '1528', '加载 Oracle 配置不能将自定义端口重置成默认端口')
  assert.strictEqual(state.formData.input, 'APPDB')
  assert.strictEqual(state.formData.password, '********')
  assert.deepStrictEqual(calls.map(item => item[0]), ['details'], '打开连接弹窗只获取详情')
  state.saveConnection('save')
  assert.strictEqual(calls.length, 1, '只读连接模式禁止保存写入')
  response = { success: false, errorMessage: '无法连接' }
  await state.submit()
  assert.strictEqual(events.length, 0, '连接失败不允许标记已连接或关闭弹窗')
  assert.strictEqual(errors.pop(), '无法连接')
  response = { success: true }
  await state.submit()
  assert.strictEqual(events[0][0], 'connected')
  assert.strictEqual(events[0][1].id, 7)
  assert.strictEqual(JSON.stringify(calls.at(-1)), JSON.stringify(['connect', { id: 7 }]), '连接仅提交 id，不能修改配置')
  state.connecting = true
  const callCount = calls.length
  await state.submit()
  assert.strictEqual(calls.length, callCount, '连接过程中禁止重复提交')
  state.connecting = false

  state.mode = 'edit'
  await state.initializeForm()
  assert.strictEqual(state.formData.password, '', '修改不展示原密码，留空保留')
  state.formData.connectionName = '修改后的连接名'
  await state.$refs.formData.validate(() => state.saveConnection('update'))
  await new Promise(resolve => setImmediate(resolve))
  assert.strictEqual(calls.at(-1)[0], 'update', '修改必须使用更新接口，不得创建或连接')
  assert.strictEqual(calls.at(-1)[1].alias, '修改后的连接名')
  assert.strictEqual(events.at(-1)[0], 'saved')
  assert.strictEqual(events.at(-1)[1].id, 7)
  state.mode = 'create'
  await state.initializeForm()
  assert.strictEqual(state.formData.id, '', '修改后新增不能残留 id')
  assert.strictEqual(state.formData.connectionName, '')
  state.handleTypeChange('ORACLE')
  assert.strictEqual(state.formData.port, '1521')
  assert.strictEqual(state.formData.radio, 'serviceName')
  Object.assign(state.formData, { connectionName: '新库', username: 'APP', host: 'db.local', password: 'test-password', input: 'APPDB' })
  state.submit()
  await new Promise(resolve => setImmediate(resolve))
  assert.strictEqual(calls.at(-1)[0], 'save')
  assert.strictEqual(events.at(-1)[1].dataSource.id, 7)
  state.fromDataRules.port[0].validator({}, '1521abc', error => assert(error, '端口应严格校验纯数字'))

  const sideContext = {
    connectionServer: {
      async getList() { calls.push(['list']); return { success: true, data: { data: [dataSource] } } },
      async closeDataSource(params) { calls.push(['close', params]); return { success: true } },
    },
    sqlServer: {}, viewServer: {}, tableServer: {}, userServer: {}, tableSpaceServer: {},
    SqlPreview: {}, CreateOption: {}, addTableSpaceVue: {}, addUser: {}, addTable: {}, addView: {}, ViewImport: {}, draggable: {}, database: {},
    sessionStorage: { removeItem() {} },
  }
  const { component: sidebar } = loadComponent('views/main/workspace/SideBar.vue', sideContext)
  const treeEvents = []
  const tree = {
    ...sidebar.methods,
    databaseTree: [], resourceTreeLoading: false,
    $route: { query: { dataSourceId: 7, tableName: 'OLD_TABLE' } }, dataInfo: { dataSource },
    $store: { state: { workspace: { connectedDataSourceIds: {} } }, commit() {} },
    $emit: (...args) => treeEvents.push(args),
    $message: { error: message => { throw new Error(message) } },
    activateDataSourceContext() { throw new Error('首页不允许自动激活数据源') },
  }
  calls.length = 0
  await tree.initializeResourceTree()
  assert.deepStrictEqual(calls.map(item => item[0]), ['list'], '首次进入即使保留旧表路由也只能加载保存的数据源列表')
  assert.strictEqual(tree.databaseTree[0].leaf, true, '未连接根节点不能展开加载')
  await tree.handleResourceNodeClick(tree.databaseTree[0])
  assert.strictEqual(treeEvents[0][0], 'manageDataSources')
  assert.strictEqual(treeEvents[0][1].mode, 'connect')
  assert.strictEqual(calls.length, 1, '点击根节点只打开弹窗，不连接')
  tree.openDataSourceManager(dataSource)
  assert.strictEqual(treeEvents.at(-1)[1].mode, 'edit')
  tree.openDataSourceManager()
  assert.strictEqual(treeEvents.at(-1)[1].mode, 'create')
  tree.$store.state.workspace.connectedDataSourceIds[7] = true
  const connectedNode = tree.createDataSourceNode(dataSource)
  assert.strictEqual(connectedNode.leaf, false)
  let expanded = 0
  tree.activateDataSourceContext = () => {}
  await tree.handleResourceNodeClick(connectedNode, { isLeaf: false, expanded: false, expand() { expanded++ } })
  assert.strictEqual(expanded, 1, '已连接节点点击正常展开，不再弹连接框')
  const closedCommits = []
  tree.$confirm = async () => {}
  tree.$message.success = () => {}
  tree.$store.commit = (type, data) => closedCommits.push([type, data])
  tree.$EventBus = { $emit() {} }
  tree.closeDataSourceFromTree(dataSource)
  await new Promise(resolve => setImmediate(resolve))
  assert.strictEqual(calls.at(-1)[0], 'close', '原关闭连接能力应在树上保留')
  assert.strictEqual(closedCommits[0][0], 'SET_DATASOURCE_CONNECTED')
  assert.strictEqual(closedCommits[0][1].connected, false)
  console.log('数据源按需连接、只读连接、新增空表单、编辑保存与失败路径回归测试通过')
}

main().catch(error => { console.error(error); process.exitCode = 1 })
