# 第二阶段写操作与事务验收记录

- 日期：2026-09-26
- 环境：Docker 测试环境，`http://localhost:8080/workspace`
- 范围：DM/Oracle 隔离账号上的对象权限授予/撤销、表数据保存/撤销、物化视图识别与只读打开、SQL 会话提交/回滚

## 隔离范围

- DM：对象所有者 `JQDMO_260926`、被授权用户 `JQDMG_260926`，测试对象 `QA_EDIT`、`QA_MV`。
- Oracle：对象所有者 `JQORO_260926`、被授权用户 `JQORG_260926`，测试对象 `QA_EDIT`、`QA_MV`。
- `SYSDBA` / `SYSTEM` 仅用于创建隔离账号以及在隔离对象上执行授权管理；未修改、删除或写入任何既有系统对象和业务对象。
- 隔离账号与对象暂时保留，供下一轮复验使用；本轮未执行清理或删除。

## HTTP 页面验收结果

| 验收项 | DM | Oracle |
| --- | --- | --- |
| 用户对象权限授予/撤销 | 通过；`SELECT` 授予后刷新为已勾选，撤销后刷新为未勾选 | 通过；授予成功，撤销后整页刷新并重新选择用户、模式和表，`SELECT` 仍为未勾选 |
| 表数据保存 | 通过；新增第 2 行并保存，刷新后仍存在 | 通过；新增第 2 行并保存，刷新后仍存在 |
| 表数据撤销 | 通过；新增第 3 行后撤销，行被移除且保存/撤销按钮禁用 | 通过；新增第 3 行后撤销，恢复为 2 行且保存/撤销按钮禁用 |
| 物化视图类型与打开行为 | 通过；目录标识为物化视图，打开为只读数据页 | 通过；目录显示“物化 / 物化视图”，打开显示“物化视图 · 只读”，无新增、删除、保存入口 |
| SQL 会话提交 | 通过；待提交状态可见，提交后按钮禁用，数据持久化 | 通过；待提交状态可见，提交后按钮禁用，数据持久化 |
| SQL 会话回滚 | 通过；更新后回滚，表页确认值恢复为 `baseline` | 通过；更新后回滚，表页确认值恢复为 `baseline` |

本轮结束时没有待提交 SQL 会话；未重启任何 Docker 容器。前端修复通过构建后将静态文件复制到正在运行的前端容器进行复验。

## 发现并修复的真实缺陷

用户“对象权限”页的权限表使用固定 `700px/650px` 高度，而父级标签页可视区域更小且会裁剪溢出内容，导致 `SELECT`、`UPDATE` 等底部权限在页面上无法操作。

本轮仅调整 `objectPermissions.vue` 的高度约束，使权限表跟随标签页剩余空间并使用内部滚动条；未改动授权业务逻辑。新增 `object-permissions-ui.test.js` 锁定该布局约束。DM 与 Oracle 页面均已用修复后的生产构建复验通过。

## 自动化回归

- 前端：`object-permissions-ui.test.js`、`table-editor-ui.test.js`，2 项通过。
- 后端：`DefaultSqlBuilderDmlTest` 7 项、Oracle DML/物化视图/授权 SQL 6 项、`TableUserServiceRoleChangeTest` 4 项、`SQLExecutorBlobTest` 4 项、`SQLExecutorSessionTest` 1 项、`TableUserControllerTest` 2 项，共 24 项通过，0 失败。
- 前端生产构建通过；仅有既有依赖警告。

## 关键证据

### DM

- SQL 会话：[提交前](qa-phase2/dm-sql-pending-before-commit.png)、[提交后](qa-phase2/dm-sql-after-commit.png)、[回滚前](qa-phase2/dm-sql-pending-before-rollback.png)、[回滚后](qa-phase2/dm-sql-after-rollback.png)、[回滚值确认](qa-phase2/dm-sql-rollback-verified.png)
- 表编辑：[保存前](qa-phase2/dm-table-before-save.png)、[保存后](qa-phase2/dm-table-after-save.png)、[撤销前](qa-phase2/dm-table-before-undo.png)
- 物化视图：[只读打开](qa-phase2/dm-materialized-view-readonly.png)
- 权限页修复：[可滚动权限表](qa-phase2/dm-object-permissions-fixed.png)、[可见 SELECT 行](qa-phase2/dm-object-permissions-scroll-select.png)

### Oracle

- SQL 会话：[提交前](qa-phase2/oracle-sql-pending-before-commit.png)、[提交后](qa-phase2/oracle-sql-after-commit.png)、[回滚前](qa-phase2/oracle-sql-pending-before-rollback.png)、[回滚后](qa-phase2/oracle-sql-after-rollback.png)、[回滚值确认](qa-phase2/oracle-sql-rollback-verified.png)
- 表编辑：[保存前](qa-phase2/oracle-table-before-save.png)、[保存后](qa-phase2/oracle-table-after-save.png)、[撤销前](qa-phase2/oracle-table-before-undo.png)、[撤销后](qa-phase2/oracle-table-after-undo.png)
- 物化视图：[类型识别](qa-phase2/oracle-materialized-view-type.png)、[只读打开](qa-phase2/oracle-materialized-view-readonly.png)
- 对象权限：[授予成功](qa-phase2/oracle-object-grant-after-submit.png)、[撤销成功](qa-phase2/oracle-object-revoke-after-submit.png)、[整页刷新后仍为未授权](qa-phase2/oracle-object-revoke-reloaded.png)

## 后续计划（稳定性与数据安全优先）

1. **下一轮从对象级 DDL 安全闭环继续**：只在上述隔离账号内验证建表/改表/重命名/删除、SQL 预览、失败后的状态一致性与审计记录，并覆盖 DM/Oracle。
2. 验证导入/导出及 LOB 大字段的失败重试、事务边界、部分成功提示和回滚安全。
3. 验证连接与 SQL 会话生命周期，包括超时清理、页面关闭、断线重连和多标签页会话隔离。
4. 扩大权限与审计覆盖，补齐失败路径、最小权限账号和用户可理解的错误定位。
