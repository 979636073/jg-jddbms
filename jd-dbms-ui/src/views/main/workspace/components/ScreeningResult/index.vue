<script>
import MonacoEditor from "@/components/MonacoEditor/index.vue";

export default {
  name: 'screeningResult',
  components: {MonacoEditor},
  props: {
    queryResultData: {
      type: Object,
      default: () => {}
    }
  },
  data() {
    return {
      editorConfig: {
        lineNumbers: false,
        renderLineHighlight: 'none',
        scrollBeyondLastLine: false,
        wordWrap: 'off',
        minimap: {
          enabled: false,
        },
        // 不显示滚动条
        scrollbar: {
          vertical: 'hidden',
          horizontal: 'hidden',
        },
        overviewRulerBorder: false,
        glyphMargin: false,
        folding: false,
        lineDecorationsWidth: 0, // 行号宽度
        lineNumbersMinChars: 0, // 行号最小宽度
      },
      columnList: []
    }
  },
  watch: {
    queryResultData: {
      handler(newVal) {
        this.columnList = newVal?.headerList.map(item => {
          return item.name
        })
      },
      immediate: true,
      deep: true
    }
  },
  mounted() {
    window.addEventListener('keydown', this.handleKeydown);
  },
  beforeDestroy() {
    window.removeEventListener('keydown', this.handleKeydown);
  },
  methods: {
    search() {
      const notChangedSql = `select * from ${this.queryResultData.tableName}`
      const whereValue = this.$refs.WhereMonacoEditor.getValue().trim() || '';
      const orderByValue = this.$refs.OrderMonacoEditor.getValue().trim() || '';
      let sql = whereValue ? notChangedSql + ' WHERE ' + whereValue : notChangedSql;
      sql = orderByValue ? sql + ' ORDER BY ' + orderByValue : sql;
      this.$emit('getTableList', { sql })
    },
    handleKeydown(event) {
      if (event.key === 'Enter') {
        event.preventDefault();
        this.search()
      }
    },
    getMonacoEditorValue() {
      const whereValue = this.$refs.WhereMonacoEditor.getValue().trim() || '';
      const orderByValue = this.$refs.OrderMonacoEditor.getValue().trim() || '';
      return {
        whereValue,
        orderByValue
      }
    },
    setMonacoEditorValue(whereValue, orderByValue) {
      this.$nextTick(() => {
        this.$refs.WhereMonacoEditor.setValue(whereValue)
        this.$refs.OrderMonacoEditor.setValue(orderByValue)
      })
    }
  }
}
</script>

<template>
  <div class="screeningResult">
    <div class="whereBox">
      <div class="titleBox">
        <i class="icon iconfont">&#xe888;</i>
        <div :class="{title: true, activeTitle: true}">
          WHERE
        </div>
      </div>
      <MonacoEditor
        ref="WhereMonacoEditor"
        dom="where"
        :editorConfig="editorConfig"
        :columnList="columnList"
        style="width: 100%; height: 18px;"
        @keyup.enter="search"
      />
    </div>
    <div class="orderByBox">
      <div class="titleBox">
        <i class="icon iconfont">&#xe69a;</i>
        <div :class="{title: true, activeTitle: true}">
          ORDER BY
        </div>
      </div>
      <MonacoEditor
        ref="OrderMonacoEditor"
        dom="orderBy"
        style="width: 100%; height: 18px;"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
.screeningResult{
  display: flex;
  align-items: center;
  height: 18px;
  padding: 4px 0px;
  border-bottom: 1px solid rgba(211, 211, 212, 0.4);
  font-size: 12px;
  color: rgba(35, 36, 41, 0.88);
  .whereBox,.orderByBox{
    width: 50%;
    display: flex;
    align-items: center;
    height: 100%;
    margin: 0px 4px;
    .titleBox{
      display: flex;
      align-items: center;
      margin-right: 10px;
      width: fit-content;
      flex-shrink: 0;
      i {
        font-size: 14px;
        margin-right: 4px
      }
    }
    .titleIcon{
      margin-right: 4px;
    }
    .title{
      flex-shrink: 0;
      font-weight: 500;
    }
    .activeTitle{
      color: #9373ee;
    }
    .monacoEditor{
      flex: 1;
      height: 100%;
      width: 0px;
    }
  }
  :global {
    .decorationsOverviewRuler{
      display: none !important;
    }
  }
}
</style>
