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

// Chromium can write each response chunk to disk; other browsers use their Blob download fallback.
export async function downloadBinaryFile(params, { signal, fileHandle, onProgress } = {}) {
  let writable;
  try {
    if (fileHandle) writable = await fileHandle.createWritable();
    const response = await fetch(process.env.VUE_APP_BASE_API + '/api/rdb/dml/blob/download', {
      method: 'POST', signal,
      headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + getToken() },
      body: JSON.stringify(params)
    });
    if ((response.headers.get('content-type') || '').includes('application/json')) {
      const error = await response.json();
      throw new Error(error.errorMessage || error.msg || '文件下载失败');
    }
    if (response.status === 204) throw new Error('该字段为 NULL，没有文件可下载');
    if (!response.ok) throw new Error(`下载失败（HTTP ${response.status}）`);
    const reader = response.body.getReader();
    const lengthHeader = response.headers.get('content-length');
    const expectedLength = lengthHeader == null ? null : Number(lengthHeader);
    let loaded = 0;
    const verifyLength = () => {
      if (expectedLength != null && loaded !== expectedLength) throw new Error('文件传输未完成，请重新下载');
    };
    if (writable) {
      for (let chunk = await reader.read(); !chunk.done; chunk = await reader.read()) {
        await writable.write(chunk.value);
        loaded += chunk.value.byteLength;
        if (onProgress) onProgress(loaded);
      }
      verifyLength();
      await writable.close();
      writable = null;
    } else {
      const stream = new ReadableStream({
        async pull(controller) {
          const { done, value } = await reader.read();
          if (done) { controller.close(); return; }
          loaded += value.byteLength;
          if (onProgress) onProgress(loaded);
          controller.enqueue(value);
        },
        cancel() { return reader.cancel(); }
      });
      const blob = await new Response(stream).blob();
      verifyLength();
      const blobUrl = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = blobUrl; anchor.download = params.fileName || 'download.bin';
      document.body.appendChild(anchor); anchor.click(); anchor.remove();
      setTimeout(() => URL.revokeObjectURL(blobUrl), 1000);
    }
    return loaded;
  } catch (error) {
    if (writable) await writable.abort().catch(() => {});
    throw error;
  }
}

export function binaryFileType(base64) {
  const bytes = atob((base64 || '').slice(0, 32));
  if (bytes.startsWith('\x89PNG\r\n\x1a\n')) return '.png';
  if (bytes.startsWith('\xff\xd8\xff')) return '.jpg';
  if (bytes.startsWith('GIF87a') || bytes.startsWith('GIF89a')) return '.gif';
  if (bytes.startsWith('RIFF') && bytes.slice(8, 12) === 'WEBP') return '.webp';
  if (bytes.startsWith('BM')) return '.bmp';
  if (bytes.startsWith('%PDF-')) return '.pdf';
  if (bytes.startsWith('PK\x03\x04')) return '.zip';
  return '.bin';
}
