const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')
const Vue = require('vue')
const { renderToString } = require('@vue/server-renderer')
const ElementPlus = require('element-plus')
const compiler = require('./helpers/vue-compiler')
const root = path.join(__dirname, '../src')

function moduleSource(file, context, exports) {
  const source = fs.readFileSync(path.join(root, file), 'utf8')
    .replace(/^import[\s\S]*?from ['"][^'"]+['"];?\s*$/gm, '')
    .replace(/export default /g, 'const component = ').replace(/export /g, '')
  return vm.runInNewContext(source + `\n;(${exports})`, { ...context })
}

async function run() {
  const generator = moduleSource('utils/generator/render.js', Vue, 'component')
  for (const file of ['views/system/user/index.vue', 'views/system/role/index.vue',
    'views/main/workspace/components/renderTableEditor/ConstraintList.vue']) {
    const template = compiler.parseComponent(fs.readFileSync(path.join(root, file), 'utf8')).template.content
    const compiled = compiler.compileBrowser(template)
    assert(!compiled.code.includes('_ctx.scope'), file + ' 的行条件必须在插槽作用域内执行')
  }
  const tagsSource = compiler.parseComponent(fs.readFileSync(path.join(root, 'components/JdTagsView/index.vue'), 'utf8')).script.content
  const cached = [{ params: { id: 'cached' } }]
  const tags = vm.runInNewContext(tagsSource.replace(/^import .*;?\s*$/gm, '').replace('export default', 'const tags =') + '\ntags', {
    ScrollPane: {}, mapGetters: () => ({}), localStorage: { getItem: () => JSON.stringify(cached) }, console: { log() {} }
  })
  const navigations = []
  const stateForTags = { $store: { getters: { visitedViews2: [] }, state: { jdTagsView: { visitedViews: [] } } },
    $route: { path: '/logManagement', params: {}, query: {} }, $router: { push: route => navigations.push(route) } }
  tags.computed.visitedViews.call(stateForTags)
  assert.strictEqual(navigations.length, 0, '缓存窗口恢复不能覆盖日志管理路由')
  stateForTags.$route.path = '/workspace/look/other'
  tags.computed.visitedViews.call(stateForTags)
  assert.strictEqual(navigations.length, 1, '工作区仍应恢复缓存窗口')
  const icons = moduleSource('assets/element-icons/index.js', Vue, 'component')
  for (const conf of [
    { tag: 'el-input', vModel: 'title', defaultValue: 'Vue 3', prepend: '前缀', append: '后缀' },
    { tag: 'el-select', vModel: 'choice', defaultValue: 2, options: [{ label: '甲', value: 1 }, { label: '乙', value: 2 }] },
    { tag: 'el-radio-group', vModel: 'choice', defaultValue: 2, options: [{ label: '甲', value: 1 }, { label: '乙', value: 2 }] },
    { tag: 'el-upload', buttonText: '上传', showTip: true, fileSize: 5, sizeUnit: 'MB', accept: 'image/png' }
  ]) {
    const app = Vue.createSSRApp({ render: () => Vue.h(generator, { conf }) })
    app.use(ElementPlus)
    app.use(icons)
    app.provide(ElementPlus.ID_INJECTION_KEY, { prefix: 100, current: 0 })
    app.provide(ElementPlus.ZINDEX_INJECTION_KEY, { current: 0 })
    const html = await renderToString(app)
    assert(!html.includes('<el-'), '动态渲染必须解析为真实 Element Plus 组件')
    if (conf.tag === 'el-input') assert(html.includes('Vue 3') && html.includes('前缀') && html.includes('后缀'))
    if (conf.tag === 'el-radio-group') assert(html.includes('value="2"') && html.includes('乙'))
    if (conf.tag === 'el-upload') assert(html.includes('5MB') && html.includes('上传'))
  }

  const config = moduleSource('utils/generator/config.js', {}, '({formConf,inputComponents,selectComponents,trigger})')
  const panelSource = compiler.parseComponent(fs.readFileSync(path.join(root, 'views/tool/build/RightPanel.vue'), 'utf8')).script.content
  const panel = vm.runInNewContext(panelSource.replace(/^import[\s\S]*?from ['"][^'"]+['"];?\s*$/gm, '')
    .replace('export default', 'const panel =') + '\npanel', {
    ...Vue, ...config, draggable: {}, TreeNodeDialog: {}, IconsDialog: {}, isNumberStr: value => /^\d+$/.test(value)
  })
  assert.strictEqual(panel.methods.setDefaultValue([1, 2]), '1,2')
  const treeData = { id: 2 }, parent = { data: [treeData] }
  const state = { ...panel.methods, dialogVisible: false }
  const vnode = panel.methods.renderContent.call(state, Vue.h, { node: { label: '节点', parent }, data: treeData })
  const buttons = vnode.children[1].children
  buttons[0].props.onClick()
  assert(state.dialogVisible && state.currentNode === treeData.children, 'Vue 3 原生 onClick 必须打开新增子节点弹窗')
  buttons[1].props.onClick()
  assert.strictEqual(parent.data.length, 0, '删除节点事件必须生效')
  const html = moduleSource('utils/generator/html.js', { trigger: config.trigger }, '({makeUpHtml})')
  const js = moduleSource('utils/generator/js.js', { trigger: config.trigger, exportDefault: 'export default ', titleCase: s => s[0].toUpperCase() + s.slice(1) }, '({makeUpJs})')
  const fields = [...config.inputComponents, ...config.selectComponents].map((item, index) => ({
    ...item, layout: 'colFormItem', vModel: 'field' + index, formId: index + 1
  }))
  fields[0].prepend = '前缀'
  fields[0].append = '后缀'
  for (const type of ['file', 'dialog']) {
    const conf = { ...config.formConf, fields }
    const template = html.makeUpHtml(conf, type)
    assert.deepStrictEqual(compiler.compile(template).errors, [], '所有生成控件及插槽必须能在原生 Vue 3 编译')
    assert(!/\$listeners|slot=/.test(template))
    require('@babel/parser').parse(js.makeUpJs(conf, type), { sourceType: 'module' })
  }

  for (const name of ['FileUpload', 'ImageUpload']) {
    const source = compiler.parseComponent(fs.readFileSync(path.join(root, 'components', name, 'index.vue'), 'utf8'))
    const component = vm.runInNewContext(source.script.content.replace(/^import .*;?\s*$/gm, '').replace('export default', 'const component =') + '\ncomponent', { getToken: () => '', process: { env: {} } })
    const state = { fileList: [], modelValue: '/qa/a.png,/qa/b.png', $emit: (event, value) => { state.event = event; state.value = value },
      listToString: component.methods.listToString }
    component.watch.modelValue.handler.call(state, state.modelValue)
    assert.strictEqual(state.fileList.length, 2)
    if (name === 'FileUpload') component.methods.handleDelete.call(state, 0)
    else component.methods.handleDelete.call(state, state.fileList[0])
    assert.strictEqual(state.event, 'update:modelValue')
    assert.strictEqual(state.value, '/qa/b.png')
  }
  console.log('Vue 3 组件：原生动态渲染、表单导出编译、上传双向绑定通过')
}
run().catch(error => { console.error(error); process.exitCode = 1 })
