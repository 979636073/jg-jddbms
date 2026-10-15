// Opt-in browser stress test. Uses synthetic data, not a real database response.
// Open the local app in `agent-browser --session sqlfix` before running this file.
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
const browserScript = `
(async () => {
  const Vue = document.querySelector('#app').__vue__.$options._base;
  ${helper}
  const requests = [];
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
  window.sqlResultQa = {app, table, requests};
  return {fixture:'synthetic 1000 rows x 300 columns', mountMs:Math.round(mountMs),
    maxScrollTwoFramesMs:Math.round(Math.max(...frames)), cells:cellCount(),
    rows:table.rows.length, pageNo:table.pageNo, total:table.total, requests:requests.length, status:'PASS'};
})()
`
const result = spawnSync('agent-browser', ['--session', 'sqlfix', 'eval', '--stdin'],
  { input: browserScript, encoding: 'utf8', shell: process.platform === 'win32', timeout: 60000 })
process.stdout.write(result.stdout || '')
process.stderr.write(result.stderr || '')
if (result.error) throw result.error
process.exitCode = result.status || 0
