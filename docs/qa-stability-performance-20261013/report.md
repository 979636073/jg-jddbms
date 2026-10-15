# 稳定性与加载性能验收 · 20261013-r2

## 结论与范围

本轮完成两个已复现问题的修复：新安装 SQL 的公告 BLOB 写入错误，以及 DM/Oracle 外键元数据读取的逐字段重复查询。未新增业务模块，未重做界面，外部客户端 DMP 互导继续暂缓。

自动化、独立实库安装升级、400 表规模 JDBC 验证已通过。登录后的容器页面验收尚未完成：用户提供的 cURL 登录态对 38080 容器返回 401，已请求更新。不得将本报告理解为所有功能、现场性能或所有数据库版本均已验收。

## 本轮修改

| 内容 | 原问题 | 本轮处理 |
|---|---|---|
| 空库安装 | SYS_NOTICE.NOTICE_CONTENT 为 BLOB，中文字符串直接插入触发 DM“无效的十六进制数据”，第 490 条 SQL 失败 | 两条公告种子内容改为 UTF-8 字节的 HEXTORAW，保持中文内容不变 |
| 外键读取 | 元数据查询已返回 STATUS，却按每个外键字段再次查询状态 | 复用当前结果集，保留 ENABLED→VALID、其他状态→INVALID 映射 |
| CI | 前端核心回归没有纳入工作流 | lint 后执行 npm run test:core；本地已通过，未推送，远端 CI 尚未运行 |
| 产物标识 | 安装包与运行容器缺少统一版本摘要 | 新增 VERSION.json，记录版本、构建时间、Git 基点、未提交状态及 Jar/入口 HTML/两份 SQL 的 SHA-256 |

新安装请使用 r2 初始化脚本，不再使用 r1 初始化 SQL。现有业务元数据库本轮未重新初始化，也未执行升级脚本。

## 自动化结果

- 前端 `npm run test:core`：23 项通过。
- 后端 `mvn -B -pl jd-dbms-admin -am package`：隔离测试 home 后 BUILD SUCCESS；各模块汇总 302 项，263 通过、39 个可选实库测试跳过，0 失败、0 错误。
- 首次全套执行有 1 个错误：本机原生服务占用用户 home 下的 Ehcache 持久化目录。未关闭服务或删除锁，使用 `-Duser.home=D:/code/jddbms-windows-local-20261013/test-home/r2` 后通过。
- 新增外键回归：DM/Oracle 各一项；修复前均能失败，修复后通过。联合外键字段、启用/禁用状态正确，元数据读取只执行一条 SQL。
- 实库规模回归：DM、Oracle 各一项通过，另外验证禁用外键返回 INVALID。
- `git diff --check` 通过；存在原有 CRLF 提示，不属于测试失败。

日志位于 `D:/code/jddbms-windows-local-20261013/logs/`：

- `release-r2-full-test-20261013.log`：首次缓存锁冲突。
- `release-r2-full-test-isolated-20261013.log`：隔离后完整构建。
- `foreign-key-before-20261013.log`：修复前回归失败证据。
- `workspace-performance-before-20261013.log`、`workspace-performance-after-20261013.log`：实库前后对照。
- `workspace-loading-before-DM.json`、`workspace-loading-before-ORACLE.json`、`workspace-loading-after-DM.json`、`workspace-loading-after-ORACLE.json`：分项耗时。

## 安装、旧结构修复与重复升级

最终分发目录 `D:/code/jddbms-releases/jg-jddbms-linux-arm64-20261013-r2/sql` 已重新实测，不仅验证源 SQL 分片。

1. 建立随机命名、独立拥有的 DM 用户/模式，授予 RESOURCE。
2. 执行完整初始化：603 条 SQL 通过；检查表、admin、中文菜单及公告 BLOB 字节回读。
3. 写入账号、密码、数据源保护样本并记录用户角色/角色菜单关系。
4. 删除隔离模式中特定兼容字段和表，模拟旧结构。
5. 连续执行升级两次，每次 63 条 SQL 通过；结构补齐且样本与关联关系不变。
6. 清理本轮随机用户/模式，确认已不存在。

最终日志：`install-sql-r2-final-20261013.log`。这是 JDBC 执行验证，不代替 DIsql 客户端编码验收，也不是从真实历史整库备份升级的演练。未修改现有 JDDBMS 业务数据。

## 400 表规模性能

每个数据库独立建立 400 张随机前缀 QA 表：普通表 60 列，宽表 100 列，1,592 个外键，宽表 10,001 行，含 1 MiB BLOB 与中文 CLOB。测试结束后仅清理本轮实际创建的对象，DM/Oracle 各 400 表已清理。

范围是本机直连 JDBC，包括插件元数据方法和测试中的数据分页查询，不含 HTTP 鉴权、业务服务完整链路、浏览器渲染、现场网络或物理 ARM 硬件；数据分页样本未读取 LOB 正文。

| 指标 | DM | Oracle |
|---|---:|---:|
| 4 外键元数据 SQL 数：修复前→后 | 5→1 | 5→1 |
| 对应一次元数据读取耗时：前→后 | 639.18→445.24 ms | 1110.72→619.11 ms |
| 修复后 200 表分页，3 次 | 310.67 / 313.37 / 293.57 ms | 145.29 / 40.64 / 47.70 ms |
| 修复后 100 列元数据 | 506.68 ms | 88.30 ms |
| 修复后 50 行数据页，排除 LOB 正文 | 4.63 ms | 12.27 ms |

外键样本耗时分别下降约 30.3%、44.3%；单次对照有波动，不能外推为页面整体提速比例。已覆盖表分页、第二页、名称搜索、宽字段、外键映射、万行总数，不宣称完成模式加载、排序/筛选所有组合或 LOB 在线读取的端到端压测。

## 部署和回退

目标版本为 `20261013-r2`。本轮应用更新保留现有数据卷、元数据库连接与数据源加密密钥，不清理旧版本包。Docker 离线包与原生 ARM64 包使用同一份 Jar、前端资源及两份 SQL。

- `jddbms-arm64-backend`、`jddbms-arm64-frontend` 已更新到 r2，二者和原 Redis 均 healthy；Redis 没有重建。
- 镜像平台已核验为 linux/arm64。
- 后端 `/app/VERSION.json` 为 r2，Jar、前端 index.html、两份 SQL 的 SHA-256 与安装包完全一致。
- 后端 `/app/sql/` 仅包含 `JDDBMS_metadata_dm.sql`、`JDDBMS_upgrade_dm.sql`。
- 持久化数据源加密密钥、Token 密钥摘要与更新前一致，实际密钥未写入本报告或安装包。
- 38080 首页 HTTP 200、代理验证码接口 code 200；[浏览器记录](browser-report.md)覆盖登录页、空值校验与浏览器错误检查。
- 离线 Compose 配置语法检查通过；归档目录没有实际 `.env`、数据库备份或数据卷，仅提供 `docker.example.env`。
- 本轮未替换仍运行的 Windows 原生部署，因此两个入口的应用版本可能不同。r2 本轮验证入口为 `http://127.0.0.1:38080/`。
- 已保留 `jddbms-arm64-backend:rollback-20261013-r2` 与 `jddbms-arm64-frontend:rollback-20261013-r2`，对应更新前 r1。仅准备了可回退镜像，本轮未实际切回演练。

产物位于 `D:/code/jddbms-releases/`：

| 产物 | 字节数 | SHA-256 |
|---|---:|---|
| jg-jddbms-docker-arm64-20261013-r2.tar.gz | 241823579 | 28f7e758dbe87c4d936a54229a89fa82161710490b417ee517c109ed3648bfc0 |
| jg-jddbms-linux-arm64-20261013-r2.tar.gz | 150441922 | a557fbd32c5861ab3de72692b27108caa901bde3035bdea76951722b7efd48c3 |

同目录提供 `.sha256` 校验文件。离线镜像包含前端、后端、Redis，不包含达梦元数据库服务。工作区基点 `ba3455ebf75474bcc52c9b83fd83c17abac3e7af`，`includesUncommittedChanges=true`；本轮没有 Git 提交或推送。

## 后续验收边界

1. 用户更新容器登录态后，补做资源树、模式/表打开、窗口切换、排序/高级查询及对象管理的页面回归，采集 HTTP 与页面耗时。
2. 再按已测瓶颈决定是否优化模式目录与数据页业务链路，不以增加缓存或批量预加载作为默认方案。
3. 补真实旧库备份升级、数据库恢复、应用回退演练；本轮独立样本升级不代替这些验收。
4. 需要固定代码版本时另行提交/推送。当前包包含未提交改动，VERSION.json 的 Git 基点不是本轮完整源码提交。
5. 外部客户端 DMP 互导不在本轮范围。
