<script>
import sqlServer from "@/api/main/sql"
import * as monaco from 'monaco-editor/esm/vs/editor/edcore.main'
import 'monaco-editor/esm/vs/basic-languages/sql/sql.contribution'
import { language } from 'monaco-editor/esm/vs/basic-languages/sql/sql'
import { format } from 'sql-formatter'

export default {
  name: 'index',
  props: {
    dom: String,
    editorConfig: {
      type: Object,
      default: () => {}
    },
    database: {
      type: String,
      default: ''
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
      fieldList: [],
    }
  },
  computed: {
    databaseList() {
      return this.$store.state.workspace.databaseList.map(item => item.name)
    },
    tableList() {
      return this.$store.state.workspace.tableList.map(item => item.name)
    },
    // 控制台选中的数据库
    currentDataBase() {
      return this.$store.state.workspace.currentDataBase
    },
    currentDataSource() {
      return this.$store.state.workspace.currentConnectionDetails
    }
  },
  mounted () {
    this.initEditor()
  },

  beforeDestroy () {
    if (this.editor) {
      this.clearMistake()
      this.editor.dispose()
      this.color.dispose()
      this.suggestion.dispose()
      this.formatProvider.dispose()
    }
  },

  methods: {
    initEditor () {
      this.editor = monaco.editor.create(document.getElementById(this.dom + 'code'), {
        fontFamily:
          `"Menlo",
          "DejaVu Sans Mono",
          "Liberation Mono",
          "Consolas",
          "Ubuntu Mono",
          "Courier New",
          "andale mono",
          "lucida console",
          "monospace"`,
        scrollBeyondLastLine: false, // 滚动超过最后一行
        automaticLayout: true, // 自动布局
        dragAndDrop: false, // 拖拽
        fontSize: 12, // 字体大小
        tabSize: 2, // tab大小
        lineHeight: 18, // 行高
        theme: 'vscode', // 主题
        roundedSelection: false, // 圆角选择
        readOnly: false, // 只读
        folding: false, // 不显示折叠
        insertSpaces: true, // 插入空格
        autoClosingQuotes: 'always', // 自动闭合引号
        detectIndentation: false, // 检测缩进
        wordWrap: 'on', // 自动换行
        fixedOverflowWidgets: true, // 固定溢出小部件
        // renderLineHighlight: 'none', // 渲染行高亮
        codeLens: false, // 代码镜头
        scrollbar: {
          // 滚动条
          alwaysConsumeMouseWheel: false, // 总是消耗鼠标滚轮
        },
        unicodeHighlight: {
          ambiguousCharacters: false,
          invisibleCharacters: false,
        },
        minimap: {
          // 缩略图
          enabled: false, // 启用
        },
        // //初始化配置
        value: '',
        autoIndex: true,
        language: 'sql', // 语言类型
        tabCompletion: 'on',
        cursorSmoothCaretAnimation: true,
        formatOnPaste: true,
        mouseWheelZoom: true,
        autoClosingBrackets: 'always',
        autoClosingOvertype: 'always',
        ...this.editorConfig
      })
      // 自动补全提示
      this.suggestion = monaco.languages.registerCompletionItemProvider('sql', {
        // 触发条件，也可以不写，不写的话只要输入满足配置的label就会提示；仅支持单字符
        triggerCharacters: ['.', ' '],
        provideCompletionItems: async (model, position) => {
          let suggestions = []

          // 数据表查询字段提示
          if (this.columnList.length) {
            let _columnList = this.columnList.map(item => {
              return {
                label: item,
                kind: monaco.languages.CompletionItemKind.Constant,
                insertText: item,
                detail: '字段名'
              }
            })
            return {
              suggestions: _columnList
            }
          }
          const { lineNumber, column } = position
          const textBeforePointer = model.getValueInRange({
            startLineNumber: lineNumber,
            startColumn: 0,
            endLineNumber: lineNumber,
            endColumn: column,
          })
          const oldTokens = textBeforePointer.trim().split(/\s+/)
          const tokens = textBeforePointer.toLocaleLowerCase().trim().split(/\s+/)
          const lastToken = tokens[tokens.length - 1] // 获取最后一段非空字符串
          if (lastToken.endsWith('.')) {
            // 提示该数据库下的表名
            suggestions = [...this.getTableSuggest()]
          } else if (lastToken === '.') {
            suggestions = []
          } else if (textBeforePointer.endsWith(' ')) {
            if (textBeforePointer.endsWith('select * from ')) {
              // select * from 提示指定数据库的表名
              suggestions = this.getTableSuggest()
            } else if (lastToken === 'where') {
              const lastToken2 = oldTokens[tokens.length - 2]
              const lastToken3 = tokens[tokens.length - 3]
              const lastToken4 = tokens[tokens.length - 4]
              const lastToken5 = tokens[tokens.length - 5]
              if (lastToken5 + lastToken4 + lastToken3 === 'select*from') {
                // select * from tableName where 提示指定表的字段名
                let fieldList = await this.getParamSuggest(lastToken2.split('.'))
                suggestions = [...fieldList]
              } else {
                suggestions = []
              }
            }else {
              suggestions = []
            }
          } else {
            // 提示数据库名和关键词
            suggestions = [...this.getDBSuggest(), ...this.getSQLSuggest()]
          }
          return {
            suggestions,
          }
        }
      })

      //自定义文本颜色,也可以不设置，自带也有颜色区分
      let reg = '/'
      language.keywords.forEach((keyword) => {
        reg += `${keyword}|`
      })
      reg += '/'
      this.color = monaco.languages.setMonarchTokensProvider('sql', {
        ignoreCase: true,
        tokenizer: {
          root: [
            [
              reg,
              { token: 'keyword' },
            ], //蓝色
            [
              /[+]|[-]|[*]|[/]|[%]|[>]|[<]|[=]|[!]|[:]|[&&]|[||]/,
              { token: 'string' },
            ], //红色
            [/'.*?'|".*?"/, { token: 'string.escape' }], //橙色
            [/#--.*?\--#/, { token: 'comment' }], //绿色
            [/null/, { token: 'regexp' }], //粉色
            [/[{]|[}]/, { token: 'type' }], //青色
            [/[\u4e00-\u9fa5]/, { token: 'predefined' }],//亮粉色
            [/''/, { token: 'invalid' }],//红色
            [/[\u4e00-\u9fa5]/, { token: 'number.binary' }],//浅绿
            [/(?!.*[a-zA-Z])[0-9]/, { token: 'number.hex' }], //浅绿
            [/[(]|[)]/, { token: 'number.octal' }], //浅绿
            [/[\u4e00-\u9fa5]/, { token: 'number.float' }],//浅绿
          ]
        }
      })

      // 改写插件自带格式化功能
      const self = this
      this.formatProvider = monaco.languages.registerDocumentFormattingEditProvider('sql', {
        provideDocumentFormattingEdits(model) {
          return [{
            text: self.formatSql(1),
            range: model.getFullModelRange()
          }]
        }
      })
      this.editor.onDidChangeModelContent(() => {
        // console.log('value', this.editor.getValue())
      })
    },
    // 关键字
    getSQLSuggest () {
      return language.keywords.map((key) => ({
        label: key,
        kind: monaco.languages.CompletionItemKind.Keyword,
        insertText: key,
        detail: 'keyword'
      }))
    },
    // 表名
    getTableSuggest () {
      return this.tableList.map((name) => ({
        label: name,
        kind: monaco.languages.CompletionItemKind.Constant,
        insertText: name,
        detail: '表'
      }))
    },
    // 字段名
    async getParamSuggest (table) {
      let tableName = table && table[1] ? table[1] : table[0]
      let send = {
        dataSourceId: this.currentDataSource.id,
        databaseName: null,
        schemaName: this.currentDataBase,
        tableName: tableName,
      }
      let res = await sqlServer.getAllFieldByTable(send)
      return res.data.map((item) => ({
        label: item.name,
        kind: monaco.languages.CompletionItemKind.Constant,
        insertText: item.name,
        detail: '字段名'
      }))
    },
    // 数据库名
    getDBSuggest () {
      return this.databaseList.map((key) => ({
        label: key,
        kind: monaco.languages.CompletionItemKind.Enum,
        insertText: key,
        detail: '数据库'
      }))
    },
    // 获取选中数据
    getSelectValue() {
      return this.editor.getModel().getValueInRange(this.editor.getSelection())
    },
    // 父组件获取值
    getValue() {
      return this.editor.getValue()
    },
    // 父组件设置值
    setValue(content) {
      this.editor.setValue(content)
    },
    // 格式化代码
    formatSql(needValue) {
      this.clearMistake()
      try {
        this.setValue(format((this.editor).getValue()))
      } catch (e) {
        const {message} = e
        const list = message.split(' ')
        const line = list.indexOf('line')
        const column = list.indexOf('column')
        this.markMistake({
          startLineNumber: Number(list[line + 1]),
          endLineNumber: Number(list[line + 1]),
          startColumn: Number(list[column + 1]),
          endColumn: Number(list[column + 1])
        }, 'Error', message)
      }
      if (needValue) {
        return this.editor.getValue()
      }
    },
    // 标记错误信息
    markMistake(range, type, message) {
      const {startLineNumber, endLineNumber, startColumn, endColumn} = range
      monaco.editor.setModelMarkers(
        this.editor.getModel(),
        'eslint',
        [{
          startLineNumber,
          endLineNumber,
          startColumn,
          endColumn,
          severity: monaco.MarkerSeverity[type], // type可以是Error,Warning,Info
          message
        }]
      )
    },
    // 清除错误信息
    clearMistake() {
      monaco.editor.setModelMarkers(
        this.editor.getModel(),
        'eslint',
        []
      )
    },
  }
}
</script>

<template>
  <div :id="dom + 'code'"></div>
</template>

<style scoped lang="scss">

</style>
