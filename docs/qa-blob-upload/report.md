# BLOB 字段上传响应性回归

## 问题记录

- 记录时间：2026-09-27 13:28:23 +08:00
- 环境：DM / `JQDMO_260926.QA_IO_P4` / 数据编辑器
- 文件：`fixtures/blob-16mb.bin`（16 MiB）
- 操作：双击已有 BLOB 单元格，点击“上传 / 替换”并选择文件。
- 实际结果：文件选择后数据进入待保存状态，但读取/处理期间无进度、忙碌状态或“已选择”反馈，上传控件仍可重复操作；用户只能等到最终提示。
- 期望结果：选择文件后立即显示处理中状态，处理中禁止重复上传，完成后明确显示文件已就绪；失败时恢复控件并提示原因。
- 证据：`screenshots/01-before-upload.png`、`screenshots/03-after-selection.png`。

## 修复与回归

### 根因

1. BLOB 文件读取期间没有加载状态，用户只能等待文件读取和 Base64 转换结束。
2. 大请求拦截器通过 `Object.keys(JSON.stringify(requestObj))` 计算请求体积，会为 Base64 字符串创建海量字符索引，造成主线程停顿和额外内存占用。
3. 点击保存前会把整张结果表（包含 Base64 BLOB）复制并写入 `localStorage`；64 MiB 用例稳定触发 `QuotaExceededError`，保存请求因此被中断。
4. 已进入 `sqlInfo` 的 BLOB 变更仍会在每次界面渲染时重复比较，放大大字段操作时的卡顿。

### 处理

- 文件读取期间显示“读取中…”，禁用重复上传；完成后提示文件已读取并等待保存。
- 保存入口先渲染“保存中…”，再执行大字段检查和请求，完成或失败后恢复按钮。
- 大请求体积改为按字符串长度计算，不再生成字符键数组。
- 移除单元格提交时已失去用途的整表 `localStorage` 快照；BLOB 编辑不再重复提交单元格快照。
- 待保存摘要优先使用 `sqlInfo`，不重复遍历比较已经登记的 BLOB 值。

### 验收结果

- 64 MiB 文件读取：观察到按钮在 196 ms 时进入“读取中…/禁用”，403 ms 时恢复；读取完成提示明确。
- 16 MiB 文件保存：观察到“保存中…”状态，最终恢复按钮并显示“无待保存变更”。
- 数据库落库核验：`ID=1` 的 BLOB 长度为 `67108864` 字节，`ID=2` 的 BLOB 长度为 `16777216` 字节。
- 页面控制台：最终回归无 `QuotaExceededError`；仅保留大于 5 MiB 时跳过防重复提交校验的预期警告。
- 自动化：`node tests/table-editor-ui.test.js` 通过。
- 构建：`npm run build` 通过；项目原有 62 条 CSS 顺序警告未增加。
- 测试环境：前端 Docker 镜像完整构建成功，容器已由新镜像重建并通过健康检查；临时使用的锁文件副本已移除。

### 证据

- 修复前：`screenshots/01-before-upload.png`、`screenshots/03-after-selection.png`。
- 保存中反馈：`screenshots/13-immediate-save-feedback.png`。
- 保存完成：`screenshots/14-final-complete.png`。

结论：BLOB 字段上传与保存的响应性问题已修复并通过现场回归。
