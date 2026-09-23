<template>
  <div class="bodyBox">
    <el-aside
      :style="{ width: sideBarWidth + 'px' }"
      :class="[opened ? 'show' : 'hide', { resizing: resizingSideBar }]"
    >
      <!-- @refreshScheam="refreshScheam" -->
      <SideBar
        :dataBaseInfo="dataBaseInfo"
        :dataInfo="dataInfo"
        :isQuery="isQuery"
        @isType="isType"
        @queryDetailTable="queryDetailTable"
        @filterChange="filterChange"
        @load="load"
        @isopenFn="isopenFn"
        ref="databaseTree"
        @setDetailsObj="setDetailsObj"
      />
      <div
        class="sidebar-resizer"
        title="拖动调整左侧宽度"
        @mousedown.prevent="startResizeSideBar"
      ></div>
    </el-aside>
    <div v-if="!opened" class="isOpen" @click="openSideBar">
      <img src="@/assets/main/1-1-sub侧边.png" alt />
    </div>

    <el-main v-loading="loading" element-loading-text="正在打开对象，请稍候…">
      <ContainerBar
        @tableRefresh="tableRefresh"
        @updateViewNameTwo="updateViewNameTwo"
        :typeView="typeView"
        :detailDataSource="detailDataSource"
        :detailDataSourceTotal="detailDataSourceTotal"
        :dataInfo="dataInfo"
        :filterName="tableFilterName"
        :schema="schema"
        :detailsObj="detailsObj"
      />
    </el-main>
  </div>
</template>

<script>
import connectionServer from "@/api/main/connection";
import SideBar from "./SideBar.vue";
import ContainerBar from "./ContainerBar.vue";

export default {
  computed: {
    dataInfo() {
      return this.$store.state.workspaceData.dataInfo;
    },
    dataBaseInfo() {
      return this.$store.state.workspaceData.dataBaseInfo;
    },
    pageId() {
      return this.$route.params.id;
    },
    isQuery() {
      return this.$route.path.indexOf("query") != -1;
    }
  },
  components: {
    SideBar,
    ContainerBar
  },
  watch: {
    dataInfo(val) {
      // console.log(val);
    }
  },
  data() {
    return {
      loading: false,
      // 综合数据
      schema: "",
      // 类型
      typeView: "",
      detailDataSource: null,
      detailDataSourceTotal: 0,
      tableFilterName: "",
      opened: true,
      sideBarWidth: 280,
      resizingSideBar: false,
      resizeStartX: 0,
      resizeStartWidth: 280,
      detailsObj: {}
    };
  },
  created() {
    const createDataCache = sessionStorage.getItem("createData");
    const createData = createDataCache && createDataCache !== "undefined"
      ? JSON.parse(createDataCache)
      : null;
    if (createData) {
      this.$store.commit(
        "workspaceData/SET_DATA_BASE_INFO",
        createData.dataSource
      );
      this.$store.commit("workspaceData/SET_DATA_INFO", createData);
      this.$store.dispatch("workspaceData/setDataSourceData", createData);
    }
  },
  mounted() {
    if (!this.pageId) return;
    // this.getDataBaseTree();
  },
  beforeDestroy() {
    this.stopResizeSideBar();
  },
  methods: {
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
    setDetailsObj(val) {
      this.detailsObj = val;
    },
    load(val) {
      this.loading = val;
    },
    isType(val, schema) {
      this.$nextTick(() => {
        this.typeView = val;
        this.schema = schema;
        this.$EventBus.$emit("type", this.typeView);
        // console.log(this.typeView, "this.typeView");
      });
    },
    queryDetailTable(data, total) {
      this.detailDataSource = data;
      this.detailDataSourceTotal = total || (data || []).length;
      this.detailDataSource.forEach(item => {
        if (this.typeView == "tables" && item.lastDDL) {
          item.lastDDL = item.lastDDL.split(".")[0];
        }
        if (item.created) {
          item.created = item.created.split(".")[0];
        }
      });
    },
    filterChange(value) {
      this.tableFilterName = value || "";
    },
    // refreshScheam() {
    //   this.getDataBaseTree();
    // },
    getDataBaseTree() {
      // if (this.dataSourceData.length) {
      //   return;
      // }
      const params = {
        dataSourceId: this.dataInfo.dateSourceId,
        dataSourceName: this.dataInfo.dateSourceName,
        refresh: false
      };
      connectionServer.getSchemaList(params).then(res => {
        this.$store.dispatch("workspaceData/setDataSourceData", res.data);
      });
    },
    isopenFn() {
      this.opened = false;
    },
    tableRefresh(node) {
      this.$refs.databaseTree.tableRefresh(node);
    },
    updateViewNameTwo(val) {
      this.$refs.databaseTree.getTableDataList(val);
    },
    openSideBar() {
      this.opened = true;
    }
  }
};
</script>

<style lang='scss' scoped>
.bodyBox {
  flex: 1;
  display: flex;
  height: 0;
}
.el-aside {
  position: relative;
  flex-shrink: 0;
  overflow: visible;
}
.sidebar-resizer {
  position: absolute;
  z-index: 100;
  top: 0;
  right: -4px;
  width: 8px;
  height: 100%;
  cursor: col-resize;
}
.sidebar-resizer:hover,
.resizing .sidebar-resizer {
  background: rgba(0, 111, 255, 0.14);
}
.show {
  display: block;
}
.hide {
  display: none;
}
.isOpen {
  width: 20px;
  height: 98%;
  margin-left: 7px;
  margin-right: 5px;
  position: relative;
  background: #fff;
  img {
    position: absolute;
    top: 40%;
    cursor: pointer;
  }
}
</style>
