# JDDBMS Docker ARM64 离线安装包 · 20261013-r2

本包包含已构建的 Linux ARM64 后端、前端及 Redis 镜像，以及两份最新元数据库 SQL。需要 Docker Engine 与 Docker Compose v2。达梦元数据库需另行准备；不包含用户数据、现场密码、密钥或外部 DMP 客户端。不支持直接在 x86 Linux 上运行，除非已配置 ARM64 模拟。

## 安装

1. 解压后执行 `docker load -i images.tar`。
2. 复制 `docker.example.env` 为 `.env`，填写数据库地址和密钥，执行 `chmod 600 .env`。容器中的 `127.0.0.1` 不是宿主机地址；本机数据库使用 `host.docker.internal`，远程数据库填写实际地址。
3. 全新、空的 JDDBMS 模式执行 `sql/JDDBMS_metadata_dm.sql`；已有库先备份，再按需要执行 `sql/JDDBMS_upgrade_dm.sql`。两个脚本不要一起执行。SQL 文件为 UTF-8，请使用匹配的客户端编码；遇到错误须停止检查。
4. 执行 `docker compose up -d --wait --wait-timeout 600`，用 `docker compose ps` 查看健康状态。访问 `http://服务器地址:38080/`。

升级现有安装前，备份元数据库、原 Compose 配置以及 `jddbms-arm64-app-data` 和 `jddbms-arm64-redis-data` 数据卷，保留旧镜像。已有数据源加密密钥及历史密钥必须沿用原值。当前包使用与本机部署相同的容器名、网络名和数据卷名，不应与同名实例并行安装。不要执行 `docker compose down -v`，否则会删除数据卷。停止服务使用 `docker compose stop`。

## 本次内容

资源树和视图/物化视图、函数、存储过程、触发器管理改进；表与模式加载优化；迁移类型及 BLOB/CLOB/NCLOB 修复；新建表弹窗内部滚动修复。前后端与原生 ARM64 安装包使用相同产物。

SQL 目录只保留全量初始化和增量升级两份最新脚本，不再分发旧日期脚本或重复分片。升级脚本不重置已有账号密码或角色分配。外部客户端 DMP 互导仍暂缓，不能视为本版已验收功能。

当前本机容器部署只更新应用和 SQL 文件，未在现有元数据库重新执行初始化脚本。容器中的 SQL 位于后端 `/app/sql/`。

r2 修复初始化脚本的公告 BLOB 写入错误，并去除外键元数据的逐字段重复状态查询。新安装不要使用 r1 的初始化 SQL。`VERSION.json` 与后端 `/app/VERSION.json` 记录版本和产物摘要；Git 基点不等同于完整源码提交，`includesUncommittedChanges` 会明确标识工作区改动。SQL 已在独立 DM QA 模式通过完整安装、模拟旧结构修复和两次升级验证，账号、角色关联及数据源保持不变。
