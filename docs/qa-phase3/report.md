# 第三阶段对象级 DDL 安全闭环验收

| 字段 | 值 |
| --- | --- |
| 日期 | 2026-09-26 |
| 应用地址 | `http://localhost:8080/workspace` |
| 浏览器会话 | `jddbms-phase2`（延续已登录隔离验收会话） |
| 范围 | DM/Oracle 隔离账号内的创建、修改、重命名、删除、SQL 预览、失败一致性与日志审计反馈 |
| 结论 | 通过；发现并修复 1 个 P1 前端交互缺陷 |

## 安全边界

- 仅使用 `JQDMO_260926`、`JQORO_260926` 及本轮新建的可丢弃对象。
- 不修改或删除 `QA_EDIT`、`QA_MV`、隔离账号本身以及任何既有系统/业务对象。
- 本轮未重启容器；仅将修复后的静态前端产物复制到测试前端容器。

## 验收结果

| 数据库 | 创建与回读 | 修改与 SQL 预览 | 重命名与 SQL 预览 | 失败一致性 | 删除确认与清理 |
| --- | --- | --- | --- | --- | --- |
| Oracle | 通过 | 通过 | 通过 | 通过 | 通过 |
| DM | 通过 | 通过 | 通过 | 通过 | 通过 |

### Oracle

- 在 `JQORO_260926` 创建 `QA_DDL_P3`：`ID NUMBER`（主键）、`NAME VARCHAR2(50)`，对象列表和建表 SQL 均可回读。
- 通过 SQL 预览执行 `ALTER TABLE "JQORO_260926"."QA_DDL_P3" ADD ("STATUS" VARCHAR2(10) NULL );`，回读确认共 3 列。
- 通过 SQL 预览执行 `ALTER TABLE "JQORO_260926"."QA_DDL_P3" RENAME TO "QA_DDL_P3_R";`，对象列表与详情同步更新。
- 对同名 `QA_DDL_P3_R` 再次建表，界面明确返回 `ORA-00955: name is already used by an existing object`；失败后原表仍为 3 列，可正常读取。
- 删除前弹出“此操作不可恢复”的二次确认；确认后 `QA_DDL_P3_R` 已消失，既有 `QA_EDIT` 保留。

### DM

- 在 `JQDMO_260926` 创建 `QA_DDL_P3`：`ID INTEGER`（主键）、`NAME VARCHAR(50)`，对象列表和建表 SQL 均可回读。
- 通过 SQL 预览新增 `STATUS VARCHAR(10)`，回读确认共 3 列。
- 通过 SQL 预览执行 `ALTER TABLE "JQDMO_260926"."QA_DDL_P3" RENAME TO "QA_DDL_P3_R";`，对象列表与详情同步更新。
- 对同名 `QA_DDL_P3_R` 再次建表，界面明确返回 `Object [QA_DDL_P3_R] already exists`；失败后原表仍为 3 列，可正常读取。
- 删除前弹出“此操作不可恢复”的二次确认；确认后 `QA_DDL_P3_R` 已消失，既有 `QA_EDIT`、`QA_MV` 保留。

## 问题与修复

### ISSUE-01：表结构保存按钮被返回按钮覆盖

- 严重级别：P1（核心表结构修改入口无法通过鼠标使用）。
- 环境：Oracle / `JQORO_260926.QA_DDL_P3`，列信息页；相同布局也影响索引信息和基本信息页。
- 实际结果：点击“保存”区域会执行“返回模式总览”，SQL 预览不出现，修改不落库。
- 根因：`renderTableEditor/index.vue` 中两个绝对定位按钮区域重叠，且返回按钮的层级更高。
- 修复：保存按钮使用独立样式，移动到返回按钮左侧；增加布局回归断言。
- 状态：已修复并完成浏览器复验。Oracle、DM 的修改及 SQL 预览链路均通过。

## 失败反馈与日志审计

- Oracle：后端于 `10:52:45.400` 在 `TableController.createTable:589` 记录同名对象创建失败，错误为 `ORA-00955`。
- DM：后端于 `11:01:20.171` 在 `TableController.createTable:589` 记录同名对象创建失败，错误为 `Object [QA_DDL_P3_R] already exists`。
- 两次失败均在界面返回明确错误；随后对原对象的查询成功，结构保持 3 列，没有出现半成功或状态漂移。

## 自动化与构建验证

- `node jd-dbms-ui/tests/table-editor-ui.test.js`：通过。
- `vue-cli-service build`：通过；保留 62 条既有 CSS 顺序警告，无新增构建错误。
- 浏览器页面错误检查：无错误。
- `git diff --check`：无空白错误，仅提示工作区既有的 LF/CRLF 转换。

## 截图证据

### Oracle

- 创建前确认：[oracle-create-ready.png](screenshots/oracle-create-ready.png)
- 创建成功：[oracle-created.png](screenshots/oracle-created.png)
- 建表 SQL：[oracle-create-ddl.png](screenshots/oracle-create-ddl.png)
- 缺陷复现前：[oracle-alter-repro-ready.png](screenshots/oracle-alter-repro-ready.png)
- 错误返回总览：[oracle-alter-repro-after-click.png](screenshots/oracle-alter-repro-after-click.png)
- 修复后按钮布局：[oracle-save-layout-fixed.png](screenshots/oracle-save-layout-fixed.png)
- 修改 SQL 预览：[oracle-alter-sql-preview.png](screenshots/oracle-alter-sql-preview.png)
- 修改回读：[oracle-alter-verified-fixed.png](screenshots/oracle-alter-verified-fixed.png)
- 重命名 SQL 预览：[oracle-rename-sql-preview.png](screenshots/oracle-rename-sql-preview.png)
- 失败反馈：[oracle-failure-feedback-final.png](screenshots/oracle-failure-feedback-final.png)
- 失败后一致性：[oracle-failure-consistency.png](screenshots/oracle-failure-consistency.png)
- 删除确认：[oracle-drop-confirm.png](screenshots/oracle-drop-confirm.png)
- 删除后确认：[oracle-drop-verified.png](screenshots/oracle-drop-verified.png)

### DM

- 创建前确认：[dm-create-ready.png](screenshots/dm-create-ready.png)
- 创建成功：[dm-created.png](screenshots/dm-created.png)
- 建表 SQL：[dm-create-ddl.png](screenshots/dm-create-ddl.png)
- 修改 SQL 预览：[dm-alter-sql-preview.png](screenshots/dm-alter-sql-preview.png)
- 修改回读：[dm-alter-verified.png](screenshots/dm-alter-verified.png)
- 重命名 SQL 预览：[dm-rename-sql-preview.png](screenshots/dm-rename-sql-preview.png)
- 重命名回读：[dm-rename-verified.png](screenshots/dm-rename-verified.png)
- 失败反馈：[dm-failure-feedback.png](screenshots/dm-failure-feedback.png)
- 失败后一致性：[dm-failure-consistency.png](screenshots/dm-failure-consistency.png)
- 删除确认：[dm-drop-confirm.png](screenshots/dm-drop-confirm.png)
- 删除后确认：[dm-drop-verified.png](screenshots/dm-drop-verified.png)
