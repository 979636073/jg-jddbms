const assert = require('assert')
const fs = require('fs')
const path = require('path')

const navBarSource = fs.readFileSync(path.join(__dirname, '../src/views/main/workspace/NavBar.vue'), 'utf8')
const managerSource = fs.readFileSync(path.join(__dirname, '../src/views/main/workspace/components/DmpToolManager.vue'), 'utf8')
const apiSource = fs.readFileSync(path.join(__dirname, '../src/api/main/dmpTool.js'), 'utf8')

assert(navBarSource.includes('<dmp-tool-manager'), '工作区应挂载客户端工具管理器')
assert(navBarSource.includes('dmpToolMode === "MANUAL" ? this.dmpToolId : undefined'), '导入导出应支持手动指定客户端')
assert(navBarSource.includes('ORACLE_DATAPUMP'), '应支持Oracle Data Pump格式')
assert(navBarSource.includes('ORACLE_LEGACY'), '应保留Oracle传统EXP/IMP格式')
assert(navBarSource.includes('targetVersion'), 'Data Pump导出应提交目标兼容版本')

assert(managerSource.includes('上传并检测'), '客户端工具管理器应提供上传检测流程')
assert(managerSource.includes('setDmpToolEnabled'), '客户端工具应支持启停')
assert(managerSource.includes('deleteDmpTool'), '客户端工具应支持删除')
assert(apiSource.includes("'/api/rdb/dmp-tools/upload'"), '前端应调用工具包上传接口')

console.log('DMP客户端工具管理与导入导出选型界面回归测试通过')
