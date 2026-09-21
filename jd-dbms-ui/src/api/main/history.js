import request from '@/utils/request'

function createConsole(data) {
  return request({
    url: '/api/operation/saved/create',
    method: 'post',
    data: data
  })
}

function updateSavedConsole(data) {
  return request({
    url: '/api/operation/saved/update',
    method: 'post',
    data: data
  })
}

function getConsoleList(data) {
  return request({
    url: '/api/operation/saved/list',
    method: 'get',
    params: data
  })
}

function getHistoryList(data) {
  return request({
    url: '/api/operation/log/list',
    method: 'get',
    params: data
  })
}

function deleteSavedConsole(id) {
  return request({
    url: '/api/operation/saved/' + id,
    method: 'delete'
  })
}

function deleteOpenConsole(data) {
  return request({
    url: '/api/operation/saved/batch_tab_close',
    method: 'post',
    data: data
  })
}




export default {
  createConsole,
  updateSavedConsole,
  getConsoleList,
  getHistoryList,
  deleteSavedConsole,
  deleteOpenConsole
}
