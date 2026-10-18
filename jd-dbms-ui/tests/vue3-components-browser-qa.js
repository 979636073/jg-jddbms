// Opt-in native Vue 3 interaction check. Uses an authenticated development page;
// mounts temporary components and removes them without persisting business data.
const fs = require('fs')
const path = require('path')
const { spawnSync } = require('child_process')
const compiler = require('./helpers/vue-compiler')
const root = path.join(__dirname, '../src')
const read = file => fs.readFileSync(path.join(root, file), 'utf8')
const component = (file, name) => {
  const sfc = compiler.parseComponent(read(file))
  const compiled = compiler.compileBrowser(sfc.template.content)
  if (compiled.errors?.length) throw Error(compiled.errors.join('\n'))
  return sfc.script.content.replace(/^import .*;?\s*$/gm, '').replace('export default', `const ${name} =`) +
    `\n${name}.render = new Function('Vue', ${JSON.stringify(compiled.code)})(Vue);`
}
const binary = process.env.AGENT_BROWSER_BIN || 'agent-browser'
const invoke = args => {
  const result = spawnSync(binary, ['--session', process.env.AGENT_BROWSER_SESSION || 'jddbms', ...args], { encoding: 'utf8', timeout: 60000 })
  if (result.error || result.status !== 0) throw Error(result.stderr || result.error || result.stdout)
  return result.stdout
}
const evaluate = code => invoke(['eval', '-b', Buffer.from(code).toString('base64')])
const setup = `(async()=>{
  ${require('./helpers/browser-vue').runtime}
  const {h,resolveComponent} = Vue;
  const draggableModule = requireAppModule(Object.keys(requireAppModule.m).find(id=>id.endsWith('/vuedraggable/dist/vuedraggable.umd.js')));
  const draggable = draggableModule.default || draggableModule;
  ${read('utils/generator/render.js').replace(/^import .*;?\s*$/gm, '').replace('export default', 'const render =')}
  ${component('views/tool/build/DraggableItem.vue', 'DraggableItem')}
  const Editor = await application.component('Editor').__asyncLoader();
  const views = requireAppModule(Object.keys(requireAppModule.m).find(id=>id.includes('src/views lazy')));
  const UserAvatar = (await views('./system/user/profile/userAvatar.vue')).default;
  const host = document.createElement('div');host.id='vue3-component-qa';
  host.style='position:fixed;inset:12px;background:white;z-index:5000;overflow:auto;padding:20px';
  document.body.appendChild(host);
  const checks=[];
  const ensure=(ok,name)=>{if(!ok)throw Error(name);checks.push(name)};
  const app=createTestApp({data:()=>({fields:[1,2,3].map(id=>({tag:'el-input',label:'原生控件'+id,formId:id,span:24,
    layout:'colFormItem',vModel:'field'+id,defaultValue:'值'+id})),content:'<p>初始富文本</p>',copies:0,deletes:0}),
    render(){return h('div',{},[
      h(draggable,{list:this.fields,itemKey:'formId',animation:0,style:'width:600px',ref:'drag'},
        {item:({element,index})=>h(DraggableItem,{element,index,drawingList:this.fields,formConf:{},
          'data-qa':'field-'+element.formId,style:'height:85px;background:#eef3fa;margin-bottom:12px;padding:8px',
          onCopyItem:()=>this.copies++,onDeleteItem:()=>this.deletes++})}),
      h(Editor,{ref:'editor',modelValue:this.content,'onUpdate:modelValue':value=>this.content=value,type:'base64',height:120}),
      h(UserAvatar,{ref:'avatar'})])}
  });
  const screen=app.mount(host);
  window.__vue3ComponentQA={app,host,screen,checks,ensure};
  await Vue.nextTick();
  ensure(host.querySelectorAll('.drawing-item').length===3,'draggable item slot renders all controls');
  const input=host.querySelector('[data-qa="field-1"] input');input.value='Vue3双向绑定';input.dispatchEvent(new Event('input',{bubbles:true}));
  await Vue.nextTick();ensure(screen.fields[0].defaultValue==='Vue3双向绑定','generated input updates Vue 3 model');
  host.querySelector('.drawing-item-copy').click();host.querySelector('.drawing-item-delete').click();
  ensure(screen.copies===1 && screen.deletes===1,'copy/delete listeners fire exactly once');
  const editor=screen.$refs.editor;
  ensure(editor.Quill.getText().includes('初始富文本'),'Quill displays modelValue');
  editor.Quill.setText('原生Vue3富文本编辑');await Vue.nextTick();
  ensure(screen.content.includes('原生Vue3富文本编辑'),'Quill updates parent model');
  screen.content='<p>外部更新</p>';await Vue.nextTick();
  ensure(editor.Quill.getText().includes('外部更新'),'Quill reacts to external model change');
  const avatar=screen.$refs.avatar, canvas=document.createElement('canvas');
  canvas.width=400;canvas.height=300;canvas.getContext('2d').fillRect(0,0,400,300);
  avatar.options.img=canvas.toDataURL('image/png');avatar.editCropper();
  for(let i=0;i<50 && !avatar.previews.url;i++)await new Promise(resolve=>setTimeout(resolve,100));
  ensure(!!avatar.$refs.cropper && !!avatar.previews.url,'Vue 3 cropper opens and produces preview');
  const decoded=new Image();
  for(let i=0;i<50;i++){
    decoded.src=await new Promise(resolve=>avatar.$refs.cropper.getCropData(resolve));
    try{await decoded.decode();if(decoded.naturalWidth>0 && decoded.naturalWidth===decoded.naturalHeight)break;}catch(_){}
    await new Promise(resolve=>setTimeout(resolve,100));
  }
  ensure(avatar.$refs.cropper.cropW===200 && avatar.$refs.cropper.cropH===200 && decoded.naturalWidth>0 &&
    decoded.naturalWidth===decoded.naturalHeight && decoded.src.startsWith('data:image/png;'),
    '200 x 200 crop selection exports a decodable square PNG');
  avatar.open=false;await Vue.nextTick();
  return {stage:'mounted',checks};
})()`
try {
  evaluate(setup)
  invoke(['drag', '#vue3-component-qa [data-qa="field-1"]', '#vue3-component-qa [data-qa="field-3"]'])
  process.stdout.write(evaluate(`(()=>{const qa=window.__vue3ComponentQA;
    qa.ensure(qa.screen.fields.map(f=>f.formId).join(',')!=='1,2,3','pointer drag reorders reactive list');
    return {status:'PASS',checks:qa.checks,order:qa.screen.fields.map(f=>f.formId)};})()`))
} finally {
  evaluate(`(()=>{const qa=window.__vue3ComponentQA;if(qa){qa.app.unmount();qa.host.remove();delete window.__vue3ComponentQA}return 'cleaned'})()`)
}
