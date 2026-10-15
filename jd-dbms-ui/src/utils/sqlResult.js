// 控制台结果只读；避免 Vue 深度观察每一个单元格。
export function prepareSqlResult(result) {
  if (Array.isArray(result.dataList)) Object.freeze(result.dataList)
  if (Array.isArray(result.headerList)) Object.freeze(result.headerList)
  return result
}
