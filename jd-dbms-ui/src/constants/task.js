export const TASK_STATUS = Object.freeze({
  INIT: 'INIT',
  PROCESSING: 'PROCESSING',
  FINISH: 'FINISH',
  ERROR: 'ERROR'
})

export function isTaskTerminal(status) {
  return status === TASK_STATUS.FINISH || status === TASK_STATUS.ERROR
}

