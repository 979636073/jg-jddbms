const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')
const compiler = require('vue-template-compiler')

const source = fs.readFileSync(path.join(__dirname, '../src/views/main/workspace/components/siedBarDialog/addUser.vue'), 'utf8')
const descriptor = compiler.parseComponent(source)
assert.deepStrictEqual(compiler.compile(descriptor.template.content).errors, [])
assert(source.includes('密码还需满足目标数据库的复杂度策略'))

const messages = []
let response = { success: false, errorCode: 'user.password.policy.failed', errorMessage: '密码不符合目标数据库的复杂度策略' }
const context = {
  module: { exports: {} },
  userServer: { addUserRole: async () => response },
  tableSpaceServer: {},
}
vm.runInNewContext(descriptor.script.content.replace(/^import .*;?\s*$/gm, '').replace('export default', 'module.exports ='), context)
const component = context.module.exports
const state = {
  dataBaseInfo: { id: 1 },
  schema: 'QA',
  $refs: { userFrom: { validate: callback => callback(true), resetFields() {} } },
  $message: { error: message => messages.push(message), success: message => messages.push(message) },
  $emit() {},
  getTableDataList() {},
  getDataBaseTreeData() {},
}
Object.assign(state, component.data.call(state), component.methods)
state.userFrom.userName = 'QA_USER'
state.userFrom.newPassWord = 'Pass12345!'

async function run() {
  state.saveUser()
  await new Promise(resolve => setImmediate(resolve))
  assert.strictEqual(messages.pop(), '密码不符合目标数据库的复杂度策略')

  response = { success: true }
  state.saveUser()
  await new Promise(resolve => setImmediate(resolve))
  assert.strictEqual(messages.pop(), '新建用户成功!')
  console.log('数据库用户创建结果与密码策略提示回归测试通过')
}

run().catch(error => { console.error(error); process.exit(1) })
