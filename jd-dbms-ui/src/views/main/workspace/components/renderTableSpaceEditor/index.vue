<script>
import viewServer from "@/api/main/view";
import tableSpaceServer from "@/api/main/tableSpace";
import MonacoEditor from "@/components/MonacoEditor/index.vue";
import MonacoSqlEditor from "@/components/MonacoSqlEditor/index.vue";
import ColumnTableSpaceList from "../renderTableSpaceEditor/ColumnTableSpaceList.vue";
import SqlPreview from "@/views/main/workspace/components/DataSource/SqlPreview.vue";

export default {
  components: {
    SqlPreview,
    MonacoEditor,
    MonacoSqlEditor,
    ColumnTableSpaceList,
  },
  props: {
    queryResultData: {
      type: Object,
      default: () => {},
    },
  },
  data() {
    return {
      currentConfig: null,
      activeName: "first",
      activeTab: "first",
      // 基本信息
      basicForm: {
        dataSourceId: "",
        databaseName: "",
        schemaName: "",
        tableName: "",
      },
      columnList: [],
      tableSpaceList: [],

      databaseSupportField: {
        columnTypes: [],
        charsets: [],
        collations: [],
        indexTypes: [],
        defaultValues: [],
      },

      clickRow: null, // 当前点击的行
      clickCell: null, // 当前点击的列
    };
  },
  computed: {
    openConfigList() {
      return this.$store.state.workspace.workspaceTabList;
    },
    openConfigValue() {
      return this.$store.state.workspace.activeConsoleId;
    },
    currentDataSource() {
      return this.$store.state.workspace.currentConnectionDetails;
    },
  },
  watch: {
    // openConfigValue: {
    //   handler() {
    //     this.configChange();
    //     this.initData();
    //   },
    //   deep: true,
    //   immediate: true,
    // },
    queryResultData: {
      handler(newVal) {
        this.configChange();
        this.initData();
      },
      immediate: true,
      deep: true,
    },
  },
  methods: {
    initData() {
      return new Promise((resolve) => {
        this.currentConfig = this.queryResultData;
        let send = {
          dataSourceId: this.currentConfig?.uniqueData.dataSourceId,
          databaseName: this.currentConfig?.uniqueData.databaseName,
          path: this.currentConfig?.uniqueData.path,
        };
        tableSpaceServer.getTableSpaceDetails(send).then((res) => {
          this.tableSpaceList = [];
          this.tableSpaceList.push(res.data.tableDetails);
          this.$refs.MonacoEditor?.setValue(res.data.querySql);
          this.formatSql();
        });
        resolve();
      });
    },
    configChange() {
      if (this.currentConfig) {
        this.currentConfig.basicForm = JSON.parse(
          JSON.stringify(this.basicForm)
        );
        this.currentConfig.columnList = JSON.parse(
          JSON.stringify(this.columnList)
        );
        this.currentConfig.activeName = this.activeName;
      }
    },

    // 获取依赖关系
    getExecuteSQL() {
      let viewSql = this.$refs.MonacoEditor.getValue();
      if (!viewSql) {
        this.$message.error("请先输入SQL");
        return;
      }

      let send = {
        dataSourceId: this.currentConfig?.uniqueData.dataSourceId,
        databaseName: this.currentConfig?.uniqueData.databaseName,
        schemaName: this.currentConfig?.uniqueData.schemaName,
        tableName: this.basicForm.tableName,
        viewSql: viewSql,
      };
      viewServer.getExecuteSQL(send).then((res) => {
        if (res.success) {
          this.$refs.MonacoSqlEditor.setValue(res.message);

          this.$message.success("获取成功！");
        } else {
          this.$message.error(res.message);
        }
      });
    },

    // 运行
    executeSQL() {
      let viewSql = this.$refs.MonacoEditor.getValue();
      if (!viewSql) {
        this.$message.error("请先输入SQL");
        return;
      }
      console.log("this.currentConfig", this.currentConfig);
      console.log(
        "uniqueData.tableName",
        this.currentConfig?.uniqueData.tableName
      );

      let send = {
        dataSourceId: this.currentConfig?.uniqueData.dataSourceId,
        databaseName: this.currentConfig?.uniqueData.databaseName,
        schemaName: this.currentConfig?.uniqueData.schemaName,
        tableName: this.basicForm.tableName,
        viewSql: viewSql,
      };
      viewServer.getViewColumnList(send).then((res) => {
        if (res.success) {
          this.columnList = res.data.map((item) => {
            return {
              name: item.name,
              comment: "",
            };
          });
          this.$message.success("运行成功！");
        } else {
          this.$message.error(res.errorMessage);
        }
      });
    },
    // 清空
    emptySql() {
      this.$refs.MonacoEditor.setValue("");
    },
    // 格式化sql代码
    formatSql() {
      this.$refs.MonacoEditor.formatSql(this.$refs.MonacoEditor.getValue());
    },
    sqlPreview() {
      let viewSql = this.$refs.MonacoEditor.getValue();
      let send = {
        dataSourceId: this.currentConfig?.uniqueData.dataSourceId,
        databaseName: this.currentConfig?.uniqueData.databaseName,
        schemaName: this.currentConfig?.uniqueData.schemaName,
        tableName: this.basicForm.tableName,
        viewSql: viewSql,
        columnList: this.columnList,
      };
      viewServer.viewShowSql(send).then((res) => {
        if (res.success) {
          this.$refs.sqlPreview.init(res.data.sql);
        } else {
          this.$message.error(res.errorMessage);
        }
      });
    },
    submit() {
      let viewSql = this.$refs.MonacoEditor.getValue();
      let send = {
        dataSourceId: this.currentConfig?.uniqueData.dataSourceId,
        databaseName: this.currentConfig?.uniqueData.databaseName,
        schemaName: this.currentConfig?.uniqueData.schemaName,
        tableName: this.basicForm.tableName,
        viewSql: viewSql,
        columnList: this.columnList,
      };
      viewServer.viewExecute(send).then((res) => {
        if (res.success) {
          this.$message.success("保存成功！");
          this.$emit("tableRefresh", this.currentConfig.uniqueData.node.parent);
        } else {
          this.$message.error(res.errorMessage);
        }
      });
    },
    handleClick(tab, event) {
      console.log(tab, event);
    },
    // 双击单元格修改数据
    celldblclick(row, column, cell, event) {
      if (column.index !== 0) {
        this.clickRow = row.index;
        this.clickCell = column.index;
        this.$nextTick(() => {
          this.$refs.editInput.focus();
        });
      }
    },
    // 把每一行的索引放进row
    tableRowClassName({ row, rowIndex }) {
      row.index = rowIndex;
      return "";
    },
    // 把每一列的索引放进column
    tableCellClassName({ column, columnIndex }) {
      column.index = columnIndex;
    },
    inputBlur() {
      this.clickRow = null;
      this.clickCell = null;
    },
  },
};
</script>

<template>
  <div class="view_editor">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="基本信息" name="first">
        <ColumnTableSpaceList
          ref="columnTableSpaceList"
          :currentConfig="currentConfig"
          :tableData="tableSpaceList"
        />
      </el-tab-pane>
      <el-tab-pane label="Script" name="second">
        <MonacoEditor
          ref="MonacoEditor"
          dom="editor"
          style="width: 100%; height: calc(100% - 40px)"
        />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped lang="scss">
.view_editor {
  height: calc(100% - 58px);
  background: #fff;
  padding: 10px 10px 0;
  border-radius: 5px;
  position: relative;
  .btn_box {
    display: flex;
    justify-content: end;
  }
  .monaco_btn {
    display: flex;
    align-items: center;
    padding: 10px 20px;
    ::v-deep .el-button {
      & > span {
        display: flex;
        align-items: center;
        img {
          margin-right: 6px;
        }
      }
    }
    .el-button_before::before {
      content: "";
      position: absolute;
      right: -15px;
      top: 8px;
      width: 1px;
      height: 14px;
      background: #68728c;
    }
  }
}
.el-tabs {
  height: calc(100% - 205px);
  ::v-deep .el-tabs__content {
    height: calc(100% - 55px);
    .el-tab-pane {
      height: 100%;
    }
  }
}
</style>
