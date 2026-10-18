const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')
const compiler = require('./helpers/vue-compiler')
const source = fs.readFileSync(path.join(__dirname, '../src/views/main/dataMigration/index.vue'), 'utf8')
const page = compiler.parseComponent(source)
assert.deepStrictEqual(compiler.compile(page.template.content).errors, [])
assert(source.includes('达梦的 TEXT 与 NCLOB 均显示为 TEXT，默认迁为 CLOB'))
assert(source.includes('s.row.sourceType === \'TEXT\'') && source.includes('value="NCLOB" label="NCLOB"'), '达梦 TEXT 字段须允许显式选择 NCLOB')
const pending = []
const migration = {
  delete: id => { migration.deletedId = id; return Promise.resolve(true) },
  get: () => new Promise(resolve => pending.push(resolve)),
  objects: () => new Promise(resolve => pending.push(resolve)),
  objectRetryCheck: () => new Promise(resolve => pending.push(resolve)),
  retryDraft: (id, request) => { migration.lastDraftRetry = { id, request }; return new Promise(resolve => pending.push(resolve)) },
  resume: (id, request) => { migration.lastRetry = request; return Promise.resolve({ id, status: 'MIG_RUNNING', config: {} }) }
}
const connectionRows = [
  { id: 1, alias: '  自定义连接  ', user: 'DM_QA', type: 'DM' },
  { id: 2, alias: '@host.docker.internal', user: 'SYSTEM', type: 'ORACLE' },
  { id: 3, alias: '@host.docker.internal', user: 'ORACLE_QA', type: 'ORACLE' },
  { id: 4, alias: '', userName: '旧连接用户', type: 'DM' },
  { id: 5, alias: '', type: 'DM' },
  { id: 6, alias: '不支持', type: 'MYSQL' }
]
const connectionServer = { getList: () => Promise.resolve({ success: true, data: { data: connectionRows } }) }
const sandbox = { migration, connectionServer, JSON, Blob, setTimeout: () => 99, clearTimeout: () => {}, console }
vm.runInNewContext(page.script.content.replace(/^import .*$/gm, '').replace('export default', 'this.component ='), sandbox)
const component = sandbox.component
function create() {
  const state = component.data()
  Object.assign(state, component.methods, { $message: { warning() {}, error() {}, success() {} }, $router: { replace() {} }, $route: { path: '/data-migration/index' } })
  for (const key of Object.keys(component.computed)) Object.defineProperty(state, key, { get: () => component.computed[key].call(state) })
  return state
}
async function run() {
  const names = create(); names.$route.query = {}; names.loadList = async () => {}
  await component.created.call(names)
  assert.deepStrictEqual(Array.from(names.sources, row => row.alias), ['自定义连接', 'SYSTEM', 'ORACLE_QA', '旧连接用户', '数据源 5'], '连接显示名与左侧树一致，旧自动别名必须以用户名区分')
  assert.strictEqual(names.sourceName('3', '旧别名'), 'ORACLE_QA', '任务列表按数据源ID显示同一个连接名')
  assert.strictEqual(names.sourceName(999, '历史连接'), '历史连接', '已删除连接的历史报告仍保留原名称')
  names.config.sourceId = 3; migration.connection = () => Promise.resolve({ id: 3, name: '@host.docker.internal', type: 'ORACLE', schemas: [] })
  await names.connectEndpoint('source')
  assert.strictEqual(names.endpoints.source.name, 'ORACLE_QA', '连接后的摘要不能重新显示容易混淆的旧别名')
  assert(!names.sources.some(row => row.user || row.userName), '名称映射不复制额外的连接字段')
  const state = create()
  assert.strictEqual(state.config.errorPolicy, 'STOP', '默认必须遇错停止')
  assert.strictEqual(state.config.parallelism, 1, '旧草稿及新任务默认串行，不静默扩大数据库连接数')
  assert.strictEqual(state.config.targetTablespace, '', '默认继承目标用户表空间，不创建或扩容')
  state.toggleObject({ name: 'A', type: 'TABLE' }, true)
  state.toggleObject({ name: 'B', type: 'TABLE' }, true)
  state.toggleObject({ name: 'A', type: 'TABLE' }, true)
  state.toggleObject({ name: 'V', type: 'VIEW' }, true)
  assert.strictEqual(state.config.tables.length, 3, '表和待人工确认的视图可选择，但不能重复选中')
  state.objects = [{ name: 'C', type: 'TABLE' }]; state.selectVisible()
  assert(state.selected('A') && state.selected('C'), '搜索与翻页不能清除其他页面的选择')
  state.toggleObject({ name: 'A', type: 'TABLE' }, false)
  assert(!state.selected('A') && state.selected('B'))
  state.config.sourceSchema = 'ONE'; state.toggleObject({ name: 'SAME', type: 'TABLE' }, true)
  state.config.sourceSchema = 'TWO'; state.toggleObject({ name: 'SAME', type: 'TABLE' }, true)
  assert.strictEqual(state.config.tables.filter(table => table.source === 'SAME').length, 2, '不同模式中的同名表可同时选择')
  state.toggleObject({ name: 'SAME', type: 'TABLE' }, false)
  assert(state.config.tables.some(table => table.source === 'SAME' && table.sourceSchema === 'ONE'), '取消只影响当前模式')
  state.config.scope = 'DATA'; state.toggleObject({ name: 'NO_VIEW', type: 'VIEW' }, true)
  assert(!state.config.tables.some(table => table.source === 'NO_VIEW'), '仅数据模式不可新增非表对象')
  state.config.scope = 'ALL'
  const originalObjects = migration.objects
  const firstPage = Array.from({ length: 200 }, (_, i) => ({ name: 'P1_' + i, type: i < 195 ? 'MATERIALIZED VIEW' : 'TABLE' }))
  const secondPage = [{ name: 'P1_196', type: 'TABLE' }, { name: 'V', type: 'VIEW' }, ...Array.from({ length: 8 }, (_, i) => ({ name: 'P2_' + i, type: 'TABLE' }))]
  const all = create(); all.config.sourceId = 1; all.config.sourceSchema = 'ONE'; all.objectSearch = 'P'; all.objectPage = 2
  all.config.tables = [{ source: 'P1_195', sourceSchema: 'ONE', type: 'TABLE', target: 'CUSTOM' }, { source: 'KEEP', sourceSchema: 'OTHER', type: 'TABLE', target: 'KEEP' }]
  const existingMapping = all.config.tables[0], calls = []
  migration.objects = params => { calls.push(params); return Promise.resolve({ data: params.page === 1 ? firstPage : secondPage, total: 210 }) }
  await all.selectAllObjects()
  assert.deepStrictEqual(calls.map(params => params.page), [1, 2], '全选必须读取当前筛选条件的全部分页')
  assert(calls.every(params => params.id === 1 && params.schema === 'ONE' && params.search === 'P' && params.size === 200))
  assert.strictEqual(all.config.tables.length, 15, '排除不支持类型，并消除已选对象和跨页重复对象')
  assert.strictEqual(all.config.tables[0], existingMapping, '全选不得覆盖已有目标名称和单对象配置')
  assert(all.config.tables.some(row => row.sourceSchema === 'OTHER') && all.selected('V', 'VIEW'))
  assert.strictEqual(all.objectPage, 2, '全选不得改变用户当前查看的分页')
  assert(!all.selectingAll)

  const limited = create(); limited.config.sourceId = 1; limited.config.sourceSchema = 'ONE'
  const warnings = []; limited.$message.warning = message => warnings.push(message)
  const overLimit = Array.from({ length: 101 }, (_, i) => ({ name: 'T' + i, type: 'TABLE' }))
  migration.objects = () => Promise.resolve({ data: overLimit, total: 101 })
  await limited.selectAllObjects()
  assert.strictEqual(limited.config.tables.length, 101, '超过 100 个对象时应全选全部结果')
  assert.strictEqual(warnings.length, 0)
  limited.objects = [{ name: 'EXTRA', type: 'TABLE' }]; limited.selectVisible()
  assert.strictEqual(limited.config.tables.length, 102, '本页批量选择应允许超过旧的 100 个上限')
  const combined = create(); combined.config.sourceId = 1; combined.config.sourceSchema = 'ONE'
  combined.config.tables = Array.from({ length: 99 }, (_, i) => ({ source: 'OLD_' + i, sourceSchema: 'OTHER', type: 'TABLE', target: 'CUSTOM_' + i }))
  const retained = JSON.stringify(combined.config.tables)
  migration.objects = () => Promise.resolve({ data: [{ name: 'NEW1', type: 'TABLE' }, { name: 'NEW2', type: 'TABLE' }], total: 2 })
  await combined.selectAllObjects()
  assert.strictEqual(combined.config.tables.length, 101, '其他模式的已有选择与新筛选结果应合并')
  assert.strictEqual(JSON.stringify(combined.config.tables.slice(0, 99)), retained, '批量选择不得改写已有映射')
  combined.mappingPage = 2
  assert.strictEqual(combined.pagedMappings.length, 50, '大量对象的映射列表应按页渲染')
  combined.removeMapping(combined.config.tables[100])
  assert.strictEqual(combined.config.tables.length, 100, '分页后的移除必须定位原始对象')
  const capped = create(); capped.config.sourceId = 1; capped.config.sourceSchema = 'ONE'
  capped.config.tables = Array.from({ length: 9999 }, (_, i) => ({ source: 'OLD_' + i, sourceSchema: 'OTHER', type: 'TABLE', target: 'OLD_' + i }))
  capped.$message.warning = message => warnings.push(message)
  const cappedBefore = capped.config.tables.slice()
  await capped.selectAllObjects()
  assert.strictEqual(capped.config.tables.length, cappedBefore.length, '达到安全上限时全选必须保持原选择')
  assert(warnings.some(message => message.includes('10000')))
  const dataOnly = create(); dataOnly.config.sourceId = 1; dataOnly.config.sourceSchema = 'ONE'; dataOnly.config.scope = 'DATA'
  migration.objects = () => Promise.resolve({ data: [{ name: 'T', type: 'TABLE' }, { name: 'V', type: 'VIEW' }], total: 2 })
  await dataOnly.selectAllObjects()
  assert.deepStrictEqual(Array.from(dataOnly.config.tables, row => row.source), ['T'], '仅数据范围的全选必须排除非表对象')

  const interrupted = create(); interrupted.config.sourceId = 1; interrupted.config.sourceSchema = 'ONE'
  const errors = []; interrupted.$message.error = message => errors.push(message)
  migration.objects = params => params.page === 1 ? Promise.resolve({ data: firstPage, total: 210 }) : Promise.reject(new Error('第二页读取失败'))
  await interrupted.selectAllObjects()
  assert.strictEqual(interrupted.config.tables.length, 0, '分页读取失败不得留下部分选择')
  assert(errors.includes('第二页读取失败') && !interrupted.selectingAll)
  let resolveSelection
  migration.objects = () => new Promise(resolve => { resolveSelection = resolve })
  const concurrent = interrupted.selectAllObjects(); const pendingResolver = resolveSelection
  await interrupted.selectAllObjects()
  assert.strictEqual(resolveSelection, pendingResolver, '全选加载期间不能重复发起相同操作')
  resolveSelection({ data: [], total: 0 }); await concurrent
  const staleSelection = interrupted.selectAllObjects(); interrupted.config.sourceSchema = 'TWO'
  resolveSelection({ data: [{ name: 'OLD', type: 'TABLE' }], total: 1 }); await staleSelection
  assert.strictEqual(interrupted.config.tables.length, 0, '旧模式的全选响应不能写入新模式配置')
  const hiddenSelection = interrupted.selectAllObjects(); component.deactivated.call(interrupted)
  resolveSelection({ data: [{ name: 'HIDDEN', type: 'TABLE' }], total: 1 }); await hiddenSelection
  assert.strictEqual(interrupted.config.tables.length, 0, '离开页面后迟到的全选响应不得更改草稿')
  assert(!interrupted.selectingAll)
  const locked = create(); locked.task = { status: 'MIG_RUNNING' }; locked.config.sourceId = 1; locked.config.sourceSchema = 'ONE'
  calls.length = 0; migration.objects = params => { calls.push(params); return Promise.resolve({ data: [], total: 0 }) }
  await locked.selectAllObjects(); assert.strictEqual(calls.length, 0, '不可编辑任务不执行全选读取')
  migration.objects = originalObjects

  const deletion = create(); deletion.tasks = [{ id: 8, status: 'MIG_DRAFT', config: { name: '旧任务' } }]
  deletion.$confirm = () => Promise.resolve(); deletion.loadList = async () => {}
  await deletion.deleteTask(deletion.tasks[0])
  assert.strictEqual(migration.deletedId, 8, '删除按钮必须调用任务删除接口')
  migration.deletedId = null
  await deletion.deleteTask({ id: 9, status: 'MIG_RUNNING', config: { name: '执行中' } })
  assert.strictEqual(migration.deletedId, null, '执行中的任务不能删除')

  assert.strictEqual(state.previewDdl({ targetDdl: 'CREATE TABLE X', afterDdl: ['COMMENT X'], postDdl: [{ sql: 'CREATE INDEX X_I' }, { sql: 'ALTER TABLE X ADD FOREIGN KEY' }] }), 'CREATE TABLE X;\n\nCOMMENT X;\n\nCREATE INDEX X_I;\n\nALTER TABLE X ADD FOREIGN KEY', '预览必须包括索引和外键，不能只展示建表')
  state.config.scope = 'DATA'; assert.strictEqual(state.previewDdl({ targetDdl: 'EXISTING DDL', postDdl: [{ sql: 'NOT EXECUTED' }] }), 'EXISTING DDL')
  state.config.scope = 'ALL'; state.targetChangeConfirmed = true; state.newTask()
  assert.strictEqual(state.targetChangeConfirmed, false, '新任务不能沿用上一次的高风险确认')
  state.config.tables = [{ source: 'V1', ddlConfirmed: true }, { source: 'V2', targetSchema: 'EXPLICIT', ddlConfirmed: true }]
  state.targetSchemaChanged(); assert(!state.config.tables[0].ddlConfirmed && state.config.tables[1].ddlConfirmed, '默认目标模式变化只清除受影响对象的人工确认')
  state.endpointChanged('target'); assert(state.config.tables.every(table => !table.ddlConfirmed && !table.targetSchema), '更换目标数据源必须重新确认目标模式和定义')

  state.config.tables = [{ source: 'ONE', target: 'ONE', scope: 'DATA', conflict: 'CLEAR', filters: [] }, { source: 'TWO', target: 'TWO', conflict: 'STOP' }]
  assert(state.highRisk && state.riskyTables.length === 1, '单表高风险覆盖不能绕过确认，也不能把无风险表列入清空名单')
  state.mappingIndex = 0; state.mappingEditor = JSON.parse(JSON.stringify(state.config.tables[0]))
  state.mappingFields = [{ source: 'ID', target: 'KEY_ID', sourceType: 'NUMBER', kind: 'NUMBER' }, { source: 'NOTE', target: 'NOTE', sourceType: 'VARCHAR', kind: 'TEXT' }]
  state.addFilter(); state.mappingEditor.filters[0] = { column: 'ID', operator: 'EQ', value: '1' }
  assert(!state.filterOperators('ID').some(op => op.value === 'LIKE') && state.filterOperators('NOTE').some(op => op.value === 'LIKE'), 'LIKE 只允许文本筛选')
  state.saveMapping(); assert.strictEqual(state.config.tables[0].filters[0].value, '1'); assert.strictEqual(state.config.tables[0].columns[0].target, 'KEY_ID')
  assert.strictEqual(state.previewDdl({ scope: 'DATA', targetDdl: 'EXISTING', postDdl: [{ sql: 'NEVER EXECUTE' }] }), 'EXISTING', 'DDL 预览依单表范围而非全局范围')
  state.task = { assessment: { tables: [{ source: 'TWO' }, { source: 'ONE', filters: [{ column: 'ID' }] }] } }
  state.previewMapping(state.config.tables[0]); assert.strictEqual(state.preview.source, 'ONE', '依赖排序后预览必须按源对象标识定位，不可按配置索引')

  const conversion = create()
  conversion.config.tables = [{ source: 'A', target: 'A', filters: [], columns: [] }]
  conversion.mappingIndex = 0; conversion.mappingEditor = { source: 'A', target: 'A', filters: [], targetTablespace: 'QA_DATA' }
  conversion.mappingFields = [{ source: 'PAYLOAD', target: 'AMOUNT', targetType: 'NUMBER(12,2)', sourceAttribute: 'AMOUNT', conversionConfirmed: false }]
  conversion.saveMapping(); assert(conversion.mappingEditor, '转换语义未确认不能保存')
  conversion.mappingFields[0].conversionConfirmed = true; conversion.saveMapping()
  assert.strictEqual(conversion.config.tables[0].targetTablespace, 'QA_DATA')
  assert.strictEqual(conversion.config.tables[0].columns[0].sourceAttribute, 'AMOUNT')
  assert.strictEqual(conversion.config.tables[0].columns[0].targetType, 'NUMBER(12,2)')
  conversion.mappingEditor = { source: 'A', target: 'A', filters: [] }; conversion.mappingFields[0].targetType = ''
  conversion.saveMapping(); assert(conversion.mappingEditor, '属性投影没有目标类型仍必须阻断')

  state.task = { id: 1, status: 'MIG_READY', assessment: { ready: true }, config: JSON.parse(JSON.stringify(state.config)) }
  state.applyTask(state.task); state.offlineConfirmed = true
  assert(state.ready)
  state.config.commitSize = 100; component.watch.config.handler.call(state)
  assert.strictEqual(state.ready, false); assert.strictEqual(state.task.assessment, null); assert.strictEqual(state.offlineConfirmed, false)

  const reversed = Object.keys(state.config).reverse().reduce((copy, key) => { copy[key] = state.config[key]; return copy }, {})
  state.applyTask({ id: 7, status: 'MIG_READY', config: reversed, assessment: { ready: true, tables: [] } }, true)
  component.watch.config.handler.call(state)
  assert(state.ready && state.assessment, '前后端字段顺序不同不应清除真实预检查结果')
  state.config.errorPolicy = 'CONTINUE_INDEPENDENT'; component.watch.config.handler.call(state)
  assert(!state.ready && !state.assessment, '错误策略改变仍必须重新预检查')

  const prechecked = create(); prechecked.step = 3; prechecked.config.name = '往返配置'
  prechecked.config.sourceId = 1; prechecked.config.targetId = 2
  prechecked.config.sourceSchema = 'SRC'; prechecked.config.targetSchema = 'DEST'
  prechecked.config.tables = [{ source: 'A', target: 'A', sourceSchema: 'SRC', targetSchema: '', type: 'TABLE', filters: [], columns: [], dependencies: [] }]
  let serverConfig
  migration.save = (id, config) => {
    serverConfig = JSON.parse(JSON.stringify(config)); serverConfig.tables[0].targetSchema = null
    return Promise.resolve({ id: 30, status: 'MIG_DRAFT', config: serverConfig })
  }
  migration.precheck = () => Promise.resolve({ id: 30, status: 'MIG_READY', config: serverConfig, assessment: { ready: true, tables: [{ source: 'A', blockers: [] }] } })
  await prechecked.runPrecheck()
  component.watch.config.handler.call(prechecked)
  assert(prechecked.ready && prechecked.assessment, '服务端规范化配置后，预检查通过状态必须保持有效')
  await prechecked.nextStep()
  assert.strictEqual(prechecked.step, 4, '预检查通过后下一步应进入审阅执行')
  prechecked.$confirm = () => { throw new Error('已保存配置不应提示丢弃') }
  prechecked.loadList = async () => {}
  await prechecked.backToList()
  assert.strictEqual(prechecked.editing, false, '保存后的配置返回列表不应被误判为未保存')

  const failed = { source: '"QA"."B"', stage: 'FAILED' }, completed = { source: '"QA"."G"', stage: 'COMPLETED' }
  state.config.sourceSchema = 'QA'; state.task = { id: 8, status: 'MIG_PARTIAL', assessment: { tables: [{ sourceSchema: 'QA', source: 'B', type: 'TABLE' }, { sourceSchema: 'QA', source: 'G', type: 'TABLE' }] } }
  assert(state.retryable(failed)); assert(!state.retryable(completed), '成功对象不得提供重跑入口')
  const assessed = Object.assign({}, state.task, { resumeObject: failed.source, resumeFingerprint: 'one-object' })
  state.$confirm = () => Promise.resolve()
  const retry = state.retryObject(failed); pending.shift()(assessed); await retry
  assert.strictEqual(migration.lastRetry.sourceObject, failed.source); assert.strictEqual(migration.lastRetry.fingerprint, 'one-object'); assert(migration.lastRetry.reviewConfirmed && migration.lastRetry.offlineConfirmed)
  migration.lastRetry = null; state.task = Object.assign({}, assessed); state.$confirm = () => Promise.reject('cancel')
  const cancelled = state.retryObject(failed); pending.shift()(assessed); await cancelled
  assert.strictEqual(migration.lastRetry, null, '取消二次确认不得启动重跑')
  const staleRetry = state.retryObject(failed); state.generation++; state.task = { id: 9 }
  pending.shift()(assessed); await staleRetry
  assert.strictEqual(state.task.id, 9, '迟到的对象重跑检查不得覆盖另一任务')

  const newTarget = create(); newTarget.task = { id: 20, status: 'MIG_FAILED' }; newTarget.$confirm = () => Promise.resolve()
  const newDraft = newTarget.retryToNewTarget(failed); await new Promise(resolve => setImmediate(resolve))
  assert.strictEqual(migration.lastDraftRetry.id, 20); assert.strictEqual(migration.lastDraftRetry.request.sourceObject, failed.source)
  pending.shift()({ id: 21, status: 'MIG_DRAFT', parentTaskId: 20, config: {} }); await newDraft
  assert.strictEqual(newTarget.task.id, 21); assert.strictEqual(newTarget.step, 0); assert.strictEqual(newTarget.endpoints.target, null)
  assert.strictEqual(newTarget.config.parallelism, 1, '关联草稿缺少新字段时继续兼容旧任务')
  newTarget.retrySelection = failed.source; newTarget.applyTask({ id: 21, status: 'MIG_DRAFT', config: {} }, true)
  assert.strictEqual(newTarget.retrySelection, '', '打开另一任务不能沿用旧任务的重迁移对象选择')
  migration.lastDraftRetry = null; newTarget.$confirm = () => Promise.reject('cancel'); await newTarget.retryToNewTarget(failed)
  assert.strictEqual(migration.lastDraftRetry, null, '取消不得创建关联草稿')
  newTarget.$confirm = () => Promise.resolve(); const delayedDraft = newTarget.retryToNewTarget(); await new Promise(resolve => setImmediate(resolve))
  assert.strictEqual(migration.lastDraftRetry.request.sourceObject, undefined, '空选择交给后端选择所有未完成对象，不重跑成功对象')
  newTarget.generation++; newTarget.task = { id: 22 }
  pending.shift()({ id: 23, status: 'MIG_DRAFT', config: {} }); await delayedDraft
  assert.strictEqual(newTarget.task.id, 22, '关联草稿迟到响应不得覆盖其他任务')

  state.config.sourceId = 1; state.config.sourceSchema = 'ONE'
  const old = state.loadObjects(); state.config.sourceSchema = 'TWO'
  pending.shift()({ data: [{ name: 'STALE' }], total: 1 }); await old
  assert(!state.objects.some(row => row.name === 'STALE'), '旧模式响应不能覆盖新模式对象')

  state.editing = true; state.task = { id: 1, status: 'MIG_RUNNING', config: state.config }
  const refresh = state.refreshTask(); state.generation++; state.task = { id: 2, status: 'MIG_RUNNING', config: state.config }
  pending.shift()({ id: 1, status: 'MIG_COMPLETED', config: state.config }); await refresh
  assert.strictEqual(state.task.id, 2, '迟到任务响应不能覆盖另一任务')
  state.task.status = 'MIG_COMPLETED'; state.schedulePolling(); assert.strictEqual(state.pollTimer, null)
  state.task.status = 'MIG_RUNNING'; state.schedulePolling(); assert.strictEqual(state.pollTimer, 99)
  component.beforeUnmount.call(state); assert.strictEqual(state.pollTimer, null)

  const apiSource = fs.readFileSync(path.join(__dirname, '../src/api/main/migration.js'), 'utf8')
  const api = { request: () => Promise.resolve({ success: false, errorMessage: '阻断项' }) }
  vm.runInNewContext(apiSource.replace(/^import .*$/gm, '').replace('export function migrationRequest', 'function migrationRequest').replace('export default', 'this.api ='), api)
  await assert.rejects(() => api.api.precheck(1), /阻断项/, '业务失败响应不得当作成功')
  assert(source.includes('font-size: 12px'))
  assert(source.includes('height: calc(100vh - 84px); overflow: hidden'), '迁移页不能整体滚动')
  assert(source.includes('class="editor-body"') && source.includes('class="table-region"') && source.includes('height="100%"'), '正文与表格必须使用内部滚动容器')
  const tabs = compiler.compile(page.template.content).ast
  const visit = (node, callback) => { if (!node || node.type !== 1) return; callback(node); (node.children || []).forEach(child => visit(child, callback)); (node.ifConditions || []).slice(1).forEach(condition => visit(condition.block, callback)) }
  const panes = []; visit(tabs, node => { if (node.tag === 'el-tab-pane') panes.push(node) })
  assert.deepStrictEqual(panes.map(node => compiler.attribute(node, 'label')), ['对象映射', '执行设置'])
  const modelFields = node => { const fields = []; visit(node, child => { const model = compiler.directive(child, 'model'); if (model) fields.push(model.exp.content) }); return fields }
  assert(modelFields(panes[0]).includes('config.targetSchema') && !modelFields(panes[0]).includes('config.commitSize'), '对象映射不能混杂执行参数')
  for (const field of ['fetchSize', 'commitSize', 'parallelism', 'rowsPerSecond', 'targetTablespace', 'errorPolicy']) assert(modelFields(panes[1]).includes('config.' + field), '执行设置不得丢失字段 ' + field)
  const cached = create(); cached.config.commitSize = 700; cached.config.targetSchema = 'TARGET'; cached.config.tables = [{ source: 'A', target: 'RENAMED' }]
  const beforeTab = JSON.stringify(cached.config); cached.mappingTab = 'execution'; cached.mappingTab = 'objects'
  assert.strictEqual(JSON.stringify(cached.config), beforeTab, '页签只改变布局状态，不修改业务配置')
  assert(source.includes('max-height: calc(92vh - 120px); overflow-y: auto'), '对象配置扩展后正文内部滚动，窄屏保存按钮不得掉出视口')
  assert(!source.includes('Math.random') && !source.includes('localStorage'), '正式页面不使用随机进度或本地模拟任务')
  assert(source.includes('resumeCheck') && source.includes('reviewConfirmed: true'), '恢复必须检查并显式确认')
  console.log('数据迁移：模板编译、跨页选择、评估失效、迟到响应、轮询停止、业务失败响应检查通过')
}
run().catch(error => { console.error(error); process.exitCode = 1 })
