import request from '@/utils/request'

export function getDatabaseSnapshot(dataSourceId) {
  return request({ url: '/api/rdb/monitor/snapshot', method: 'get', params: { dataSourceId } })
}
