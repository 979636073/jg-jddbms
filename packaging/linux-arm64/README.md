# jg-jddbms Linux ARM64 运行包

本包包含数据库管理平台的 Java Jar、前端静态文件、静态编译的 Linux ARM64 Redis 7.2.16（服务端和客户端）、Linux 启停脚本、Nginx 配置和 **JDDBMS 达梦元数据库初始化 SQL**。适用于 aarch64/ARM64 Linux（包括相应架构的麒麟桌面系统）。本包不包含 Docker、达梦数据库或与发行版绑定的 Java/Nginx 二进制文件。

## 准备

1. 在 ARM64 系统安装 Java 8 或更高版本、Nginx、curl，并确认 `java`、`nginx`、`curl` 在 `PATH` 中。Redis 可直接使用包内版本。导入 SQL 时需要达梦 DIsql 客户端。
2. 准备空的达梦 `JDDBMS` 用户/模式。首次安装时，以该用户运行 `sql/JDDBMS_metadata_dm.sql`。脚本包含 40 张元数据表、索引、基础账号和角色菜单数据；不要在已有 JDDBMS 库上再次执行整份初始化脚本。分步源文件在 `sql/parts/`。
3. 复制 `config/local.example.env` 为 `config/local.env`，填写元数据库地址、用户名、密码、令牌密钥和数据源加密密钥。已有部署迁移时，数据源加密密钥必须沿用原值。执行 `chmod 600 config/local.env`。
4. 在解压后的目录执行 `bash start.sh`，再执行 `bash status.sh` 检查。访问 `http://<本机IP>:18081/`。停止使用 `bash stop.sh`。

达梦 DIsql 连接到 **JDDBMS 用户**后可执行：

```sql
SQL> START /解压后的绝对路径/sql/JDDBMS_metadata_dm.sql
```

`sql/JDDBMS_metadata_dm.sql` 是全新安装脚本，包含建表和初始化数据，不包含当前运行环境中已有的用户、数据源及业务记录。对已有元数据库升级时只执行所需的 `sql/parts/10-...` 至 `12-...` 脚本，并先备份数据库。

## 运行说明

- 在 `config/local.env` 中设置 `JDDBMS_REDIS_HOST`、`JDDBMS_REDIS_PORT`、`JDDBMS_REDIS_PASSWORD`。默认地址和端口是 `127.0.0.1:6379`；密码为空表示不启用密码。例如使用本机包内 Redis 的 `6380` 端口及密码：

  ```bash
  JDDBMS_REDIS_HOST='127.0.0.1'
  JDDBMS_REDIS_PORT='6380'
  JDDBMS_REDIS_PASSWORD='请替换为自己的密码'
  ```

  若使用外部 Redis，将 `JDDBMS_REDIS_HOST` 改为其地址；启动脚本不会再启动本机 Redis。修改本机 Redis 的端口或密码后，先执行 `bash stop.sh`，再执行 `bash start.sh`。`bash status.sh` 会检查配置的地址、端口和认证。
- 后端端口在 `config/local.env` 中设置 `JDDBMS_BACKEND_PORT`，默认 `28080`。例如设为 `28081`：`JDDBMS_BACKEND_PORT='28081'`。`bash start.sh` 会同时更新后端监听端口和 Nginx 转发配置；修改后先执行 `bash stop.sh` 再启动。
- 前端端口：`18081`；后端仅监听本机配置的端口；包内 Redis 仅监听本机配置的端口。
- 如果本机端口已有相应服务，启动脚本会复用服务；停止脚本只处理本包启动时写入 PID 文件的进程。
- 日志在 `logs/`，上传和后台任务文件在 `data/`，Redis 持久化文件在 `data/redis/`。
- 可在 `config/local.env` 中调整 `JDDBMS_JAVA_XMS`、`JDDBMS_JAVA_XMX`。上传达梦或 Oracle 原生客户端时需选择 **Linux ARM64** 版本。
- Redis 源码来自 `download.redis.io` 的 7.2.16 版本；包内的 `redis/REDIS-LICENSE` 为其许可文件。启动脚本优先使用包内 ARM64 Redis，已有本机 Redis 时会复用现有服务。
- 压缩包由 Windows 构建，解压后可用 `bash start.sh` 直接运行；若要直接执行脚本，请先 `chmod +x start.sh stop.sh status.sh`。
