import request from '@/utils/request'

function getTableList(data) {
  return request({
    url: '/api/rdb/table/list',
    method: 'get',
    params: data
  })
}

/** 获取视图列表 */
function getViewList(data) {
  return request({
    url: '/api/rdb/view/list',
    method: 'get',
    params: data
  })
}

/** 获取用户列表 */
function getUserList(data) {
  return request({
    url: '/api/rdb/user/list',
    method: 'get',
    params: data
  })
}
/** 获取表空间列表 */
function getTableSpaceList(data) {
  return request({
    url: '/api/rdb/tableSpace/list',
    method: 'get',
    params: data
  })
}

/** 获取表空间名称列表 */
function getTableSpaceNameList(data) {
  return request({
    url: '/api/rdb/tableSpace/tableSpaceNames',
    method: 'get',
    params: data
  })
}

function getFunctionList(data) {
  return request({
    url: '/api/rdb/function/list',
    method: 'get',
    params: data
  })
}

function getProcedureList(data) {
  return request({
    url: '/api/rdb/procedure/list',
    method: 'get',
    params: data
  })
}

function getTriggerList(data) {
  return request({
    url: '/api/rdb/trigger/list',
    method: 'get',
    params: data
  })
}

function executeSql(data) {
  data.sql = data.sql.replace(/</g,"&lt;").replace(/>/g,"&gt;")
  return request({
    url: '/api/rdb/dml/execute',
    method: 'post',
    data: data
  })
}

// 提交
function commitSession(data) {
  return request({
    url: '/api/rdb/dml/commitSession',
    method: 'post',
    data: data
  })
}

// 删除一个或多个新建查询窗口
function delSession(data) {
  return request({
    url: '/api/rdb/dml/delSession',
    method: 'post',
    data: data
  })
}

// 回退
function rollBackSession(data) {
  return request({
    url: '/api/rdb/dml/rollbackSession',
    method: 'post',
    data: data
  })
}

function executeJDBCSql(data) {
  return request({
    url: '/api/rdb/dml/JDBCExecute',
    method: 'post',
    data: data
  })
}

function viewTable(data) {
  return request({
    url: '/api/rdb/dml/execute_table',
    method: 'post',
    data: data
  })
}

/** 获取库的所有表 */
function getAllTableList(data) {
  return request({
    url: '/api/rdb/table/table_list',
    method: 'get',
    params: data
  })
}

/** 获取表的所有字段 */
function getAllFieldByTable(data) {
  return request({
    url: '/api/rdb/table/column_list',
    method: 'get',
    params: data
  })
}

// 执行修改表数据的sql
function executeUpdateDataSql(data) {
  return request({
    url: '/api/rdb/dml/execute_update',
    method: 'post',
    data: data
  })
}

function addTablePin(data) {
  return request({
    url: '/api/pin/table/add',
    method: 'post',
    data: data
  })
}

function deleteTablePin(data) {
  return request({
    url: '/api/pin/table/delete',
    method: 'post',
    data: data
  })
}

function exportCreateTableSql(data) {
  return request({
    url: '/api/rdb/ddl/export',
    method: 'get',
    params: data
  })
}

function deleteTable(data) {
  return request({
    url: '/api/rdb/table/delete',
    method: 'post',
    data: data
  })
}

/** 数据库支持的数据类型 */
function getDatabaseFieldTypeList(data) {
  return request({
    url: '/api/rdb/table/table_meta',
    method: 'get',
    params: data
  })
}

/** 获取修改表的sql */
function getModifyTableSql(data) {
  return request({
    url: '/api/rdb/table/modify/sql',
    method: 'post',
    data: data
  })
}

/** 获取表的详情 */
function getTableDetails(data) {
  return request({
    url: '/api/rdb/table/query',
    method: 'get',
    params: data
  })
}
function getTableCoulumnDetails(data) {
  return request({
    url: '/api/rdb/table/columnDefault',
    method: 'get',
    params: data
  })
}


/** 获取数据库用户的详情 */
function getUserDetails(data) {
  return request({
    url: '/api/rdb/user/query',
    method: 'get',
    params: data
  })
}

/** 执行编辑表的sql, 专为编辑表而生 */
function executeDDL(data) {
  return request({
    url: '/api/rdb/dml/execute_ddl',
    method: 'post',
    data: data
  })
}

/** 获取修改表数据的接口 */
function getExecuteUpdateSql(data) {
  return request({
    url: '/api/rdb/dml/get_update_sql',
    method: 'post',
    data: data
  })
}

/** 获取视图详情 */
function getViewDetail(data) {
  return request({
    url: '/api/rdb/view/detail',
    method: 'get',
    params: data
  })
}

/** 获取函数详情 */
function getFunctionDetail(data) {
  return request({
    url: '/api/rdb/function/detail',
    method: 'get',
    params: data
  })
}

/** 获取过程详情 */
function getProcedureDetail(data) {
  return request({
    url: '/api/rdb/procedure/detail',
    method: 'get',
    params: data
  })
}

/** 获取触发器详情 */
function getTriggerDetail(data) {
  return request({
    url: '/api/rdb/trigger/detail',
    method: 'get',
    params: data
  })
}

function databaseExport(data) {
  return request({
    url: '/api/rdb/database/export2',
    method: 'post',
    data: data
  })
}

function databaseImport(data) {
  return request({
    url: '/api/rdb/database/import',
    method: 'post',
    data: data
  })
}

function tableImport(data) {
  return request({
    url: '/api/rdb/table/import',
    method: 'post',
    data: data
  })
}

/** 获取视图字段列表 */
function getViewColumnList(data) {
  return request({
    url: '/api/rdb/view/column_list',
    method: 'post',
    data: data
  })
}
function viewShowSql(data) {
  return request({
    url: '/api/rdb/view/showSql',
    method: 'post',
    data: data
  })
}

function viewExecute(data) {
  return request({
    url: '/api/rdb/view/execute',
    method: 'post',
    data: data
  })
}
function allExecute(data) {
  return request({
    url: '/api/rdb/view/allExecute',
    method: 'post',
    data: data
  })
}
function getExecuteSQL(data) {
  return request({
    url: '/api/rdb/view/getExecuteSQL',
    method: 'post',
    data: data
  })
}
function viewDelete(data) {
  return request({
    url: '/api/rdb/view/delete',
    method: 'post',
    data: data
  })
}

// 新建存储过程
function create_procedure(data) {
  return request({
    url: '/api/rdb/procedure/create_procedure',
    method: 'post',
    data: data
  })
}

// 删除存储过程
function delete_procedure(data) {
  return request({
    url: '/api/rdb/procedure/delete_procedure',
    method: 'post',
    data: data
  })
}


// 创建函数
function create_function(data) {
  return request({
    url: '/api/rdb/function/create_function',
    method: 'post',
    data: data
  })
}

// 删除函数SQL
function delete_function(data) {
  return request({
    url: '/api/rdb/function/delete_function',
    method: 'post',
    data: data
  })
}


// 创建触发器
function create_triggers(data) {
  return request({
    url: '/api/rdb/trigger/create_triggers',
    method: 'post',
    data: data
  })
}

// 创建触发器
function delete_triggers(data) {
  return request({
    url: '/api/rdb/trigger/delete_triggers',
    method: 'post',
    data: data
  })
}


function SortSqlFormat(data) {
  return request({
    url: '/api/sql/SortSqlFormat',
    method: 'get',
    params: data
  })
}


function filterSqlFormat(data) {
  return request({
    url: '/api/sql/filterSqlFormat',
    method: 'get',
    params: data
  })
}

function importCheck(data) {
  return request({
    url: '/api/rdb/table/import/check',
    method: 'post',
    data: data
  })
}

function exportDmp(data) {
  return request({
    url: '/api/rdb/database/exportDmp',
    method: 'post',
    data: data
  })
}

function importDmp(data) {
  return request({
    url: '/api/rdb/database/importDmp',
    method: 'post',
    data: data
  })
}
function importDmpUser(data) {
  return request({
    url: '/api/rdb/database/importDmpList',
    method: 'post',
    data: data
  })
}

function downloadImportLog(data) {
  return request({
    url: '/api/rdb/database/downloadImportLog',
    method: 'post',
    data: data
  })
}

function getRoleGrantsList(data) {
  return request({
    url: '/api/rdb/user/role',
    method: 'get',
    params: data
  })
}

function getTableSpaceDetails(data) {
  return request({
    url: '/api/rdb/tableSpace/query',
    method: 'get',
    params: data
  })
}

function getGrantList(data) {
  return request({
    url: '/api/rdb/table/queryRoles',
    method: 'get',
    params: data
  })
}
//被引用情况
function getQueryRefer(data) {
  return request({
    url: '/api/rdb/table/queryReferenced',
    method: 'get',
    params: data
  })
}

// 查找归档导入title字段
function queryArchiveTitle() {
  return request({
    url: '/api/rdb/pigenhole/getPigenhole',
    method: 'get',
  })
}
// 归档导入
function importArchive(data) {
  return request({
    url: '/api/rdb/pigenhole/importTable',
    method: 'post',
    data: data
  })
}

function createConstraint(data) {
  return request({
    url: '/api/rdb/table/createTable_constraint',
    method: 'post',
    data
  })
}
function deleteConstraint(data) {
  return request({
    url: '/api/rdb/table/drop_table_constraint',
    method: 'get',
    params: data
  })
}

// 新增表
function addTable(data) {
  return request({
    url: '/api/rdb/table/createTable',
    method: 'post',
    data
  })
}
function deleteTableData(data) {
  return request({
    url: '/api/rdb/table/dropTable',
    method: 'post',
    data
  })
}

// 视图编译
function viewAllExecute(data) {
  return request({
    url: '/api/rdb/view/allExecute',
    method: 'post',
    data
  })
}
function addViewFn(data) {
  return request({
    url: '/api/rdb/view/execute',
    method: 'post',
    data
  })
}
// 删除视图
function deleteViewFn(data) {
  return request({
    url: '/api/rdb/view/delete',
    method: 'post',
    data
  })
}
// 视图依赖
function viewDependent(data) {
  return request({
    url: '/api/rdb/table/queryViewReferenced',
    method: 'get',
    params: data
  })
}
// 表数据导入预览
function previewTableData(data) {
  return request({
    url: 'common/previewData',
    method: 'post',
    data
  })
}
// 校验数据预览
function executePreview(data) {
  return request({
    url: 'common/executePreview',
    method: 'post',
    data
  })
}
// 表数据导入预览
function previewTableAllData(data) {
  return request({
    url: 'common/previewData',
    method: 'post',
    data
  })
}

// 表数据导入预览
function previewTableAllPage(data) {
  return request({
    url: 'common/previewDataAllPage',
    method: 'post',
    data
  })
}

//创建表空间
function createTableSpace(data) {
  return request({
    url: '/api/rdb/tableSpace/createTablespaceSql',
    method: 'post',
    data: data
  })
}
//修改表空间
function updateTableSpace(data) {
  return request({
    url: '/api/rdb/tableSpace/updateTablespaceSql',
    method: 'post',
    data: data
  })
}

//获取表空间文件列表
function getTableSpaceFileName(data) {
  return request({
    url: '/api/rdb/tableSpace/listPath',
    method: 'get',
    params: data
  })
}

//获取用户角色列表
function getUserRoleList(data) {
  return request({
    url: '/api/rdb/user/allRole',
    method: 'get',
    params: data
  })
}

function dataSaveExecute(data) {
  return request({
    url: 'common/dataSave',
    method: 'post',
    data
  })
}
//  表导入预览分页
function queryPageTable(data) {
  return request({
    url: 'common/previewDataPage',
    method: 'post',
    data
  })
}

// 预览清空
function clearPageData(data) {
  return request({
    url: 'common/delPreviewData',
    method: 'post',
    data
  })
}
// 触发器新增
function addTriggerData(data) {
  return request({
    url: '/api/rdb/trigger/createTriggersWH',
    method: 'post',
    data
  })
}

// 触发器删除
function deleteTriggerData(data) {
  return request({
    url: '/api/rdb/trigger/deleteTriggers',
    method: 'post',
    data
  })
}

// 获取服务器地址接口
function queryServerPath() {
  return request({
    url: '/api/rdb/pigenhole/getDictUrl',
    method: 'get',
  })
}

// 前置校验
function queryRule(data) {
  return request({
    url: '/api/rdb/pigenhole/exportCheckTable',
    method: 'post',
    data
  })
}
function queryRuleExportTable(data) {
  return request({
    url: '/api/rdb/pigenhole/exportTable',
    method: 'post',
    data
  })
}
// 执行计划
function implementationPlan(data) {
  return request({
    url: '/api/rdb/dml/executeExplain',
    method: 'post',
    data
  })
}

// 新建查询sql导出
function createExportSql(data) {
  return request({
    url: '/api/rdb/table/customExport',
    method: 'post',
    data
  })
}

// 新建查询sql 日志查看
function sqlLog(data) {
  return request({
    url: '/api/operation/log/list',
    method: 'get',
    params:data
  })
}

export default {
  getTableList,
  getViewList,
  getUserList,
  getTableSpaceList,
  getFunctionList,
  getProcedureList,
  getTriggerList,
  executeSql,
  executeJDBCSql,
  viewTable,
  getAllTableList,
  getAllFieldByTable,
  executeUpdateDataSql,
  addTablePin,
  deleteTablePin,
  exportCreateTableSql,
  deleteTable,
  getDatabaseFieldTypeList,
  getModifyTableSql,
  getTableDetails,
  executeDDL,
  getExecuteUpdateSql,
  getViewDetail,
  getFunctionDetail,
  getProcedureDetail,
  getTriggerDetail,
  databaseExport,
  databaseImport,
  tableImport,
  getViewColumnList,
  viewShowSql,
  viewExecute,
  allExecute,
  getExecuteSQL,
  viewDelete,
  filterSqlFormat,
  SortSqlFormat,
  create_procedure,
  delete_procedure,
  create_function,
  delete_function,
  create_triggers,
  delete_triggers,
  importCheck,
  exportDmp,
  importDmp,
  importDmpUser,
  downloadImportLog,
  getUserDetails,
  getRoleGrantsList,
  getTableSpaceDetails,
  getGrantList,
  getQueryRefer,
  queryArchiveTitle,
  importArchive,
  createConstraint,
  deleteConstraint,
  addTable,
  deleteTableData,
  viewAllExecute,
  addViewFn,
  deleteViewFn,
  viewDependent,
  createTableSpace,
  updateTableSpace,
  getTableSpaceFileName,
  getUserRoleList,
  getTableSpaceNameList,
  previewTableData,
  executePreview,
  previewTableAllData,
  previewTableAllPage,
  dataSaveExecute,
  addTriggerData,
  deleteTriggerData,
  getTableCoulumnDetails,
  queryPageTable,
  clearPageData,
  queryServerPath,
  queryRule,
  queryRuleExportTable,
  implementationPlan,
  createExportSql,
  sqlLog,
  rollBackSession,
  delSession,
  commitSession
}
