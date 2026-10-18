// Read-only smoke of registered management routes in an authenticated native Vue 3 build.
const { spawnSync } = require('child_process')
const script = `(async()=>{
  const root=document.querySelector('#app').__vue_app__.config.globalProperties;
  const router=root.$router, saved=router.currentRoute.value.fullPath, errors=[];
  const onError=e=>errors.push(e.message || e.reason?.message || String(e.reason));
  window.addEventListener('error',onError);window.addEventListener('unhandledrejection',onError);
  const report={status:'PASS',pages:[]};
  try {
    const routes=['/access/user','/access/role','/user/profile','/operations/log/operlog','/operations/log/logininfor',
      '/logManagement','/operations/database-monitor','/settings/dmp-tools'];
    for(const path of routes){
      if(!router.getRoutes().some(r=>r.path===path))throw Error('Route unavailable: '+path);
      await router.push(path);
      const expected=router.currentRoute.value.matched.at(-1).components.default;
      let main;
      for(let i=0;i<50;i++){
        await new Promise(resolve=>setTimeout(resolve,100));
        for(const el of document.querySelectorAll('#app *')){
          for(let c=el.__vueParentComponent;c;c=c.parent){
            if(c.type===expected && c.vnode.el?.nodeType===1){main=c.vnode.el;break;}
          }
          if(main)break;
        }
        if(main?.isConnected && main.innerText.trim() && !main.querySelector('.el-loading-mask'))break;
      }
      const overlay=[...document.querySelectorAll('iframe')].map(f=>f.contentDocument?.body.innerText).find(t=>t?.includes('Uncaught runtime errors'));
      if(errors.length || overlay)throw Error(path+': '+(errors[0] || overlay));
      if(router.currentRoute.value.path!==path || !main?.isConnected || !main.innerText.trim())throw Error('Blank or redirected page: '+path);
      report.pages.push({path,rows:main.querySelectorAll('.el-table__row').length,controls:main.querySelectorAll('input,button').length,
        excerpt:main.innerText.trim().slice(0,160)});
    }
    return report;
  } finally {
    window.removeEventListener('error',onError);window.removeEventListener('unhandledrejection',onError);
    await router.push(saved);
  }
})()`
const result=spawnSync(process.env.AGENT_BROWSER_BIN || 'agent-browser', ['--session',process.env.AGENT_BROWSER_SESSION || 'jddbms','eval','-b',Buffer.from(script).toString('base64')],
  {encoding:'utf8',timeout:60000,maxBuffer:1024*1024})
process.stdout.write(result.stdout || '');process.stderr.write(result.stderr || '')
if(result.error)throw result.error
if(result.status!==0 || !/"status"\s*:\s*"PASS"/.test(result.stdout))process.exitCode=result.status || 1
