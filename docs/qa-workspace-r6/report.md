# 工作区后续回归 · r6

| 项目 | 内容 |
|---|---|
| 日期 | 2026-10-13 |
| 地址 | http://127.0.0.1:38180/ |
| 会话 | jddbms-r6 |
| 范围 | 左树、物化视图、数据分页、客户端包管理；不包含外部 DMP 互导 |

## 结果

本轮完成下列定向页面验收，修复 1 项布局问题。不将既有单元测试或 JDBC 测试代替未执行的端到端验收；尚未完成的矩阵见末节。

| 严重程度 | 已复现问题数 |
|---|---:|
| Critical | 0 |
| High | 0 |
| Medium | 1 |
| Low | 0 |

## 检查记录

- 本轮开始时原授权的 getInfo 返回 200，登录故障不再阻塞页面验收。

## 问题

### ISSUE-001：客户端上传弹窗底部按钮超出短窗口

- 严重程度：Medium；分类：UI / UX。
- 在 1254×620 浏览器窗口，进入个人中心 → 系统设置 → 导入导出客户端 → 上传客户端。重复打开可复现。
- 文件区域和平台字段占满窗口，上传/取消按钮落在屏幕下方；需要滚动外层弹窗才能操作。
- 预期：弹窗不超出窗口，正文内部滚动，底部操作始终可见。
- 证据：[上传弹窗](screenshots/client-upload-platform.png)。静态布局问题，无需复现视频。
- 状态：已修复并回归。上传、修改弹窗限定高度 88vh（沿用全局顶部 6vh 留白），仅正文内滚动，标题和底部操作不缩小；保持原颜色、字体、字段顺序和提交逻辑。
- 修复前新增回归失败；修复后通过。页面测量：1254×620 底部 582.78px；1280×720 底部 640.19px；1440×900 底部 651px；800×600 底部 564px。均在窗口内，正文 overflow-y=auto；未选文件时可直接点击底部按钮并收到校验提示。
- 修复截图：[1254](screenshots/client-dialog-fixed-1254.png)、[1280](screenshots/client-dialog-1280.png)、[1440](screenshots/client-dialog-1440.png)、[800](screenshots/client-dialog-800.png)。

## 本轮已验证

- DM：连接只读确认、模式展开、表目录、物化视图独立分组、物化视图数据只读展示。
- Oracle：专用 QA 模式中新建临时 QA_R6_PAGE_7B91，10,001 行；目录自动更新；手动统计得到 10,001；101 页只有 1 行且下一页禁用；窗口切换后保持 101 页；ID 降序回到第一页；高级查询 ID >= 9999 返回 3 行、总数同步为 3。
- 客户端：空提交提示选择 ZIP；真实 Windows DM 客户端中文 ZIP（58,943,772 字节）上传成功，识别 DM DEXP/DIMP、V8、WINDOWS/X86_64。只测试包管理，未执行 DMP 导入导出。

## 证据与清理

- [DM 物化视图](screenshots/dm-mview.png)、[Oracle 末页](screenshots/oracle-last-page.png)、[窗口切回仍在末页](screenshots/oracle-return-last-page.png)、[高级查询三行](screenshots/oracle-filter.png)、[客户端上传成功](screenshots/client-upload-success.png)。
- 专用 QA 临时表 JQORO_260926.QA_R6_PAGE_7B91 已通过界面确认删除，随后查询 USER_TABLES 的 REMAINING=0：[清理验证](screenshots/cleanup-verified.png)。未修改既有表。
- 本轮上传的客户端先停用再删除，管理列表重新显示 0 个版本。原页面初始也是 0 个版本；未删除既有客户端。
- 本地复现 ZIP 保留在 D:/code/jddbms-windows-local-20261013/qa-r6/，未纳入源码或对外安装包。

## 构建与部署

- 前端核心自动化 26 项通过，包括新增 client-dialog-layout.test.js；生产构建成功；定向 git diff --check 通过。
- 日志：D:/code/jddbms-windows-local-20261013/logs/r6-ui-tests.log、r6-ui-build.log。
- Windows 前端已更新；旧前端保存在 D:/code/jddbms-windows-local-20261013/backup-before-r6/web。
- 容器只更新前端为 release-20261013-r6，保留 rollback-20261013-r6 镜像。后端仍为上一轮 r5，未重启；数据库和 Redis 未修改。三容器均 healthy。
- 本机 dist 与容器入口 HTML SHA-256 一致：1400c69a9d8657361b369a14964afbd85edecea233d7fbfa7f8586e4ed6ece16。
- 本轮登录后的业务页面实测在 Windows 38180 完成；容器 38080 完成健康与构建一致性检查。结束时原 cURL 授权在容器 getInfo 返回 401，因此未把容器业务页记为已验收；不能仅凭 401 断言密钥错误或会话过期。
- 未进行 Git 提交、推送或重新生成离线安装归档。已有 r2 离线安装包不包含 r3–r6 的后续修复。

## 尚待继续的矩阵

1. 400 表目录的浏览器端完整性、分页续载、名称过滤和失败重试；既有 400 表 JDBC 测试不替代此项。
2. DM 10,001 行的浏览器端分页/筛选全流程，以及两库不同 pageSize、连续快速翻页组合。
3. Oracle 物化视图与普通视图切换、列/DDL/依赖页的完整页面矩阵。
4. 客户端重新检测、编辑保存、错误包提示，以及容器 Linux ARM64 真实客户端包管理；不包含外部 DMP 互导。
5. 在固定源码版本后刷新离线安装包、版本摘要和更新文档；真实旧库恢复/回退演练仍需单独执行。
