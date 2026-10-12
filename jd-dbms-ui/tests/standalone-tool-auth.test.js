const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')
const compiler = require('vue-template-compiler')

const root = path.join(__dirname, '../src')
const layoutSource = fs.readFileSync(path.join(root, 'layout/index.vue'), 'utf8')
const layoutTemplate = compiler.parseComponent(layoutSource).template.content
assert.deepStrictEqual(compiler.compile(layoutTemplate).errors, [], '独立工具布局应可编译')
assert(layoutTemplate.includes('<div v-if="!isStandaloneTool"'), '独立工具页不应显示面包屑和右上角用户')
assert(layoutTemplate.includes('<sidebar v-if="!sidebar.hide&&!isStandaloneTool"'), '独立工具页不应显示左侧树')
assert(layoutSource.includes('setInterval(this.checkStandaloneAuth, 60000)'), '已打开的工具页应定期校验登录状态')
assert(layoutSource.includes('window.addEventListener(\'focus\', this.checkStandaloneAuth)'), '重新聚焦时应立即校验登录状态')

const requestSource = fs.readFileSync(path.join(root, 'utils/request.js'), 'utf8')
const redirectFn = requestSource.match(/function redirectExpiredStandaloneSession\(\) \{[\s\S]*?\n\}/)
assert(redirectFn, '应提供独立工具页的过期跳转处理')

async function verifyRedirect(pathname, search, shouldRedirect) {
  const dispatched = []
  const redirects = []
  const context = {
    module: { exports: {} },
    window: { location: { pathname, search, replace(url) { redirects.push(url) } } },
    store: { dispatch(action) { dispatched.push(action); return Promise.resolve() } },
    encodeURIComponent,
    Promise
  }
  vm.runInNewContext(`let standaloneRedirecting = false; ${redirectFn[0]}; module.exports = redirectExpiredStandaloneSession`, context)
  const expire = context.module.exports
  assert.strictEqual(expire(), shouldRedirect)
  assert.strictEqual(expire(), shouldRedirect, '重复 401 不应重复触发退出')
  await Promise.resolve()
  assert.deepStrictEqual(dispatched, shouldRedirect ? ['FedLogOut'] : [])
  assert.deepStrictEqual(redirects, shouldRedirect
    ? [`/login?redirect=${encodeURIComponent(pathname + search)}`]
    : [])
}

Promise.resolve()
  .then(() => verifyRedirect('/data-migration/index', '?taskId=2', true))
  .then(() => verifyRedirect('/table-compare/index', '', true))
  .then(() => verifyRedirect('/workspace/look/example', '', false))
  .then(() => console.log('独立工具页隐藏管理栏及会话过期跳转回归测试通过'))
  .catch(error => { console.error(error); process.exit(1) })
