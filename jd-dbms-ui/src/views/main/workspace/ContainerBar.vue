<script>
import renderSqlExecute from "./components/reanderSqlExecute/index.vue";
import renderTableEditor from "./components/renderTableEditor/index.vue";
import renderViewEditor from "./components/renderViewEditor/index.vue";
import renderUserEditor from "./components/renderUserEditor/index.vue";
import renderTableSpaceEditor from "./components/renderTableSpaceEditor/index.vue";
import DataSourceConfig from "./components/DataSource/index.vue";
import renderSearchResult from "./components/renderSearchResult/index.vue";
import ViewAllTable from "./components/ViewAllTable";
import ViewAllView from "./components/ViewAllView";
import CreateSql from "@/views/main/workspace/components/CreateSql/index.vue";
import TableViewList from "./components/mainListView/tableViewList.vue";
import ViewList from "./components/mainListView/viewList.vue";
import UserList from "./components/mainListView/userList.vue";
import TableSpace from "./components/mainListView/tableSpace.vue";
import historyServer from "@/api/main/history";
import { workspaceTabConfig } from "@/views/main/workspace/constants/workspace";
export default {
  name: "consoleTabs",
  props: {
    typeView: {
      type: String,
      default: ""
    },
    detailDataSource: {
      type: Array,
      default: []
    },
    detailDataSourceTotal: {
      type: Number,
      default: 0
    },
    filterName: {
      type: String,
      default: ""
    },
    schema: {
      type: String,
      default: ""
    },
    dataInfo: {
      type: Object,
      default: {}
    },
    detailsObj: {
      default: {}
    }
  },
  components: {
    renderSqlExecute,
    renderTableEditor,
    renderViewEditor,
    renderUserEditor,
    renderTableSpaceEditor,
    DataSourceConfig,
    renderSearchResult,
    ViewAllTable,
    ViewAllView,
    CreateSql,
    TableViewList,
    ViewList,
    UserList,
    TableSpace
  },
  data() {
    return {
      // 正在修改的tab
      cachePageId: "",
      editing: {
        key: "",
        label: ""
      },
      consoleList: [],
      tableData: [],
      // 当前控制台展示结果
      currentConfig: null,
      workspaceTabItems: [],
      // 根据不同的tab类型渲染不同的内容
      workspaceTabConnectionMap: {
        reanderSqlExecute: [
          "table",
          "console",
          "function",
          "procedure",
          "trigger",
          "view"
        ],
        renderTableEditor: ["createTable", "editTable"],
        tableView: ["testTable"],
        renderViewEditor: ["createView", "editView"],
        renderSearchResult: ["editTableData"],
        renderViewAllTable: ["viewAllTable"],
        renderViewAllView: ["viewAllView"],
        renderUserEditor: ["editUser"],
        renderTableSpaceEditor: ["editTableSpace"],
        createSql: ["createSql"]
      }
    };
  },
  computed: {
    workspaceTabList() {
      return this.$store.state.workspace.workspaceTabList;
    },
    // activeConsoleId() {
    //   return this.$store.state.workspace.activeConsoleId;
    // },
    currentDataSource() {
      return this.$store.state.workspace.currentConnectionDetails;
    },
    pageId() {
      return this.$route.params.id;
    },
    dataCurrentDataMap() {
      return this.$store.state.workspaceData.dataCurrentData;
    },
    changeFlag() {
      return this.$store.state.workspaceData.changeFlag;
    }
  },
  watch: {
    // consoleList: {
    //   handler(val) {
    //     const _workspaceTabItems = val?.map(item => {
    //       return {
    //         id: item.id,
    //         type: item.operationType,
    //         title: item.name,
    //         uniqueData: {
    //           dataSourceId: item.dataSourceId,
    //           dataSourceName: item.dataSourceName,
    //           databaseType: item.type,
    //           databaseName: item.databaseName,
    //           schemaName: item.schemaName,
    //           status: item.status,
    //           ddl: item.ddl,
    //           connectable: item.connectable
    //         }
    //       };
    //     });
    //     this.$store.commit("SET_WORKSPACETABLIST", _workspaceTabItems);
    //   },
    //   deep: true
    // },
    workspaceTabList: {
      handler(newVal) {
        this.workspaceTabItems = newVal?.map(item => {
          return {
            prefixIcon: workspaceTabConfig[item.type]?.icon,
            label: item.title,
            key: item.id,
            editableName: item.type === "console"
          };
        });
      },
      immediate: true,
      deep: true
    },
    // activeConsoleId(val) {
    //   this.currentConfig = this.workspaceTabList.find(item => item.id === val);
    //   console.log(
    //     "this.currentConfig",
    //     this.currentConfig,
    //     this.currentConfig.type
    //   );

    //   this.$nextTick(() => {
    //     if (this.currentConfig?.uniqueData?.loadSQL) {
    //       this.currentConfig?.uniqueData.loadSQL().then(res => {
    //         this.$refs.renderSqlExecute.setViewSql(res);
    //       });
    //     } else {
    //       this.$refs?.renderSqlExecute?.setViewSql(
    //         this.currentConfig?.uniqueData?.ddl
    //       );
    //       this.$refs?.createSql?.setViewSql(
    //         this.currentConfig?.uniqueData?.ddl
    //       );
    //     }
    //   });
    // },
    changeFlag() {
      if (this.cachePageId != this.pageId) {
        // console.log('其他tab页的操作，弹出');
        return;
      } else {
        // console.log('当前tab页的操作，执行下一步');
        this.currentConfig = this.dataCurrentDataMap.get(this.pageId);
      }
    }
  },
  mounted() {
    this.cachePageId = this.pageId;
    this.currentConfig = this.dataCurrentDataMap.get(this.pageId);
  },
  methods: {
    backToOverview() {
      this.$store.dispatch("workspaceData/setDataCurrentData", {
        pageId: this.pageId,
        uniqueData: false
      });
    },
    // 手动更新currentConfig 中的Header
    // updateCurrentConfigHeader(headerList) {

    //   let dataCurrentDataTemp = this.$store.state.workspaceData.dataCurrentData
    //   dataCurrentDataTemp.get(this.pageId)['headerList'] = headerList
    //   this.$store.dispatch("workspaceData/setDataCurrentData", dataCurrentDataTemp);
    //   console.log("更新容器",dataCurrentDataTemp);
    // },
    setQueryResultData(newVal) {
      console.log("调用容器组件方法", newVal);
      console.log(newVal, "newVal");
      let _workspaceTabList = JSON.parse(JSON.stringify(this.workspaceTabList));
      for (let i = 0; i < _workspaceTabList.length; i++) {
        if (_workspaceTabList[i].id === newVal.id)
          _workspaceTabList[i] = newVal;
      }
      this.$store.commit("SET_WORKSPACETABLIST", _workspaceTabList);
      this.currentConfig = newVal;
    },
    // 获取打开的控制台列表
    getWorkspaceTabList() {
      let send = {
        tabOpened: "y",
        pageNo: 1,
        pageSize: 20
      };
      historyServer.getConsoleList(send).then(res => {
        this.consoleList = res.data.data;
        this.$store.commit(
          "SET_ACTIVECONSOLEID",
          this.consoleList.length ? this.consoleList[0].id : ""
        );
      });
    },
    deleteOtherTab(data) {
      const newInternalTabs = this.workspaceTabList?.filter(
        t => t.id === data.key
      );
      const deleteInternalTabs = this.workspaceTabList?.filter(
        t => t.id !== data.key
      );
      this.deleteOpenConsole(deleteInternalTabs.map(item => item.id));
      this.$store.commit("SET_ACTIVECONSOLEID", data.key);
      this.$store.commit("SET_WORKSPACETABLIST", newInternalTabs);
    },
    deleteAllTab() {
      this.deleteOpenConsole(this.workspaceTabList.map(item => item.id));
      this.$store.commit("SET_ACTIVECONSOLEID", null);
      this.$store.commit("SET_WORKSPACETABLIST", []);
    },
    deleteOpenConsole(idList) {
      historyServer.deleteOpenConsole({ idList });
    },
    handleContextMenu(item) {
      this.$contextmenu.destroy();
      this.$contextmenu({
        items: [
          {
            label: "关闭",
            onClick: () => {
              this.deleteTab(item);
            }
          },
          {
            label: "关闭其他",
            onClick: () => {
              this.deleteOtherTab(item);
            }
          },
          {
            label: "全部关闭",
            onClick: () => {
              this.deleteAllTab(item);
            }
          }
        ],
        event,
        customClass: "resource-context-menu",
        zIndex: 999,
        minWidth: 100
      });
      return false;
    },
    onDoubleClick(data) {
      if (data.editableName) {
        this.editing.key = data.key;
        this.editing.label = data.label;
      }
    },
    onBlur(data) {
      this.editableNameOnBlur(data);
      this.editing.key = "";
      this.editing.label = "";
    },
    // 编辑名称
    editableNameOnBlur(data) {
      const _params = {
        id: data.key,
        name: this.editing.label
      };
      historyServer.updateSavedConsole(_params);

      const _workspaceTabList =
        this.workspaceTabList?.map(item => {
          if (item.id === data.key) {
            return {
              ...item,
              title: this.editing.label
            };
          }
          return item;
        }) || [];
      this.$store.commit("SET_WORKSPACETABLIST", _workspaceTabList);
    },
    tableRefresh(node) {
      this.$emit("tableRefresh", node);
    },
    updateViewName(val) {
      this.$emit("updateViewNameTwo", val);
    },
    setCurrentConfig(key, value) {
      console.log(key, value, "setCurrentConfig");
      // this.currentConfig[key] = value;
      this.$set(this.currentConfig, key, value);
      console.log(this.currentConfig, "this.currentConfig");
    }
  }
};
</script>

<template>
  <div class="monaco_editor">
    <div class="monaco_editor_box" v-if="currentConfig && currentConfig.uniqueData">
      <template v-if="currentConfig">
        <renderSqlExecute
          ref="renderSqlExecute"
          :currentConfig="currentConfig"
          @setCurrentConfig="setCurrentConfig"
          v-if="workspaceTabConnectionMap.reanderSqlExecute.includes(currentConfig.type)"
          :detailsObj="detailsObj"
        />
        <renderTableEditor
          ref="renderTableEditor"
          :queryResultData="currentConfig"
          @tableRefresh="tableRefresh"
          v-else-if="workspaceTabConnectionMap.renderTableEditor.includes(currentConfig.type)"
        />
        <renderViewEditor
          ref="renderViewEditor"
          :queryResultData="currentConfig"
          :typeView="typeView"
          @tableRefresh="tableRefresh"
          @updateViewName="updateViewName"
          v-else-if="workspaceTabConnectionMap.renderViewEditor.includes(currentConfig.type)"
        />
        <renderUserEditor
          ref="renderUserEditor"
          @tableRefresh="tableRefresh"
          :queryResultData="currentConfig"
          v-else-if="workspaceTabConnectionMap.renderUserEditor.includes(currentConfig.type)"
        />
        <renderTableSpaceEditor
          ref="renderTableSpaceEditor"
          @tableRefresh="tableRefresh"
          :queryResultData="currentConfig"
          v-else-if="workspaceTabConnectionMap.renderTableSpaceEditor.includes(currentConfig.type)"
        />
        <renderSearchResult
          ref="renderSearchResult"
          v-else-if="workspaceTabConnectionMap.renderSearchResult.includes(currentConfig.type)"
          :typeView="typeView"
          :queryResultData="currentConfig"
          @setQueryResultData="setQueryResultData"
        />
        <DataSourceConfig v-else-if="currentConfig.type === 'datasource'" />
        <ViewAllTable
          v-else-if="workspaceTabConnectionMap.renderViewAllTable.includes(currentConfig.type)"
          :uniqueData="currentConfig.uniqueData"
        />
        <ViewAllView
          v-else-if="workspaceTabConnectionMap.renderViewAllView.includes(currentConfig.type)"
          :uniqueData="currentConfig.uniqueData"
        />
        <CreateSql
          ref="createSql"
          :currentConfig="currentConfig"
          @tableRefresh="tableRefresh"
          v-else-if="workspaceTabConnectionMap.createSql.includes(currentConfig.type)"
        />
        <div v-else>Unknown</div>
      </template>
      <div v-else>Unknown</div>
    </div>
    <div class="monaco_editor_box" v-else>
      <TableViewList
        v-if="typeView == 'tables'"
        :detailDataSource="detailDataSource"
        :detailDataSourceTotal="detailDataSourceTotal"
        :dataInfo="dataInfo"
        :filterName="filterName"
        :schema="schema"
      />
      <ViewList
        v-else-if="typeView == 'views'"
        :detailDataSource="detailDataSource"
        :dataInfo="dataInfo"
      />
      <UserList
        v-else-if="typeView == 'users'"
        :detailDataSource="detailDataSource"
        :dataInfo="dataInfo"
      />
      <TableSpace
        v-else-if="typeView == 'tableSpace'"
        :detailDataSource="detailDataSource"
        :dataInfo="dataInfo"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
.monaco_editor {
  width: 100%;
  height: 100%;
  .monaco_editor_box {
    width: 100%;
    height: 98%;
    display: flex;
    flex-flow: column;
    background: #fff;
    border-radius: 5px;
    &::-webkit-scrollbar {
      display: none;
    }
  }
}
::v-deep .tag_input {
  width: 80px;
  input {
    background: transparent;
    color: #fff;
    border: none;
  }
}
</style>
