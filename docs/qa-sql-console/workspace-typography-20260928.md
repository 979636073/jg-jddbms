# 工作区字号统一验收（2026-09-28）

## 调整范围

- 以左侧资源树的 12px 为基准，左右共用工作区字号变量。
- 右侧按钮、页签、输入框、表格、分页、SQL 上下文和编辑器统一为 12px。
- 保留标题和图标层级、蓝白配色及既有布局；没有使用缩放实现。

## 验证结果

- 修改前实测：左树 12px；按钮、页签、连接名和 SQL 编辑器 14px；上下文标签、模式框和分页 13px。
- 修改后实测：上述控件均为 12px。
- Oracle SQL 控制台执行 `SELECT 'FONT_QA' MARKER FROM DUAL;` 成功返回 `FONT_QA`。
- 模式表概览和 QA_EDIT 表数据正常展示，表头、单元格、分页字号均为 12px。
- 截图检查没有文字裁切，布局及左树选中状态正常。
- 15 个前端测试文件通过，生产构建通过。
- 本次验证未修改业务数据，未涉及导入导出功能。

## 部署

- 仅更新 ARM64 前端容器，后端和 Redis 未重启。
- 保留回退镜像：`jddbms-arm64-frontend:rollback-20260928-before-typography`。

## 截图

- [修改前](screenshots/workspace-type-before.png)
- [SQL 控制台](screenshots/workspace-type-sql.png)
- [表数据页](screenshots/workspace-type-table.png)
