import request from '@/utils/request'

export function compareTables(data) {
  return request({
    url: '/api/rdb/table/compare',
    method: 'get',
    params: data
  })
}

export default { compareTables }
