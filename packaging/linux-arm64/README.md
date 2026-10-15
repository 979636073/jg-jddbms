# jg-jddbms Linux ARM64 原生运行包

包内程序为 ARM64 静态版本：Nginx 1.30.5、Redis 7.2.16（包含 redis-server 和 redis-cli），附带许可证文件。

系统账号、权限、数据源配置和任务等元数据由安装目录下的 `data/h2/jddbms.mv.db` 保存。全新安装首次启动时自动初始化 H2，不需要部署达梦元数据库或手工执行 SQL。被管理的达梦、Oracle 等数据库仍按各自的数据源配置连接。

## 安装与启动

1. 在提供 Bash 和基础系统命令的 aarch64/ARM64 Linux 上准备 Java 8 或更高版本（JDK），并确保 `java` 可在 `PATH` 中找到。除此之外不需安装任何应用服务：Nginx 和 Redis 的 ARM64 静态程序已随包提供，H2 在应用内置，也不依赖 curl 或系统安装的 Nginx/Redis。
2. 全新安装可以直接执行 `bash start.sh`：自动创建 `config/local.env`，自动生成密钥并保存至 `data/.secrets/runtime.env`（目录权限 700、文件权限 600）。日常配置不需要填写密钥；重启沿用原文件，不会重新生成。
3. 如需自定义端口，先复制 `config/local.example.env` 为 `config/local.env`，调整 `JDDBMS_BACKEND_PORT`、`JDDBMS_REDIS_PORT`、`JDDBMS_FRONTEND_PORT`；H2 数据目录由 `JDDBMS_DATA_DIR` 指定。修改后先 `bash stop.sh` 再 `bash start.sh`，代理地址自动同步。使用 `bash status.sh` 检查，访问 `http://<本机IP>:<前端端口>/`（默认 18081）。全新初始化的管理员为 `admin/123456`，首次登录后请修改密码。

应用、Redis 和上传文件默认存储在当前安装目录下；不要将 `data/` 放进公开下载目录。H2 默认位于本包的 `data/h2/`，以 `druid,h2` 配置启动。不要同时启动两个使用同一个 H2 文件的后端进程。左侧树中的业务数据库连接仍在系统界面新增和维护，与平台元数据库分开。

## 升级与密钥保留

升级必须同时保留原 `data/`（尤其 `data/.secrets/`）与 `config/local.env`。旧包将密钥放在配置文件时，首次运行新脚本会将原密钥保存到受保护文件；确认启动成功后可以移除配置文件中的密钥项。如果已有 H2 数据但原密钥丢失，脚本会拒绝生成新密钥并提示恢复备份，避免把已有数据源密码变成不可解密的状态。

## 配置外部元数据库

平台元数据库支持内置 H2（默认）和项目原有达梦适配。编辑 `config/local.env`：

```bash
JDDBMS_META_DB_TYPE='dm'
JDDBMS_META_DB_URL='jdbc:dm://数据库地址:5236/JDDBMS'
JDDBMS_META_DB_USERNAME='JDDBMS'
JDDBMS_META_DB_PASSWORD='填写数据库密码'
```

执行 `bash stop.sh` 后再 `bash start.sh` 生效。外部库须已安装本版本平台表结构和数据；启动不会创建、迁移或覆盖外部数据库，也不会初始化 H2。保留 H2 文件，改回 `JDDBMS_META_DB_TYPE='h2'` 后即可使用原 H2 数据。两种存储的数据不会自动同步。

接入原有元数据库时，必须同时带回原 `data/.secrets/runtime.env`，或者在首次启动前通过旧配置提供原数据源加密密钥；否则已有数据源密码无法解密。数据库密码仍需配置并保护 `config/local.env`，它与应用自动管理的加密密钥不同。此处配置的是平台元数据库，不是左侧树中的业务数据源。其他外部数据库尚未适配，不能仅更改 JDBC 地址使用。

## 旧达梦元数据库迁移

升级前先备份原达梦数据库、现有安装包以及令牌/数据源加密密钥。迁移脚本位于源码的 `packaging/h2/`，用于在旧服务仍可访问时导出元数据，并核对逐表行数和 BLOB 长度。迁移后的 `jddbms.mv.db` 应放入新包的 `data/h2/`；不要在已有 H2 文件上再次导入。先在隔离端口验证登录、角色、数据源连接和任务，再停止旧服务并切换流量。原达梦库在验收完成前保持只读备份，不要删除。数据源密码保留密文，必须使用原来的 `JDDBMS_DATASOURCE_ENCRYPTION_KEY` 才能解密。

本包不含现场数据、密钥和外部 DMP 客户端，也不再附带达梦元数据库初始化 SQL。若原密钥丢失，旧数据源密码不能直接恢复，需要在系统内逐个重新填写；不要把随机新密钥用于未处理的迁移数据。

## 备份与恢复

先执行 `bash stop.sh`，确认后端进程已经退出，再复制 `data/h2/jddbms.mv.db`（自定义目录时复制对应文件）、`data/.secrets/`、`data/redis/`、`data/uploadUrl/`、`config/local.env` 和任务文件目录。恢复时先停止服务，使用同一安装版本和原密钥放回这些文件，再启动。运行中的 H2 文件不要直接复制作为一致性备份。

日志位于 `logs/`。`config/local.env` 可调整三个服务端口、Redis 地址/认证、H2 数据目录和 Java 内存。程序固定使用 `nginx/nginx`、`redis/redis-server` 和 `redis/redis-cli`，不依赖 `JDDBMS_NGINX_BIN` 或系统安装的 Nginx/Redis。前端默认端口为 `18081`，后端默认端口为 `28080`，Redis 默认端口为 `6379`，后两者默认仅监听本机。`VERSION.json` 包含构建时间、Git 基点、是否包含未提交改动及应用与包内程序 SHA-256；安装前请核对压缩包的 `.sha256` 文件。
