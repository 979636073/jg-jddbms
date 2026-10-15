// Local release preview: static production UI and streaming API proxy, using Node built-ins only.
import http from 'node:http'
import fs from 'node:fs'
import path from 'node:path'

const root = fs.realpathSync(process.argv[2])
const mime = { '.html': 'text/html; charset=utf-8', '.js': 'application/javascript', '.css': 'text/css',
  '.json': 'application/json', '.svg': 'image/svg+xml', '.png': 'image/png', '.jpg': 'image/jpeg',
  '.ico': 'image/x-icon', '.woff': 'font/woff', '.woff2': 'font/woff2', '.ttf': 'font/ttf', '.wasm': 'application/wasm' }
const server = http.createServer((req, res) => {
  if (req.url === '/dev-api' || req.url.startsWith('/dev-api/')) {
    const upstream = http.request({ hostname: '127.0.0.1', port: 28080, path: req.url.slice(8) || '/',
      method: req.method, headers: { ...req.headers, host: '127.0.0.1:28080' } }, response => {
      res.writeHead(response.statusCode, response.headers)
      response.on('error', () => res.destroy())
      res.on('close', () => { if (!res.writableEnded) response.destroy() })
      response.pipe(res)
    })
    upstream.on('error', () => {
      if (!res.headersSent) res.writeHead(502, { 'Content-Type': 'text/plain; charset=utf-8' })
      res.end('本地后端暂不可用')
    })
    req.on('aborted', () => upstream.destroy())
    res.on('close', () => { if (!res.writableEnded) upstream.destroy() })
    req.pipe(upstream)
    return
  }
  if (!['GET', 'HEAD'].includes(req.method)) { res.writeHead(405).end(); return }
  let pathname
  try { pathname = decodeURIComponent(new URL(req.url, 'http://localhost').pathname) }
  catch { res.writeHead(400).end(); return }
  let file = path.resolve(root, '.' + pathname)
  const relative = path.relative(root, file)
  if (relative.startsWith('..') || path.isAbsolute(relative)) { res.writeHead(403).end(); return }
  if (!fs.existsSync(file) || fs.statSync(file).isDirectory()) {
    if (path.extname(pathname)) { res.writeHead(404).end(); return }
    file = path.join(root, 'index.html')
  }
  res.writeHead(200, { 'Content-Type': mime[path.extname(file)] || 'application/octet-stream',
    'Content-Length': fs.statSync(file).size, 'Cache-Control': 'no-cache' })
  if (req.method === 'HEAD') { res.end(); return }
  const stream = fs.createReadStream(file)
  stream.on('error', () => res.destroy())
  res.on('close', () => stream.destroy())
  stream.pipe(res)
})
server.listen(8080, '127.0.0.1', () => console.log('Release UI: http://localhost:8080'))
