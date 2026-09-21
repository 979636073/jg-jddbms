<script>
import tableServer from "@/api/main/table";
import * as monaco from "monaco-editor/esm/vs/editor/edcore.main";
import "monaco-editor/esm/vs/basic-languages/sql/sql.contribution";
import { language } from "monaco-editor/esm/vs/basic-languages/sql/sql";
import { format } from "sql-formatter";
import { editorDefaultOptions } from "./monacoEditorConfig";

let suggestionFlag = true;

export default {
  name: "index",
  props: {
    dom: String,
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
      color: null,
      suggestion: null,
      formatProvider: null,
      syntaxCheckTimer: null,
      schemaName: "", // 输入的模式名
      tableName: "",
      tableList: [],
      // 定义一些自定义的字段名建议，包括一些常用的 SQL 字段
      customColumns: [
        { name: "id", dataType: "int" },
        { name: "name", dataType: "varchar" },
        { name: "age", dataType: "int" },
        { name: "created_at", dataType: "timestamp" },
        { name: "updated_at", dataType: "timestamp" },
        { name: "is_active", dataType: "boolean" },
        { name: "description", dataType: "text" }
      ]
    };
  },
  computed: {
    pageId() {
      return this.$route.params.id;
    },
    databaseList() {
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
      return this.dataCurrentDataMap.get(this.pageId);
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
    if (this.syntaxCheckTimer) {
      clearTimeout(this.syntaxCheckTimer);
      this.syntaxCheckTimer = null;
    }
    if (this.editor) {
      this.clearMistake();
      this.editor.dispose();
      if (this.suggestion) {
        this.suggestion.dispose();
      }
      this.formatProvider.dispose();
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
        document.getElementById(this.dom + "code"),
        {
          ...editorDefaultOptions,
          ...this.editorConfig
        }
      );

      // console.log(this.editor,'111111111');
      this.initSuggestions();
      this.initColor();
      //自定义文本颜色,也可以不设置，自带也有颜色区分

      // 改写插件自带格式化功能
      const self = this;
      this.formatProvider = monaco.languages.registerDocumentFormattingEditProvider(
        "sql",
        {
          provideDocumentFormattingEdits(model) {
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
              this.$EventBus.$emit("openLookPage", { databaseName, tableName });
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

      this.editor.onDidChangeModelContent(() => {
        if (this.syntaxCheckTimer) clearTimeout(this.syntaxCheckTimer);
        this.syntaxCheckTimer = setTimeout(() => {
          try {
            format(this.editor.getValue());
            this.clearMistake();
          } catch (e) {
            const { message } = e;
            const list = message.split(".")[0].split(" ");
            const line = list.indexOf("line");
            const column = list.indexOf("column");
            const unexpected = list.indexOf("Unexpected");
            const str = list[unexpected + 1] ? list[unexpected + 1].replace(/"/g, "") : "";
            if (line < 0 || column < 0 || unexpected < 0) return;
            this.markMistake(
              {
                startLineNumber: Number(list[line + 1]),
                endLineNumber: Number(list[line + 1]),
                startColumn: Number(list[column + 1]),
                endColumn: Number(list[column + 1]) + str.length
              },
              "Error",
              message
            );
          }
        }, 300);
      });
    },

    initSuggestions() {
      if (suggestionFlag == false) return;
      // 自动补全提示
      this.suggestion = monaco.languages.registerCompletionItemProvider("sql", {
        // 触发条件，也可以不写，不写的话只要输入满足配置的label就会提示；仅支持单字符
        triggerCharacters: [".", " ", ","],
        provideCompletionItems: async (model, position) => {
          let suggestions = [];

          // 数据表查询字段提示
          if (this.columnList.length) {
            let _columnList = this.columnList.map(item => {
              return {
                label: item,
                kind: monaco.languages.CompletionItemKind.Field,
                insertText: item,
                detail: "字段名"
              };
            });
            return {
              suggestions: _columnList
            };
          }
          const { lineNumber, column } = position;
          // 光标前文本
          const textBeforePointer = model.getValueInRange({
            startLineNumber: lineNumber,
            startColumn: 0,
            endLineNumber: lineNumber,
            endColumn: column
          });
          const textBeforePointerMulti = model.getValueInRange({
            startLineNumber: 1,
            startColumn: 0,
            endLineNumber: lineNumber,
            endColumn: column
          });
          // 光标后文本
          const textAfterPointer = model.getValueInRange({
            startLineNumber: lineNumber,
            startColumn: column,
            endLineNumber: lineNumber,
            endColumn: model.getLineMaxColumn(model.getLineCount())
          });
          const textAfterPointerMulti = model.getValueInRange({
            startLineNumber: lineNumber,
            startColumn: column,
            endLineNumber: model.getLineCount(),
            endColumn: model.getLineMaxColumn(model.getLineCount())
          });
          const oldTokens = textBeforePointer.trim().split(/\s+/);
          const oldLastToken = oldTokens[oldTokens.length - 1];
          const tokens = textBeforePointer
            .toLocaleLowerCase()
            .trim()
            .split(/\s+/);
          const lastToken = tokens[tokens.length - 1]; // 获取最后一段非空字符串
          let tableInfo = oldLastToken.split(".");
          let schemaName = tableInfo[1] ? tableInfo[0] : "";
          let tableName = tableInfo[1] ? tableInfo[1] : tableInfo[0];
          // debugger

          if (lastToken.endsWith(".")) {
            // <库名>.<表名> || <别名>.<字段>
            const tokenNoDot =
              oldLastToken.indexOf('"') != -1
                ? oldLastToken.slice(1, oldLastToken.length - 2)
                : oldLastToken.slice(0, oldLastToken.length - 1);
            if (
              this.databaseList.find(
                db => db === tokenNoDot.replace(/^.*,/g, "")
              )
            ) {
              // <库名>.<表名>联想
              suggestions = [
                ...(await this.getTableSuggest(tokenNoDot.replace(/^.*,/g, "")))
              ];
              // } else if (this.getTableNameAndTableAlia(textBeforePointerMulti.split(';')[textBeforePointerMulti.split(';').length - 1] + textAfterPointerMulti.split(';')[0])) {
              //   const tableInfoList = this.getTableNameAndTableAlia(textBeforePointerMulti.split(';')[textBeforePointerMulti.split(';').length - 1] + textAfterPointerMulti.split(';')[0])
              //   const currentTable = tableInfoList.find(item => item.tableAlia === tokenNoDot.replace(/^.*,/g, ''))
              //   // <别名>.<字段>联想
              //   if (currentTable && currentTable.tableName) {
              //     suggestions = await this.getTableColumnSuggestByTableAlia(currentTable.tableName)
              //   } else {
              //     suggestions = []
              //   }
            } else {
              suggestions = [];
            }
          } else if (
            lastToken === "from" ||
            lastToken === "join" ||
            /(from|join)\s+.*?\s?,\s*$/.test(
              textBeforePointer.replace(/.*?/gm, "").toLowerCase()
            )
          ) {
            // 库名联想
            suggestions = this.getDBSuggest();
            console.log(suggestions, "suggestions 库名联想");
          } else if (
            [
              "select",
              "where",
              "order by",
              "group by",
              "by",
              "and",
              "or",
              "having",
              "distinct",
              "on"
            ].includes(lastToken.replace(/.*?/g, "")) ||
            (schemaName && tableName) ||
            /(select|where|order by|group by|by|and|or|having|distinct|on)\s+.*?\s?,\s*$/.test(
              textBeforePointer.toLowerCase()
            )
          ) {
            // 字段联想
            if (
              this.databaseList.find(
                db => db.toLocaleLowerCase() === schemaName.toLocaleLowerCase()
              ) &&
              this.tableList.find(
                table =>
                  table.name.toLocaleLowerCase() ===
                  tableName.toLocaleLowerCase()
              )
            ) {
              suggestions = [
                ...(await this.getParamSuggest(schemaName, tableName))
              ];
            } else {
              suggestions = [
                ...(await this.getParamSuggest(schemaName, tableName))
              ];
            }
          } else {
            // 默认联想
            suggestions = [...this.getDBSuggest(), ...this.getSQLSuggest()];
            suggestions = Array.from(
              new Map(suggestions.map(item => [item.label, item])).values()
            );
            console.log(suggestions, "suggestions 默认联想");
          }
          return {
            suggestions
          };
        }
      });
      suggestionFlag = false;
    },
    initColor() {
      let reg = "/";
      language.keywords.forEach(keyword => {
        reg += `${keyword}|`;
      });
      reg += "/";

      this.color = monaco.languages.setMonarchTokensProvider("sql", {
        ignoreCase: true,
        tokenizer: {
          root: [
            [reg, { token: "keyword" }], //蓝色
            [
              /[+]|[-]|[*]|[/]|[%]|[>]|[<]|[=]|[!]|[:]|[&&]|[||]/,
              { token: "string" }
            ], //红色
            [/'.*?'|".*?"/, { token: "string.escape" }], //橙色
            [/#--.*?\--#/, { token: "comment" }], //绿色
            [/null/, { token: "regexp" }], //粉色
            [/[{]|[}]/, { token: "type" }], //青色
            [/[\u4e00-\u9fa5]/, { token: "predefined" }], //亮粉色+
            [/''/, { token: "invalid" }], //红色
            [/[\u4e00-\u9fa5]/, { token: "number.binary" }], //浅绿
            [/(?!.*[a-zA-Z])[0-9]/, { token: "number.hex" }], //浅绿
            [/[(]|[)]/, { token: "number.octal" }], //浅绿
            [/[\u4e00-\u9fa5]/, { token: "number.float" }] //浅绿
          ]
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
      if (this.schemaName !== schemaName) {
        this.schemaName = schemaName;
        let send = {
          dataSourceId: this.dataInfo.dataSource.id,
          databaseName: this.dataInfo.dataSource.alias,
          databaseType: this.dataInfo.dataSource.type,
          schemaName: this.schemaName,
          refresh: true,
          requestType: 1
        };
        let res = await tableServer.getTableList(send);
        this.tableList = res.data.data;
      }
      return this.tableList.map(item => ({
        label: item.name,
        kind: monaco.languages.CompletionItemKind.Constant,
        insertText: '"' + item.name + '"',
        detail: "表"
      }));
    },
    // 字段名
    async getParamSuggest(schemaName, tableName) {
      if (schemaName && tableName) {
        if (this.tableName !== tableName) {
          this.tableName = tableName;
          let send = {
            dataSourceId: this.dataInfo.dataSource.id,
            databaseName: this.dataInfo.dataSource.alias,
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
          this.customColumns = res.data;
        }
      }
      // 将自定义字段列表转换为代码提示的格式
      const suggestions = this.customColumns.map(column => ({
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
      if (content) {
        this.editor.setValue(content);
      }
    },
    // 格式化代码
    formatSql(needValue) {
      this.clearMistake();
      try {
        this.setValue(format(this.editor.getValue()));
      } catch (e) {
        const { message } = e;
        const list = message.split(".")[0].split(" ");
        const line = list.indexOf("line");
        const column = list.indexOf("column");
        const Unexpected = list.indexOf("Unexpected");
        let str = list[Unexpected + 1].replace(/"/g, "");

        this.markMistake(
          {
            startLineNumber: Number(list[line + 1]),
            endLineNumber: Number(list[line + 1]),
            startColumn: Number(list[column + 1]),
            endColumn: Number(list[column + 1]) + str.length
          },
          "Error",
          message
        );
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
    },
    handleEditorClick() {
      if (this.editor) {
        console.log("视角状态");
        let tempInput = document.createElement("input");
        tempInput.style.position = "absolute";
        tempInput.style.left = "-1000px";
        tempInput.style.top = "0";
        tempInput.style.opacity = "0";
        tempInput.style.height = "0";
        tempInput.style.width = "0";
        tempInput.style.pointerEvents = "none";
        document.body.appendChild(tempInput);
        tempInput.focus();
        setTimeout(() => {
          document.body.removeChild(tempInput);
          this.editor.focus();
        }, 50);
      }
    }
  }
};
</script>

<template>
  <div @mousedown="handleEditorClick" :id="dom + 'code'"></div>
</template>

<style scoped lang="scss">
</style>
