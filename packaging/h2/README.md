# 达梦元数据库迁移到 H2

仅迁移系统元数据 `JDDBMS`；用户管理的达梦/Oracle 数据库不会被转换。导出 SQL、H2 文件、认证请求文件均含敏感信息，请保存在受保护的本机目录，不要提交 Git 或放进公开安装包。

1. 备份旧达梦库和旧安装目录，保留原 `JDDBMS_TOKEN_SECRET`、`JDDBMS_DATASOURCE_ENCRYPTION_KEY`。尽量在无人操作的维护窗口导出：导出脚本逐表检查前后行数，但不能为通过 HTTP 的多次查询提供整库事务快照。
2. 用已登录旧系统的 `Copy as cURL` 文件运行 `export-live-metadata.ps1`。指定旧系统后端/代理地址、元数据连接 ID 和空的输出目录，例如：

   ```powershell
   .\export-live-metadata.ps1 -TokenFile 'C:\private\request.txt' -BaseUrl 'http://127.0.0.1:28080' -DataSourceId 208 -OutputDirectory 'D:\private\jddbms-export'
   ```

3. 使用与项目一致的 H2 2.2.224 Jar，把导出数据导入**新的**目标目录：

   ```powershell
   .\import-h2.ps1 -H2Jar 'C:\private\h2-2.2.224.jar' -DataSql 'D:\private\jddbms-export\JDDBMS_data_h2.sql' -Manifest 'D:\private\jddbms-export\JDDBMS_data_h2_manifest.json' -TargetDirectory 'D:\private\jddbms-h2'
   ```

   导入脚本使用项目的 H2 表结构，逐表核对行数及清单中的 BLOB 字节数；全部通过才写入初始化标记。已有 `jddbms.mv.db` 时会拒绝覆盖。
4. 将通过校验的 `jddbms.mv.db` 放到安装目录 `data/h2/`，配置原令牌与数据源加密密钥，在隔离端口启动 `druid,h2` 配置。验证原账号登录、权限、数据源列表与实际连接、任务后，再停止旧服务并切换。原达梦库保留以便回退。

若旧数据源加密密钥丢失，迁入的连接密码将无法解密。不要用新密钥直接覆盖配置；先保留旧服务与迁移文件，重新取得原密钥或逐个重录连接密码。
