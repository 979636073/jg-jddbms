# SQL 查询结果默认展示验收

## ISSUE-001：成功查询默认停留在消息汇总

- 严重程度：中
- 状态：通过
- 影响：SQL 已成功返回数据，但用户必须再点击“消息1”才能看到结果表格，容易误判为查询没有返回数据。
- 复现：执行成功的 `SELECT` 后，结果区域默认选中“消息合计”。
- 期望：存在成功的 `SELECT` 时，自动展示第一个成功查询的结果；仅执行 DML/DDL 时继续展示消息汇总。
- 修复：执行完成后，在成功结果列表中定位第一个 `SELECT` 并将其设为当前结果。

## 验收证据

- 修复前默认显示消息汇总：[`oracle-source-lob-query.png`](../qa-phase4/screenshots/oracle-source-lob-query.png)
- 手动点击“消息1”后数据完整显示：[`oracle-result-after-message-click.png`](../qa-phase4/screenshots/oracle-result-after-message-click.png)
- Oracle `SELECT` 默认展示结果：[`oracle-select-default.png`](screenshots/oracle-select-default.png)
- DM `SELECT` 默认展示结果：[`dm-select-default.png`](screenshots/dm-select-default.png)
- 混合执行默认展示首个成功查询：[`oracle-mixed-select-default.png`](screenshots/oracle-mixed-select-default.png)
- 仅 DML 时默认展示消息汇总：[`oracle-dml-summary-default.png`](screenshots/oracle-dml-summary-default.png)
- 浏览器录屏未生成：当前环境缺少 ffmpeg，已使用静态截图记录交互前后状态。

## 回归范围

- 成功 `SELECT` 自动展示结果集。
- 混合执行时，下标对应过滤后的成功结果列表。
- 仅 DML/DDL 时保留消息汇总默认视图。

## 回归结果

- `node tests/query-result-default-ui.test.js`：通过。
- 前端生产构建：成功；仅有项目既存的 Monaco CSS 顺序及资源体积警告。
- 部署镜像：`sha256:168a1aad90be12523c3d8b5c679e1bbf7fd328438f657345a41d22e8427ac2d2`；前端容器健康。
- Oracle `SELECT 1 AS QA_VALUE FROM DUAL`：结果网格默认展示，值为 `1`。
- DM `SELECT 2 AS QA_VALUE`：结果网格默认展示，值为 `2`。
- Oracle 混合执行 `0` 行更新后再查询：默认选中第二个结果并展示 `QA_VALUE=3`。
- Oracle 仅执行 `0` 行更新：默认保留“消息合计”；执行后已回滚。
- 回滚后 `JQORO_260926.QA_IO_P4` 仍为 `2` 行，数据量未变化。
- 浏览器终验过程中无 JavaScript 运行时错误。
