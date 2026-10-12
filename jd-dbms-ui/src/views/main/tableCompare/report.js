const STATUS_LABELS = {
  changed: '有变化', 'source-only': '仅源侧', 'target-only': '仅目标侧',
  same: '一致', unknown: '无法判定'
}

const OBJECT_LABELS = {
  INDEX: '索引', CONSTRAINT: '约束', VIEW: '视图', TRIGGER: '触发器',
  PROGRAM: '程序', SEQUENCE: '序列', SYNONYM: '同义词'
}

const FIELD_LABELS = [
  ['dataType', '数据类型'], ['columnType', '完整类型'], ['columnSize', '长度'],
  ['decimalDigits', '小数位'], ['nullable', '是否可空'], ['defaultValue', '默认值'],
  ['primaryKey', '是否主键'], ['autoIncrement', '是否自增'],
  ['ordinalPosition', '字段顺序'], ['comment', '注释']
]

function escapeHtml(value) {
  return String(value === null || value === undefined ? '' : value)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;')
}

function present(value) {
  return escapeHtml(value === null || value === undefined || value === '' ? '—' : value)
}

function completeType(column) {
  if (!column) return '不存在'
  const explicit = String(column.columnType || '').trim()
  if (explicit) return explicit.replace(/\s+/g, ' ').replace(/\s*([(),])\s*/g, '$1').toUpperCase()
  const type = String(column.dataType || '').trim().toUpperCase()
  const size = column.columnSize === null || column.columnSize === undefined ? '' : String(column.columnSize).trim()
  if (!size || size === '0') return type || '—'
  const decimal = column.decimalDigits === null || column.decimalDigits === undefined ? '' : String(column.decimalDigits).trim()
  return `${type}(${size}${decimal && decimal !== '0' ? `,${decimal}` : ''})`
}

function valueForField(column, field) {
  if (!column) return '不存在'
  if (field === 'columnType') return completeType(column)
  const value = column[field]
  if (field === 'dataType') return String(value || '').trim().toUpperCase() || '—'
  if (['columnSize', 'decimalDigits'].includes(field) && (value === null || value === undefined || value === '' || Number(value) === 0)) return '—'
  if (field === 'nullable') return value === true || value === 1 || ['1', 'TRUE', 'Y', 'YES'].includes(String(value).toUpperCase()) ? '可为空' : '不可为空'
  if (field === 'primaryKey' || field === 'autoIncrement') return value === true || value === 1 || ['1', 'TRUE', 'Y', 'YES'].includes(String(value).toUpperCase()) ? '是' : '否'
  return value === null || value === undefined || value === '' ? '—' : String(value)
}

function statusCounts(rows) {
  return rows.reduce((counts, row) => {
    counts[row.status] = (counts[row.status] || 0) + 1
    return counts
  }, { changed: 0, 'source-only': 0, 'target-only': 0, same: 0, unknown: 0 })
}

function fieldDetails(row) {
  const changes = row.status === 'changed'
    ? `<div class="changes">${(row.changeDetails || []).map(detail => `<div><strong>${escapeHtml(detail.label)}</strong><span>${present(detail.source)}</span><b>→</b><em>${present(detail.target)}</em></div>`).join('')}</div>`
    : `<p class="note">${row.status === 'source-only' ? '目标侧缺少该字段' : row.status === 'target-only' ? '源侧缺少该字段' : '参与比较的属性一致'}</p>`
  const properties = FIELD_LABELS.map(([field, label]) => `<tr><th>${label}</th><td class="${(row.changedFields || []).includes(field) ? 'changed-cell' : ''}">${present(valueForField(row.source, field))}</td><td class="${(row.changedFields || []).includes(field) ? 'changed-cell' : ''}">${present(valueForField(row.target, field))}</td></tr>`).join('')
  return `<article class="field-card"><h4>${escapeHtml(row.columnName)} <small>${STATUS_LABELS[row.status] || escapeHtml(row.status)}</small></h4>${changes}<table class="properties"><thead><tr><th>属性</th><th>源侧</th><th>目标侧</th></tr></thead><tbody>${properties}</tbody></table></article>`
}

function tableSummary(rows) {
  if (!rows.length) return ''
  return `<section><h2>数据表汇总</h2><table><thead><tr><th>表名</th><th>结果</th><th>字段变化</th></tr></thead><tbody>${rows.map(row => `<tr><td>${escapeHtml(row.sourceName || row.targetName)}</td><td>${STATUS_LABELS[row.status] || escapeHtml(row.status)}</td><td>${row.status === 'changed' ? `修改 ${row.counts.changed}，仅源侧 ${row.counts.sourceOnly}，仅目标侧 ${row.counts.targetOnly}` : '—'}</td></tr>`).join('')}</tbody></table></section>`
}

function objectSummary(rows, issues) {
  const warnings = issues.length ? `<div class="warning"><strong>读取提示</strong>${issues.map(issue => `<p>${escapeHtml(issue)}</p>`).join('')}</div>` : ''
  if (!rows.length) return `<section><h2>其他结构对象</h2>${warnings}<p class="note">未发现可比较的结构对象。</p></section>`
  const counts = statusCounts(rows)
  return `<section><h2>其他结构对象</h2><p class="note">共 ${rows.length} 个；有变化 ${counts.changed}，仅源侧 ${counts['source-only']}，仅目标侧 ${counts['target-only']}，无法判定 ${counts.unknown}。</p>${warnings}<table><thead><tr><th>类型</th><th>对象名</th><th>结果</th></tr></thead><tbody>${rows.map(row => `<tr><td>${OBJECT_LABELS[row.kind] || escapeHtml(row.kind)}</td><td>${escapeHtml(row.name)}</td><td>${STATUS_LABELS[row.status] || escapeHtml(row.status)}</td></tr>`).join('')}</tbody></table>${rows.filter(row => row.status !== 'same').map(row => `<article class="object-card"><h3>${OBJECT_LABELS[row.kind] || escapeHtml(row.kind)} · ${escapeHtml(row.name)} <small>${STATUS_LABELS[row.status] || escapeHtml(row.status)}</small></h3><div class="definition-grid"><div><strong>源侧定义</strong><pre>${present(row.source && row.source.definition)}</pre></div><div><strong>目标侧定义</strong><pre>${present(row.target && row.target.definition)}</pre></div></div></article>`).join('')}</section>`
}

export function buildCompareReport({ mode, sourcePath, targetPath, fieldRows = [], tableRows = [], objectRows = [], issues = [] }) {
  const now = new Date()
  const date = now.toLocaleString('zh-CN', { hour12: false })
  const fields = mode === 'table' ? fieldRows : tableRows.reduce((total, row) => total.concat(row.fieldRows || []), [])
  const fieldCounts = statusCounts(fields)
  const tableCounts = statusCounts(tableRows)
  const detailSections = mode === 'table'
    ? `<section><h2>字段逐项明细</h2><p class="note">有变化和单侧缺失的字段列出完整属性；一致字段在下方清单中列出。</p>${fields.filter(row => row.status !== 'same').map(fieldDetails).join('') || '<p>未发现字段差异。</p>'}</section>`
    : `<section><h2>逐表字段明细</h2>${tableRows.filter(row => row.status !== 'same').map(row => `<div class="table-detail"><h3>${escapeHtml(row.sourceName || row.targetName)} <small>${STATUS_LABELS[row.status]}</small></h3>${row.fieldRows && row.fieldRows.length ? row.fieldRows.filter(field => field.status !== 'same').map(fieldDetails).join('') : '<p class="note">另一侧不存在同名表，未读取字段明细。</p>'}</div>`).join('') || '<p>所有数据表字段结构一致。</p>'}</section>`
  const fieldInventory = mode === 'table' ? `<section><h2>全部字段清单</h2><table><thead><tr><th>字段</th><th>结果</th><th>源侧完整类型</th><th>目标侧完整类型</th></tr></thead><tbody>${fields.map(row => `<tr><td>${escapeHtml(row.columnName)}</td><td>${STATUS_LABELS[row.status]}</td><td>${present(completeType(row.source))}</td><td>${present(completeType(row.target))}</td></tr>`).join('')}</tbody></table></section>` : ''
  return `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><title>表结构对比报告-${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}</title><style>
  @page{size:A4;margin:17mm 15mm}*{box-sizing:border-box}body{font-family:"Microsoft YaHei","Noto Sans CJK SC","PingFang SC",sans-serif;color:#243244;font-size:10.5pt;line-height:1.55;margin:0 auto;max-width:960px;padding:20px}h1{font-size:22pt;margin:0 0 8px}h2{font-size:15pt;border-bottom:2px solid #3b82f6;padding-bottom:5px;margin:28px 0 12px;break-after:avoid-page;page-break-after:avoid}h3{font-size:12pt;margin:18px 0 8px;break-after:avoid-page}h4{font-size:11pt;margin:0 0 8px}small{font-size:9pt;color:#b42318;margin-left:8px}section{page-break-inside:auto}.meta{border:1px solid #dce6f3;background:#f5f9ff;padding:12px 16px;margin:14px 0}.meta p{margin:4px 0;overflow-wrap:anywhere}.summary{display:flex;flex-wrap:wrap;gap:8px}.summary span{border:1px solid #dce6f3;border-radius:4px;padding:5px 9px}.note{color:#66758a;font-size:9pt}.warning{border-left:4px solid #d39b25;background:#fff9e8;padding:8px 12px;margin:10px 0}.warning p{margin:3px 0}table{width:100%;border-collapse:collapse;table-layout:fixed;margin:8px 0 14px}th,td{border:1px solid #d9e2ec;padding:5px 7px;text-align:left;vertical-align:top;overflow-wrap:anywhere}th{background:#f0f5fb}.properties th:first-child{width:24%}.properties td{width:38%}.changed-cell{background:#fff0eb;color:#9f2d20;font-weight:600}.field-card,.object-card,.table-detail{break-inside:avoid;page-break-inside:avoid;border:1px solid #dce6f3;border-radius:4px;padding:10px 12px;margin:10px 0}.table-detail{break-inside:auto}.changes{border-left:3px solid #e29b50;background:#fffaf4;padding:6px 10px;margin:6px 0}.changes div{display:flex;gap:8px;flex-wrap:wrap}.changes strong{min-width:90px}.changes em{font-style:normal;color:#b42318;font-weight:600}.definition-grid{display:grid;grid-template-columns:1fr 1fr;gap:10px}.definition-grid pre{font-family:Consolas,monospace;font-size:8pt;white-space:pre-wrap;overflow-wrap:anywhere;background:#f7f9fb;padding:8px;margin:3px 0}.print-tools{position:sticky;top:0;background:white;padding:8px 0;border-bottom:1px solid #ddd;display:flex;justify-content:space-between;align-items:center}.print-tools button{background:#1769c2;color:white;border:0;border-radius:4px;padding:8px 15px;cursor:pointer}footer{color:#66758a;font-size:8pt;margin-top:20px}@media print{body{padding:0;max-width:none}.print-tools{display:none}a{color:inherit;text-decoration:none}}
  </style></head><body><div class="print-tools"><span>对比报告预览 · 在打印窗口选择“保存为 PDF”</span><button onclick="window.print()">打印 / 保存 PDF</button></div><h1>表结构对比报告</h1><div class="meta"><p><strong>对比方式：</strong>${mode === 'table' ? '单表对比' : '整库对比'}</p><p><strong>源侧：</strong>${escapeHtml(sourcePath)}</p><p><strong>目标侧：</strong>${escapeHtml(targetPath)}</p><p><strong>生成时间：</strong>${escapeHtml(date)}</p></div><div class="summary"><span>字段 ${fields.length}</span><span>字段修改 ${fieldCounts.changed}</span><span>仅源侧字段 ${fieldCounts['source-only']}</span><span>仅目标侧字段 ${fieldCounts['target-only']}</span>${mode === 'database' ? `<span>数据表 ${tableRows.length}</span><span>表结构变化 ${tableCounts.changed}</span>` : ''}</div><p class="note">“源侧 → 目标侧”表示属性值从源侧定义到目标侧定义的差异。字段名称按数据库大小写精确匹配；无法读取的对象会单独列出。</p>${tableSummary(tableRows)}${detailSections}${fieldInventory}${objectSummary(objectRows, issues)}<footer>本报告依据生成时读取的数据库元数据制作。请在打印窗口选择“保存为 PDF”；页面上的筛选条件不会省略报告内容。</footer><script>window.addEventListener('load',function(){setTimeout(function(){window.print()},400)})<\/script></body></html>`
}
