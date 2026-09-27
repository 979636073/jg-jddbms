const assert = require('assert')
const fs = require('fs')
const path = require('path')

const dialogFile = path.join(__dirname, '../src/views/main/workspace/components/linkServerDialog/index.vue')
const source = fs.readFileSync(dialogFile, 'utf8')

assert(
  source.includes('<el-button type="primary" @click="saveConnection(\'save\')">保存并连接</el-button>'),
  '连接数据源主按钮应明确显示“保存并连接”，并保留原有保存处理函数'
)
assert(
  !source.includes('<el-button type="primary" @click="saveConnection(\'save\')">连接</el-button>'),
  '连接数据源主按钮不应继续使用含义不清的“连接”文案'
)
assert(
  /visibleDialog\(val\)\s*\{[\s\S]*?if \(val\)\s*\{[\s\S]*?this\.queryDataSoureList\(\)/.test(source),
  '连接数据源弹窗每次打开时都应重新查询数据源列表'
)
assert(
  source.includes('this.$message.error(res.errorMessage || res.errorCode || "数据源列表加载失败")'),
  '数据源列表接口失败时应显示真实错误，不能误显示为暂无数据'
)

console.log('连接数据源弹窗回归测试通过')
