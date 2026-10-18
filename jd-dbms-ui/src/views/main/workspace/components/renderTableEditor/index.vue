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
import axios from "axios";
export default {
  props: {
    queryResultData: {
      type: Object,
      default: () => ({})
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
      loadedSections: {},
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
      relationCancelSource: null,
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
        this.initData();
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
  methods: {
    backToOverview() {
      this.$emit("restoreOverview", this.currentConfig?.uniqueData || {});
    },
    initData() {
      this.currentConfig = this.queryResultData;
      if (!this.currentConfig?.uniqueData?.dataSourceId) return Promise.resolve();
      const config = this.currentConfig;
      this.basicForm = config.basicForm || { name: config.uniqueData.tableName || "", comment: "", charset: "", engine: "", incrementValue: "" };
      this.columnList = config.columnList || [];
      this.indexList = config.indexList || [];
      this.constraints = config.constraints || [];
      this.triggerList = config.triggerList || [];
      this.grantList = config.grantList || [];
      this.querySql = config.querySql || "";
      this.oldTableDetails = config.oldTableDetails || null;
      this.loadedSections = config.loadedSections || {};
      this.activeName = config.activeName || "sixth";
      this.databaseSupportField = config.databaseSupportField || { columnTypes: [], charsets: [], collations: [], indexTypes: [], defaultValues: [] };
      if (config.title === "新建表") return this.getDatabaseFieldTypeList();
      return this.handleClick();
    },
    getDatabaseFieldTypeList() {
      let send = {
        dataSourceId: this.currentConfig?.uniqueData?.dataSourceId,
        databaseName: this.currentConfig?.uniqueData?.databaseName
      };
      if (!send.dataSourceId) return;
      if (this.currentConfig.databaseSupportField) return Promise.resolve();
      if (this._fieldTypeRequest && this._fieldTypeConfig === this.currentConfig) return this._fieldTypeRequest;
      const config = this.currentConfig;
      this._fieldTypeConfig = config;
      this._fieldTypeRequest = tableServer.getDatabaseFieldTypeList(send).then(({ data }) => {
        if (this.currentConfig !== config) return;
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
        this.currentConfig.databaseSupportField = this.databaseSupportField;
      }).catch(() => {
        this.$message.error("加载字段类型失败，请稍后重试");
      }).finally(() => {
        if (this._fieldTypeConfig === config) this._fieldTypeRequest = null;
      });
      return this._fieldTypeRequest;
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
        this.currentConfig.loadedSections = { ...this.loadedSections };
        this.currentConfig.constraints = this.constraints;
        this.currentConfig.triggerList = this.triggerList;
        this.currentConfig.grantList = this.grantList;
        this.currentConfig.querySql = this.querySql;
      }
    },
    getTableDetails(value) {
      return this.loadTableSection(this.activeName, value === true);
    },
    loadColumns() {
      return this.loadTableSection("second");
    },
    loadTableSection(tab, refresh = false) {
      const section = { second: "columns", third: "indexes", forth: "constraints", first: "basic", seventh: "ddl" }[tab];
      if (!section || !this.currentConfig?.uniqueData?.tableName) return Promise.resolve(true);
      if (!refresh && this.loadedSections[tab]) {
        if (tab === "seventh") this.$nextTick(() => this.$refs.MonacoEditor?.setValue(this.querySql));
        return Promise.resolve(true);
      }
      const config = this.currentConfig;
      const key = [config.id, config.uniqueData.dataSourceId, config.uniqueData.schemaName, config.uniqueData.tableName, section].join(":");
      if (!this._sectionRequests) this._sectionRequests = new Map();
      if (this._sectionRequests.has(key)) return this._sectionRequests.get(key);
      this.isLoading = true;
      const params = {
        dataSourceId: this.currentConfig?.uniqueData.dataSourceId,
        databaseName: this.currentConfig?.uniqueData.databaseName,
        tableName: this.currentConfig?.uniqueData.tableName,
        schemaName: this.currentConfig?.uniqueData.schemaName,
        refresh: true,
        isRefreshCache: refresh
      };
      const request = tableServer.getTableSection(params, section).then(res => {
        if (this.currentConfig !== config || config.uniqueData.tableName !== params.tableName ||
          config.uniqueData.schemaName !== params.schemaName || config.uniqueData.dataSourceId !== params.dataSourceId) return false;
        if (!res || !res.success || !res.data) {
          this.$message.error(res?.errorMessage || "加载表结构失败");
          return false;
        }
        const field = { second: "columnList", third: "indexList", forth: "constraints", seventh: "querySql" }[tab];
        const baseline = tab === "first" ? Object.fromEntries(Object.keys(this.basicForm).map(name => [name, res.data[name]])) : { [field]: res.data[field] };
        this.oldTableDetails = { ...this.oldTableDetails, name: config.uniqueData.tableName, schemaName: config.uniqueData.schemaName, ...JSON.parse(JSON.stringify(baseline)) };
        if (tab === "first") {
          for (const name in this.basicForm) this.basicForm[name] = res.data[name];
        } else {
          this[field] = res.data[field] || (tab === "seventh" ? "" : []);
          if (tab === "second" || tab === "third") this[field].forEach(item => {
            if (!item.key) item.key = uuidv4();
            item.oldName = item.name;
          });
          if (tab === "seventh") this.$nextTick(() => {
            this.$refs.MonacoEditor?.setValue(this.querySql);
            this.formatSql();
          });
        }
        this.loadedSections[tab] = true;
        if (refresh && tab === "forth") {
          for (const relatedTab of ["second", "third", "seventh"]) delete this.loadedSections[relatedTab];
        }
        return true;
      }).catch(() => {
        this.$message.error("加载表结构失败，请稍后重试");
        return false;
      }).finally(() => {
        this._sectionRequests.delete(key);
        this.isLoading = this._sectionRequests.size > 0;
      });
      this._sectionRequests.set(key, request);
      return request;
    },
    queryTriggerList(refresh = false) {
      if (!refresh && this.loadedSections.fifth) return Promise.resolve();
      const config = this.currentConfig;
      const params = {
        dataSourceId: this.currentConfig?.uniqueData.dataSourceId,
        databaseName: this.currentConfig?.uniqueData.databaseName,
        schemaName: this.currentConfig?.uniqueData.schemaName,
        databaseType: this.currentConfig?.uniqueData.databaseType,
        tableName: this.currentConfig?.uniqueData.tableName,
        refresh,
        pageNo: 1,
        pageSize: 1000
      };
      return sqlServer.getTriggerList(params).then(res => {
        if (this.currentConfig !== config || !res.success) return;
        if (res.data.data.length > 0) {
          this.triggerList = res.data.data;
        } else {
          this.triggerList = [];
        }
        this.loadedSections["fifth"] = true;
      }).catch(() => {
        this.$message.error("加载触发器失败，请稍后重试");
      });
    },
    queryGrantList() {
      if (this.loadedSections.eigth) return Promise.resolve();
      const config = this.currentConfig;
      const params = {
        dataSourceId: this.currentConfig?.uniqueData.dataSourceId,
        databaseName: this.currentConfig?.uniqueData.databaseName,
        schemaName: this.currentConfig?.uniqueData.schemaName,
        tableName: this.currentConfig?.uniqueData.tableName
      };
      return tableServer.getGrantList(params).then(res => {
        if (this.currentConfig !== config || !res.success) return;
        this.grantList = res.data;
        this.loadedSections["eigth"] = true;
      }).catch(() => {
        this.$message.error("加载授权失败，请稍后重试");
      });
    },

    // 被引用情况
    queryReferencedList(ref, relationType) {
      const type = relationType || this.relationType;
      const relationKey = [
        this.currentConfig?.uniqueData.dataSourceId,
        this.currentConfig?.uniqueData.databaseName,
        this.currentConfig?.uniqueData.schemaName,
        this.currentConfig?.uniqueData.tableName,
        type,
      ];
      const key = JSON.stringify(relationKey);
      if (!ref && (this.relationLoadingKey === key || this.relationLoadedKey === key)) {
        return;
      }
      const requestId = ++this.relationRequestId;
      if (this.relationCancelSource) this.relationCancelSource.cancel("关联关系查询已取消");
      this.relationCancelSource = axios.CancelToken.source();
      this.relationLoadingKey = key;
      this.isLoading = true;
      const params = {
        isRefreshCache: ref ? true : false,
        dataSourceId: this.currentConfig?.uniqueData.dataSourceId,
        databaseName: this.currentConfig?.uniqueData.databaseName,
        schemaName: this.currentConfig?.uniqueData.schemaName,
        tableName: this.currentConfig?.uniqueData.tableName
      };
      const requestConfig = {
        cancelToken: this.relationCancelSource.token,
        timeout: 15000,
      };
      const query = type === "view"
        ? tableServer.viewDependent({ ...params, check: "1" }, requestConfig)
        : tableServer.getQueryRefer(params, requestConfig);
      return query
        .then(res => {
          if (requestId !== this.relationRequestId) return;
          if (res.success && res.data) {
            this.ReferencedList = [res.data];
          } else {
            this.ReferencedList = [];
          }
          if (res.success) this.relationLoadedKey = key;
        })
        .catch(error => {
          if (axios.isCancel(error)) return;
          if (requestId === this.relationRequestId) this.ReferencedList = [];
        })
        .finally(() => {
          if (requestId !== this.relationRequestId) return;
          this.relationCancelSource = null;
          this.relationLoadingKey = "";
          this.isLoading = false;
        });
    },
    refreshFn() {
      this.queryTriggerList(true);
    },
    refreshForKe() {
      this.queryReferencedList(true);
    },
    changeRelationType(type) {
      this.relationType = type;
      this.queryReferencedList(false, type);
    },
    cancelRelationQuery(showMessage = true) {
      if (!this.relationCancelSource) return;
      this.relationRequestId += 1;
      this.relationCancelSource.cancel("关联关系查询已取消");
      this.relationCancelSource = null;
      this.relationLoadingKey = "";
      this.isLoading = false;
      if (showMessage) this.$message.info("已取消关联关系查询");
    },
    // 格式化sql代码
    formatSql() {
      this.$refs.MonacoEditor?.formatSql(this.$refs.MonacoEditor.getValue());
    },
    handleClick() {
      if (this.activeName !== "ninth") this.cancelRelationQuery(false);
      if (this.activeName == "ninth") {
        return this.queryReferencedList(false);
      } else if (this.activeName == "eigth") {
        return this.queryGrantList();
      } else if (this.activeName == "fifth") {
        return this.queryTriggerList();
      }
      if (this.activeName === "second" || this.activeName === "third") this.getDatabaseFieldTypeList();
      return this.loadTableSection(this.activeName);
    },
    async submit() {
      if (this.currentConfig?.uniqueData?.tableName) {
        const results = await Promise.all(["second", "third", "first"].map(tab => this.loadTableSection(tab)));
        if (results.some(result => !result)) return;
      }
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
          if (!res.data[0]?.sql?.trim() || res.data[0].sql.trim() === ";") {
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
        if (!res.success || !res.data.success) return;
        this.$refs.sqlPreview.dialogClose();
        this.currentConfig.title = this.basicForm.name;
        this.currentConfig.uniqueData.tableName = this.basicForm.name;
        let _workspaceTabList = this.openConfigList.map(item => {
          if (item.id === this.currentConfig.id) return this.currentConfig;
          return item;
        });
        this.$store.commit("SET_WORKSPACETABLIST", _workspaceTabList);
        this.loadedSections = {};
        this.currentConfig.loadedSections = {};
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
  },
  beforeUnmount() {
    this.cancelRelationQuery(false);
  }
};
</script>

<template>
  <div class="table_editor">
    <el-button
      class="back-overview-button"
      size="small"
      type="primary"
      plain
      @click="backToOverview"
    >
      <i class="el-icon-back"></i> 返回模式总览
    </el-button>
    <el-tabs v-model="activeName" @tab-change="handleClick">
      <el-tab-pane label="数据" name="sixth">
        <renderSearchResult :queryResultData="queryResultData" ref="renderSearchResult"></renderSearchResult>
      </el-tab-pane>
      <el-tab-pane label="列信息" name="second" lazy>
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
      <el-tab-pane label="索引信息" name="third" lazy>
        <IndexList
          :currentConfig="currentConfig"
          :databaseSupportField="databaseSupportField"
          :columnList="columnList"
          :loadColumns="loadColumns"
          :tableData="indexList"
          :isLoading="isLoading"
          :getTableDetails="getTableDetails"
        />
      </el-tab-pane>
      <el-tab-pane label="约束" name="forth" lazy>
        <constraintList
          :tableData="constraints"
          :currentConfig="currentConfig"
          :isLoading="isLoading"
          :getTableDetails="getTableDetails"
        />
      </el-tab-pane>
      <el-tab-pane label="建表语句" name="seventh" lazy>
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
      <el-tab-pane label="基本信息" name="first" lazy>
        <BaseInfo ref="baseInfo" :currentConfig="currentConfig" :basic-form="basicForm" />
      </el-tab-pane>
      <el-tab-pane label="触发器" name="fifth" lazy>
        <TriggerList
          :tableData="triggerList"
          :currentConfig="currentConfig"
          @refreshFn="refreshFn"
        />
      </el-tab-pane>
      <el-tab-pane label="授权" name="eigth" lazy>
        <GrantList :tableData="grantList" />
      </el-tab-pane>
      <el-tab-pane label="关联关系" name="ninth" lazy>
        <UsedByList
          :tableData="ReferencedList"
          :isLoading="isLoading"
          :relationType="relationType"
          @refreshForKe="refreshForKe"
          @changeRelationType="changeRelationType"
          @cancelRelationQuery="cancelRelationQuery"
        />
      </el-tab-pane>
    </el-tabs>
    <el-button
      class="table-save-button"
      @click="submit()"
      size="small"
      type="primary"
      plain
      v-if=" activeName == 'second' || activeName == 'third' || activeName == 'first'"
    >保存</el-button>
    <SqlPreview ref="sqlPreview" title="sql预览">
      <el-button
        style="position: absolute; right: 60px; top: 12px"
        @click="executeUpdateDataSql"
        size="small"
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
  right: 20px;
  top: 17px;
  z-index: 2;
}
.table-save-button {
  position: absolute;
  right: 160px;
  top: 17px;
  z-index: 2;
}
</style>
