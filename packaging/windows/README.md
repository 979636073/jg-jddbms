# Windows 本地开发与验收

## 环境基线

- PowerShell 7、Git、Maven 3.6 或以上、JDK 17。
- Node 24.15.0、npm 11.12.1；前端版本记录在 `jd-dbms-ui/.nvmrc` 和 `package.json`。
- 保留 Java 8 编译目标。本次不升级业务框架。
- 需要可用的元数据库和 Redis，数据库账号、密码、JWT 密钥及数据源加密密钥按 `doc/运行配置与密钥.md` 配置。

## 全新检出后的验证

在仓库根目录运行，按本机安装位置替换 JDK 路径：

```powershell
$env:JAVA_HOME = 'D:/code_env/jdk-17.0.20.1+1'
$env:Path = "$env:JAVA_HOME/bin;$env:Path"
mvn --batch-mode verify
Set-Location jd-dbms-ui
npm ci
npm run lint -- --quiet
npm run test:core
npm run build
Set-Location ..
```

前端锁文件必须随依赖变更一同提交；日常安装使用 `npm ci`。只有有意调整依赖时才使用 `npm install` 并审查锁文件差异。

运行中的后端会使用用户目录中的本地缓存。如果测试与服务发生缓存文件占用冲突，使用独立测试目录：

```powershell
mvn --batch-mode '-DargLine=-Duser.home=D:/jddbms-test-home' verify
```

## 启动

本地脚本读取根目录中被 Git 忽略的 `.env.docker`，再读取 `-ConfigFile` 指定的配置文件；非空进程环境变量优先。配置文件使用 `NAME=value` 格式，不执行文件中的代码。默认额外配置文件为用户目录下的 `.codex/jddbms-local.env`。

```powershell
./packaging/windows/start-local.ps1 -JavaHome $env:JAVA_HOME -CheckOnly
./packaging/windows/start-local.ps1 -JavaHome $env:JAVA_HOME
```

脚本将后端 JAR 复制到被 Git 忽略的 `.local/run/`，从副本启动，构建输出 `target/` 可以独立更新。日志也保存在 `.local/run/`。8080 或 28080 已被占用时脚本会停止，不会另外选择端口。启动命令返回后仍需确认健康接口和页面可访问。

- 页面：`http://localhost:8080`
- 后端健康接口：`http://localhost:28080/captchaImage`
- 前端版本：个人中心的“前端版本”，以及 HTML 中的 `jddbms-build` 元信息；有已跟踪文件未提交时会显示提示。Git 信息不可用的源码包会明确标识，不能视为某个 Git 提交的正式产物。

不要从正在运行的 `target/jd-dbms-admin.jar` 原地打包。更新本地服务时，先确认并停止脚本输出的前后端 PID，再执行启动脚本。脚本不停止已有服务、不修改数据库配置、不更换加密密钥。

## 浏览器回归

需要安装 `agent-browser` 及其浏览器，先启动前端：

```powershell
agent-browser --session sqlfix open http://localhost:8080
Set-Location jd-dbms-ui
npm run test:browser
```

该回归挂载真实 Vue 组件，使用模拟接口数据，检查 1000 行 × 300 列虚拟滚动、分页、排序、复制、自动列宽、拖拽、双击恢复、BLOB 按需读取、PNG 预览、NULL 和空文件。它不代表真实数据库或权限验收。执行失败或浏览器超时必须返回非零退出码。

发布前还需在已登录页面完成以下真实接口检查：

| 场景 | 预期 |
| --- | --- |
| 登录、数据源列表、连接 | 登录完成，列表及模式可加载；坏连接不能拖垮整个列表 |
| 普通视图、物化视图 | 能打开数据；错误状态可见，不把失败显示为无数据 |
| 表数据和 SQL 控制台 | BLOB 显示占位，未点击查看时没有正文读取请求 |
| BLOB 查询条件 | `>=`、`<=` 等条件不被 HTTP 过滤器转义成无效 SQL |
| BLOB 查看 | NULL、空文件、PNG、JPEG、大图片分别验证，只读取目标值 |
| 结果列宽、树注释 | 内容自适应；拖拽后表头与表体对齐；双击恢复；注释颜色与表名不同 |
| 共享授权 | 用户／团队搜索、授予、修改、撤销；无管理权限时拒绝，检查后清理测试授权 |

CI 执行后端 `verify`、前端安装／lint／核心回归／构建，并保存按提交号命名的前端产物。真实数据库和已登录浏览器验收单独记录，不能用模拟测试替代。

## 第一阶段验收记录（2026-10-15）

本阶段固化环境、启动方式、版本识别和核心回归，保留结果列自动宽度／拖拽及资源树表注释功能。真实验收还发现 SQL 的比较运算符被 HTML 过滤器改写，导致带条件查询的 BLOB 查看失败；现在 SQL 执行、执行计划与 BLOB 读取三条接口保留请求原文，其他接口继续原有清洗，并删除控制器和底层执行器中会改写 SQL 字符串字面量的实体反转义。

| 验证 | 本次结果 |
| --- | --- |
| 后端全模块 `verify` | 352 项，0 失败、0 错误、50 跳过；新增 4 项 SQL／HTTP 过滤器回归，修复前已复现失败 |
| 前端全新依赖安装 | 源码归档并覆盖本次修改，在无 `node_modules` 的独立目录执行 `npm ci` 成功 |
| 前端 lint／核心回归／生产构建 | 通过，核心回归 36 项 |
| 浏览器组件回归 | 11 类检查通过；使用模拟响应，覆盖虚拟滚动、分页、排序、复制、列宽及 BLOB 行为 |
| 本地 DM 真实视图 | `TEST-TEST.TEST_VIEW3` 普通视图和 `TEST-TEST.TEST_VIEW` 物化视图均打开成功，各显示 3 行 |
| 本地 DM 真实 BLOB 表 | `BLOB_LARGE_TEST_20261015` 表格翻页及控制台条件查询正常；未点击时正文请求为 0，点击后请求为 1，PNG 显示为 640 × 360 |
| 真实列宽与注释 | 控制台拖拽列宽及双击恢复通过；表注释使用灰色显示 |
| 数据源共享授权 | 弹窗加载成功，连接 226 的共享配置查询返回 HTTP 200；本阶段未执行真实授予／修改／撤销 |
| 本地启动 | 新脚本检查与实际启动通过；运行 JAR 使用独立副本，后续完整构建不影响服务 |

后端另做过一次无 `target` 的独立源码构建；其时间早于本阶段最后的 SQL 修复。上表后端统计来自包含最终修复的工作目录完整 `verify`。跳过项涉及外部数据库等运行条件，不能视为已通过的真实集成验证。

后续按第二阶段推进权限与审计：先补用户／团队授权、撤销及非管理员端到端用例，再实现有效权限预览和审计关联。真实授权全流程、JPEG／大图片的本阶段重验、远端 CI 结果分别记录，不以本表中的模拟回归代替。
