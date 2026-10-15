# 视图与客户端管理验收 · r8

| 项目 | 内容 |
|---|---|
| 日期 | 2026-10-13 |
| 地址 | http://127.0.0.1:38180/ |
| 会话 | jddbms-r8 |
| 范围 | Oracle 普通/物化视图、客户端版本管理；外部 DMP 互导暂缓 |

## 汇总

本轮定向验收完成。发现并修复 Medium 2 项，Critical/High/Low 为 0；未完成项目不计为通过，不等同于全系统验收完成。

## 检查记录

- 本机 getInfo 返回 200，使用用户已授权的本地会话文件，不记录 Cookie 或密钥。
- QA 样本 QR8_6D2_BASE / QR8_6D2_VIEW / QR8_6D2_MV：创建前 USER_OBJECTS 同名数量 0，创建后均 VALID；物化视图对应的 TABLE 是 Oracle 自身存储对象。
- 普通视图数据、列 NUMBER/VARCHAR2、DDL、依赖基表、被依赖空状态通过。
- 物化视图独立分组、数据只读、列信息、MATERIALIZED VIEW DDL、依赖基表通过；基表名称从 r8-initial 改为 r8-updated 并提交后，点击“刷新物化视图”得到更新值。

## ISSUE-001：视图切到 SQL 窗口再返回时丢失子页签状态

- 严重程度：Medium；分类：状态保持；状态：已修复并页面回归。
- 普通/物化视图之间切换可保留页签，但跨到 SQL 控制台再返回会重新选择“数据”。已重复复现两次。
- 步骤：打开 QR8_6D2_MV → 点击“脚本”（[脚本页](screenshots/issue-001-step1.png)）→ 点击“新建查询”窗口（[SQL 窗口](screenshots/view-to-sql.png)）→ 点击 QR8_6D2_MV 窗口（[实际回到数据页](screenshots/sql-to-view.png)）。预期仍停留脚本页。
- 录像环境缺少 ffmpeg，使用逐步截图；不声称已生成复现视频。issue-001-step2 / issue-001-result 记录的是视图间切换成功的对照，不作为失败证据。
- 根因：ContainerBar 的 pageId 直接取全局路由；keep-alive 内隐藏窗口也随路由改用其他窗口配置。最终修复将其固定到创建时的 cachePageId，所属窗口自身的配置更新仍保留。
- 期间尝试过视图页签/脚本快照补丁，实测仍存在多窗口互相覆盖，已全部撤回。最终不增加第二套状态缓存；中间版本截图不作为最终通过证据。
- 新增回归验证隐藏窗口路由变化后仍持有自己的配置、其他窗口更新不串入、所属窗口刷新仍生效。修复前失败，修复后通过。
- 最终版本证据：[普通视图脚本草稿保留](screenshots/view-state-final.png)、[物化视图脚本保留](screenshots/mv-state-final.png)、[物化视图依赖页保留](screenshots/mv-dependency-return-final.png)。往返 SQL 窗口后脚本页仍选中、DDL 非空；依赖页仍显示正确基表。脚本往返的 view/detail 请求数 48→48；依赖往返的 /api/rdb/ 请求数 111→111，没有新增数据库请求。

## ISSUE-002：顶部“刷新数据”成功后表格仍显示旧值

- 严重程度：Medium；分类：刷新结果一致性；状态：已修复并页面回归。
- 更新并提交测试基表 NAME 后，点击物化视图顶部“刷新数据”，出现“物化视图已刷新”但表格仍显示旧值。再次点击表格内“刷新”才显示新值。已用不同值重复复现两次。
- 步骤证据：[更新基表](screenshots/issue-002-update-base.png) → [点击顶部后仍旧值](screenshots/issue-002-stale-repro.png)。对照：[第一次旧值](screenshots/header-refresh-stale.png) → [手动刷新才读到新值](screenshots/header-manual-refresh.png)。
- 预期：顶部操作成功后，表格直接显示物化视图的新数据。
- 录像同 ISSUE-001 限制，使用逐步截图。
- 修复：为结果表格绑定引用，顶部物化刷新成功后调用其既有 refresh()。新增回归在修复前失败、修复后通过；实库从 r8-header-repro 更新到 r8-header-fixed，点击顶部按钮后表格直接显示新值：[刷新回归](screenshots/header-refresh-fixed.png)。
- 最终构建再次把基表改为 r8-final，只点击顶部“刷新数据”后表格直接显示 r8-final：[最终回归](screenshots/header-refresh-final.png)。

## 客户端管理

- 初始 0 个版本；上传现有真实 DM Windows 客户端 ZIP，自动识别 DM DEXP/DIMP、8、WINDOWS/X86_64；重新检测完成后保持正确结果：[检测完成](screenshots/client-redetect.png)。
- 测试包版本改为 8.0 并保存，整页刷新后仍为 8.0：[保存持久化](screenshots/client-edit-persisted.png)。仅修改本轮测试登记信息。
- 只有文本的错误 ZIP 被拒绝，明确提示缺少完整导出/导入工具对，没有登记为可用版本：[错误包](screenshots/client-invalid.png)。
- 已停用并删除本轮测试包，页面恢复 0 个版本：[清理确认](screenshots/client-cleanup.png)。源 ZIP 和错误包测试样本保留在本机，未加入安装包。

## 清理与构建

- 本轮 Oracle 临时基表、普通视图和物化视图均已删除，USER_OBJECTS 按 3 个精确对象名查询 REMAINING=0：[数据库清理](screenshots/oracle-cleanup.png)。Oracle 自动生成的物化视图存储表随 DROP MATERIALIZED VIEW 一并清理；未改动原有 QA_MV 等对象。样本可用 [oracle-fixture.sql](oracle-fixture.sql) 重建。
- 未执行普通视图草稿“保存定义”，只验证切换时保留内容，不把草稿写入数据库。
- 前端核心回归 27 项通过，生产构建成功，定向 git diff --check 通过；浏览器未发现未处理 JS 异常。
- 日志：D:/code/jddbms-windows-local-20261013/logs/r8-ui-tests.log、r8-ui-build.log。
- Windows 本机前端已更新，上一轮 r7 备份保存在 D:/code/jddbms-windows-local-20261013/backup-before-r8/web。
- 最终入口 HTML SHA-256：8d8d62d7d5863196c4e986226ac1b7095e82faf661b62768b638941aec8336e2。
- 本轮没有更新容器、后端、元数据库脚本或离线安装包，没有 Git 提交/推送。

## 尚未完成的计划

1. Linux ARM64 真实客户端包的管理验收；本轮 Windows 包测试不代替此项。
2. Oracle 不同页大小与快速翻页组合，以及现场复杂外键、宽表和大 LOB 的性能组合。
3. 固定源码版本后统一容器、离线安装包与更新文档；旧库真实恢复/回退演练。
4. 外部客户端 DMP 互导继续按用户要求暂缓。
