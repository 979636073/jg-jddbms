import request from '@/utils/request'

export function getDatabaseSnapshot(dataSourceId) {
  return request({ url: '/api/rdb/monitor/snapshot', method: 'get', params: { dataSourceId } })
}

export function getMonitorState(dataSourceId) {
  return request({ url: '/api/rdb/monitor/state', method: 'get', params: { dataSourceId } })
}

export function saveMonitorConfig(config) {
  return request({ url: '/api/rdb/monitor/config', method: 'put', data: config })
}
