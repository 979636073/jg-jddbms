<script>
import BaseInfo from "@/views/main/workspace/components/renderTableEditor/BaseInfo.vue";
import ColumnList from "@/views/main/workspace/components/renderTableEditor/ColumnList.vue";
import IndexList from "@/views/main/workspace/components/renderTableEditor/IndexList.vue";
import ConstraintList from "@/views/main/workspace/components/renderTableEditor/ConstraintList.vue";
import GrantList from "@/views/main/workspace/components/renderTableEditor/GrantList.vue";
import ScriptInfo from "@/views/main/workspace/components/renderTableEditor/ScriptInfo.vue";
import TriggerList from "@/views/main/workspace/components/renderTableEditor/TriggerList.vue";
import UsedByList from "@/views/main/workspace/components/renderTableEditor/UsedByList.vue";
import sqlServer from "@/api/main/sql";
import tableServer from "@/api/main/table";
import SqlPreview from "@/views/main/workspace/components/DataSource/SqlPreview.vue";
import CreateSql from "@/views/main/workspace/components/CreateSql/index.vue";
import renderSearchResult from "../../components/renderSearchResult/index.vue";
import MonacoEditor from "@/components/MonacoEditor/index.vue";
import { v4 as uuidv4 } from "uuid";
import { downloadFile } from "@/utils/file";
export default {
  props: {
    queryResultData: {
      type: Object,
      default: () => {}
    }
  },
  name: "createOrUpdateTable",
  components: {
    renderSearchResult,
    SqlPreview,
    IndexList,
    ColumnList,
    BaseInfo,
    ConstraintList,
    GrantList,
    ScriptInfo,
    TriggerList,
    UsedByList,
    CreateSql,
    MonacoEditor
  },
  data() {
    return {
      flag: false,
      currentConfig: null,
      activeName: "sixth",
      oldTableDetails: null,
      // 基本信息
      basicForm: {
        name: "",
        comment: "",
        charset: "",
        engine: "",
        incrementValue: ""
      },
      columnList: [],
      indexList: [],
      constraints: [],
      triggerList: [],
      grantList: [],
      querySql: "",
      dataTable: [],
      ReferencedList: [],
      relationType: "table",
      relationRequestId: 0,
      relationLoadingKey: "",
      relationLoadedKey: "",
      isLoading: false,
      databaseSupportField: {
        columnTypes: [],
        charsets: [],
        collations: [],
        indexTypes: [],
        defaultValues: []
      }
    };
  },
  watch: {
    changeFlag: {
      handler() {
        this.configChange();
        this.initData().then(() => {
          this.getDatabaseFieldTypeList();
        });
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
      },
      immediate: true,
      deep: true
    }
  },
  computed: {
    openConfigList() {
      return this.$store.state.workspace.workspaceTabList;
    },
    openConfigValue() {
      return this.$store.state.workspace.activeConsoleId;
    },
    changeFlag() {
      return this.$store.state.workspaceData.changeFlag;
    },
    currentDataSource() {
      return this.$store.state.workspace.currentConnectionDetails;
    }
  },
  mounted() {
    this.flag = false;
  },
  methods: {
    backToOverview() {
      this.$store.dispatch("workspaceData/setDataCurrentData", {
        pageId: this.$route.params.id,
        uniqueData: false
      });
    },
    initData() {
      return new Promise(resolve => {
        this.currentConfig = this.queryResultData;
        if (this.currentConfig?.basicForm) {
          this.basicForm = this.currentConfig?.basicForm;
          this.columnList = this.currentConfig?.columnList;
          this.indexList = this.currentConfig?.indexList;
          this.oldTableDetails = this.currentConfig?.oldTableDetails;
          this.activeName = this.currentConfig?.activeName;
        } else {
          if (this.currentConfig?.title !== "新建表") {
            if (this.activeName == "ninth") {
              this.queryReferencedList(false);
            } else if (this.activeName == "eigth") {
              this.queryGrantList();
            } else if (this.activeName == "fifth") {
              this.queryTriggerList();
            }
            // else if ( this.activeName == "second" || this.activeName == "third" || this.activeName == "forth" || this.activeName == "first" ||this.activeName == "seventh") {
            //   this.getTableDetails();
            // }
            // 默认停留在“数据”页签时无需立即查询完整表元数据，切换到
            // 列信息、索引、约束等页签后由 handleClick 按需加载。
            if (this.activeName !== "sixth" && this.activeName !== "ninth" && this.activeName !== "eigth" && this.activeName !== "fifth") {
              this.getTableDetails();
            }
          } else {
            this.basicForm = {
              name: "",
              comment: "",
              charset: "",
              engine: "",
              incrementValue: ""
            };
            this.columnList = [];
            this.indexList = [];
            this.oldTableDetails = null;
          }
        }
        resolve();
      });
    },
    getDatabaseFieldTypeList() {
      let send = {
        dataSourceId: this.currentConfig?.uniqueData.dataSourceId,
        databaseName: this.currentConfig?.uniqueData.databaseName
      };
      tableServer.getDatabaseFieldTypeList(send).then(({ data }) => {
        const columnTypes =
          data?.columnTypes?.map(i => {
            return {
              ...i,
              value: i.typeName,
              label: i.typeName
            };
          }) || [];

        const charsets =
          data?.charsets?.map(i => {
            return {
              value: i.charsetName,
              label: i.charsetName
            };
          }) || [];

        const collations =
          data?.collations?.map(i => {
            return {
              value: i.collationName,
              label: i.collationName
            };
          }) || [];

        const indexType =
          data?.indexTypes?.map(i => {
            return {
              value: i.typeName,
              label: i.typeName
            };
          }) || [];

        const defaultValues =
          data?.defaultValues?.map(i => {
            return {
              value: i.defaultValue,
              label: i.defaultValue
            };
          }) || [];

        const indexTypes = indexType.filter(
          item => item.label == "Primary" || item.label == "Unique"
        );
        this.databaseSupportField = {
          columnTypes,
          charsets,
          collations,
          indexTypes,
          defaultValues
        };
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
        this.currentConfig.indexList = JSON.parse(
          JSON.stringify(this.indexList)
        );
        this.currentConfig.oldTableDetails = JSON.parse(
          JSON.stringify(this.oldTableDetails)
        );
        this.currentConfig.activeName = this.activeName;
      }
    },
    getTableDetails(value) {
      this.isLoading = true;
      const params = {
        dataSourceId: this.currentConfig?.uniqueData.dataSourceId,
        databaseName: this.currentConfig?.uniqueData.databaseName,
        tableName: this.currentConfig?.uniqueData.tableName,
        schemaName: this.currentConfig?.uniqueData.schemaName,
        refresh: true,
        isRefreshCache: value === true ? true : false
      };
      tableServer.getTableDetails(params).then(res => {
        if (!res || !res.success || !res.data) {
          this.$message.error(res?.errorMessage || "加载表结构失败");
          return;
        }
        this.$refs.MonacoEditor?.setValue(res.data.querySql);
        this.formatSql();
        this.oldTableDetails = JSON.parse(JSON.stringify(res.data));
        res.data?.columnList?.forEach(item => {
          if (!item.key) item.key = uuidv4();
          item.oldName = item.name;
        });

        res.data?.indexList?.forEach(item => {
          if (!item.key) item.key = uuidv4();
          item.oldName = item.name;
        });

        this.columnList = res.data?.columnList || [];
        this.indexList = res.data?.indexList || [];
        this.constraints = res.data?.constraints || [];
        for (const resKey in this.basicForm) {
          this.basicForm[resKey] = res.data[resKey];
        }
      }).catch(() => {
        this.$message.error("加载表结构失败，请稍后重试");
      }).finally(() => {
        this.isLoading = false;
      });
    },
    queryTriggerList() {
      const params = {
        dataSourceId: this.currentConfig?.uniqueData.dataSourceId,
        databaseName: this.currentConfig?.uniqueData.dataSourceName,
        schemaName: this.currentConfig?.uniqueData.schemaName,
        databaseType: this.currentConfig?.uniqueData.databaseType,
        tableName: this.currentConfig?.uniqueData.tableName,
        refresh: false,
        pageNo: 1,
        pageSize: 1000
      };
      sqlServer.getTriggerList(params).then(res => {
        if (res.data.data.length > 0) {
          this.triggerList = res.data.data;
        } else {
          this.triggerList = [];
        }
      });
    },
    queryGrantList() {
      const params = {
        dataSourceId: this.currentConfig?.uniqueData.dataSourceId,
        databaseName: this.currentConfig?.uniqueData.dataSourceName,
        schemaName: this.currentConfig?.uniqueData.schemaName,
        tableName: this.currentConfig?.uniqueData.tableName
      };
      tableServer.getGrantList(params).then(res => {
        this.grantList = res.data;
      });
    },

    // 被引用情况
    queryReferencedList(ref, relationType) {
      const type = relationType || this.relationType;
      const relationKey = [
        this.currentConfig?.uniqueData.dataSourceId,
        this.currentConfig?.uniqueData.schemaName,
        this.currentConfig?.uniqueData.tableName,
        type,
      ].join(":");
      if (!ref && (this.relationLoadingKey === relationKey || this.relationLoadedKey === relationKey)) {
        return;
      }
      const requestId = ++this.relationRequestId;
      this.relationLoadingKey = relationKey;
      this.isLoading = true;
      const params = {
        isRefreshCache: ref ? true : false,
        dataSourceId: this.currentConfig?.uniqueData.dataSourceId,
        databaseName: this.currentConfig?.uniqueData.dataSourceName,
        schemaName: this.currentConfig?.uniqueData.schemaName,
        tableName: this.currentConfig?.uniqueData.tableName
      };
      const query = type === "view"
        ? tableServer.viewDependent({ ...params, check: "1" })
        : tableServer.getQueryRefer(params);
      query
        .then(res => {
          if (requestId !== this.relationRequestId) return;
          if (res.success && res.data) {
            this.ReferencedList = [res.data];
          } else {
            this.ReferencedList = [];
          }
          this.relationLoadedKey = relationKey;
        })
        .catch(() => {
          if (requestId === this.relationRequestId) this.ReferencedList = [];
        })
        .finally(() => {
          if (requestId !== this.relationRequestId) return;
          this.relationLoadingKey = "";
          this.isLoading = false;
        });
    },
    refreshFn() {
      this.queryTriggerList();
    },
    refreshForKe() {
      this.queryReferencedList(true);
    },
    changeRelationType(type) {
      this.relationType = type;
      this.queryReferencedList(false, type);
    },
    // 格式化sql代码
    formatSql() {
      this.$refs.MonacoEditor?.formatSql(this.$refs.MonacoEditor.getValue());
    },
    handleClick() {
      if (this.activeName == "ninth") {
        this.queryReferencedList(false);
      } else if (this.activeName == "eigth") {
        this.queryGrantList();
      } else if (this.activeName == "fifth") {
        this.queryTriggerList();
      } else if (
        this.activeName == "second" ||
        this.activeName == "third" ||
        this.activeName == "forth" ||
        this.activeName == "first" ||
        this.activeName == "seventh"
      ) {
        if (!this.flag) {
          this.getTableDetails();
          this.flag = true;
        }
      } else if (this.activeName == "sixth") {
        this.$refs.renderSearchResult.advancedQuery();
        this.$refs.renderSearchResult.isColor = false;
      }
    },
    submit() {
      const newTable = {
        ...this.oldTableDetails,
        ...this.basicForm,
        columnList: this.columnList,
        indexList: this.indexList
      };

      let addRow = this.indexList
        .map((item, index) => {
          delete item.comment;
          return {
            ...item,
            index
          };
        })
        .filter(item => {
          return item.editStatus == "ADD";
        });
      if (addRow.length) {
        let keys = Object.keys(addRow[0]);
        try {
          addRow.forEach(row => {
            row.index++;
            console.log(row, "行数据");
            keys.forEach(key => {
              if (row[key] == "") {
                throw {
                  ...row
                };
              }
            });
          });
        } catch (errData) {
          let { index } = errData;
          this.$message.warning(`请补全列表第 ${index} 行数据！`);
          return;
        }
      }
      const params = {
        dataSourceId: this.currentConfig?.uniqueData.dataSourceId,
        databaseName: this.currentConfig?.uniqueData.databaseName,
        schemaName: this.currentConfig?.uniqueData.schemaName,
        refresh: true,
        newTable
      };

      if (this.currentConfig?.name !== "新增表") {
        params.oldTable = this.oldTableDetails;
      }
      tableServer.getModifyTableSql(params).then(res => {
        if (res.success) {
          if (res.data[0].sql == ";") {
            this.$message.warning("未发生修改，无需保存!");
          } else {
            this.$refs.sqlPreview.init(res.data[0]?.sql);
          }
        } else {
          this.$message.error(res.errorMessage);
        }
      });
    },
    executeUpdateDataSql() {
      let sql = this.$refs.sqlPreview.getValue();
      const params = {
        dataSourceId: this.currentConfig?.uniqueData.dataSourceId,
        databaseName: this.currentConfig?.uniqueData.databaseName,
        databaseType: this.currentConfig?.uniqueData.databaseType,
        sql: sql
      };
      sqlServer.executeDDL(params).then(res => {
        if (res.success) {
          if (res.data.success) {
            this.$message.success("执行成功");
          } else {
            this.$notify.error({
              title: "错误",
              message: res.data.message
            });
          }
        } else {
          this.$message.error("系统发生异常!");
        }
        this.$refs.sqlPreview.dialogClose();
        this.currentConfig.title = this.basicForm.name;
        this.currentConfig.uniqueData.tableName = this.basicForm.name;
        let _workspaceTabList = this.openConfigList.map(item => {
          if (item.id === this.currentConfig.id) return this.currentConfig;
          return item;
        });
        this.$store.commit("SET_WORKSPACETABLIST", _workspaceTabList);
        this.getTableDetails(true);
        if(this.activeName == 'first'){
           this.$emit("tableRefresh", 'tableRefresh');
        }else{
           this.$emit("tableRefresh", '');
        }
      });
    },
    setColumnList(columnList) {
      this.columnList = columnList;
    },
    // 建表语句导出
    exportTablestatement() {
      const params = {
        tableName: this.currentConfig?.uniqueData.tableName,
        schemaName: this.currentConfig?.uniqueData.schemaName,
        dataSourceId: this.currentConfig?.uniqueData.dataSourceId
      };
      downloadFile(
        process.env.VUE_APP_BASE_API + "/api/rdb/table/exportDDL",
        params
      );
    }
  }
};
</script>

<template>
  <div class="table_editor">
    <el-tabs v-model="activeName" @tab-click="handleClick">
      <el-button
        class="back-overview-button"
        size="mini"
        type="primary"
        plain
        @click="backToOverview"
      >
        <i class="el-icon-back"></i> 返回模式总览
      </el-button>
      <el-tab-pane label="数据" name="sixth">
        <renderSearchResult :queryResultData="queryResultData" ref="renderSearchResult"></renderSearchResult>
      </el-tab-pane>
      <el-tab-pane label="列信息" name="second">
        <ColumnList
          @setColumnList="setColumnList"
          :databaseSupportField="databaseSupportField"
          :tableData="columnList"
          :getTableDetails="getTableDetails"
          :isLoading="isLoading"
          :dataSourceId="currentConfig.uniqueData.dataSourceId"
          :queryResultData="queryResultData.params"
          ref="columList"
        />
      </el-tab-pane>
      <el-tab-pane label="索引信息" name="third">
        <IndexList
          :currentConfig="currentConfig"
          :databaseSupportField="databaseSupportField"
          :columnList="columnList"
          :tableData="indexList"
          :isLoading="isLoading"
          :getTableDetails="getTableDetails"
        />
      </el-tab-pane>
      <el-tab-pane label="约束" name="forth">
        <constraintList
          :tableData="constraints"
          :currentConfig="currentConfig"
          :isLoading="isLoading"
          :getTableDetails="getTableDetails"
        />
      </el-tab-pane>
      <el-tab-pane label="建表语句" name="seventh">
        <el-button
          type="primary"
          style="margin-bottom:15px"
          size="small"
          @click="exportTablestatement"
        >导出</el-button>
        <MonacoEditor
          ref="MonacoEditor"
          dom="editor"
          style="width: 100%; height: calc(100% - 47px)"
        />
      </el-tab-pane>
      <el-tab-pane label="基本信息" name="first">
        <BaseInfo ref="baseInfo" :currentConfig="currentConfig" :basic-form="basicForm" />
      </el-tab-pane>
      <el-tab-pane label="触发器" name="fifth">
        <TriggerList
          :tableData="triggerList"
          :currentConfig="currentConfig"
          @refreshFn="refreshFn"
        />
      </el-tab-pane>
      <el-tab-pane label="授权" name="eigth">
        <GrantList :tableData="grantList" />
      </el-tab-pane>
      <el-tab-pane label="关联关系" name="ninth">
        <UsedByList
          :tableData="ReferencedList"
          :isLoading="isLoading"
          :relationType="relationType"
          @refreshForKe="refreshForKe"
          @changeRelationType="changeRelationType"
        />
      </el-tab-pane>
    </el-tabs>
    <el-button
      @click="submit()"
      size="mini"
      type="primary"
      plain
      style="position: absolute; right: 10px; top: 10px"
      v-if=" activeName == 'second' || activeName == 'third' || activeName == 'first'"
    >保存</el-button>
    <SqlPreview ref="sqlPreview" title="sql预览">
      <el-button
        style="position: absolute; right: 60px; top: 12px"
        @click="executeUpdateDataSql"
        size="mini"
        type="primary"
        plain
      >
        <img src="@/assets/main/1-con-ico22.png" alt />
        执行
      </el-button>
    </SqlPreview>
  </div>
</template>

<style scoped lang="scss">
.table_editor {
  height: calc(100% - 58px);
  background: #fff;
  padding: 10px 10px 0 10px;
  border-radius: 5px;
  position: relative;
}
.el-tabs {
  height: 100%;
  ::v-deep .el-tabs__header {
    position: relative;
    padding-right: 145px;
  }
  ::v-deep .el-tabs__content {
    height: calc(100% - 55px);
    .el-tab-pane {
      height: 100%;
    }
  }
}
.back-overview-button {
  position: absolute;
  right: 10px;
  top: 5px;
  z-index: 2;
}
</style>
