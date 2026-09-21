<script>
import renderSearchResult from "@/views/main/workspace/components/renderSearchResult/index.vue";
import MonacoEditor from "@/components/MonacoEditor/index.vue";
import sqlServer from "@/api/main/sql";

export default {
  components: {MonacoEditor, renderSearchResult},
  props: {
    currentConfig: {
      type: Object,
    }
  },
  data() {
    return {

    }
  },
  computed: {
    activeConsoleId() {
      return this.$store.state.workspace.activeConsoleId
    },
    currentDataSource() {
      return this.$store.state.workspace.currentConnectionDetails
    },
  },
  methods: {
    // 执行sql
    executeSQL() {
      let { uniqueData } = this.currentConfig
      let sql = this.$refs.MonacoEditor.getSelectValue() || this.$refs.MonacoEditor.getValue()
      if (!sql) return;

      let send = {
 
      }
      sqlServer.executeJDBCSql(send)
        .then(res => {
          if (res.success) {
            this.$message.success('运行成功！')
            this.$emit('tableRefresh', this.currentConfig.uniqueData.node)
          } else {
            this.$message.error(res.errorMessage)
          }
        })
    },
    // 清空
    emptySql() {
      this.$refs.MonacoEditor.setValue('')
    },
    // 格式化sql代码
    formatSql() {
      this.$refs.MonacoEditor.formatSql(this.$refs.MonacoEditor.getValue())
    },
    setViewSql(sql) {
      this.$refs.MonacoEditor.setValue(sql)
    }
  }
}
</script>

<template>
  <div class="sql_execute">
    <div class="sql_search_box">
      <div class="monaco_btn">
        <el-button size="mini" @click="executeSQL" type="text" class="el-button_before" style="margin-left: 0">
          <img src="@/assets/main/1-con-ico01.png" alt="">
          运行
        </el-button>
        <el-button size="mini" @click="emptySql" type="text" class="el-button_before">
          <img src="@/assets/main/1-con-ico03.png" alt="">
          清空
        </el-button>
        <el-button size="mini" @click="formatSql" type="text">
          <img src="@/assets/main/1-con-ico04.png" alt="">
          美化
        </el-button>
      </div>
      <MonacoEditor ref="MonacoEditor" dom="editor" style="width: 100%; height: calc(100% - 48px);" />
    </div>
  </div>
</template>

<style scoped lang="scss">
.sql_execute {
  height: 100%;
  display: flex;
  flex-flow: column;
  .sql_search_box {
    width: 100%;
    height: 100%;
    background: #fff;
    border-radius: 5px;
    flex-shrink: 0;
    .monaco_btn {
      display: flex;
      align-items: center;
      padding: 10px 20px;
      ::v-deep .el-button {
        & > span{
          display: flex;
          align-items: center;
          img {
            margin-right: 6px;
          }
        }
      }
      .el-button_before::before {
        content: '';
        position: absolute;
        right: -15px;
        top: 8px;
        width: 1px;
        height: 14px;
        background: #68728C;
      }
    }
  }
  .monaco_title {
    flex-shrink: 0;
    font-size: 16px;
    color: #636363;
    padding: 12px 0 12px 20px;
  }
}
</style>
