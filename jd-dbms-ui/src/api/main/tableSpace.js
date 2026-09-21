import request from '@/utils/request'

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

function getTableSpaceDetails(data) {
  return request({
    url: '/api/rdb/tableSpace/query',
    method: 'get',
    params: data
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
// 删除表空间
function deleteTableSpace(data) {
  return request({
    url: '/api/rdb/tableSpace/dropTablespace',
    method: 'post',
    data
  })
}


export default {
  getTableSpaceList,
  getTableSpaceDetails,
  createTableSpace,
  updateTableSpace,
  getTableSpaceFileName,
  getTableSpaceNameList,
  deleteTableSpace
}
