import request from '@/utils/request'

export function migrationRequest(options) {
  return request(Object.assign({ timeout: 300000 }, options)).then(response => {
    if (!response || response.success !== true) {
      throw new Error((response && response.errorMessage) || '迁移操作失败')
    }
    return response.data
  })
}

export default {
  connection: id => migrationRequest({ url: `/api/migration/connection/${id}`, method: 'get' }),
  objects: params => migrationRequest({ url: '/api/migration/objects', method: 'get', params }),
  definition: params => migrationRequest({ url: '/api/migration/definition', method: 'get', params }),
  list: params => migrationRequest({ url: '/api/migration/tasks', method: 'get', params }),
  get: id => migrationRequest({ url: `/api/migration/tasks/${id}`, method: 'get' }),
  save: (id, data) => migrationRequest({ url: id ? `/api/migration/tasks/${id}` : '/api/migration/tasks', method: id ? 'put' : 'post', data }),
  delete: id => migrationRequest({ url: `/api/migration/tasks/${id}`, method: 'delete' }),
  precheck: id => migrationRequest({ url: `/api/migration/tasks/${id}/precheck`, method: 'post' }),
  start: (id, data) => migrationRequest({ url: `/api/migration/tasks/${id}/start`, method: 'post', data }),
  cancel: id => migrationRequest({ url: `/api/migration/tasks/${id}/cancel`, method: 'post' }),
  resumeCheck: id => migrationRequest({ url: `/api/migration/tasks/${id}/resume-check`, method: 'post' }),
  objectRetryCheck: (id, data) => migrationRequest({ url: `/api/migration/tasks/${id}/object-retry-check`, method: 'post', data }),
  retryDraft: (id, data) => migrationRequest({ url: `/api/migration/tasks/${id}/retry-draft`, method: 'post', data }),
  resume: (id, data) => migrationRequest({ url: `/api/migration/tasks/${id}/resume`, method: 'post', data }),
  report: id => migrationRequest({ url: `/api/migration/tasks/${id}/report`, method: 'get' })
}
