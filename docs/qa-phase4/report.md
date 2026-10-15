# 第四阶段导入、导出与 LOB 数据安全验收

| 字段 | 值 |
| --- | --- |
| 日期 | 2026-09-26 |
| 应用地址 | `http://localhost:8080/workspace` |
| 浏览器会话 | `jddbms-phase2`（延续隔离验收会话） |
| 范围 | DM/Oracle 隔离账号内的数据导入、导出、LOB、失败处理与数据一致性 |
| 状态 | 验收执行完毕；DM 通过，Oracle LOB 通过，Oracle DMP 被环境依赖阻塞 |

## 验收结论

第四阶段不能判定为“全部通过”。DM 的 DMP 导出、重映射导入和 LOB 一致性已形成完整闭环；Oracle 的 CLOB/BLOB 写入与精确回读通过，但 Oracle DMP 导入/导出无法在当前 Linux 后端完成，因为运行环境没有 `exp`、`imp`，仓库中也没有可供 Linux 容器使用的 Oracle 客户端工具。

本轮发现的应用代码缺陷已修复：Linux 下 DMP 子进程不再固定使用无权限的 `/root`，改为使用应用配置的可写目录 `dbms.profile`。由于约束是不重启容器，当前运行实例未加载新 JAR；现场 DM 回归通过向现有测试容器补入 DM 工具并给予旧进程访问 `/root` 的最小执行权限完成，代码修复本身由自动化测试验证。

## 安全边界

- 仅使用 `JQDMO_260926`、`JQDM4_260926`、`JQORO_260926` 及本阶段新建的可丢弃对象。
- 不修改或删除既有系统对象、业务对象及前三阶段保留的验证对象。
- 未重启任何容器。
- 验收对象和证据保留，便于后续补齐 Oracle 工具后复测。

## 验收矩阵

| 验收项 | 结果 | 关键证据 |
| --- | --- | --- |
| DM 用户级 DMP 导出 | 通过 | 后台任务于 `19:51:40` 完成；导出日志显示 `QA_IO_P4` 共 2 行，文件 `jqdmo-260926.dmp` 为 6093 字节。见 [导出选择](screenshots/dm-user-export-selected.png)、[任务成功](screenshots/dm-user-export-success.png)、[DMP 文件](jqdmo-260926.dmp) |
| DM DMP 用户重映射导入 | 通过 | `JQDMO_260926` 重映射到隔离用户 `JQDM4_260926`，后台任务于 `19:57:45` 完成。见 [导入配置](screenshots/dm-import-ready.png)、[二次确认](screenshots/dm-import-confirm.png)、[任务成功](screenshots/dm-import-success.png) |
| DM CLOB/BLOB 数据一致性 | 通过 | 导入后 2 行；CLOB 总长度 14、BLOB 总长度 7；空 CLOB/BLOB 各 1 行；中文和引号文本保持为 `中文,comma'quote`；BLOB 保持为 `0001027F80FEFF` |
| Oracle CLOB/BLOB 写入和回读 | 通过 | 隔离表 `JQORO_260926.QA_IO_P4` 写入 2 行；应用接口精确回读得到 CLOB 长度 14、BLOB `0001027F80FEFF`，第二行两个 LOB 均为 `NULL`。见 [查询现场](screenshots/oracle-source-lob-query.png) |
| Oracle DMP 导出 | 阻塞 | 后台任务于 `20:00:41` 失败：`Cannot run program "exp": error=2, No such file or directory`。见 [导出选择](screenshots/oracle-user-export-selected.png) |
| Oracle DMP 导入 | 阻塞 | 容器检查结果为 `imp=missing`；没有可用于生成有效 Oracle DMP 的导出工具，因此不执行伪造文件导入 |
| DMP/LOB 自动化回归 | 通过 | `DatabaseServiceImplDmpTest` 1 项、`DlTemplateBlobTest` 5 项、`DlTemplateServiceImplLobTest` 2 项，共 8/8 通过 |
| 浏览器运行时错误 | 通过 | 本阶段操作结束后 `agent-browser errors` 无输出 |

## 问题与处理

### ISSUE-001：Linux DMP 子进程固定使用 `/root` 导致权限失败

| 字段 | 值 |
| --- | --- |
| 严重级别 | High |
| 类别 | Functional / Deployment |
| 状态 | 已修复并完成代码回归；待下次部署加载新 JAR |

**实际结果**

首次选择 `JQDMO_260926.QA_EDIT` 并提交 DMP 导出后，后台任务返回：`导出失败：Cannot run program "dexp" (in directory "/root"): error=13, Permission denied`。

**处理**

- 将导出和导入两个 `ProcessBuilder` 的 Linux 工作目录从硬编码 `/root` 改为 `dbms.profile`。
- 新增 `DatabaseServiceImplDmpTest`，验证进程工作目录始终跟随应用配置。
- 在不重启容器的约束下，向当前测试容器补入现有 DM Linux 工具并给予旧 JAR 所需的最小目录执行权限，完成真实导出、导入与 LOB 回读闭环。

**复现与回归证据**

1. 首次导出配置：[issue-001-step-1.png](screenshots/issue-001-step-1.png)
2. 首次提交：[issue-001-confirm-visible.png](screenshots/issue-001-confirm-visible.png)
3. 首次失败任务：[issue-001-task-failed.png](screenshots/issue-001-task-failed.png)
4. 修复后 DM 导出成功：[dm-user-export-success.png](screenshots/dm-user-export-success.png)
5. 修复后 DM 导入成功：[dm-import-success.png](screenshots/dm-import-success.png)

### ISSUE-002：Oracle DMP 原生工具缺失

| 字段 | 值 |
| --- | --- |
| 严重级别 | High |
| 类别 | Environment / Release blocker |
| 状态 | 阻塞，需提供 Oracle Linux 客户端工具后复测 |

当前后端容器内 `exp=missing`、`imp=missing`，仓库的 `orcale_tools/linux` 也没有可执行文件。Oracle 用户级 DMP 导出任务真实返回 `No such file or directory`。该问题不能用应用代码或伪造 DMP 文件替代验证。

解除阻塞所需条件：在后端镜像或受控挂载目录中提供与目标 Oracle 版本兼容的 Linux `exp`、`imp` 及其依赖库，并配置现有 DMP 工具路径；随后重复“用户导出 → 重映射导入 → CLOB/BLOB 精确回读”闭环。

## 自动化结果

```text
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 备注

- 视频录制器未能捕获帧，本次以逐步截图、后台任务记录、导出文件和数据库精确回读结果作为证据。
- Oracle 查询控制台当前只显示“执行成功”消息而未绘制结果网格；本阶段通过同一应用接口的认证请求读取并核对返回行。该现象不影响本阶段对 LOB 数据本身的判定，但建议在查询控制台专项验收中单独处理。
