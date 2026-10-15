const assert = require('assert')
const fs = require('fs')
const path = require('path')
const vm = require('vm')

const source = fs.readFileSync(path.join(__dirname, '../src/store/modules/permission.js'), 'utf8')
const context = {
  Layout: {}, ParentView: {}, InnerLink: {}, auth: {}, router: {},
  constantRoutes: [], dynamicRoutes: [], getRouters: () => Promise.resolve({ data: [{
    path: '/settings', component: 'Layout', children: [
      { path: 'menu', component: 'system/menu/index' },
      { path: 'config', component: 'system/config/index' },
      { path: 'dmp-tools', component: 'main/dmpTools/index' },
    ],
  }] }),
  process: { env: { NODE_ENV: 'production' } },
}
context.router.addRoutes = () => {}
vm.runInNewContext(source.replace(/^import .*$/gm, '')
  .replace(/export /g, '').replace('default permission', 'this.permission = permission'), context)

async function run() {
  const commits = {}
  const routes = await context.permission.actions.GenerateRoutes({ commit: (key, value) => { commits[key] = value } })
  for (const items of [routes, commits.SET_SIDEBAR_ROUTERS, commits.SET_TOPBAR_ROUTES]) {
    const children = items.find(item => item.path === '/settings').children
    assert.strictEqual(children.find(item => item.path === 'menu').hidden, true)
    assert.strictEqual(children.find(item => item.path === 'config').hidden, true)
    assert.notStrictEqual(children.find(item => item.path === 'dmp-tools').hidden, true,
      '移除两个入口不影响导入导出客户端')
    assert(children.every(item => item.component), '保留路由，不删除已有配置或权限数据')
  }
  console.log('个人中心菜单入口回归测试通过')
}
run().catch(error => { console.error(error); process.exitCode = 1 })
