const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')
const compiler = require('vue-template-compiler')
const root = path.join(__dirname, '../src')
const login = compiler.parseComponent(fs.readFileSync(path.join(root, 'views/login.vue'), 'utf8'))
assert.deepStrictEqual(compiler.compile(login.template.content).errors, [])
assert(!login.template.content.includes('验证码'))
assert(!login.script.content.includes('getCodeImg'))
const context = { module: { exports: {} }, Cookies: { get() {} } }
vm.runInNewContext(login.script.content.replace(/^import .*;?\s*$/gm, '').replace('export default', 'module.exports ='), context)
const component = context.module.exports
const state = { ...component.data(), ...component.methods }
component.created.call(state)
assert.deepStrictEqual(Object.keys(state.loginRules), ['username', 'password'])
assert(!('code' in state.loginForm))
let request
const api = { request: value => { request = value } }
vm.runInNewContext(fs.readFileSync(path.join(root, 'api/login.js'), 'utf8')
  .replace(/^import .*\s*$/gm, '').replace(/export function /g, 'function '), api)
api.login('qa-user', 'test-only-password')
assert.strictEqual(request.url, '/login')
assert.deepStrictEqual(Object.keys(request.data), ['username', 'password'])
console.log('登录页仅校验账号密码、无验证码请求、登录提交参数回归通过')
