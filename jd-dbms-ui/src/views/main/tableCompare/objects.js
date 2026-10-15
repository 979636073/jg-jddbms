const TABLE_KINDS = ['INDEX', 'CONSTRAINT', 'TRIGGER']

const LABELS = {
  UNIQUENESS: '唯一性', INDEX_TYPE: '索引类型', STATUS: '状态',
  COLUMN_NAME: '字段', DESCEND: '排序方向', COLUMN_EXPRESSION: '索引表达式',
  CONSTRAINT_TYPE: '约束类型', SEARCH_CONDITION: '检查条件', REFERENCE_OWNER: '引用模式',
  REFERENCE_TABLE: '引用表', REFERENCE_COLUMN: '引用字段', DELETE_RULE: '删除规则',
  DEFERRABLE: '可延迟', DEFERRED: '延迟状态', VALIDATED: '验证状态', GENERATED: '系统生成名称'
}

function fieldValue(fields, key) {
  if (!Object.prototype.hasOwnProperty.call(fields, key)) return '不存在'
  return fields[key] === null ? 'NULL' : fields[key] === '' ? '空字符串' : String(fields[key])
}

export function buildObjectRows(left, right, mode, sourceTable, targetTable) {
  const objectMap = (inventory, tableName) => {
    const occurrences = new Map()
    return new Map((inventory.objects || [])
      .filter(item => mode === 'database' || TABLE_KINDS.includes(item.kind) && item.tableName === tableName)
      .map(item => {
        const table = mode === 'table' ? '' : item.tableName
        let key = item.generated ? JSON.stringify([item.kind, table, 'generated', item.fingerprint])
          : JSON.stringify([item.kind, table, 'named', mode === 'table' ? item.objectName : item.name])
        const count = (occurrences.get(key) || 0) + 1
        occurrences.set(key, count)
        key += `|${count}`
        return [key, item]
      }))
  }
  const sourceMap = objectMap(left, sourceTable)
  const targetMap = objectMap(right, targetTable)
  const failedKinds = new Set([
    ...Object.keys(left.errors || {}), ...Object.keys(right.errors || {}),
    ...(left.truncated || []), ...(right.truncated || [])
  ])
  const rows = [...new Set([...sourceMap.keys(), ...targetMap.keys()])].sort().map(key => {
    const source = sourceMap.get(key)
    const target = targetMap.get(key)
    const item = source || target
    const status = failedKinds.has(item.kind) ? 'unknown' : !source ? 'target-only'
      : !target ? 'source-only' : source.fingerprint === target.fingerprint ? 'same' : 'changed'
    const sourceFields = (source && source.attributes) || {}
    const targetFields = (target && target.attributes) || {}
    const changeDetails = [...new Set([...Object.keys(sourceFields), ...Object.keys(targetFields)])]
      .filter(field => sourceFields[field] !== targetFields[field] ||
        Object.prototype.hasOwnProperty.call(sourceFields, field) !== Object.prototype.hasOwnProperty.call(targetFields, field))
      .map(field => ({
        field, label: field.replace(/^([^[]+)/, name => LABELS[name] || name),
        source: fieldValue(sourceFields, field), target: fieldValue(targetFields, field)
      }))
    return { key, kind: item.kind, name: mode === 'table' ? item.objectName : item.name,
      tableName: item.tableName, source, target, status, changeDetails }
  })
  // A failed category may have no visible objects on either side.
  failedKinds.forEach(kind => {
    if ((mode === 'database' || TABLE_KINDS.includes(kind)) && !rows.some(row => row.kind === kind)) {
      rows.push({ key: `${kind}|unknown`, kind, name: '对象清单未完整读取', status: 'unknown', changeDetails: [] })
    }
  })
  return rows
}

export function applyObjectDifferences(tables, objects, incomplete = false) {
  const unknown = incomplete || objects.some(row => TABLE_KINDS.includes(row.kind) && row.status === 'unknown')
  tables.forEach(table => {
    if (!table.sourceName || !table.targetName) return
    table.objectRows = objects.filter(row => TABLE_KINDS.includes(row.kind) && row.tableName === table.sourceName)
    table.objectDifferenceCount = table.objectRows.filter(row => row.status !== 'same').length
    const fieldsChanged = table.counts.changed || table.counts.sourceOnly || table.counts.targetOnly
    table.status = unknown ? 'unknown' : fieldsChanged || table.objectDifferenceCount ? 'changed' : 'same'
  })
}
