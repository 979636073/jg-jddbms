# Vue 3 框架迁移记录

更新日期：2026-10-18。

## 分支与阶段边界

- 稳定基线：`main`，提交 `5c217a95b8c31f705dfb50b639a9b7910eb0cd4a`。开始迁移前工作区干净，已 fetch 并 push，确认与 `origin/main` 一致。
- 迁移分支：`codex/vue3-migration`。本阶段代码不合并回 main，不作为正式安装包发布。
- 第一阶段目标：切换 Vue 3 运行时和主要依赖，让数据库工作区、SQL 控制台及结果展示可以运行，建立后续逐页迁移的构建和回归基线。
- 当前仍使用 `@vue/compat`：编译器和运行时保留 Vue 2 兼容行为，尚未完成纯 Vue 3 迁移。框架升级本身不代表性能改善，仍需相同数据、场景和环境下的前后对比。

## 已完成

| 项目 | 当前版本或处理 |
| --- | --- |
| Vue / @vue/compat / compiler-sfc | 3.5.44，同版本锁定 |
| Vue Router / Vuex | 4.6.4 / 4.1.0 |
| UI 组件库 | Element Plus 2.14.7，替换 Element UI |
| 构建工具 | Vue CLI 5.0.9 / Webpack 5，保留现有构建入口 |
| Vue 2 依赖 | 替换树选择、计数、右键菜单、裁剪、拖拽组件依赖；移除 vue-meta 和 vue-template-compiler |
| 应用入口 | createApp、插件注册、全局属性、指令生命周期、轻量事件总线 |
| 路由与缓存 | Router 4 动态路由、RouterView 插槽配合 KeepAlive、窗口初始化、滚动容器 API |
| 模板 | 插槽、事件、Dialog、控件尺寸及 Element Plus 单向 modelValue 绑定迁移 |
| SQL 控制台 | 修复拖拽容器升级后编辑器不渲染；使用 activated/deactivated 隔离缓存窗口快捷键 |
| 资源树 | Vue 3 虚拟列表，保留现有懒加载、搜索、选中、展开和节点操作接口 |
| 图标和主题 | 保留原图标字体及 MIT 许可，提供 Vue 3 图标适配；主题改用 CSS 变量 |
| 测试基础 | 使用 Vue 3 编译器及实际树模型，新增事件清理与万节点树回归 |

兼容配置注意事项：

1. `vue.config.js` 将 `vue` 指向 `@vue/compat`，模板编译保留 `MODE: 2`。业务组件仍存在待迁移的旧 API。
2. `main.js` 全局关闭 `COMPONENT_ASYNC` 兼容选项；异步组件通过 `defineAsyncComponent` 显式声明，避免 Vue 3 函数组件被当作 Vue 2 异步工厂调用。
3. Element Plus 组件使用 `compatConfig.MODE: 3`，以维持原生 Vue 3 的 modelValue 行为。
4. 虚拟资源树暂时使用 Element Plus 内部 TreeStore（`element-plus/es/components/tree/src/model/tree-store.mjs`）。已锁定依赖版本；后续升级 Element Plus 前必须验证该边界，不能将其视为稳定公开 API。
5. 开发环境不再用 dynamic-import-node 将动态导入改写为 require；旧模块转换配置会与 Vue 3 依赖的 ESM 导出冲突。

## 本地运行

```powershell
cd D:\code\28\dbms\jg-jddbms\jd-dbms-ui
npm ci
npm run dev -- --host 127.0.0.1 --port 8081
```

本次验收时稳定版本在 `http://localhost:8080`，迁移版本在 `http://localhost:8081`，共同使用本地后端服务。8080 的运行目录独立于本次源码，不能用其页面判定迁移效果。

## 验证记录

验证环境：Windows、Node 24、管理员会话、本地 DM 数据源。下列是本阶段的实际验证范围，不等同于全部业务验收。

| 检查 | 结果 |
| --- | --- |
| `npm run test:core` | 41 项全部通过 |
| `npm run lint -- --no-fix` | 0 错误，683 项警告；警告未作为本次无关清理范围 |
| `npm run build` | 成功；构建 Hash `0a992ec96d2dcee8`，耗时 22019ms |
| `npm ls vue vue-router vuex element-plus @vue/compat --depth=0` | 版本与上表一致 |
| 浏览器运行时 | Vue 3.5.44，工作区可打开 |
| 数据源连接及树 | 本地 DM 连接成功，展开 TEST-TEST 模式和数据表，表注释正常展示 |
| 点击数据表 | BLOB 测试表显示 100 行及列头；首次展示没有请求 BLOB 接口，点击“查看”可打开文件弹窗 |
| SQL 输入与执行 | Monaco 可输入并执行 `SELECT 1 AS VUE3_QA FROM DUAL`，结果为 1 |
| SQL 查询 BLOB | 查询 BLOB_LARGE_TEST_20261015 的 ID >= 1000，返回 30 条；首次 BLOB 请求为 0，点击“查看”后触发 blob/preview 请求 |
| SQL 结果列宽 | FILE_NAME 初始 164px，通过拖动调整至 224px |
| 树规模 | 浏览器临时内存数据 10000 个节点、660px 视口，仅渲染 34 个树节点；滚动可到最后一项。测试后恢复原树，没有新增数据库对象 |
| 共享连接授权 | “测试”连接弹窗正常打开，授权管理、有效权限、授权记录三个页签可切换；未执行授权或撤销，不代表权限写入已完成验收 |

本地构建和浏览器证据存放于忽略目录 `.local/vue3/`，不将运行日志、登录资料或缓存提交 Git。

本轮另观察到：连接本地 DM 后，共享授权按钮可能消失。`canManageDataSource` 依赖 `canManage`，连接后替换连接对象的业务逻辑与 main 相同。本阶段未修改权限判断；后续需核查连接响应与连接列表的权限字段，补充连接前后的按钮及角色回归。

## 后续迁移与验收顺序

1. **数据库工作区切换为原生 Vue 3。** 逐项移除 `$set/$delete`、旧自定义 v-model 和旧 render API；逐组件启用 `MODE: 3`，覆盖多窗口、搜索状态、右键菜单、懒加载及快捷键，然后移除相关兼容选项。
2. **逐页适配业务组件。** 表单设计器的 vuedraggable 4 `item-key/#item` 插槽、复杂日期控件、富文本、裁剪、图表、管理表单需要单独验证。依赖已经替换、模板能够编译，不代表这些页面已验收。
3. **业务及角色回归。** 管理员、数据库开发和其他角色分别验证菜单、连接共享、只读限制、结构编辑、导入导出、结构对比、任务与日志；业务写操作仅在专用测试对象执行。
4. **记录性能基线与对比。** 在同一数据源和相同返回数据下，分别测量首屏、树展开、表格首帧、SQL 输入长任务、结果滚动及多标签内存；将后端请求耗时与前端渲染耗时分开记录。
5. **移除兼容层。** 相关页面全部切换为 `MODE: 3` 并通过回归后，移除 `@vue/compat` 和临时桥接。Vite、Pinia 等工具调整另行评估，不与本阶段同时推进。
6. **合并及发布门槛。** 完成上述验收、生产包冒烟和部署脚本验证后再合并 main、制作安装包。现场 DM 2024、新版 Oracle、Linux ARM64 仍需要外部环境验收。
