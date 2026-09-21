# 项目代码检索约定

- 本项目已启用 CodeGraph，索引目录为 `.codegraph/`。
- 任何代码搜索、定位符号或分析调用关系前，必须先在项目根目录执行 `codegraph sync`，确保索引是最新的。
- 同步后优先使用 `codegraph explore "问题或符号名"`；按需使用 `codegraph query`、`codegraph node`、`codegraph callers`、`codegraph callees` 和 `codegraph impact`。
- 不要直接使用 `grep`、`find` 或其他文本搜索替代 CodeGraph；只有 CodeGraph 无法覆盖的非代码文件或索引故障排查才可使用这些工具。
