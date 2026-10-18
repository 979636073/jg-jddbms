// Opt-in: run against an authenticated administrator session on the local Vue 3 build.
// AGENT_BROWSER_BIN=<CLI path> AGENT_BROWSER_SESSION=<session> node tests/management-layout-browser-qa.js
// Set the browser viewport before each run; this script never changes business data.
const fs = require('fs')
const path = require('path')
const { execFileSync } = require('child_process')
const bin = process.env.AGENT_BROWSER_BIN || 'agent-browser'
const session = process.env.AGENT_BROWSER_SESSION || 'vue3-style-final'
const output = path.resolve(__dirname, '../../.local/vue3/management-layout')
fs.mkdirSync(output, { recursive: true })
const browser = (...args) => execFileSync(bin, ['--session', session, ...args], {
  encoding: 'utf8', timeout: 60000, maxBuffer: 4 * 1024 * 1024
}).trim()
const evaluate = code => JSON.parse(browser('eval', '-b', Buffer.from(code).toString('base64')))
browser('wait', '.app-main')
const routes = [
  ['/user/profile', '个人中心'], ['/access/user', '平台用户'], ['/access/role', '平台角色'],
  ['/operations/online', '在线用户'], ['/operations/database-monitor', '数据库运行监控'],
  ['/operations/log/operlog', '操作日志'], ['/operations/log/logininfor', '登录日志'],
  ['/settings/dmp-tools', '导入导出客户端'], ['/task-center/index', '任务中心']
]
const pages = []
for (const [route, title] of routes) {
  const result = evaluate(`(async () => {
    const app = document.querySelector('#app').__vue_app__;
    const globals = app.config.globalProperties;
    const errors = [];
    const original = app.config.errorHandler;
    app.config.errorHandler = error => { errors.push(String(error)); if (original) original(error); };
    const frame = () => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
    try {
      await globals.$router.push(${JSON.stringify(route)});
      await frame();
      const visible = element => element.getClientRects().length && getComputedStyle(element).visibility !== 'hidden';
      for (let i = 0; i < 100 && (document.querySelector('.fade-transform-leave-active, .fade-transform-enter-active') || !document.querySelector('.app-main')?.innerText.trim()); i++) await new Promise(resolve => setTimeout(resolve, 50));
      for (let i = 0; i < 100; i++) {
        if (!Array.from(document.querySelectorAll('.el-loading-mask')).some(visible)) break;
        await new Promise(resolve => setTimeout(resolve, 100));
      }
      await frame();
      const checks = [];
      const check = (pass, name) => checks.push({ name, pass: Boolean(pass) });
      check(globals.$router.currentRoute.value.path === ${JSON.stringify(route)}, 'route');
      const sidebar = document.querySelector('.sidebar-container');
      check(sidebar && sidebar.querySelectorAll('.el-sub-menu').length >= 3, 'navigation rendered');
      check(!document.querySelector('el-submenu, el-sub-menu'), 'no unresolved menu elements');
      check(globals.$router.currentRoute.value.meta.title === ${JSON.stringify(title)} &&
        document.querySelector('.app-main')?.innerText.trim().length > 0, 'page content');
      check(document.documentElement.scrollWidth <= innerWidth, 'no document horizontal overflow');
      const main = document.querySelector('.app-main');
      check(main.scrollWidth <= main.clientWidth + 1, 'no clipped page content');
      check(document.documentElement.scrollHeight <= innerHeight + 1, 'no phantom footer');
      check(!errors.length, 'no Vue render errors');
      const formControls = Array.from(document.querySelectorAll('.app-main .el-form .el-input, .app-main .el-form .el-select__wrapper')).filter(visible);
      check(formControls.every(element => element.getBoundingClientRect().height >= 31 && element.getBoundingClientRect().height <= 33 && parseFloat(getComputedStyle(element).fontSize) >= 14), 'form controls have balanced size');
      check(!Array.from(document.querySelectorAll('.el-loading-mask')).some(visible), 'loading completed');
      if (innerWidth >= 992) {
        if (!globals.$store.state.app.sidebar.opened) document.querySelector('#hamburger-container').click();
        await new Promise(resolve => setTimeout(resolve, 350));
        check(Math.abs(sidebar.getBoundingClientRect().width - 216) <= 1, 'expanded width');
        check(Math.abs(document.querySelector('.main-container').getBoundingClientRect().left - 216) <= 1, 'expanded content offset');
        document.querySelector('#hamburger-container').click();
        await new Promise(resolve => setTimeout(resolve, 350));
        check(Math.abs(sidebar.getBoundingClientRect().width - 54) <= 1, 'collapsed width');
        check(Math.abs(document.querySelector('.main-container').getBoundingClientRect().left - 54) <= 1, 'collapsed content offset');
        document.querySelector('#hamburger-container').click();
      } else {
        document.querySelector('#hamburger-container').click();
        await new Promise(resolve => setTimeout(resolve, 350));
        check(sidebar.getBoundingClientRect().left >= 0 && document.querySelector('.drawer-bg'), 'small-screen navigation opens');
        document.querySelector('.drawer-bg').click();
      }
      await new Promise(resolve => setTimeout(resolve, 350));
      if (${JSON.stringify(route)} === '/user/profile') {
        const cards = Array.from(document.querySelectorAll('.profile-page__card'));
        check(cards.length === 2 && cards[1].getBoundingClientRect().height < 400, 'password card fits form');
        check(document.querySelector('.password-form').getBoundingClientRect().width <= 520, 'password form readable width');
        document.querySelector('.password-form button').click();
        for (let i = 0; i < 40 && document.querySelectorAll('.password-form .el-form-item__error').length < 3; i++) await new Promise(resolve => setTimeout(resolve, 50));
        check(document.querySelectorAll('.password-form .el-form-item__error').length === 3, 'password validation visible');
      }
      if (['/access/user', '/access/role'].includes(${JSON.stringify(route)})) {
        const cells = Array.from(document.querySelectorAll('td.fixed-width .cell'));
        check(cells.every(cell => {
          const buttons = Array.from(cell.querySelectorAll('button')).filter(visible);
          return buttons.length < 2 || buttons.every(button => Math.abs(button.getBoundingClientRect().top - buttons[0].getBoundingClientRect().top) < 2);
        }), 'row actions stay aligned');
      }
      if (${JSON.stringify(route)} === '/task-center/index') {
        check(document.querySelector('.el-select').getBoundingClientRect().width >= 150, 'status selector readable width');
        check(Array.from(document.querySelectorAll('.task-progress-text')).every(element => element.getBoundingClientRect().height <= 37), 'long messages do not stretch rows');
      }
      const dialogRoute = ['/access/user', '/access/role', '/operations/log/operlog'].includes(${JSON.stringify(route)});
      if (dialogRoute) {
        const label = ${JSON.stringify(route)} === '/operations/log/operlog' ? '详细' : '新增';
        Array.from(main.querySelectorAll('button')).find(button => button.innerText.trim() === label).click();
        for (let i = 0; i < 100 && !Array.from(document.querySelectorAll('.el-dialog')).some(visible); i++) await new Promise(resolve => setTimeout(resolve, 50));
        await new Promise(resolve => setTimeout(resolve, 350));
        const dialog = Array.from(document.querySelectorAll('.el-dialog')).find(visible);
        const rect = dialog.getBoundingClientRect();
        check(rect.left >= 0 && rect.right <= innerWidth + 1, 'dialog fits width');
        check(rect.top >= 0 && rect.bottom <= innerHeight + 1, 'dialog fits height');
        const input = dialog.querySelector('input.el-input__inner');
        if (input) {
          input.focus(); await frame();
          check(getComputedStyle(input).boxShadow === 'none' && getComputedStyle(input).backgroundColor === 'rgba(0, 0, 0, 0)', 'input has no nested focus border');
        }
      }
      return { route: ${JSON.stringify(route)}, viewport: [innerWidth, innerHeight], checks, errors, dialog: dialogRoute };
    } finally { app.config.errorHandler = original; }
  })()`)
  pages.push(result)
  const filename = `${result.viewport[0]}-${route.replace(/\//g, '-').slice(1)}.png`
  if (result.dialog) {
    browser('screenshot', path.join(output, filename.replace('.png', '-dialog.png')))
    evaluate(`(async()=>{Array.from(document.querySelectorAll('.el-dialog')).find(element=>element.getClientRects().length).querySelector('.el-dialog__headerbtn').click();await new Promise(resolve=>setTimeout(resolve,350));return true})()`)
  }
  browser('screenshot', path.join(output, filename))
  console.log(`${result.checks.every(check => check.pass) ? 'PASS' : 'FAIL'} ${route} ${result.viewport.join('x')}`)
}
const width = pages[0].viewport[0]
const failures = pages.flatMap(page => page.checks.filter(check => !check.pass).map(check => `${page.route}: ${check.name}`))
fs.writeFileSync(path.join(output, `report-${width}.json`), JSON.stringify({ pages, failures }, null, 2))
if (failures.length) throw new Error(failures.join('\n'))
console.log(`通过 ${pages.reduce((sum, page) => sum + page.checks.length, 0)} 项布局检查；截图：${output}`)
