<script>
import NavBar from "./NavBar.vue";
import JdTagsView from "@/components/JdTagsView";
import linkServerDialog from './components/linkServerDialog';
import SideBar from './SideBar.vue';
import connectionServer from '@/api/main/connection';
import { v4 as uuidv4 } from 'uuid';

export default {
  name: "index",
  components: {
    NavBar,
    JdTagsView,
    linkServerDialog,
    SideBar,
  },
  data() {
    return {
      opened: true,
      visibleDialog: false,
      selectedDataSourceId: null,
      dataSourceDialogMode: "create",
      headerCollapsed: false,
      sideBarWidth: 280,
      resizingSideBar: false,
      resizeStartX: 0,
      resizeStartWidth: 280,
      objectLoading: {},
      queryDialogVisible: false,
      queryDataSources: [],
      queryDataSourceId: '',
      queryCreating: false,
    };
  },
  mounted(){
     if (this.$route.path === "/workspace") {
       if (this.$route.query?.isShow) this.openDataSourceManager()
       this.$router.replace({ path: "/workspace/look/workspace-home" })
       return
     }
  },
  computed: {
    dataInfo() {
      return this.$store.state.workspaceData.dataInfo;
    },
    dataBaseInfo() {
      return this.$store.state.workspaceData.dataBaseInfo;
    },
    key() {
      return this.$route.path;
    },
  },
  beforeUnmount() {
    this.stopResizeSideBar();
  },
  methods: {
    async openNewQuery(request = {}) {
      const requestedSource = request?.dataSource;
      const connectedIds = this.$store.state.workspace.connectedDataSourceIds;
      if (requestedSource) {
        await this.createQueryForDataSource(requestedSource, request.extraParams || {});
        return;
      }
      const active = this.dataInfo?.dataSource;
      if (active && connectedIds[active.id]) {
        await this.createQueryForDataSource(active, {
          databaseName: this.dataInfo.databaseName || '',
          schemaName: this.dataInfo.userName || '',
        });
        return;
      }
      try {
        const response = await connectionServer.getList({ pageNo: 1, pageSize: 10000 });
        if (!response.success) throw new Error(response.errorMessage || '读取数据源失败');
        this.queryDataSources = response.data?.data || [];
        const connected = this.queryDataSources.filter(source => connectedIds[source.id]);
        if (connected.length === 1) {
          await this.createQueryForDataSource(connected[0]);
          return;
        }
        this.queryDataSourceId = '';
        this.queryDialogVisible = true;
      } catch (error) {
        this.$message.error(error.message || '读取数据源失败');
      }
    },
    async confirmNewQuery() {
      const source = this.queryDataSources.find(item => String(item.id) === String(this.queryDataSourceId));
      if (!source) return this.$message.warning('请选择数据源');
      await this.createQueryForDataSource(source);
    },
    async createQueryForDataSource(dataSource, context = {}) {
      if (this.queryCreating) return;
      this.queryCreating = true;
      try {
        if (!this.$store.state.workspace.connectedDataSourceIds[dataSource.id]) {
          const connected = await connectionServer.connect({ id: dataSource.id });
          if (!connected.success) throw new Error(connected.errorMessage || '数据库连接失败');
          this.handleDataSourceConnected(dataSource);
        }
        const schemaName = context.schemaName || dataSource.user || dataSource.userName || '';
        const databaseName = context.databaseName || '';
        this.$store.commit('workspaceData/SET_DATA_BASE_INFO', dataSource);
        this.$store.commit('workspaceData/SET_DATA_INFO', {
          dateSourceId: dataSource.id,
          dateSourceName: dataSource.alias,
          dataSource,
          databaseName,
          userName: schemaName,
        });
        this.$store.commit('SET_CURRENTCONNECTIONDETAILS', dataSource);
        const pageId = uuidv4();
        await this.$store.dispatch('workspaceData/createConsole', {
          pageId,
          dataSourceId: dataSource.id,
          dataSourceName: dataSource.alias,
          databaseType: dataSource.type,
          databaseName,
          schemaName,
        });
        this.queryDialogVisible = false;
        await this.$router.push({
          path: '/workspace/query/' + pageId,
          query: { dataSourceId: dataSource.id, databaseName, schemaName },
        });
      } catch (error) {
        this.$message.error(error.message || '新建查询失败');
      } finally {
        this.queryCreating = false;
      }
    },
    startResizeSideBar(event) {
      this.resizingSideBar = true;
      this.resizeStartX = event.clientX;
      this.resizeStartWidth = this.sideBarWidth;
      window.addEventListener("mousemove", this.resizeSideBar);
      window.addEventListener("mouseup", this.stopResizeSideBar);
    },
    resizeSideBar(event) {
      if (!this.resizingSideBar) return;
      const width = this.resizeStartWidth + event.clientX - this.resizeStartX;
      this.sideBarWidth = Math.min(Math.max(width, 260), 720);
    },
    stopResizeSideBar() {
      this.resizingSideBar = false;
      window.removeEventListener("mousemove", this.resizeSideBar);
      window.removeEventListener("mouseup", this.stopResizeSideBar);
    },
    forwardTreeEvent(method, ...args) {
      this.$refs.workspaceContent?.[method](...args);
    },
    handleTreeType(...args) {
      this.forwardTreeEvent("isType", ...args);
    },
    handleTreeObjects(...args) {
      this.forwardTreeEvent("queryDetailTable", ...args);
    },
    setObjectLoading(loading, pageId = this.$route.params.id) {
      this.objectLoading[pageId] = loading;
    },
    openRouteObject(route) {
      return this.$refs.databaseTree.loadRouteObject(route);
    },
    tableRefresh(node) {
      this.$refs.databaseTree.tableRefresh(node);
    },
    updateViewNameTwo(schema) {
      this.$refs.databaseTree.getTableDataList(schema);
    },
    toggleSideBar() {
      this.opened = !this.opened;
    },
    isopenFn() {
      this.opened = false;
    },
    openSideBar() {
      this.opened = true;
    },
    openDataSourceManager(request) {
      this.selectedDataSourceId = request?.id || null;
      this.dataSourceDialogMode = request?.mode || "create";
      this.visibleDialog = true;
    },
    closeDataSourceManager() {
      this.visibleDialog = false;
      this.selectedDataSourceId = null;
    },
    handleDataSourceSaved(data) {
      const createdDataSource = data?.dataSource;
      if (!createdDataSource && data?.id) {
        this.$store.commit("SET_DATASOURCE_CONNECTED", { id: data.id, connected: false });
        if (String(this.$store.state.workspaceData.dataInfo?.dataSource?.id) === String(data.id)) {
          this.$store.commit("workspaceData/SET_DATA_BASE_INFO", {});
          this.$store.commit("workspaceData/SET_DATA_INFO", {});
          this.$store.commit("SET_CURRENTCONNECTIONDETAILS", null);
          sessionStorage.removeItem("createData");
        }
      }
      this.closeDataSourceManager();
      this.$EventBus.$emit("refreshDataSourceTree");
      if (createdDataSource) this.handleDataSourceConnected(createdDataSource);
    },
    handleDataSourceConnected(dataSource) {
      this.$store.commit("SET_DATASOURCE_CONNECTED", { id: dataSource.id, connected: true });
      this.closeDataSourceManager();
      this.$EventBus.$emit("dataSourceConnected", dataSource);
    },
  },
};
</script>

<template>
  <div class="container">
    <el-container>
      <el-header :class="{ 'workspace-header-collapsed': headerCollapsed }">
        <NavBar
          @toggleSideBar="toggleSideBar"
          :opened="opened"
          :collapsed="headerCollapsed"
          @toggleHeader="headerCollapsed = !headerCollapsed"
          @manageDataSources="openDataSourceManager"
          @newQuery="openNewQuery"
          @openRouteObject="openRouteObject"
        />
      </el-header>
      <div class="tag_list">
        <JdTagsView @newQuery="openNewQuery" />
      </div>
      <div class="workspace-body">
        <el-aside :style="{ width: sideBarWidth + 'px' }" :class="[opened ? 'show' : 'hide', { resizing: resizingSideBar }]">
          <SideBar ref="databaseTree" :dataBaseInfo="dataBaseInfo" :dataInfo="dataInfo"
            @isType="handleTreeType"
            @queryDetailTable="handleTreeObjects"
            @showSchemaOverview="forwardTreeEvent('showSchemaOverview', $event)"
            @filterChange="forwardTreeEvent('filterChange', $event)"
            @setDetailsObj="forwardTreeEvent('setDetailsObj', $event)"
            @load="setObjectLoading" @isopenFn="isopenFn" @manageDataSources="openDataSourceManager" @newQuery="openNewQuery" />
          <div class="sidebar-resizer" title="拖动调整左侧宽度" @mousedown.prevent="startResizeSideBar"></div>
        </el-aside>
        <div v-if="!opened" class="isOpen" @click="openSideBar"><img src="@/assets/main/1-1-sub侧边.png" alt /></div>
        <div class="workspace-content">
          <router-view v-slot="{ Component }">
            <keep-alive>
              <component :is="Component" ref="workspaceContent" :key="key" :objectLoading="objectLoading"
                @openRouteObject="openRouteObject" @tableRefresh="tableRefresh" @updateViewNameTwo="updateViewNameTwo" />
            </keep-alive>
          </router-view>
        </div>
      </div>
    </el-container>
    <linkServerDialog
      :visibleDialog="visibleDialog"
      :selectedDataSourceId="selectedDataSourceId"
      :mode="dataSourceDialogMode"
      @close="closeDataSourceManager"
      @saved="handleDataSourceSaved"
      @connected="handleDataSourceConnected"
    />
    <el-dialog title="选择数据源并新建查询" v-model="queryDialogVisible" width="420px" append-to-body>
      <el-select v-model="queryDataSourceId" filterable placeholder="请选择数据源" style="width: 100%">
        <el-option v-for="source in queryDataSources" :key="source.id" :value="String(source.id)"
          :label="`${source.alias || source.user || source.userName || source.id}${$store.state.workspace.connectedDataSourceIds[source.id] ? '（已连接）' : '（未连接）'}`" />
      </el-select>
      <div v-if="!queryDataSources.length" style="margin-top: 12px; color: #909399">暂无数据源，请先新增连接。</div>
      <template #footer><span  class="dialog-footer">
        <el-button @click="queryDialogVisible = false">取消</el-button>
        <el-button v-if="!queryDataSources.length" type="primary" @click="queryDialogVisible = false; openDataSourceManager()">新增连接</el-button>
        <el-button v-else type="primary" :loading="queryCreating" @click="confirmNewQuery">连接并新建查询</el-button>
      </span></template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.show {
  display: block;
}
.hide {
  display: none;
}
.workspace-body { --workspace-body-font-size: 12px; flex: 1; display: flex; height: 0; min-height: 0; }
.workspace-content {
  flex: 1; min-width: 0; min-height: 0; display: flex;
  font-size: var(--workspace-body-font-size);
  ::v-deep {
    .el-button,
    .el-button.el-button--mini,
    .el-tabs__item,
    .el-table,
    .el-table .cell,
    .el-input__inner,
    .el-textarea__inner,
    .el-form-item__label,
    .el-checkbox__label,
    .el-radio__label,
    .el-pagination__total,
    .el-pagination__jump,
    .el-pager li {
      font-size: var(--workspace-body-font-size);
    }
  }
}
.el-aside { position: relative; flex-shrink: 0; overflow: visible; }
.sidebar-resizer { position: absolute; z-index: 100; top: 0; right: -4px; width: 8px; height: 100%; cursor: col-resize; }
.sidebar-resizer:hover, .resizing .sidebar-resizer { background: rgba(0, 111, 255, 0.14); }
.isOpen { width: 20px; height: 98%; margin-left: 7px; margin-right: 5px; position: relative; background: #fff; }
.isOpen img { position: absolute; top: 40%; cursor: pointer; }

.tag_list {
  // padding: 10px 0;
  margin-bottom: 5px;
  padding: 5px 0px 0px 8px;
  flex-shrink: 0;
  display: flex;
  min-width: 100%;
  overflow: hidden;
  // overflow-x: auto;
  .el-tag {
    margin-right: 10px;
    cursor: pointer;
    display: flex;
    align-items: center;
    i {
      flex-shrink: 0;
      margin-right: 4px;
    }
    .textBox {
      flex: 1;
      display: flex;
      align-items: center;
    }

    .text {
      flex: 1;
      width: fit-content;
      overflow: hidden;
      white-space: nowrap;
      text-overflow: ellipsis;
    }
  }
  ::v-deep .el-tag--dark.el-tag--info {
    background: #fff;
    border-color: #fff;
    color: #68728c;
    .el-tag__close,
    .el-tag__close:hover {
      background: #fff;
      color: #68728c;
    }
  }
}

::v-deep .el-header {
  padding: 0px !important;
}
::v-deep .workspace-header-collapsed {
  height: 20px !important;
  flex: 0 0 20px !important;
}
@media (max-width: 800px) {
  ::v-deep .el-header:not(.workspace-header-collapsed) {
    height: 96px !important;
    flex: 0 0 96px !important;
  }
}
::v-deep .el-main {
  padding: 0px;
}
.container > ::v-deep .el-container {
  display: -webkit-box;
  flex: 1;
  padding-right: 5px;
  flex-direction: column;
  // position: relative;
}
::v-deep aside {
  padding: 0px 8px !important;
}

::v-deep .el-form-item--mini .el-form-item__label {
  line-height: 0px;
}
::v-deep .el-form-item--mini.el-form-item {
  margin-bottom: 17px;
}
.container {
  display: flex;
  height: 100%;
  overflow: hidden;
  &::-webkit-scrollbar {
    display: none;
  }
}

::v-deep {
  .el-button--mini {
    padding: 5px 10px;
    font-size: 14px;
  }
  .el-button--text {
    margin: 0 15px;
    position: relative;
    color: #68728c;
  }
  .el-button--primary {
    background: #006fff;
    border: 1px solid #006fff;
    color: #fff;
  }
  .el-button--primary:hover,
  .el-button--primary:focus {
    background: #2052dd;
    border: 1px solid #2052dd;
    color: #fff;
  }
  .el-button--primary.is-plain {
    background: #f4f9ff;
    border: 1px solid #006fff;
    color: #006fff;
  }
  .el-button--primary.is-plain:hover,
  .el-button--primary.is-plain:focus {
    background: #eaf2fc;
    border: 1px solid #006fff;
    color: #006fff;
  }
  .el-button--danger.is-plain {
    background: #fff6f4;
    border: 1px solid #ff5733;
    color: #ff5733;
  }
  .el-button--danger.is-plain:hover,
  .el-button--danger.is-plain:focus {
    background: #ffe9e4;
    border: 1px solid #eb4927;
    color: #ff5733;
  }
  .el-button.is-disabled:not(.el-button--primary),
  .el-button.is-disabled:not(.el-button--primary):hover,
  .el-button.is-disabled:not(.el-button--primary):focus {
    color: #c0c4cc;
  }
  .el-button--primary.is-disabled,
  .el-button--primary.is-disabled:hover,
  .el-button--primary.is-disabled:focus {
    background: #eef0f3;
    border-color: #d8dce3;
    color: #909399;
    box-shadow: none;
  }
  .el-pagination {
    margin: 5px 0;
    float: right;
  }

  .el-dialog {
    background: #ffffff;
    border-radius: 5px;
    .el-dialog__header {
      height: 50px;
      background: #006fff;
      border-radius: 5px 5px 0 0;
      padding: 0 0 0 24px;
      .el-dialog__title {
        font-size: 18px;
        font-weight: 400;
        color: #ffffff;
        line-height: 50px;
      }
      .el-dialog__headerbtn .el-dialog__close {
        color: #e5f4ff;
      }
    }
  }
  .el-table {
    .delete-row {
      background: #fdd4cd;
    }
    .update-row {
      background: #f6ffed;
    }
  }

  .el-tree {
    color: #68728c;
    font-size: 14px;
  }
}
::v-deep .el-button--text {
  margin: 0 !important;
}
::v-deep .el-table--medium .el-table__cell {
  padding: 6px;
}
</style>
