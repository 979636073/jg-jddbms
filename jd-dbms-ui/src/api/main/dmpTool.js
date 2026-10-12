import request from '@/utils/request'

export function listDmpTools() {
  return request({
    url: '/api/rdb/dmp-tools',
    method: 'get'
  })
}

export function uploadDmpTool(data) {
  return request({
    url: '/api/rdb/dmp-tools/upload',
    method: 'post',
    data,
    headers: {
      'Content-Type': 'multipart/form-data',
      repeatSubmit: false
    },
    timeout: 10 * 60 * 1000
  })
}

export function setDmpToolEnabled(id, enabled) {
  return request({
    url: `/api/rdb/dmp-tools/${id}/status`,
    method: 'put',
    params: { enabled }
  })
}

export function redetectDmpTool(id, version) {
  return request({
    url: `/api/rdb/dmp-tools/${id}/redetect`,
    method: 'post',
    params: version ? { version } : undefined,
    timeout: 30000
  })
}

export function updateDmpTool(id, version, platform) {
  return request({
    url: `/api/rdb/dmp-tools/${id}`,
    method: 'put',
    params: { version, platform }
  })
}

export function deleteDmpTool(id) {
  return request({
    url: `/api/rdb/dmp-tools/${id}`,
    method: 'delete'
  })
}
