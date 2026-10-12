# JDDBMS 麒麟 ARM64 安装包（沿用旧包目录布局）

本包以 `E:\code\dbms` 的安装目录结构为模板：根目录保留 `jd-dbms-admin.jar`，前端在 `dist/`，Redis 在 `redis/bin/`，驱动在 `assets/jar/`，并保留模板的 ARM64 `ssh/` 工具。Jar、前端和 Redis 已换为当前版本；元数据库初始化 SQL 在 `sql/JDDBMS_metadata_dm.sql`。

旧包的 `application.yml`、`authorize.properties`、日志、备份、上传数据和旧服务脚本没有复制进来；它们可能包含现场配置或指向固定路径。配置统一放在本包的 `config/local.env`。本包不包含达梦数据库、Java 或 Nginx 可执行文件。

## 首次安装

1. 在麒麟 V10 AArch64 系统准备 Java、Nginx 和 curl。Nginx 若未加入 `PATH`，在配置中填写其绝对路径，例如 `JDDBMS_NGINX_BIN='/home/dbms/nginx/sbin/nginx'`。
2. 仅在全新、空的达梦 `JDDBMS` 模式中执行 `sql/JDDBMS_metadata_dm.sql`。已有元数据库不能重新执行整份初始化脚本。
3. `cp config/local.example.env config/local.env`，填写元数据库、令牌和数据源密钥等参数；执行 `chmod 600 config/local.env`。迁移已有数据时须使用原有数据源加密密钥。
4. 在解压后的根目录执行 `bash start.sh`，再执行 `bash status.sh`。也可使用兼容入口 `bash bin/start.sh` 和 `bash bin/stop.sh`。

## 端口和密码

只修改 `config/local.env`，例如：

```bash
JDDBMS_REDIS_HOST='127.0.0.1'
JDDBMS_REDIS_PORT='26379'
JDDBMS_REDIS_PASSWORD='请替换为自己的密码'
JDDBMS_BACKEND_PORT='28081'
```

本包的示例配置已将 Redis 端口设为旧模板使用的 `26379`，后端端口设为你指定的 `28081`。启动脚本会按这些值启动本机 Redis 和后端，并生成 Nginx 转发配置。前端默认监听 `18081`。使用外部 Redis 时，将 `JDDBMS_REDIS_HOST` 改为外部地址，脚本不会启动包内 Redis。修改端口或密码后，先执行 `bash stop.sh`，再执行 `bash start.sh`。

若已运行旧包，请先在**旧安装目录**停止旧服务，再启动此包。不要将旧包的 `application.yml` 直接覆盖到这里。

## 可选 systemd 服务

在确认手动启动正常后，可使用 `sudo bash bin/install-service.sh install` 安装单个 `jddbms.service`；移除时使用 `sudo bash bin/install-service.sh uninstall`。此服务会调用本包的启停脚本，不使用旧模板中硬编码路径的服务文件。

Redis 二进制为静态链接的 AArch64 7.2.16；源码许可见 `redis/REDIS-LICENSE`。`ssh/` 中的辅助程序沿用旧模板，需在现场另行确认其运行依赖。
