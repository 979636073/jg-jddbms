import request from '@/utils/request'
import { Message } from 'element-ui'
import { saveAs } from 'file-saver'

export function getTaskList(data) {
  return request({
    url: '/api/task/list',
    method: 'get',
    params: data
  })
}

export function downloadTask(id) {
  return request({
    url: `/api/task/download/${id}`,
    method: 'get',
    responseType: 'blob'
  })
}

export function clearTask(id) {
  return request({
    url: `/api/task/clear/${id}`,
    method: 'post'
  })
}

export function cancelTask(id) {
  return request({
    url: `/api/task/cancel/${id}`,
    method: 'post'
  })
}

export function clearFinishedTasks() {
  return request({
    url: '/api/task/clearFinished',
    method: 'post'
  })
}

export async function downloadTaskFile(id, taskType) {
  const data = await downloadTask(id)
  if (data.type && data.type.indexOf('application/json') === 0) {
    const text = await data.text()
    let message = '下载文件失败'
    try {
      message = JSON.parse(text).msg || message
    } catch (e) {
      // 保留默认错误提示
    }
    Message.error(message)
    return
  }
  const filename = taskType === 'UPLOAD_TABLE_STRUCTURE' ? `DMP导入_${id}.log` : `DMP导出_${id}.dmp`
  saveAs(new Blob([data]), filename)
}

export default {
  getTaskList,
  downloadTask,
  clearTask,
  cancelTask,
  clearFinishedTasks,
  downloadTaskFile
}
