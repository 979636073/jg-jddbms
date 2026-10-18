// Opt-in DM acceptance using the existing administrator browser session. No credentials are printed.
const fs = require('fs')
const path = require('path')
const { spawnSync } = require('child_process')
const compiler = require('./helpers/vue-compiler')
function component(file, name) {
  const source = compiler.parseComponent(fs.readFileSync(path.join(__dirname, '../src', file), 'utf8'))
  const template = compiler.compileBrowser(source.template.content)
  if (template.errors?.length) throw Error(template.errors.join('\n'))
  return source.script.content.replace(/^import .*;?\s*$/gm, '').replace('export default', 'const ' + name + ' =') +
    `\n${name}.render = new Function('Vue', ${JSON.stringify(template.code)})(Vue);`
}
const helper = fs.readFileSync(path.join(__dirname, '../src/utils/file.js'), 'utf8').replace(/^import .*;?\s*$/gm, '').replace(/export /g, '')
const script = `(async()=>{
  ${require('./helpers/browser-vue').runtime}
  const token = application.config.globalProperties.$store.getters.token;
  const getToken = () => token, process = {env:{VUE_APP_BASE_API:'/dev-api'}};
  const report = {status:'PASS', checks:[], timings:[], downloads:[]};
  const ensure = (ok, name) => { if (!ok) throw Error(name); report.checks.push(name); };
  const api = async (url, body) => {
    const started = performance.now();
    const r = await fetch('/dev-api' + url,{method:'POST',headers:{Authorization:'Bearer '+token,'Content-Type':'application/json'},body:JSON.stringify(body)});
    const text = await r.text(); report.timings.push({endpoint:url.split('/').pop(),ms:Math.round(performance.now()-started),bytes:new TextEncoder().encode(text).length});
    return JSON.parse(text);
  };
  const sql = async text => {
    const r = await api('/api/rdb/dml/execute',{dataSourceId:226,consoleId:1,sql:text,pageNo:1,pageSize:200,confirmDangerousSql:true});
    if (!r.success || !r.data?.every(x=>x.success)) throw Error(r.errorMessage || r.data?.[0]?.message || 'SQL failed'); return r.data[0];
  };
  const locator = id => ({dataSourceId:226,schemaName:'TEST-TEST',blobColumn:'PAYLOAD',readId:crypto.randomUUID(),
    sql:'SELECT ID,PAYLOAD FROM "TEST-TEST".BLOB_LARGE_TEST_20261015 ORDER BY ID',resultColumns:['ID','PAYLOAD'],resultValues:[String(id),'A']});
  const preview = request => api('/api/rdb/dml/blob/preview',request);
  const digest = async bytes => Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',bytes))).map(x=>x.toString(16).padStart(2,'0')).join('');
  const download = async request => {
    const start=performance.now(); const response=await fetch('/dev-api/api/rdb/dml/blob/download',{method:'POST',headers:{Authorization:'Bearer '+token,'Content-Type':'application/json'},body:JSON.stringify(request)});
    if (!response.ok || (response.headers.get('content-type')||'').includes('json')) throw Error('Binary download failed');
    const bytes = await response.arrayBuffer(); report.downloads.push({bytes:bytes.byteLength,contentLength:response.headers.get('content-length'),ms:Math.round(performance.now()-start),sha256:await digest(bytes)}); return bytes;
  };
  const tableServer = {previewBlob:(request,signal)=>fetch('/dev-api/api/rdb/dml/blob/preview',{method:'POST',signal,headers:{Authorization:'Bearer '+token,'Content-Type':'application/json'},body:JSON.stringify(request)}).then(r=>r.json()),
    cancelBlob:request=>api('/api/rdb/dml/blob/cancel',request)};
  ${helper}
  ${component('views/main/workspace/components/renderSearchResult/filedialog.vue', 'FileDialog')}
  const table = window.__phase3TempTable || ('QA_BLOB_'+Date.now().toString(36).toUpperCase());
  let app;
  try {
    if (!window.__phase3TempTable) await sql('CREATE TABLE "TEST-TEST"."'+table+'" (ID INT PRIMARY KEY,SORTKEY INT,PAYLOAD BLOB)');
    await sql('INSERT INTO "TEST-TEST"."'+table+'" SELECT 1,2,PAYLOAD FROM "TEST-TEST".BLOB_LARGE_TEST_20261015 WHERE ID=1027');
    const result = await sql('SELECT ID,SORTKEY,PAYLOAD FROM "TEST-TEST"."'+table+'" ORDER BY SORTKEY');
    ensure(result.dataList.length===1 && result.dataList[0][3]==='A','query returns BLOB marker only');
    const original={...locator(1),sql:result.originalSql,resultColumns:result.headerList.slice(1).map(x=>x.name),resultValues:result.dataList[0].slice(1)};
    await sql('INSERT INTO "TEST-TEST"."'+table+'" SELECT 2,1,PAYLOAD FROM "TEST-TEST".BLOB_LARGE_TEST_20261015 WHERE ID=1028');
    await sql('UPDATE "TEST-TEST"."'+table+'" SET SORTKEY=3 WHERE ID=1');
    const stable=await preview(original), png=await preview(locator(1027));
    ensure(stable.success && stable.data.base64===png.data.base64,'reordered rows retain original PK target');
    await sql('DELETE FROM "TEST-TEST"."'+table+'" WHERE ID=1');
    const missing=await preview({...original,readId:crypto.randomUUID()});
    ensure(!missing.success && missing.errorMessage.includes('已不存在'),'deleted row fails explicitly');
    const ambiguous=await preview({...locator(1027),sql:'SELECT PAYLOAD FROM "TEST-TEST".BLOB_LARGE_TEST_20261015',resultColumns:['PAYLOAD'],resultValues:['A']});
    ensure(!ambiguous.success && ambiguous.errorMessage.includes('主键'),'missing key fails explicitly');
    const jpg=await preview(locator(1028)), large=await preview(locator(1029)), nullBlob=await preview(locator(1025)), empty=await preview(locator(1026));
    ensure(binaryFileType(png.data.base64)==='.png' && binaryFileType(jpg.data.base64)==='.jpg','real PNG and JPEG signatures');
    ensure(large.data.tooLarge && large.data.base64===null && large.data.byteLength===11879648,'large image only returns header');
    ensure(nullBlob.data.nullValue && empty.data.base64==='' && !empty.data.nullValue,'NULL and zero bytes distinguished');
    ensure(await digest(await download(locator(1027)))===await digest(Uint8Array.from(atob(png.data.base64),c=>c.charCodeAt(0))),'PNG download equals preview');
    ensure((await download(locator(1029))).byteLength===11879648,'large image download complete');
    ensure((await download(locator(1024))).byteLength===16777216,'16 MiB file download complete');
    ensure((await download(locator(1026))).byteLength===0,'empty file downloads zero bytes');
    const rows=await sql('SELECT ROWID,ID,PAYLOAD FROM "TEST-TEST".BLOB_LARGE_TEST_20261015 WHERE ID=1027');
    const rowid=await preview({...locator(1027),tableName:'BLOB_LARGE_TEST_20261015',rowId:rows.dataList[0][1]});
    ensure(rowid.success && rowid.data.base64===png.data.base64,'DM numeric ROWID lookup');
    const host=document.createElement('div');document.body.appendChild(host);
    app=createTestApp({render:()=>Vue.h(FileDialog,{ref:'dialog'})});const screen=app.mount(host);const dialog=screen.$refs.dialog;
    const decodedImage=async(width,height)=>{
      for(let attempt=0;attempt<40;attempt++){
        const image=document.querySelector('.el-dialog .el-image__inner');
        if(image?.complete && image.naturalWidth===width && image.naturalHeight===height)return true;
        await new Promise(resolve=>setTimeout(resolve,50));
      }
      return false;
    };
    await dialog.open(locator(1027),'real_png');await Vue.nextTick();
    ensure(dialog.isShow && dialog.fromData.type==='.png','real PNG dialog preview');
    ensure(await decodedImage(640,360),'PNG decodes to 640 x 360');
    await dialog.open(locator(1028),'real_jpeg');await Vue.nextTick();
    ensure(dialog.isShow && dialog.fromData.type==='.jpg','real JPEG dialog preview');
    ensure(await decodedImage(1600,900),'JPEG decodes to 1600 x 900');
    await dialog.open(locator(1029),'large_image');await Vue.nextTick();
    ensure(dialog.tooLarge && !dialog.isShow && document.querySelector('.el-dialog').textContent.includes('8 MiB'),'large image dialog limit message');
    const opening=dialog.open(locator(1024),'cancel_preview');const signal=dialog._readController.signal;dialog.handleClose();await opening;
    ensure(signal.aborted && !dialog.dialogVisible && !dialog.error,'preview close aborts request');
    const cancelRequest=locator(1024),controller=new AbortController();
    const response=await fetch('/dev-api/api/rdb/dml/blob/download',{method:'POST',signal:controller.signal,headers:{Authorization:'Bearer '+token,'Content-Type':'application/json'},body:JSON.stringify(cancelRequest)});
    const reader=response.body.getReader();await reader.read();controller.abort();
    const cancellation=await api('/api/rdb/dml/blob/cancel',cancelRequest);
    let resumed;
    for(let attempt=0;attempt<20;attempt++){
      resumed=await preview({...locator(1024),readId:cancelRequest.readId});
      if(resumed.success)break;
      await new Promise(resolve=>setTimeout(resolve,100));
    }
    report.cancellation={accepted:cancellation.data,cleanupReadSucceeded:resumed.success};
    ensure(resumed.success,'cancel releases read ID and next read succeeds: '+resumed.errorMessage);
    return report;
  } finally {
    if(app){const host=app._container;app.unmount();host.remove()}
    await sql('DROP TABLE "TEST-TEST"."'+table+'"');delete window.__phase3TempTable;
  }
})()`
const binary = process.env.AGENT_BROWSER_BIN || (process.platform === 'win32' ? path.join(path.dirname(process.execPath), 'node_modules/agent-browser/bin/agent-browser-win32-x64.exe') : 'agent-browser')
const result = spawnSync(binary, ['--session','jddbms','eval','--stdin'], {input:script,encoding:'utf8',timeout:180000,maxBuffer:1024*1024})
process.stdout.write(result.stdout || '')
process.stderr.write(result.stderr || '')
if (result.error) throw result.error
if (result.status !== 0 || !/"status"\s*:\s*"PASS"/.test(result.stdout || '')) process.exitCode = result.status || 1
