import request from '@/utils/request'

export function queryTableRelations(data) {
  return request({
    url: '/api/rdb/table/queryReferenced',
    method: 'get',
    params: data
  })
}

export function queryViewRelations(data) {
  return request({
    url: '/api/rdb/table/queryViewReferenced',
    method: 'get',
    params: data
  })
}

export default { queryTableRelations, queryViewRelations }
