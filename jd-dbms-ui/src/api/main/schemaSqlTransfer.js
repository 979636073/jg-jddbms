import request from '@/utils/request'

export default {
  tables: params => request({ url: '/api/schema-sql-transfer/tables', method: 'get', params }),
  preview: file => {
    const data = new FormData(); data.append('file', file)
    return request({ url: '/api/schema-sql-transfer/preview', method: 'post', data, headers: { 'Content-Type': 'multipart/form-data' }, timeout: 300000 })
  },
  exportSql: data => request({ url: '/api/schema-sql-transfer/export', method: 'post', data, timeout: 300000 }),
  importSql: (context, file, tables, scope) => {
    const data = new FormData()
    data.append('dataSourceId', context.id); data.append('schemaName', context.schema)
    data.append('tables', JSON.stringify(tables)); data.append('scope', scope); data.append('file', file)
    return request({ url: '/api/schema-sql-transfer/import', method: 'post', data, headers: { 'Content-Type': 'multipart/form-data' }, timeout: 300000 })
  }
}
