# Oracle 分页、宽表及 LOB 回归 · r9

| 项目 | 内容 |
|---|---|
| 日期 | 2026-10-13 |
| 页面 | Windows 本机 `http://127.0.0.1:38180/`，连接 `JQORO_260926` |
| 范围 | Oracle 10,001 行分页、排序、宽表、外键、1 MiB 级 LOB；ARM64 客户端可用性核查 |

## 结果

- `QR9_PAGE` 10,001 行：默认 100 条/页、10 条/页、200 条/页均能读取。10 条/页末页 1001 和 200 条/页末页 51 均仅有 ID=10001；200 条/页连续翻页 1→2→3→4 的 ID 区间依次为 1–200、201–400、401–600、601–800。ID 倒序第 1 页为 10001–9802，第 2 页为 9801–9602。[倒序第 2 页](screenshots/oracle-sorted-page2.png)
- `QR9_WIDE` 包含主键、有效外键、35 个普通字段及 CLOB/BLOB。SQL 控制台实库核对 CLOB=1,048,576 字符、BLOB=1,025,000 字节；表数据刷新返回 1 行，无浏览器脚本错误。[宽表页面](screenshots/wide-lob.png) · [长度核对](screenshots/lob-lengths.png)
- 初始 `USER_OBJECTS` 中 `QR9_%` 为 0；结束前先删除子表再删除父表，均使用 `PURGE`，最终 `REMAINING=0`。[清理校验](screenshots/qa-cleanup.png)；[可重建样本](oracle-fixture.sql)。

## ISSUE-001：外键约束存在，但“关联关系”为空

- 严重程度：Medium；分类：功能；状态：**本地更新后接口回归未通过**，子表向父表的外键关联仍未展示。
- 复现：打开 `QR9_WIDE` → “约束”页可见 `QR9_WIDE_FK`，状态 `VALID`、列 `PARENT_ID` → “关联关系 → 外键关联”却显示“暂无数据”，点击刷新后仍为空。[有效约束](screenshots/issue-001-constraint-exists.png) · [空关联关系](screenshots/issue-001-association-empty.png)
- 已修正 `TableServiceImpl.queryReferenced` 调用 `getUnForeignKey` 的模式名/表名顺序及 Oracle 子表/父表、关联列映射，并去掉无用递归查询、更新缓存键。但页面的 `/api/rdb/table/queryReferenced` 实际调用 `queryReferencedList`（仅查被引用关系），没有调用上述出向外键方法，因此修复尚未覆盖页面路径。
- 录像工具缺少 ffmpeg，只有步骤截图，不声称有复现视频。旧进程的密钥和元数据库配置在本机进程内存中临时继承到新进程，未输出或持久化；后续独立重启仍需安全配置来源。

## 其他观察与边界

- 选中 SQL 控制台窗口后点击左树“数据表”分组，右侧会变为表列表，但窗口标题仍显示“新建查询”；切回该窗口仍是表列表。[窗口标题与内容不符](screenshots/switch-query.png)。此现象单独记录，尚未修复/复验。
- 宽表数据单元格在 DOM 中包含完整约 1 MiB CLOB 文本。单行样本未观察到卡顿，但不能据此推断多行大 LOB 的页面性能；大规模 LOB 仍需专项压测。
- Oracle 客户端 SQL 执行 DML 后需显式 `COMMIT`，表数据页才能在另一连接中看到新行；本轮已按事务规则提交。
- 找不到获授权的真实 Linux ARM64 达梦/Oracle 客户端包，用户确认目前没有；该项**未验收**，Windows 客户端测试不能替代。

## 构建与交付状态

- Java 全量 Maven reactor 测试通过：业务模块 228 项，0 失败、0 错误、43 跳过（包含未启用的可选实库测试）；最初未隔离测试缓存目录时遇到运行中 Ehcache 锁，使用独立 `user.home` 重跑通过。
- 另外新增并运行 Oracle 出向外键映射回归：`ForeignKeyLoadingTest` 3 项通过，断言子/父模式、表、列方向及 SQL 过滤条件；新增测试晚于上述全量运行，不把它重复计入 228 项。
- 前端核心回归 27 项通过；生产构建成功，保留既有 Monaco CSS 顺序警告。
- 已生成后端候选包 `D:/code/jddbms-windows-local-20261013/qa-r9/jddbms-r9.jar`，SHA-256 `62f447e95aa28c69fa896c2837e972a29eff5f7b02092fb34b722bbfe87fd5b2`。2026-10-13 23:28 已替换 Windows 本机运行包，原包备份于 `D:/code/jddbms-windows-local-20261013/backup-before-r9-20261013-232847/jddbms.jar`。
- 已生成**未发布**的 ARM64 原生安装包候选 `D:/code/jddbms-releases/jg-jddbms-linux-arm64-20261013-r9-qa1.tar.gz`，SHA-256 `47514688ac998d4ab557245539db81faf2f201d3d43494d0a8bd5039f4ba770e`。不含外部 DMP 客户端；尚未做新包安装/恢复演练。
- 容器仍为旧版本，未更新前后端和数据库；旧库恢复/回退演练尚未执行。外部客户端 DMP 互导仍按用户要求暂缓。

## Windows 本地更新后回归（2026-10-13）

- 新后端进程监听 `127.0.0.1:38181`；登录态 `/getInfo` 与前端 `http://127.0.0.1:38180/` 均返回 HTTP 200。前端 `web/index.html` 与本次构建产物 SHA-256 相同，本轮无需替换前端。备用端口验证进程已停止。
- 已保存数据源读取正常：达梦 `SYSDBA` 表列表返回总数 10009、前 5 条；Oracle `JQORO_260926` 返回总数 2、前 2 条。这验证了新进程能读取元数据库、解密并连接现有数据源。
- 在专用 Oracle QA 模式创建 `QR10_PARENT` 和带 `QR10_CHILD_FK` 外键的 `QR10_CHILD`。父表的 `/queryReferenced` 正确返回子表及 `ID`/`PARENT_ID` 关系；子表查询返回空，出向关联未通过。两表已按子→父顺序 `DROP ... PURGE`，`USER_OBJECTS` 剩余 0。
- 未在本轮重建 10001 行分页样本，也未完成浏览器页面级关系回归；不能据此宣称所有功能验收通过。
