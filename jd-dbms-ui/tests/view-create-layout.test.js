const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')

const source = fs.readFileSync(path.join(__dirname, '../src/views/main/workspace/components/siedBarDialog/viewCreateSql.js'), 'utf8')
  .replace('export function buildViewSql', 'function buildViewSql') + '\nmodule.exports = buildViewSql'
const context = { module: { exports: null } }
vm.runInNewContext(source, context)
const buildViewSql = context.module.exports
const form = { name: 'V_TEST', query: 'SELECT ID FROM T;', columns: 'ID', orReplace: true,
  force: true, restriction: 'readOnly', buildMode: 'IMMEDIATE', refreshMode: 'COMPLETE', refreshOn: 'DEMAND' }

assert.strictEqual(buildViewSql(form, 'TEST', 'normal'),
  'CREATE OR REPLACE FORCE VIEW TEST.V_TEST (ID)\nAS\nSELECT ID FROM T\nWITH READ ONLY')
assert.strictEqual(buildViewSql(form, 'TEST', 'materialized'),
  'CREATE MATERIALIZED VIEW TEST.V_TEST\nBUILD IMMEDIATE\nREFRESH COMPLETE ON DEMAND\nAS\nSELECT ID FROM T')
assert.throws(() => buildViewSql({ ...form, name: 'BAD;DROP' }, 'TEST', 'normal'), /视图名称/)
assert.throws(() => buildViewSql({ ...form, query: 'DELETE FROM T' }, 'TEST', 'normal'), /SELECT/)

const creator = fs.readFileSync(path.join(__dirname, '../src/views/main/workspace/components/siedBarDialog/addView.vue'), 'utf8')
assert(creator.includes('editorMode = \'sql\''), '保留完整 SQL 入口')
assert(creator.includes('this.editorMode = "sql"'), 'SQL 文件导入应进入原始 SQL 模式')
const importer = fs.readFileSync(path.join(__dirname, '../src/views/main/workspace/components/viewImportSql/index.vue'), 'utf8')
assert(importer.includes('@created="getTableDataList && getTableDataList(schema)"'), '导入成功后应刷新视图列表')
const role = fs.readFileSync(path.join(__dirname, '../src/views/system/role/index.vue'), 'utf8')
assert(role.includes('class="role-table-wrap"') && role.includes('height="100%"'), '角色表格应在内部滚动')
assert(role.includes('class="role-pagination"'), '分页应固定在表格下方')
assert(role.includes('max-height: 260px; overflow-y: auto'), '权限树应在内部滚动')
const tree = fs.readFileSync(path.join(__dirname, '../src/views/main/workspace/SideBar.vue'), 'utf8')
assert(tree.includes("data.treeNodeType === 'schemaOverview' ||") && tree.includes('class="resource-overview-indent"'),
  '模式总览应补齐同级缩进')
console.log('视图创建、角色布局和模式总览缩进回归测试通过')
