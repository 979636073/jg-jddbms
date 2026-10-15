// Opt-in browser regression using real Vue components and synthetic API responses.
// Open the local app in `agent-browser --session sqlfix` before running npm run test:browser.
const fs = require('fs')
const path = require('path')
const { spawnSync } = require('child_process')
const compiler = require('vue-template-compiler')
const sass = require('sass')
const source = compiler.parseComponent(fs.readFileSync(path.join(__dirname,
  '../src/views/main/workspace/components/reanderSqlExecute/SqlResultTable.vue'), 'utf8'))
const template = compiler.compile(source.template.content)
if (template.errors.length) throw new Error(template.errors.join('\n'))
const script = source.script.content.replace(/^import .*;?\s*$/gm, '').replace('export default', 'const component =')
const helper = fs.readFileSync(path.join(__dirname, '../src/utils/sqlResult.js'), 'utf8').replace(/export /g, '')
const css = sass.renderSync({ data: source.styles[0].content }).css.toString()
const fileSource = compiler.parseComponent(fs.readFileSync(path.join(__dirname,
  '../src/views/main/workspace/components/renderSearchResult/filedialog.vue'), 'utf8'))
const fileTemplate = compiler.compile(fileSource.template.content)
if (fileTemplate.errors.length) throw new Error(fileTemplate.errors.join('\n'))
const fileScript = fileSource.script.content.replace(/^import .*;?\s*$/gm, '').replace('export default', 'const FileDialog =')
const fileHelpers = fs.readFileSync(path.join(__dirname, '../src/utils/file.js'), 'utf8').replace(/^import .*;?\s*$/gm, '').replace(/export /g, '')
const browserScript = `
(async () => {
  const Vue = document.querySelector('#app').__vue__.$options._base;
  ${helper}
  const requests = [];
  const binaryRequests = [];
  const png = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aX1cAAAAASUVORK5CYII=';
  const tableServer = {
    previewBlob: async params => { binaryRequests.push(params); return {success:true, data:{base64:params.resultValues[0] === '3' ? '' : png}}; },
    cancelBlob: async () => ({success:true})
  };
  ${fileHelpers}
  ${fileScript}
  FileDialog.render = new Function(${JSON.stringify(fileTemplate.render)});
  FileDialog.staticRenderFns = ${JSON.stringify(fileTemplate.staticRenderFns)}.map(body => new Function(body));
  let copied = '';
  const copyText = text => { copied = text; };
  const fixture = (pageNo, pageSize, descending = false) => prepareSqlResult({
    success: true, description: '执行成功', duration: 12, pageNo, pageSize,
    totalExact: true, fuzzyTotal: '10001', hasNextPage: pageNo * pageSize < 10001,
    params: { sql: 'SELECT ID, C1, ... C299 FROM QA', queryTemplate: true, sessionId: 'qa-only' },
    headerList: Array.from({length: 300}, (_, i) => ({name: i === 0 ? 'ID' : 'C' + i})),
    dataList: Array.from({length: Math.min(pageSize, 10001 - (pageNo - 1) * pageSize)}, (_, r) =>
      Array.from({length: 300}, (_, c) => c === 0 ? String(descending ? 10001 - (pageNo - 1) * pageSize - r : (pageNo - 1) * pageSize + r + 1) : 'row' + r + '-col' + c))
  });
  const sqlServer = { executeSql: async params => {
    requests.push(params);
    return {success: true, data: [fixture(params.pageNo, params.pageSize, params.isAsc === false)]};
  }};
  ${script}
  component.render = new Function(${JSON.stringify(template.render)});
  component.staticRenderFns = ${JSON.stringify(template.staticRenderFns)}.map(body => new Function(body));
  const style = document.createElement('style'); style.textContent = ${JSON.stringify(css)};
  document.head.appendChild(style);
  const host = document.createElement('div'); document.body.appendChild(host);
  const started = performance.now();
  const app = new Vue({data: () => ({result: fixture(1, 1000)}), render(h) {
    return h('div', {style: {position:'fixed',inset:'0',zIndex:9999,background:'white',display:'flex',padding:'20px'}},
      [h(component, {props:{queryResultData:this.result}, on:{setQueryResultData: result => {this.result = result;}}})]);
  }}).$mount(host);
  try {
  const tick = () => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
  await tick();
  const table = app.$children[0];
  const viewport = table.$refs.viewport;
  const ensure = (value, message) => { if (!value) throw new Error(message); };
  const cellCount = () => table.$el.querySelectorAll('[role=gridcell]').length;
  ensure(table.rows.length === 1000 && table.columns.length === 300, 'stress fixture incomplete');
  ensure(!table.rows.__ob__ && cellCount() < 600, 'full matrix rendered/observed');
  const mountMs = performance.now() - started;
  const frames = [];
  for (let i = 0; i < 30; i++) {
    const before = performance.now();
    viewport.scrollTop = (viewport.scrollHeight - viewport.clientHeight) * i / 29;
    viewport.scrollLeft = (viewport.scrollWidth - viewport.clientWidth) * i / 29;
    await tick(); frames.push(performance.now() - before);
    ensure(cellCount() < 600, 'cell count grew while scrolling');
  }
  ensure(table.visibleRows.some(entry => entry.index === 999), 'last row missing');
  ensure(table.visibleColumns.some(column => column.index === 299), 'last column missing');
  table.showValue(999,299); await tick();
  ensure(table.valueText === 'row999-col299', 'full value read failed');
  table.copyCell(); ensure(copied === 'row999-col299', 'copy failed');
  table.valueVisible = false;
  const last = table.$el.querySelector('.el-pager li.number:last-child'); last.click(); await tick();
  ensure(table.pageNo === 11 && table.rows.length === 1 && table.rows[0][0] === '10001', 'last-page control failed');
  await table.changeSize(200); await tick();
  ensure(table.pageNo === 1 && table.rows.length === 200, 'page-size reset failed');
  table.$el.querySelector('.btn-next').click(); await tick();
  ensure(table.pageNo === 2 && table.rows[0][0] === '201', 'next-page control failed');
  await table.changeSort(table.columns[0], false); await tick();
  ensure(table.pageNo === 1 && table.rows[0][0] === '10001', 'sorting reset failed');
  ensure(requests.every(p => p.skipCount === false && p.queryTemplate && p.sessionId === 'qa-only'), 'pagination context lost');
  app.result = prepareSqlResult({success:true, pageNo:1, pageSize:200, totalExact:true, fuzzyTotal:'3',
    originalSql:'SELECT ID, NOTE, PAYLOAD FROM QA ORDER BY ID',
    params:{sql:'SELECT ID, NOTE, PAYLOAD FROM QA ORDER BY ID', dataSourceId:42, schemaName:'QA'},
    headerList:[{name:'行号',dataType:'CHAT2DB_ROW_NUMBER'},{name:'ID'}, {name:'NOTE'},{name:'PAYLOAD',dataType:'BLOB'}],
    dataList:[['1','1','这是一段用于验证自动列宽的长内容','A'],['2','2','NULL',null],['3','3','空文件','A']]});
  await tick();
  ensure(table.columns[2].width > table.columns[1].width, 'content-based width failed');
  const originalWidth = table.columns[2].width;
  const handle = table.$el.querySelectorAll('.column-resize-handle')[1];
  handle.dispatchEvent(new PointerEvent('pointerdown', {bubbles:true,button:0,clientX:100}));
  window.dispatchEvent(new PointerEvent('pointermove', {clientX:240}));
  window.dispatchEvent(new PointerEvent('pointerup'));
  await tick();
  ensure(table.columns[2].width === originalWidth + 140, 'pointer resize failed');
  ensure(table.columns[3].left === table.columns[2].left + table.columns[2].width, 'resized columns overlap');
  handle.dispatchEvent(new MouseEvent('dblclick', {bubbles:true})); await tick();
  ensure(table.columns[2].width === originalWidth, 'double-click autofit failed');
  ensure(binaryRequests.length === 0, 'rendering eagerly fetched BLOB');
  const binaryButton = table.$el.querySelector('.result-row .result-cell button');
  ensure(binaryButton, 'BLOB view button missing');
  binaryButton.click(); await tick();
  const file = table.$refs.fileRef;
  ensure(binaryRequests.length === 1 && binaryRequests[0].resultColumns[0] === 'ID' && binaryRequests[0].resultValues[0] === '1' && binaryRequests[0].rowIndex === undefined,
    'BLOB click locator failed: ' + JSON.stringify(binaryRequests));
  ensure(file.dialogVisible && file.isShow && file.imageSrc === 'data:image/png;base64,' + png, 'image preview failed');
  file.handleClose();
  await table.showBinary(1,3);
  ensure(binaryRequests.length === 1, 'NULL BLOB was fetched');
  await table.showBinary(2,3); await tick();
  ensure(binaryRequests.length === 2 && file.dialogVisible && file.blobStr === '' && !file.isShow, 'empty BLOB treated as missing');
  return {fixture:'synthetic 1000 rows x 300 columns', mountMs:Math.round(mountMs),
    maxScrollTwoFramesMs:Math.round(Math.max(...frames)), cells:cellCount(),
    requests:requests.length, binaryRequests:binaryRequests.length,
    checks:['virtual-scroll','pagination','sorting','copy','auto-width','pointer-resize','double-click-fit','lazy-BLOB','PNG-preview','NULL','empty-BLOB'], status:'PASS'};
  } finally {
    app.$destroy(); app.$el.remove(); style.remove();
  }
})()
`
const result = spawnSync('agent-browser', ['--session', 'sqlfix', 'eval', '--stdin'],
  { input: browserScript, encoding: 'utf8', shell: process.platform === 'win32', timeout: 60000 })
process.stdout.write(result.stdout || '')
process.stderr.write(result.stderr || '')
if (result.error) throw result.error
if (result.status !== 0 || !/"status"\s*:\s*"PASS"/.test(result.stdout || '')) process.exitCode = result.status || 1
