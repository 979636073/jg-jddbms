// Opt-in HTTP acceptance. Uses an existing large BLOB without modifying database contents.
const fs = require('fs')
const http = require('http')
const assert = require('assert')
async function main() {
  const token = fs.readFileSync(process.env.JDDBMS_QA_CURL, 'utf8').match(/Bearer\s+([A-Za-z0-9_.-]+)/)[1]
  const base = process.env.JDDBMS_QA_HTTP_URL || 'http://127.0.0.1:28080'
  const request = {
    dataSourceId: Number(process.env.JDDBMS_QA_DM_DATASOURCE_ID || 226),
    schemaName: 'TEST-TEST', blobColumn: 'PAYLOAD', readId: require('crypto').randomUUID(),
    sql: 'SELECT ID,PAYLOAD FROM "TEST-TEST".BLOB_LARGE_TEST_20261015 ORDER BY ID',
    resultColumns: ['ID', 'PAYLOAD'], resultValues: ['1024', 'A']
  }
  const headers = { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' }
  const post = async path => {
    const response = await fetch(base + '/api/rdb/dml/blob/' + path, { method: 'POST', headers, body: JSON.stringify(request) })
    assert.strictEqual(response.status, 200)
    return response.json()
  }
  let download, response
  try {
    response = await new Promise((resolve, reject) => {
      download = http.request(base + '/api/rdb/dml/blob/download', { method: 'POST', headers }, resolve)
      download.setTimeout(10000, () => download.destroy(Error('Download did not start')))
      download.on('error', reject)
      download.end(JSON.stringify(request))
    })
    assert.strictEqual(response.statusCode, 200)
    assert.strictEqual(Number(response.headers['content-length']), 16777216)
    // Pausing the actual HTTP consumer keeps the server reading/writing the large BLOB.
    response.pause()
    const cancellation = await post('cancel')
    assert.strictEqual(cancellation.success, true)
    assert.strictEqual(cancellation.data, true, 'The server must accept cancellation of an active read')
    response.destroy()
    const resumed = await post('preview')
    assert.strictEqual(resumed.success, true, resumed.errorMessage)
    assert.strictEqual(resumed.data.byteLength, 16777216)
    assert.strictEqual((await post('cancel')).data, false, 'The read identifier must have been released')
    console.log(JSON.stringify({ status: 'PASS', accepted: true, readIdReleased: true, nextReadSucceeded: true }))
  } finally {
    if (response) response.destroy()
    if (download) download.destroy()
  }
}
main().catch(error => { console.error(error.message); process.exitCode = 1 })
