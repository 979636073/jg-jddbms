<script>
import tableServer from "@/api/main/table";
import * as monaco from "monaco-editor/esm/vs/editor/edcore.main";
import "monaco-editor/esm/vs/basic-languages/sql/sql.contribution";
import { language, conf } from "monaco-editor/esm/vs/basic-languages/sql/sql";
import { format } from "sql-formatter";
import { editorDefaultOptions } from "./monacoEditorConfig";
import { sqlAtCursor } from "@/utils/sqlScript";

// Oracle / DM 的 q 引用包含单引号，普通 SQL 着色器会误把后续脚本全部当成字符串。
const SQL_LANGUAGE = "jddbms-sql";
monaco.languages.register({ id: SQL_LANGUAGE });
monaco.languages.setLanguageConfiguration(SQL_LANGUAGE, conf);
monaco.languages.setMonarchTokensProvider(SQL_LANGUAGE, {
  ...language,
  tokenizer: {
    ...language.tokenizer,
    strings: [
      [/[qQ]'\[/, { token: "string", next: "@oracleBracketString" }],
      [/[qQ]'\{/, { token: "string", next: "@oracleBraceString" }],
      [/[qQ]'\(/, { token: "string", next: "@oracleParenString" }],
      [/[qQ]'</, { token: "string", next: "@oracleAngleString" }],
      ...language.tokenizer.strings
    ],
    oracleBracketString: [[/\]'/, "string", "@pop"], [/./, "string"]],
    oracleBraceString: [[/\}'/, "string", "@pop"], [/./, "string"]],
    oracleParenString: [[/\)'/, "string", "@pop"], [/./, "string"]],
    oracleAngleString: [[/>'/, "string", "@pop"], [/./, "string"]]
  }
});

export default {
  name: "index",
  props: {
    dom: String,
    connectionContext: { type: Object, default: () => ({}) },
    schemaOptions: { type: Array, default: () => [] },
    editorConfig: {
      type: Object,
      default: () => {}
    },
    database: {
      type: String,
      default: ""
    },
    columnList: {
      type: Array,
      default: () => []
    }
  },
  data() {
    return {
      editor: null,
      suggestion: null,
      formatProvider: null,
      tableCache: {},
      columnCache: {},
      ownerPageId: this.$route.params.id
    };
  },
  computed: {
    pageId() {
      return this.$route.params.id;
    },
    databaseList() {
      if (this.connectionContext.dataSourceId) {
        return [...new Set([this.connectionContext.schemaName, ...this.schemaOptions].filter(Boolean))];
      }
      let data = this.$store.state.workspaceData.dataSourceData.map(
        item => item.name
      );
      return [...new Set(data)];
    },
    dataCurrentDataMap() {
      return this.$store.state.workspaceData.dataCurrentData;
    },
    currentDataSource() {
      // return this.$store.state.workspace.currentConnectionDetails
      return this.dataCurrentDataMap.get(this.ownerPageId);
    },
    queryContext() {
      return this.connectionContext.dataSourceId ? this.connectionContext :
        (this.currentDataSource && this.currentDataSource.uniqueData) || {};
    },
    dataInfo() {
      return this.$store.state.workspaceData.dataInfo;
    }
  },
  mounted() {
    this.initEditor();
    // document.addEventListener("mousedown", this.mouseDownEditor);
  },
  beforeDestroy() {
    if (this.editor) {
      this.clearMistake();
      this.editor.dispose();
      if (this.suggestion) {
        this.suggestion.dispose();
      }
      if (this.formatProvider) this.formatProvider.dispose();
    }
    document.removeEventListener("mousedown", this.mouseDownEditor);
  },

  methods: {
    mouseDownEditor(e) {
      if (this.editor && !this.editor.hasTextFocus()) {
        setTimeout(() => {
          this.editor.focus();
        }, 0);
      }
    },
    initEditor() {
      this.editor = monaco.editor.create(
        this.$refs.editorHost,
        {
          ...editorDefaultOptions,
          ...this.editorConfig,
          language: SQL_LANGUAGE
        }
      );

      // console.log(this.editor,'111111111');
      this.initSuggestions();
      //自定义文本颜色,也可以不设置，自带也有颜色区分

      // 改写插件自带格式化功能
      const self = this;
      this.formatProvider = monaco.languages.registerDocumentFormattingEditProvider(
        SQL_LANGUAGE,
        {
          provideDocumentFormattingEdits(model) {
            if (model !== self.editor.getModel()) return [];
            return [
              {
                text: self.formatSql(1),
                range: model.getFullModelRange()
              }
            ];
          }
        }
      );

      // 鼠标松开事件
      this.editor.onMouseUp(e => {
        if (e.event.leftButton && e.event.ctrlKey) {
          // 左键单击 并且 按下 ctrl 健
          let model = this.editor.getModel();
          let selection = this.editor.getSelection();
          let selectedText = model.getValueInRange(selection);
          if (selectedText.indexOf(" ") != -1) return;
          // 校验是否精准选中
          let databaseName = selectedText.split(".")[0].toUpperCase();
          // console.log(this.databaseList, databaseName, "123");
          let regex = '"([^"]*)"';
          databaseName = databaseName.replace(regex, "").replaceAll('"', "");
          if (this.databaseList.includes(databaseName)) {
            // 触发预览
            // console.log('跳转');
            let tableName = selectedText.split(".")[1];
            setTimeout(() => {
              this.$EventBus.$emit("openLookPage", { databaseName, tableName, dataSourceId: this.queryContext.dataSourceId });
            }, 300);
          } else {
            this.$message.error("未找到该模式或表名!");
          }
        }
        // console.log(this.editor.getSelection());
        // const data = this.editor.getPositionFromDOM(e.target)
        // console.log(data);
        // console.log('value', this.editor.getValue())
      });

    },

    initSuggestions() {
      this.suggestion = monaco.languages.registerCompletionItemProvider(SQL_LANGUAGE, {
        triggerCharacters: [".", " ", ","],
        provideCompletionItems: async (model, position, completionContext, token) => {
          if (model !== this.editor.getModel()) return { suggestions: [] };
          const word = model.getWordUntilPosition(position);
          const range = { startLineNumber: position.lineNumber, endLineNumber: position.lineNumber,
            startColumn: word.startColumn, endColumn: word.endColumn };
          if (this.columnList.length) {
            return { suggestions: this.columnList.map(item => ({ label: item, insertText: item,
              kind: monaco.languages.CompletionItemKind.Field, range })) };
          }
          const before = model.getValue().slice(0, model.getOffsetAt(position));
          const statement = sqlAtCursor(model.getValue(), model.getOffsetAt(position));
          const normalizeName = name => name.startsWith('"') ? name.slice(1, -1) :
            /^(ORACLE|DM)$/i.test(this.queryContext.databaseType || this.queryContext.type || '') ? name.toUpperCase() : name;
          const identifier = '(?:"[^"]+"|[a-zA-Z_][\\w$#]*)';
          const tableRegex = new RegExp('\\b(?:FROM|JOIN|UPDATE|INTO)\\s+(' + identifier +
            '(?:\\s*\\.\\s*' + identifier + ')?)(?:\\s+(?:AS\\s+)?(' + identifier + '))?', 'gi');
          const tables = [];
          const cteRegex = new RegExp('(?:\\bWITH|,)\\s+(' + identifier + ')\\s*(?:\\([^)]*\\)\\s*)?AS\\s*\\(', 'gi');
          const ctes = new Set();
          let match;
          while ((match = cteRegex.exec(statement))) ctes.add(match[1].replace(/^"|"$/g, '').toUpperCase());
          while ((match = tableRegex.exec(statement))) {
            const names = match[1].split('.').map(name => normalizeName(name.trim()));
            if (names.length === 1 && ctes.has(names[0].toUpperCase())) continue;
            const alias = (match[2] || '').replace(/^"|"$/g, '');
            tables.push({ schema: names.length > 1 ? names[0] : this.queryContext.schemaName,
              name: names[names.length - 1],
              alias: /^(WHERE|JOIN|LEFT|RIGHT|INNER|OUTER|FULL|CROSS|ON|GROUP|ORDER|HAVING|UNION|SET)$/i.test(alias) ? '' : alias });
          }
          let suggestions = [];
          try {
            const qualifier = before.match(/("[^"]+"|[a-zA-Z_][\w$#]*)\s*\.\s*(?:"?[^"\s.,()]*)$/);
            if (qualifier) {
              const name = normalizeName(qualifier[1]);
              const table = tables.find(item => [item.alias, item.name].some(value => value.toUpperCase() === name.toUpperCase()));
              suggestions = table ? await this.getParamSuggest(table.schema, table.name) : await this.getTableSuggest(name);
            } else if (/\b(FROM|JOIN|UPDATE|INTO)\s+[^\s.,()]*$/i.test(before)) {
              suggestions = [...this.getDBSuggest(), ...await this.getTableSuggest(this.queryContext.schemaName)];
            } else {
              const columns = await Promise.all(tables.map(table => this.getParamSuggest(table.schema, table.name)));
              suggestions = [...columns.flat(), ...this.getDBSuggest(), ...this.getSQLSuggest()];
            }
          } catch (error) {
            suggestions = this.getSQLSuggest();
          }
          if (token.isCancellationRequested) return { suggestions: [] };
          return { suggestions: [...new Map(suggestions.map(item => [item.label, { ...item, range }])).values()] };
        }
      });
    },

    // 关键字
    getSQLSuggest() {
      let keyword = language.keywords.map(key => ({
        label: key,
        kind: monaco.languages.CompletionItemKind.Keyword,
        insertText: key,
        detail: "关键字"
      }));
      let operators = language.operators.map(key => ({
        label: key,
        kind: monaco.languages.CompletionItemKind.Operator,
        insertText: key,
        detail: "运算符"
      }));
      let builtinFunctions = language.builtinFunctions.map(key => ({
        label: key,
        kind: monaco.languages.CompletionItemKind.Function,
        insertText: key,
        detail: "函数"
      }));
      let builtinVariables = language.builtinVariables.map(key => ({
        label: key,
        kind: monaco.languages.CompletionItemKind.Variable,
        insertText: key,
        detail: "变量"
      }));
      return [
        ...keyword,
        ...operators,
        ...builtinFunctions,
        ...new Set(builtinVariables)
      ];
    },
    // 表名
    async getTableSuggest(schemaName) {
      if (!schemaName || !this.queryContext.dataSourceId) return [];
      const key = [this.queryContext.dataSourceId, this.queryContext.databaseName, schemaName].join('/');
      if (!this.tableCache[key]) {
        let send = {
          dataSourceId: this.queryContext.dataSourceId,
          databaseName: this.queryContext.databaseName,
          databaseType: this.queryContext.databaseType || this.queryContext.type,
          schemaName,
          refresh: false,
          requestType: 1,
          pageNo: 1,
          pageSize: 1000
        };
        let res = await tableServer.getTableList(send);
        if (!res.success) return [];
        this.tableCache[key] = res.data.data || [];
      }
      return this.tableCache[key].map(item => ({
        label: item.name,
        kind: monaco.languages.CompletionItemKind.Constant,
        insertText: '"' + item.name + '"',
        detail: "表"
      }));
    },
    // 字段名
    async getParamSuggest(schemaName, tableName) {
      if (!schemaName || !tableName || !this.queryContext.dataSourceId) return [];
      const key = [this.queryContext.dataSourceId, this.queryContext.databaseName, schemaName, tableName].join('/');
      if (!this.columnCache[key]) {
          let send = {
            dataSourceId: this.queryContext.dataSourceId,
            databaseName: this.queryContext.databaseName,
            schemaName:
              schemaName.indexOf('"') != -1
                ? schemaName.slice(1, schemaName.length - 1)
                : schemaName,
            tableName:
              tableName.indexOf('"') != -1
                ? tableName.slice(1, tableName.length - 1)
                : tableName
          };
          let res = await tableServer.getAllFieldByTable(send);
          if (!res.success) return [];
          this.columnCache[key] = res.data || [];
      }
      // 将自定义字段列表转换为代码提示的格式
      const suggestions = this.columnCache[key].map(column => ({
        label: column.name,
        kind: monaco.languages.CompletionItemKind.Field, // 表示字段类型
        insertText: '"' + column.name + '"',
        detail: "字段名"
      }));
      return suggestions;
    },
    // 数据库名
    getDBSuggest() {
      let map = new Map();
      let newArr = [];
      // for (const item of this.databaseList) {
      //   //  console.log(item);
      //   if (!map.has(item.label)) {
      //     map.set(item.label, true);
      //     newArr.push({
      //       label: item,
      //       kind: monaco.languages.CompletionItemKind.Enum,
      //       insertText: '"' + item + '"',
      //       detail: "数据库",
      //     });
      //   }
      // }
      // console.log(newArr,'newArr');
      // return newArr;
      return this.databaseList.map(key => ({
        label: key,
        kind: monaco.languages.CompletionItemKind.Enum,
        insertText: '"' + key + '"',
        detail: "数据库"
      }));
    },
    // 获取选中数据
    getSelectValue() {
      return this.editor.getModel().getValueInRange(this.editor.getSelection());
    },
    // 父组件获取值
    getValue() {
      return this.editor.getValue();
    },
    // 父组件设置值
    setValue(content) {
      // console.log(this.editor.getValue(),'this.editor.getValue();');
      this.editor.setValue(String(content || ""));
    },
    getCurrentStatement() {
      const model = this.editor.getModel();
      return sqlAtCursor(model.getValue(), model.getOffsetAt(this.editor.getPosition()));
    },
    // 格式化代码
    formatSql(needValue) {
      this.clearMistake();
      try {
        const type = this.queryContext.databaseType || this.queryContext.type || "";
        this.setValue(format(this.editor.getValue(), { language: /ORACLE|DM/i.test(type) ? "plsql" : "sql" }));
      } catch (e) {
        this.$message.warning("格式化器不支持此语法，已保留原 SQL；仍可交由数据库执行");
      }
      if (needValue) {
        return this.editor.getValue();
      }
    },
    // 标记错误信息
    markMistake(range, type, message) {
      const { startLineNumber, endLineNumber, startColumn, endColumn } = range;
      monaco.editor.setModelMarkers(this.editor.getModel(), "eslint", [
        {
          startLineNumber,
          endLineNumber,
          startColumn,
          endColumn,
          severity: monaco.MarkerSeverity[type] // type可以是Error,Warning,Info
          // message,
        }
      ]);
    },
    // 清除错误信息
    clearMistake() {
      monaco.editor.setModelMarkers(this.editor.getModel(), "eslint", []);
    }
  }
};
</script>

<template>
  <div ref="editorHost" :id="dom + 'code'"></div>
</template>

<style scoped lang="scss">
</style>
