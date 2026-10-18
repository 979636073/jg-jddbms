const assert = require('assert')
const fs = require('fs')
const path = require('path')
const compiler = require('./helpers/vue-compiler')
const sass = require('sass')
const root = path.join(__dirname, '../src/views/main/workspace')
function read(name) { return compiler.parseComponent(fs.readFileSync(path.join(root, name), 'utf8')) }

const shell = read('index.vue')
const sidebar = read('SideBar.vue')
const sql = read('components/reanderSqlExecute/index.vue')
assert(/editor-config="\{ fontSize: 12,/.test(sql.template.content), 'SQL 编辑器应与左树 12px 正文字号一致')
const css = sass.renderSync({ data: shell.styles[0].content }).css.toString()
assert(/\.workspace-body\s*\{[^}]*--workspace-body-font-size:\s*12px/.test(css), '左右共享一个 12px 正文字号')
assert(/\.resource-node-label\s*\{[^}]*font-size:\s*var\(--workspace-body-font-size,\s*12px\)/.test(sidebar.styles[0].content))
for (const selector of ['.el-button', '.el-button.el-button--mini', '.el-tabs__item', '.el-table', '.el-table .cell',
  '.el-input__inner', '.el-textarea__inner', '.el-form-item__label', '.el-pagination__total', '.el-pager li']) {
  const rule = css.split('}').find(block => block.includes(`.workspace-content ::v-deep ${selector},`) ||
    block.includes(`.workspace-content ::v-deep ${selector} {`))
  assert(rule && rule.includes('font-size: var(--workspace-body-font-size)'), `右侧 ${selector} 应使用共享字号`)
}
assert(!/zoom:|transform:\s*scale/.test(css), '不能通过缩放整个工作区改变字号和点击区域')
assert.deepStrictEqual(compiler.compile(shell.template.content).errors, [])
console.log('工作区共享 12px 字号、右侧控件覆盖与 SQL 编辑器字号回归通过')
