// Isolated visual smoke server: real production assets and in-memory draft fixtures, never a database proxy.
const http = require('http')
const fs = require('fs')
const path = require('path')
const root = path.resolve(__dirname, '../dist')
const evidenceRoot = path.resolve(__dirname, '../../jd-dbms-bisnuess/target')
const failureReports = fs.readdirSync(evidenceRoot).filter(name => /^migration-failure-.*\.json$/.test(name)).map(name => JSON.parse(fs.readFileSync(path.join(evidenceRoot, name), 'utf8')))
const reports = failureReports.concat(['DM-DM', 'DM-ORACLE', 'ORACLE-DM', 'ORACLE-ORACLE'].map(direction => {
  const file = path.resolve(__dirname, '../../jd-dbms-bisnuess/target/migration-live-' + direction + '.json')
  return fs.existsSync(file) ? JSON.parse(fs.readFileSync(file, 'utf8')) : null
}).filter(Boolean))
const mime = { '.html': 'text/html', '.js': 'application/javascript', '.css': 'text/css', '.png': 'image/png', '.svg': 'image/svg+xml', '.woff': 'font/woff', '.woff2': 'font/woff2', '.ttf': 'font/ttf' }
const drafts = new Map()
const previewObjects = [{ name: 'SAMPLE_TABLE', type: 'TABLE' }, { name: 'SAMPLE_VIEW', type: 'VIEW' }, { name: 'SPECIAL_MVIEW', type: 'MATERIALIZED VIEW' }].concat(Array.from({ length: 68 }, (_, index) => ({ name: 'SAMPLE_TABLE_' + String(index + 1).padStart(3, '0'), type: 'TABLE' })))
const reply = (response, data) => { response.setHeader('Content-Type', 'application/json'); response.end(JSON.stringify({ success: true, data })) }
http.createServer((request, response) => {
  const pathname = decodeURIComponent(new URL(request.url, 'http://127.0.0.1').pathname)
  if (pathname.includes('/getInfo')) {
    response.setHeader('Content-Type', 'application/json'); response.end(JSON.stringify({ code: 200, user: { userId: 999000, userName: '迁移界面检查' }, roles: ['admin'], permissions: ['*:*:*'] })); return
  }
  if (pathname.includes('/getRouters')) {
    response.setHeader('Content-Type', 'application/json'); response.end(JSON.stringify({ code: 200, data: [] })); return
  }
  if (pathname.includes('/api/connection/datasource/list')) {
    response.setHeader('Content-Type', 'application/json'); response.end(JSON.stringify({ success: true, data: [{ id: 999001, alias: '界面测试 DM（无真实连接）', type: 'DM' }, { id: 999002, alias: '界面测试 Oracle（无真实连接）', type: 'ORACLE' }] })); return
  }
  const connection = pathname.match(/\/api\/migration\/connection\/(999001|999002)$/)
  if (connection) { reply(response, { id: Number(connection[1]), name: '界面隔离连接', type: connection[1] === '999001' ? 'DM' : 'ORACLE', version: '仅界面测试，无数据库连接', driver: '界面 fixture', schemas: [{ name: 'QA_UI_ONE' }, { name: 'QA_UI_TWO' }], tablespaces: [{ name: 'QA_UI_DATA' }] }); return }
  if (pathname.endsWith('/api/migration/objects')) {
    const params = new URL(request.url, 'http://127.0.0.1').searchParams, page = Number(params.get('page') || 1), size = Number(params.get('size') || 50)
    const rows = previewObjects.filter(row => row.name.toLowerCase().includes((params.get('search') || '').toLowerCase()))
    reply(response, { data: rows.slice((page - 1) * size, page * size), total: rows.length }); return
  }
  if (pathname.endsWith('/api/migration/definition')) { reply(response, { ddl: 'CREATE VIEW "QA_UI_ONE"."SAMPLE_VIEW" AS SELECT ID FROM "QA_UI_ONE"."SAMPLE_TABLE"', columns: [{ name: 'ID', sourceType: 'NUMBER', kind: 'NUMBER' }, { name: 'NOTE', sourceType: 'VARCHAR', kind: 'TEXT' }] }); return }
  if ((pathname.endsWith('/api/migration/tasks') && request.method === 'POST') || (/\/api\/migration\/tasks\/990\d+$/.test(pathname) && request.method === 'PUT')) {
    let body = ''; request.on('data', chunk => { body += chunk; if (body.length > 131072) request.destroy() })
    request.on('end', () => { try { const config = JSON.parse(body); const id = request.method === 'POST' ? 990000 + drafts.size : Number(pathname.split('/').pop()); const task = { id, config, status: 'MIG_DRAFT', stage: 'CONFIGURATION', createdAt: Date.now(), objects: [], logs: [], committedRows: 0 }; drafts.set(String(id), task); reply(response, task) } catch (_) { response.writeHead(400); response.end() } }); return
  }
  if (pathname.endsWith('/api/migration/tasks') && request.method === 'GET') {
    response.setHeader('Content-Type', 'application/json'); response.end(JSON.stringify({ success: true, data: { data: reports, total: reports.length } })); return
  }
  const task = pathname.match(/\/api\/migration\/tasks\/(\d+)(?:\/report)?$/)
  if (task && request.method === 'GET') {
    const report = drafts.get(task[1]) || reports.find(item => String(item.id) === task[1])
    response.setHeader('Content-Type', 'application/json'); response.end(JSON.stringify(report ? { success: true, data: report } : { success: false, errorMessage: '隔离报告不存在' })); return
  }
  if (pathname.includes('/api/') || pathname.startsWith('/prod-api/')) {
    response.setHeader('Content-Type', 'application/json'); response.end(JSON.stringify({ success: false, errorMessage: '隔离界面检查不连接数据库、不执行迁移', code: 500 })); return
  }
  let file = path.resolve(root, '.' + pathname)
  if (!file.startsWith(root + path.sep) && file !== root) { response.writeHead(403); response.end(); return }
  if (!path.extname(file)) file = path.join(root, 'index.html')
  if (!fs.existsSync(file) || !fs.statSync(file).isFile()) { response.writeHead(404); response.end(); return }
  response.setHeader('Content-Type', mime[path.extname(file)] || 'application/octet-stream')
  fs.createReadStream(file).pipe(response)
}).listen(38082, '127.0.0.1', () => console.log('Migration visual smoke: http://127.0.0.1:38082 (no database writes)'))
