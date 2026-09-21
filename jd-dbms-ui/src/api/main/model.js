import request from '@/utils/request'
// 获取数据源接口
 function getDataSourceList(data) {
  return request({
    url: '/api/connection/datasource/list',
    method: 'get',
    params: data
  })
}
// 获取用户
 function getSchemaList(data) {
  return request({
    url: '/api/rdb/schema/list',
    method: 'get',
    params: data
  })
}
// 获取表名
 function getTableList(data) {
  return request({
    url: '/api/rdb/table/list',
    method: 'get',
    params: data
  })
}
// 获取表数据
 function viewTable(data) {
  return request({
    url: '/api/rdb/table/column_list_wh',
    method: 'get',
    params: data
  })
}
//去往目的表格
 function getAimTableData(data) {
  return request({
    url: '/api/rdb/table/columnMapping',
    method: 'post',
    data
  })
}

// 查询模型列表
 function listModel(query) {
  return request({
    url: '/api/jdbc/model/management/list',
    method: 'get',
    params: query
  })
}
// 字段映射下一步
 function nextField(data) {
  return request({
    url: '/api/jdbc/model/management/createOrUpdate',
    method: 'post',
    data
  })
}
// 校验规则查询
 function getRuleValidation(id) {
  return request({
    url: '/api/jdbc/model/management/conflictRule',
    method: 'get',
    params: id
  })
}

// 刪除
 function addDelete(data) {
  return request({
    url: '/api/jdbc/model/management/dataDelete',
    method: 'post',
    data
  })
}
// 失焦调用修改接口
 function blurUpdata(data) {
  return request({
    url: '/api/jdbc/model/management/dataUpdate',
    method: 'post',
    data
  })
}
// 校验规则执行
 function nextRules(data) {
  return request({
    url: '/api/jdbc/model/management/execute',
    method: 'get',
    params: data
  })
}

 function delModel(data) {
  return request({
    url: 'api/jdbc/model/management/delete',
    method: 'delete',
    data: data
  })
}

 function searchSqlData(data) {
  return request({
    url: 'api/rdb/table/customizeSql',
    method: 'get',
    params: data
  })
}

 function RuleValidationData(data) {
  return request({
    url: '/api/jdbc/model/management/conflictRulePage',
    method: 'post',
    data
  })
}

// 成功完成
 function saveSuccessData(data) {
  return request({
    url: '/api/jdbc/model/management/finish',
    method: 'get',
    params:data
  })
}
// 查约束
 function queryForeignKey(data) {
  return request({
    url: '/api/jdbc/model/management/forkData',
    method: 'post',
    data
  })
}

// 创表接口
 function createTable(data) {
  return request({
    url: '/api/rdb/table/createToTable',
    method: 'post',
    data
  })
}

// 自定义映射
 function queryCustomTable(data) {
  return request({
    url: '/api/rdb/table/customizeColumnMapping',
    method: 'post',
    data
  })
}

// 导入导出文件执行
 function executeFile(data) {
  return request({
    url: '/api/jdbc/civilianwar/pushExcelData',
    method: 'post',
    data
  })
}

// 清空
 function clearMappingData() {
  return request({
    url: '/api/jdbc/civilianwar/closeExcelData',
    method: 'get',
  })
}

export default {
  viewTable,
  nextField,
  executeFile,
  clearMappingData,
  getAimTableData
}