<script>
import viewServer from "@/api/main/view";
import tableServer from "@/api/main/table";
import MonacoEditor from "@/components/MonacoEditor/index.vue";
import MonacoSqlEditor from "@/components/MonacoSqlEditor/index.vue";
import ColumnViewList from "../renderViewEditor/ColumnViewList.vue";
import DataInfoView from "../renderViewEditor/DataInfoView.vue";
import DespUses from "../renderViewEditor/DepsUsersView.vue";
import DespUsedBy from "../renderViewEditor/DepsUsedByView.vue";
import ViewGrantPanel from "./ViewGrantPanel.vue";
import SqlPreview from "@/views/main/workspace/components/DataSource/SqlPreview.vue";
import renderSearchResult from "../../components/renderSearchResult/index.vue";
import { downloadFile } from "@/utils/file";
export default {
  props: {
    queryResultData: {
      type: Object,
      default: () => {}
    },
    typeView: {
      type: String,
      default: ""
    }
  },
  components: {
    SqlPreview,
    MonacoEditor,
    MonacoSqlEditor,
    ColumnViewList,
    DataInfoView,
    DespUses,
    DespUsedBy,
    ViewGrantPanel,
    renderSearchResult
  },
  data() {
    return {
      currentConfig: null,
      activeName: "first",
      activeTab: "third",
      sql: "",
      saving: false,
      refreshing: false,
      compiling: false,
      // 基本信息
      basicForm: {
        dataSourceId: "",
        databaseName: "",
        schemaName: "",
        tableName: ""
      },
      viewName: "",
      columnList: [],
      dataTable: [],
      viewDependentData: [],
      viewDependentByData: [],
      dependencyLoading: { deps: false, depsBy: false },
      databaseSupportField: {
        columnTypes: [],
        charsets: [],
        collations: [],
        indexTypes: [],
        defaultValues: []
      },
      dependentData: [],

      clickRow: null, // 当前点击的行
      clickCell: null // 当前点击的列
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
    materializedView() {
      return String(this.queryResultData?.uniqueData?.viewType || "").toUpperCase() === "MATERIALIZED VIEW";
    }
  },
  watch: {
    openConfigValue: {
      handler() {
        this.configChange();
        // this.initData();
      },
      deep: true,
      immediate: true
    },
    queryResultData: {
      handler(newVal) {
        let tableData = newVal.dataList?.map(item => {
          let array = {};
          for (const dataKey in newVal.headerList) {
            array[newVal.headerList[dataKey].name] = item[dataKey];
          }
          return array;
        });
        this.dataTable = tableData;
        this.initData();
      },
      immediate: true,
      deep: true
    },
    activeTab: {
      handler(newVal) {
        if (newVal == "first") {
          this.$nextTick(() => {
            if (this.$refs.MonacoEditor) {
              this.$refs.MonacoEditor.setValue(this.sql || "");
              this.formatSql();
            }
          });
        } else if (newVal == "fourth") {
          this.viewDependentFn("deps");
        } else if (newVal == "deps") {
          this.viewDependentFn("depsBy");
        } else if (newVal == "info") {
          this.viewName = this.basicForm.tableName;
        }
      }
    }
  },
  methods: {
    initData(force = false) {
      return new Promise(resolve => {
        this.currentConfig = this.queryResultData;
        this.basicForm = this.currentConfig?.uniqueData;
        if (this.currentConfig?.columnList && !force) {
          this.columnList = this.currentConfig?.columnList;
        } else {
          if (this.currentConfig?.title !== "新建视图") {
            let send = {
              dataSourceId: this.currentConfig?.uniqueData.dataSourceId,
              databaseName: this.currentConfig?.uniqueData.databaseName,
              schemaName: this.currentConfig?.uniqueData.schemaName,
              tableName: this.currentConfig?.uniqueData.tableName
            };
            viewServer.getViewDetail(send).then(res => {
              if (!res.success || !res.data) {
                this.$message.error(res.errorMessage || "获取视图详情失败");
                return;
              }
              this.sql = res.data.ddl || "";
              this.columnList = res.data.columnList || [];
              if (this.activeTab == "first") {
                this.$nextTick(() => {
                  if (this.$refs.MonacoEditor) {
                    this.$refs.MonacoEditor.setValue(this.sql);
                    this.formatSql();
                  }
                });
              }
            }).catch(() => this.$message.error("获取视图详情失败，请稍后重试"));
            if (this.activeTab == "fourth") {
              this.viewDependentFn("deps");
            } else if (this.activeTab == "deps") {
              this.viewDependentFn("depsBy");
            } else if (this.activeTab == "info") {
              this.viewName = this.basicForm.tableName;
            }
          } else {
            this.basicForm = {
              dataSourceId: "",
              databaseName: "",
              schemaName: "",
              tableName: ""
            };
          }
        }
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
    viewDependentFn(type) {
      if (this.dependencyLoading[type]) return;
      const params = {
        dataSourceId: this.currentConfig?.uniqueData.dataSourceId,
        tableName: this.currentConfig?.uniqueData.tableName,
        schemaName: this.currentConfig?.uniqueData.schemaName,
        check: type == "deps" ? "1" : "0"
      };
      this.dependencyLoading[type] = true;
      tableServer.viewDependent(params).then(res => {
        if (res.success) {
          if (type == "deps") {
            this.viewDependentData = res.data ? [res.data] : [];
          } else {
            this.viewDependentByData = res.data ? [res.data] : [];
          }
        } else {
          this.$message.error(res.errorMessage || "获取依赖关系失败");
        }
      }).catch(() => this.$message.error("获取依赖关系失败"))
        .finally(() => { this.dependencyLoading[type] = false; });
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
        viewSql: viewSql
      };
      viewServer.getExecuteSQL(send).then(res => {
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
      let send = {
        dataSourceId: this.currentConfig?.uniqueData.dataSourceId,
        databaseName: this.currentConfig?.uniqueData.databaseName,
        schemaName: this.currentConfig?.uniqueData.schemaName,
        tableName: this.basicForm.tableName,
        viewSql: viewSql
      };
      viewServer.getViewColumnList(send).then(res => {
        if (res.success) {
          this.columnList = res.data.map(item => {
            return {
              name: item.name,
              comment: ""
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
        columnList: this.columnList
      };
      viewServer.viewShowSql(send).then(res => {
        if (res.success) {
          this.$refs.sqlPreview.init(res.data.sql);
        } else {
          this.$message.error(res.errorMessage);
        }
      });
    },
    async submit() {
      if (this.materializedView || this.saving) return;
      const viewSql = this.$refs.MonacoEditor.getValue().trim();
      if (!/^CREATE\s+(?:OR\s+REPLACE\s+)?VIEW\b/i.test(viewSql)) {
        this.$message.error("请填写 CREATE VIEW 语句");
        return;
      }
      let send = {
        dataSourceId: this.currentConfig?.uniqueData.dataSourceId,
        databaseName: this.currentConfig?.uniqueData.databaseName,
        schemaName: this.currentConfig?.uniqueData.schemaName,
        tableName: this.basicForm.tableName,
        viewSql: viewSql,
        columnList: this.columnList
      };
      this.saving = true;
      try {
        const res = await viewServer.viewExecute(send);
        if (!res.success || !res.data?.[0]?.success) throw new Error(res.data?.[0]?.message || res.errorMessage || "保存失败");
        this.$message.success("视图已保存");
        await this.initData(true);
        this.$emit("tableRefresh", this.currentConfig.uniqueData.node?.parent);
      } catch (error) {
        this.$message.error(error.message || "保存视图失败");
      } finally {
        this.saving = false;
      }
    },
    async compileView() {
      if (this.compiling || this.materializedView) return;
      this.compiling = true;
      try {
        const res = await viewServer.viewAllExecute({
          dataSourceId: this.basicForm.dataSourceId,
          schemaName: this.basicForm.schemaName,
          tableName: this.basicForm.tableName,
        });
        if (!res.success || !res.data?.[0]?.success) throw new Error(res.data?.[0]?.message || res.errorMessage || "编译失败");
        this.$message.success("视图编译成功");
      } catch (error) {
        this.$message.error(error.message || "视图编译失败");
      } finally {
        this.compiling = false;
      }
    },
    async refreshMaterializedView() {
      if (!this.materializedView || this.refreshing) return;
      this.refreshing = true;
      try {
        const res = await viewServer.refreshMaterialized({
          dataSourceId: this.basicForm.dataSourceId,
          schemaName: this.basicForm.schemaName,
          tableName: this.basicForm.tableName,
          viewType: "MATERIALIZED VIEW",
        });
        if (!res.success) throw new Error(res.errorMessage || "刷新失败");
        this.$message.success("物化视图已刷新");
        await this.initData(true);
        this.$refs.renderSearchResult.refresh();
      } catch (error) {
        this.$message.error(error.message || "物化视图刷新失败");
      } finally {
        this.refreshing = false;
      }
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
    saveView() {
      const params = {
        newViewName: this.viewName,
        oldViewName: this.basicForm.tableName,
        dataSourceId: this.queryResultData.uniqueData?.dataSourceId,
        schemaName: this.currentConfig?.uniqueData.schemaName
      };
      viewServer.updateViewName(params).then(res => {
        if (res.data) {
          this.$message.success("修改成功");
          this.$emit(
            "updateViewName",
            this.currentConfig?.uniqueData.schemaName
          );
        }
      });
    },
    // 纯前端做文件流导出
    importScript() {
      let viewSql = this.$refs.MonacoEditor.getValue();
      let params={
        dataSourceId: this.currentConfig?.uniqueData.dataSourceId,
        sql: viewSql,
      }
      downloadFile(
        process.env.VUE_APP_BASE_API + "/api/rdb/table/exportViewDDL",
        { ...params }
      );
      // const byteArray = new TextEncoder().encode(this.sql);
      // const blob = new Blob([byteArray], { type: "text/sql;charset=utf-8" });
      // const url = URL.createObjectURL(blob);
      // const a = document.createElement('a');
      // a.href = url;
      // // 名称后面追加时间戳
      // const currentTime = new Date().getTime()
      // a.download = `${this.queryResultData.title}${currentTime}.sql`;
      // document.body.appendChild(a);
      // a.click();
      // document.body.removeChild(a);
      // URL.revokeObjectURL(url);

    }
  }
};
</script>

<template>
  <div class="view_editor">
    <div class="view-editor-header">
      <span class="view-editor-kind">{{ materializedView ? '物化视图' : '普通视图' }}</span>
      <span class="view-editor-name">{{ basicForm && basicForm.tableName }}</span>
      <el-button v-if="materializedView" type="primary" size="mini" :loading="refreshing" @click="refreshMaterializedView">刷新数据</el-button>
      <el-button v-else size="mini" :loading="compiling" @click="compileView">编译</el-button>
    </div>
    <el-tabs v-model="activeTab">
      <el-tab-pane label="数据" name="third">
        <renderSearchResult ref="renderSearchResult" :queryResultData="queryResultData" :typeView="typeView"></renderSearchResult>
      </el-tab-pane>
      <el-tab-pane label="列信息" name="second">
        <ColumnViewList
          :tableData="columnList"
          :queryResultData="queryResultData"
          :initData="initData"
        />
      </el-tab-pane>
      <el-tab-pane label="基本信息" name="info">
        <el-button v-if="!materializedView" type="primary" @click="saveView" size="small">保存名称</el-button>
        <div v-else class="view-editor-note">物化视图的名称和定义请在脚本中查看；更新数据请点击上方“刷新数据”。</div>
        <div style="font-size:14px;margin:10px">视图名称</div>
        <el-input v-model="viewName" :disabled="materializedView" size="small" maxlength="50" show-word-limit></el-input>
      </el-tab-pane>
      <el-tab-pane label="脚本" name="first">
        <el-button type="primary" size="mini" style="margin-bottom:10px" @click="importScript">导出</el-button>
        <el-button v-if="!materializedView" type="primary" size="mini" style="margin-bottom:10px" :loading="saving" @click="submit">保存定义</el-button>
        <span v-else class="view-editor-note">物化视图脚本仅供查看</span>
        <MonacoEditor
          ref="MonacoEditor"
          dom="editor"
          style="width: 100%; height: calc(100% - 40px)"
        />
      </el-tab-pane>
      <el-tab-pane label="依赖" name="fourth">
        <DespUses v-loading="dependencyLoading.deps" :viewDependentData="viewDependentData" />
      </el-tab-pane>
      <el-tab-pane label="被依赖" name="deps">
        <DespUsedBy v-loading="dependencyLoading.depsBy" :viewDependentData="viewDependentByData" />
      </el-tab-pane>
      <el-tab-pane label="授权" name="grant">
        <ViewGrantPanel v-if="activeTab === 'grant'" :key="queryResultData.uniqueData && [queryResultData.uniqueData.dataSourceId, queryResultData.uniqueData.schemaName, queryResultData.uniqueData.tableName].join(':')" :query-result-data="queryResultData" :disabled="currentConfig && currentConfig.title === '新建视图'" />
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
  .view-editor-header { display: flex; align-items: center; gap: 8px; height: 34px; padding: 0 4px 8px; border-bottom: 1px solid #e5eaf2; font-size: 12px; }
  .view-editor-header .el-button { margin-left: auto; }
  .view-editor-kind { color: #006fff; background: #f0f5ff; border-radius: 3px; padding: 3px 6px; }
  .view-editor-name { color: #29364d; font-weight: 600; }
  .view-editor-note { color: #68758b; font-size: 12px; margin-left: 8px; }
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
  height: calc(100% - 34px);
  ::v-deep .el-tabs__content {
    height: calc(100% - 55px);
    .el-tab-pane {
      height: 100%;
    }
  }
}
</style>
