import { getToken } from '@/utils/auth'

export async function downloadFile(url, params) {
  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': 'Bearer ' + getToken()
    },
    body: JSON.stringify(params)
  })
  if (!response.ok) throw new Error(`下载失败（HTTP ${response.status}）`)
  if ((response.headers.get('content-type') || '').includes('application/json')) {
    throw new Error('下载失败：服务器未返回文件')
  }

  const disposition = response.headers.get('content-disposition') || ''
  const encoded = disposition.match(/filename\*=[^']*'[^']*'([^;]+)/i)
  const plain = disposition.match(/filename="?([^";]+)"?/i)
  let filename = params.fileName && params.prefix ? params.fileName + params.prefix : 'download.bin'
  if (encoded) {
    try {
      filename = decodeURIComponent(encoded[1])
    } catch (_) {
      // Keep the generated name when the response header is malformed.
    }
  } else if (plain) {
    filename = plain[1]
  }

  const blobUrl = URL.createObjectURL(await response.blob())
  const anchor = document.createElement('a')
  anchor.style.display = 'none'
  anchor.href = blobUrl
  anchor.download = filename
  document.body.appendChild(anchor)
  anchor.click()
  document.body.removeChild(anchor)
  setTimeout(() => URL.revokeObjectURL(blobUrl), 1000)
}
