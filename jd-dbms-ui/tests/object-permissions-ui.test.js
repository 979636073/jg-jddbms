const assert = require('assert')
const fs = require('fs')
const path = require('path')
const compiler = require('./helpers/vue-compiler')

const file = path.join(__dirname, '../src/views/main/workspace/components/renderUserEditor/objectPermissions.vue')
const componentSource = compiler.parseComponent(fs.readFileSync(file, 'utf8'))

assert.deepStrictEqual(compiler.compile(componentSource.template.content).errors, [])
assert(componentSource.template.content.includes('height="calc(100% - 92px)"'), '权限表应为工具栏和保存按钮预留空间')
assert(!componentSource.template.content.includes('height="650px"'), '权限表不得使用超出页签内容区的固定高度')
assert(/\.permission-editor\s*\{[^}]*height:\s*100%;[^}]*min-height:\s*0;/s.test(componentSource.styles[0].content), '权限页应跟随页签内容区高度')
assert(/\.object-panel\s*\{[^}]*min-height:\s*0;/s.test(componentSource.styles[0].content), '对象列表应在页签内滚动')

console.log('对象权限页可视区域回归测试通过')
