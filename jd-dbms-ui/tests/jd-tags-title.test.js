const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')

const file = path.join(__dirname, '../src/store/modules/jdTagsView.js')
const context = { module: { exports: {} } }
vm.runInNewContext(fs.readFileSync(file, 'utf8').replace('export default', 'module.exports ='), context)
const changeTitle = context.module.exports.mutations.CHANGE_VIEWS_TITLE
const state = { titleMap: [
  { id: 'page-1', title: '旧标题' },
  { id: 'page-1', title: '表标题' },
  { id: 'page-2', title: '其他窗口' },
] }

changeTitle(state, { id: 'page-1', title: '模式总览' })
assert.deepStrictEqual(state.titleMap.map(item => item.title), ['模式总览', '模式总览', '其他窗口'])
changeTitle(state, { id: 'page-3', title: '新窗口' })
assert.strictEqual(state.titleMap[3].title, '新窗口')
console.log('页签标题：同窗口重复记录全部更新且不影响其他窗口')
