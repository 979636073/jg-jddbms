import request from '@/utils/request'

/** 获取视图列表 */
function getViewList(data) {
  return request({
    url: '/api/rdb/view/list',
    method: 'get',
    params: data
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

function refreshMaterialized(data) {
  return request({
    url: '/api/rdb/view/refreshMaterialized',
    method: 'post',
    data
  })
}

function grantViewSelect(data) {
  return request({
    url: '/api/rdb/grant/grantSql',
    method: 'post',
    params: data
  })
}

function revokeViewSelect(data) {
  return request({
    url: '/api/rdb/grant/deleteGrant',
    method: 'post',
    params: data
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
// 修改视图名称
function updateViewName(data) {
  return request({
    url: '/api/rdb/view/updateViewTableName',
    method: 'post',
    data
  })
}

// 修改列名称
function updateColumnName(data) {
  return request({
    url: '/api/rdb/view/updateViewColumnName',
    method: 'post',
    data
  })
}

// 视图导入
function importSqlOptiate(data) {
  return request({
    url: 'common/importSQL',
    method: 'post',
    data
  })
}

// 视图导入
function importViewSQL(data) {
  return request({
    url: 'common/importViewSQL',
    method: 'post',
    data
  })
}




export default {
  getViewList,
  getViewDetail,
  getViewColumnList,
  viewShowSql,
  viewExecute,
  allExecute,
  getExecuteSQL,
  viewDelete,
  refreshMaterialized,
  grantViewSelect,
  revokeViewSelect,
  viewAllExecute,
  addViewFn,
  deleteViewFn,
  updateViewName,
  updateColumnName,
  importSqlOptiate,
  importViewSQL
}
