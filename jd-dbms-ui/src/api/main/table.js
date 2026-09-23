import request from '@/utils/request'

function getTableList(data) {
  return request({
    url: '/api/rdb/table/list',
    method: 'get',
    params: data
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

function tableImport(data) {
  return request({
    url: '/api/rdb/table/import',
    method: 'post',
    data: data
  })
}

function importCheck(data) {
  return request({
    url: '/api/rdb/table/import/check',
    method: 'post',
    data: data
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
function getQueryRefer(data, config = {}) {
  return request({
    url: '/api/rdb/table/queryReferenced',
    method: 'get',
    params: data,
    ...config
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

function deleteTableDataTask(data) {
  return request({
    url: '/api/rdb/table/dropTableTask',
    method: 'post',
    data
  })
}

// 视图依赖
function viewDependent(data, config = {}) {
  return request({
    url: '/api/rdb/table/queryViewReferenced',
    method: 'get',
    params: data,
    ...config
  })
}

// 导航右击带出查询sql
function querySearchSql(data) {
  return request({
    url: '/api/rdb/table/getTemplateSql',
    method: 'get',
    params: data
  })
}


// 复制表
function copyTable(data) {
  return request({
    url: '/api/rdb/table/copyTable',
    method: 'get',
    params: data
  })
}
// sql调测功能
function getDebugSql(data) { 
  return request({
    url: '/api/rdb/table/getDebugSql',
    method: 'get',
    params:data
  })
}

// 文件 blob
function getTableDataBlob(data) { 
  return request({
    url: '/api/rdb/dml/parseFileType',
    method: 'post',
    data
  })
}

// 去往目的表格
function getAimTableData(data) {
  return request({
    url: '/api/rdb/table/columnMapping',
    method: 'post',
    data
  })
}

function downLoadTableData(data) { 
  return request({
    url: '/api/rdb/dml/exportFile',
    method: 'post',
    data:data,
  })
}

// 针对表blob 新增
function addBlobData(data) { 
  return request({
    url: '/api/rdb/dml/execute_blob_sql',
    method: 'post',
    data:data,
  })
}

// 针对表blob 新增
function getBlobData(data) { 
  return request({
    url: '/api/rdb/dml/getBlobData',
    method: 'post',
    data,
  })
}



export default {
  getBlobData,
  getTableList,
  getAllTableList,
  getAllFieldByTable,
  addTablePin,
  deleteTablePin,
  deleteTable,
  getDatabaseFieldTypeList,
  getModifyTableSql,
  getTableDetails,
  tableImport,
  importCheck,
  getGrantList,
  getQueryRefer,
  createConstraint,
  deleteConstraint,
  addTable,
  deleteTableData,
  deleteTableDataTask,
  viewDependent,
  querySearchSql,
  copyTable,
  getDebugSql,
  getTableDataBlob,
  getAimTableData,
  downLoadTableData,
  addBlobData
}
