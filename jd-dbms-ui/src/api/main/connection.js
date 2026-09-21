import request from '@/utils/request'

// 查询连接列表
function getList(data) {
  return request({
    url: '/api/connection/datasource/list',
    method: 'get',
    params: data
  })
}
// 删除数据源
function deleteDataSource(id) {
  return request({
    url: `/api/connection/datasource/${id}`,
    method: 'delete',
  })
}
// 关闭数据源
function closeDataSource(params) {
  return request({
    url: `/api/connection/datasource/close`,
    method: 'get',
    params
  })
}

function getDetails(id) {
  return request({
    url: '/api/connection/datasource/' + id,
    method: 'get'
  })
}


function remove(id) {
  return request({
    url: '/api/connection/datasource/' + id,
    method: 'delete'
  })
}

function clone(data) {
  return request({
    url: '/api/connection/datasource/clone',
    method: 'post',
    data: data
  })
}

function getDatabaseList(data) {
  return request({
    url: '/api/rdb/database/list',
    method: 'get',
    params: data
  })
}

function getSchemaList(data) {
  return request({
    url: '/api/rdb/schema/list',
    method: 'get',
    params: data
  })
}

// 获取驱动列表
function getDriverList(data) {
  return request({
    url: '/api/jdbc/driver/list',
    method: 'get',
    params: data
  })
}

// 测试链接
function test(data) {
  return request({
    url: '/api/connection/datasource/pre_connect',
    method: 'post',
    data: data
  })
}

function save(data) {
  return request({
    url: '/api/connection/datasource/create',
    method: 'post',
    data: data
  })
}

function update(data) {
  return request({
    url: '/api/connection/datasource/update',
    method: 'post',
    data: data
  })
}

function getEnvList() {
  return request({
    url: '/api/common/environment/list_all',
    method: 'get'
  })
}

export default {
  closeDataSource,
  getList,
  getDetails,
  remove,
  clone,
  getDatabaseList,
  getSchemaList,
  getDriverList,
  test,
  save,
  update,
  getEnvList,
  deleteDataSource
}
