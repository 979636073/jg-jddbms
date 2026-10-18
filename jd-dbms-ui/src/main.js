import { createApp, defineAsyncComponent } from 'vue'

import Cookies from 'js-cookie'

import Element, { ElDialog } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import VirtualResourceTree from '@/components/VirtualResourceTree'
import '@/assets/element-icons/icon.css'
import elementIcons from '@/assets/element-icons'

ElDialog.props.closeOnClickModal.default = false;
import './assets/styles/element-variables.scss'

import '@/assets/styles/index.scss' // global css
import '@/assets/styles/ruoyi.scss' // ruoyi css
import '@/assets/styles/dm-ui-refresh.scss'
import App from './App'
import store from './store'
import router from './router'
import directive from './directive' // directive
import plugins from './plugins' // plugins
import { download } from '@/utils/request'

import icons from './assets/icons' // icon
import './permission' // permission control
import { getDicts } from "@/api/system/dict/data";
import { getConfigKey } from "@/api/system/config";
import { parseTime, resetForm, addDateRange, selectDictLabel, selectDictLabels, handleTree } from "@/utils/ruoyi";
// 分页组件
import Pagination from "@/components/Pagination";
// 自定义表格工具组件
import RightToolbar from "@/components/RightToolbar"
// 富文本组件
// 文件上传组件
import FileUpload from "@/components/FileUpload"
// 图片上传组件
import ImageUpload from "@/components/ImageUpload"
// 图片预览组件
import ImagePreview from "@/components/ImagePreview"
// 字典标签组件
import DictTag from '@/components/DictTag'
// 头部标签组件
// 字典数据组件
import DictData from '@/components/DictData'
// 拖动指令
import { resize } from "@/utils/resize"
import { createEventBus } from '@/utils/eventBus'
import Contextmenu from '@imengyu/vue3-context-menu'
import '@imengyu/vue3-context-menu/lib/vue3-context-menu.css'

const app = createApp(App)
app.directive("sy-resize", resize);

// 全局方法挂载
Object.assign(app.config.globalProperties, {
  getDicts, getConfigKey, parseTime, resetForm, addDateRange,
  selectDictLabel, selectDictLabels, download, handleTree,
  $EventBus: createEventBus()
})

// 全局组件挂载
app.component('DictTag', DictTag)
app.component('VueEasyTree', VirtualResourceTree)
app.component('Pagination', Pagination)
app.component('RightToolbar', RightToolbar)
app.component('Editor', defineAsyncComponent(() => import('@/components/Editor')))
app.component('FileUpload', FileUpload)
app.component('ImageUpload', ImageUpload)
app.component('ImagePreview', ImagePreview)


const contextmenu = options => Contextmenu.showContextMenu({
  ...options,
  x: options.x ?? options.event?.clientX,
  y: options.y ?? options.event?.clientY
})
contextmenu.destroy = () => Contextmenu.closeContextMenu()
app.config.globalProperties.$contextmenu = contextmenu

app.use(directive)
app.use(plugins)
app.use(icons)
app.use(elementIcons)
app.use(DictData)

/**
 * If you don't want to use mock-server
 * you want to use MockJs for mock api
 * you can execute: mockXHR()
 *
 * Currently MockJs will be used in the production environment,
 * please remove it before going online! ! !
 */

app.use(Element, {
  locale: zhCn,
  size: ({ mini: 'small', medium: 'default' })[Cookies.get('size')] || Cookies.get('size') || 'default'
})

app.use(store)
app.use(router)
router.isReady().then(() => app.mount('#app'))
