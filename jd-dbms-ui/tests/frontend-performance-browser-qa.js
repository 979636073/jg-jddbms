// Opt-in: open an authenticated Vue 3 workspace with an SQL console first.
// AGENT_BROWSER_BIN may point to the installed CLI; no database writes are made.
const { spawnSync } = require('child_process')
const fs = require('fs')
const session = process.env.AGENT_BROWSER_SESSION || 'jddbms'
const script = `(async () => {
  if (document.hidden) throw Error('Select the workspace tab with agent-browser tab before sampling');
  const locate = (selector, key) => {
    for (let el = document.querySelector(selector); el; el = el.parentElement) {
      for (let c = el.__vueParentComponent; c; c = c.parent) {
        if (c.exposed?.[key]) return c.exposed;
        if (c.proxy?.[key]) return c.proxy;
      }
    }
  };
  const tree = locate('.virtual-resource-tree', 'store');
  const editor = locate('.monaco-editor', 'editor')?.editor;
  if (!tree || !editor) throw Error('Open the Vue 3 workspace and an SQL console before sampling');
  const tick = () => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
  const viewport = document.querySelector('.virtual-resource-tree');
  const store = tree.store;
  const saved = { data: store.data, children: store.root.childNodes, rootData: store.root.data,
    nodesMap: store.nodesMap, currentNode: store.currentNode, scrollTop: viewport.scrollTop };
  const oldSql = editor.getValue(), oldView = editor.saveViewState();
  const frames = [], input = [], longTasks = [];
  const observer = new PerformanceObserver(list => longTasks.push(...list.getEntries().map(e => Math.round(e.duration))));
  observer.observe({ type: 'longtask' });
  let treeMountMs, renderedNodes, lastNodeVisible;
  try {
    const start = performance.now();
    store.setData(Array.from({length: 10000}, (_, i) => ({key:'perf-'+i, label:'PERF_NODE_'+i, isLeaf:true})));
    await tick();
    treeMountMs = performance.now() - start;
    renderedNodes = viewport.querySelectorAll('[role=treeitem]').length;
    for (let i = 0; i < 10; i++) {
      const start = performance.now();
      viewport.scrollTop = (viewport.scrollHeight - viewport.clientHeight) * i / 9;
      await tick(); frames.push(performance.now() - start);
    }
    lastNodeVisible = viewport.textContent.includes('PERF_NODE_9999');
    const sql = Array.from({length:1000}, (_, i) => 'SELECT '+i+' AS PERF_VALUE FROM DUAL;').join('\\n');
    editor.setValue(sql);
    await tick();
    for (let i = 0; i < 20; i++) {
      const start = performance.now();
      editor.executeEdits('performance-qa', [{range:{startLineNumber:1,endLineNumber:1,startColumn:1,endColumn:1},text:' '}]);
      await tick(); input.push(performance.now() - start);
    }
  } finally {
    store.data = saved.data; store.root.data = saved.rootData; store.root.childNodes = saved.children;
    store.nodesMap = saved.nodesMap; store.currentNode = saved.currentNode;
    viewport.scrollTop = saved.scrollTop;
    editor.setValue(oldSql); editor.restoreViewState(oldView);
    await tick(); observer.disconnect();
  }
  if (renderedNodes > 100 || !lastNodeVisible) throw Error('Virtual tree failed the 10000-node check');
  const summary = values => ({medianMs:Math.round([...values].sort((a,b)=>a-b)[Math.floor(values.length/2)]), maxMs:Math.round(Math.max(...values))});
  const nav = performance.getEntriesByType('navigation')[0];
  return {status:'PASS', version:document.querySelector('#app').__vue_app__.version,
    fixture:{treeNodes:10000,sqlLines:1000},tree:{mountMs:Math.round(treeMountMs),renderedNodes,lastNodeVisible,scrollTwoFrames:summary(frames)},
    editor:{editToTwoFrames:summary(input)},longTasksMs:longTasks,
    page:{domContentLoadedMs:Math.round(nav.domContentLoadedEventEnd),heapMiB:Math.round((performance.memory?.usedJSHeapSize || 0)/1048576)},
    note:'Development build, warm authenticated page, two animation frames per sample; includes browser scheduling, not pure input latency. No cold-load comparison.'};
})()`
const result = spawnSync(process.env.AGENT_BROWSER_BIN || 'agent-browser', ['--session', session, 'eval', '-b', Buffer.from(script).toString('base64')],
  { encoding: 'utf8', shell: !process.env.AGENT_BROWSER_BIN && process.platform === 'win32', timeout: 60000 })
if (process.argv[2] && result.status === 0) fs.writeFileSync(process.argv[2], result.stdout)
process.stdout.write(result.stdout || '')
process.stderr.write(result.stderr || '')
if (result.error) throw result.error
if (result.status !== 0 || !/"status"\s*:\s*"PASS"/.test(result.stdout)) process.exitCode = result.status || 1
