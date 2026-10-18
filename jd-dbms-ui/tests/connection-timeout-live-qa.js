// Opt-in acceptance against a TCP listener which accepts connections but never completes login.
const fs = require('fs')
const net = require('net')
const assert = require('assert')
async function main() {
  const token = fs.readFileSync(process.env.JDDBMS_QA_CURL, 'utf8').match(/Bearer\s+([A-Za-z0-9_.-]+)/)[1]
  const base = process.env.JDDBMS_QA_HTTP_URL || 'http://127.0.0.1:28080'
  const sockets = new Set()
  const server = net.createServer(socket => { sockets.add(socket); socket.on('close', () => sockets.delete(socket)) })
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  const port = server.address().port
  const results = []
  try {
    for (const type of ['ORACLE', 'DM']) {
      const started = performance.now()
      const response = await fetch(base + '/api/connection/datasource/pre_connect', {
        method: 'POST', signal: AbortSignal.timeout(23000),
        headers: { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' },
        body: JSON.stringify({ type, url: type === 'DM' ? 'jdbc:dm://127.0.0.1:' + port : 'jdbc:oracle:thin:@127.0.0.1:' + port + '/QA',
          host: '127.0.0.1', port: String(port), user: 'QA_TIMEOUT', password: 'QA_TIMEOUT', ssh: { use: false } })
      })
      const result = await response.json(), ms = Math.round(performance.now() - started)
      assert.strictEqual(response.status, 200)
      assert.strictEqual(result.success, false)
      assert(ms >= 12000 && ms <= 20000, type + ' exceeded 15-second login timeout: ' + ms + 'ms')
      results.push({ type, ms, success: false })
    }
    console.log(JSON.stringify({ status: 'PASS', scenario: 'TCP accepted, login silent', results }))
  } finally {
    for (const socket of sockets) socket.destroy()
    server.close()
  }
}
main().catch(error => { console.error(error.message); process.exitCode = 1 })
