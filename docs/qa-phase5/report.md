# 第五阶段验收报告

## 范围

- 查询控制台完整回归
- 事务提交与回滚
- 表数据编辑
- 数据库对象管理
- 权限管理
- 稳定性与基础性能
- 自动化回归整合

> 导入导出功能按当前要求暂停，不在本阶段执行、修改或验收。

## 验收矩阵

| 项目 | 验收点 | 状态 | 证据/备注 |
| --- | --- | --- | --- |
| 查询分页 | 每页 100 行、跨页及末页数据正确 | 通过 | Oracle 205 行结果分 3 页，末页为 201–205；见 `screenshots/oracle-pagination-page1.png`、`oracle-pagination-page2.png`、`oracle-pagination-page3.png` |
| 空值与大字段 | NULL、CLOB 等显示正确 | 通过 | Oracle NULL 显示为 `<null>`，CLOB 内容可见；见 `screenshots/oracle-null-clob.png` |
| 多语句错误策略 | 遇错继续、遇错停止行为正确 | 通过 | ISSUE-01 已修复；Oracle/DM“遇错停止”均只保留首条成功结果，DM“遇错继续”仍产生 2 个成功结果 |
| 超时与取消 | 可中止长查询，状态可恢复 | 通过 | DM 5 秒超时正确返回“执行超时”；主动停止后返回“执行已取消”；见 `screenshots/dm-query-timeout.png`、`dm-query-cancelled.png` |
| 会话恢复 | 回滚/错误后可继续执行查询 | 通过 | 取消长查询后同一控制台继续查询返回 `QA_RECOVERY_VALUE=42`；见 `screenshots/dm-session-recovery.png` |
| 事务 | INSERT/UPDATE/DELETE 的提交与回滚正确 | 通过 | DM 隔离表 `JQDMO_260926.QA_TX_P5`：INSERT 提交后可见；UPDATE 回滚后保持 `BASE/CLOB_BASE`；DELETE 回滚后仍为 1 行；见 `screenshots/dm-transaction-*.png` |
| 表数据编辑 | 新增、修改、删除及字段类型处理正确 | 通过 | DM `QA_EDIT` 通过页面新增 `9005/ui_add_p5/1`、修改为 `ui_update_p5`、再删除并保存；保存后恢复原 2 行；见 `screenshots/dm-table-editor-*.png` |
| 对象管理 | 隔离测试对象的创建、查看与维护正确 | 通过 | `QA_EDIT` 的列、主键索引、主键约束、建表语句、基本信息、触发器、授权和关联关系均与对象实际状态一致；另建 `QA_IDENTITY_P5` 验证自增列显示；见 `screenshots/dm-object-columns-fixed.png`、`dm-identity-column-fallback.png` |
| 权限管理 | 授权、回收与展示一致 | 通过 | DM 隔离用户 `JQDMG_260926` 对 `JQDMO_260926.QA_EDIT` 的 `SELECT` 经页面授予后保持勾选，撤销后恢复未勾选；见 `screenshots/dm-permission-select-granted.png`、`dm-permission-select-revoked.png` |
| 稳定性与性能 | 连续操作无前端异常、基础响应可接受 | 通过 | 连续 20 次 DM 轻量查询均返回 HTTP 200 和结果 `1`；含固定 250ms 等待及命令启动开销的端到端操作周期平均约 628ms；前端无错误，后端/前端/Redis 均为 `healthy`；见 `screenshots/dm-stability-20-runs.png` |
| 自动化回归 | 相关测试可统一执行且通过 | 通过 | 明确排除导入导出相关用例；后端 113 个相关测试、前端 4 个回归测试全部通过，12 模块后端构建成功 |

## 问题记录

### ISSUE-01 “错误不执行”未在首个错误后停止

- 数据库：Oracle
- 操作：选择“错误不执行”，依次执行成功查询、访问不存在表的查询、成功查询。
- 期望：第二条 SQL 失败后停止，第三条 SQL 不执行。
- 实际：结果合计仍为 2，第三条 SQL 返回 `AFTER_VALUE=30`。
- 证据：`screenshots/oracle-error-stop-continued.png`
- 处理：`SQLExecutor` 对“以失败结果返回、未抛异常”的执行路径也应用停止策略，并补充策略单测。
- 回归：Oracle 与 DM 均通过；见 `screenshots/oracle-error-stop-fixed.png`、`dm-error-stop-fixed.png`、`dm-error-continue-fixed.png`。
- 自动化：`SQLExecutorPolicyTest` 4/4 通过。
- 状态：已修复并回归通过。

### ISSUE-02 Windows 工作树中的启动脚本行尾导致后端容器无法启动

- 操作：使用当前工作树构建并替换后端镜像。
- 实际：容器报告 `/app/entrypoint.sh: no such file or directory`，脚本为 CRLF 行尾。
- 处理：后端运行时镜像构建阶段规范化脚本行尾。
- 回归：镜像成功构建，后端健康检查恢复为 `healthy`。
- 状态：已修复并回归通过。

### ISSUE-03 DM 表“列信息”页为空

- 数据库/对象：DM `JQDMO_260926.QA_EDIT`。
- 操作：从表列表打开对象详情，切换到“列信息”，并点击“刷新”。
- 期望：显示已知列 `ID`、`NAME`、`FLAG`。
- 实际：只显示表头，没有任何列记录；同一对象“数据”页可正常读取和编辑 3 个字段。
- 证据：`screenshots/dm-object-columns-empty.png`。
- 根因：达梦插件为列补充自增属性时直接读取 `SYS.SYSCOLUMNS`；普通对象所有者没有该系统目录的查询权限，异常使整张列信息请求失败。
- 处理：保留系统目录快速路径；目录权限不足时，改用当前用户可访问的 `DBMS_METADATA.GET_DDL` 识别 `IDENTITY/AUTO_INCREMENT` 列；两条路径均不可用时仅降级自增标记，不再阻断基础列信息。
- 自动化：`DMMetaDataColumnSqlTest` 4/4 通过，覆盖目录 SQL、字面量转义、DDL 降级 SQL 和自增列解析。
- 回归：受限账号刷新同一对象后正确显示 `ID`、`NAME`、`FLAG`，主键与非空状态正确；另建 `QA_IDENTITY_P5`，确认 `ID` 的主键、非空、自增三项均正确勾选，`NAME` 保持非自增；见 `screenshots/dm-object-columns-fixed.png`、`dm-identity-column-fallback.png`。
- 状态：已修复并回归通过。

## 最终结论

本阶段除按要求暂停的导入导出功能外，其余计划项均已完成并通过验收。现场发现的 ISSUE-01、ISSUE-02、ISSUE-03 均已修复并完成回归，目前没有未关闭的本阶段问题。

当前部署镜像：`jddbms-test-backend:latest`，manifest list `sha256:5b8d9df8564ad597bf7d17e843beb73ef998c2e2aecb6d6dce99c82105f42f7f`。

导入导出、DMP、归档导入导出及其报告结论仍保持暂停，不包含在本结论中。
