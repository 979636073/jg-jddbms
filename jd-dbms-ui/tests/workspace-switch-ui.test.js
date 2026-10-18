const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')
const compiler = require('./helpers/vue-compiler')
const src = path.join(__dirname, '../src')

const shell = compiler.parseComponent(fs.readFileSync(path.join(src, 'views/main/workspace/index.vue'), 'utf8'))
const compiled = compiler.compile(shell.template.content)
assert.deepStrictEqual(compiled.errors, [])
function findWindowHost(node) {
  if (compiler.attribute(node, 'class') === 'workspace-content') return node
  return (node.children || []).filter(child => child.type === 1).map(findWindowHost).find(Boolean)
}
const host = findWindowHost(compiled.ast)
const routerView = host.children.find(child => child.tag === 'router-view')
assert(routerView && compiler.directive(routerView, 'slot'), 'Vue Router 4 应通过插槽保留窗口缓存')
assert.strictEqual(routerView.children.filter(child => child.type === 1)[0].tag, 'keep-alive',
  '缓存窗口应直接切换，不能先淡出旧窗口再淡入新窗口')

let beforeEach, afterEach, starts = 0, completions = 0, continued = 0
const permission = fs.readFileSync(path.join(src, 'permission.js'), 'utf8').replace(/^import .*\s*$/gm, '')
vm.runInNewContext(permission, {
  router: { beforeEach(callback) { beforeEach = callback }, afterEach(callback) { afterEach = callback } },
  store: { getters: { roles: ['admin'] }, dispatch() {} },
  Message: {}, getToken: () => true, isRelogin: {}, getQueryObject: () => ({}),
  NProgress: { configure() {}, start() { starts++ }, done() { completions++ } }
})
function navigate(toPath, fromPath) {
  starts = 0
  beforeEach({ path: toPath, meta: {}, fullPath: toPath }, { path: fromPath }, () => { continued++ })
  afterEach()
  return starts
}
assert.strictEqual(navigate('/workspace/query/b', '/workspace/look/a'), 0,
  '工作区窗口切换不能显示整页导航进度条')
assert.strictEqual(navigate('/workspace/look/a', '/workspace/query/b'), 0)
assert.strictEqual(navigate('/workspace/query/c', '/workspace/query/b'), 0)
assert.strictEqual(navigate('/workspace/look/a', '/workspace/look/a'), 0,
  '同窗口参数变化使用自身加载提示，不显示整页导航进度条')
assert.strictEqual(navigate('/workspace/look/a', '/user/profile'), 1,
  '从工作区外进入仍需保留导航进度提示')
assert.strictEqual(navigate('/table-compare/index', '/workspace/query/b'), 1)
assert.strictEqual(navigate('/workspace/look/a', '/workspace'), 1)
assert.strictEqual(navigate('/workspace/lookalike/a', '/workspace/query/b'), 1)
assert.strictEqual(continued, 8, '不能影响鉴权及路由继续执行')
assert.strictEqual(completions, 8, '导航结束仍须清理已有进度条')
console.log('工作区无淡入淡出、内部导航无整页进度条及外部导航提示回归通过')
