import request from '@/utils/request'

export function exportTableFile(data) {
  return request({ url: '/api/table-transfer/export', method: 'post', data, timeout: 300000 })
}

export function importTableFile(data) {
  return request({ url: '/api/table-transfer/import', method: 'post', data, timeout: 300000,
    headers: { 'Content-Type': 'multipart/form-data' } })
}
