<template>
    <el-main class="bodyBox" v-loading="loading" element-loading-text="正在打开对象，请稍候…">
      <ContainerBar
        @tableRefresh="tableRefresh"
        @updateViewNameTwo="updateViewNameTwo"
        @restoreOverview="restoreOverview"
        :typeView="typeView"
        :detailDataSource="detailDataSource"
        :detailDataSourceTotal="detailDataSourceTotal"
        :dataInfo="dataInfo"
        :filterName="tableFilterName"
        :schema="schema"
        :detailsObj="detailsObj"
      />
    </el-main>
</template>

<script>
import ContainerBar from "./ContainerBar.vue";
import tableServer from "@/api/main/table";

export default {
  props: {
    objectLoading: { type: Object, default: () => ({}) }
  },
  computed: {
    loading() {
      return Boolean(this.objectLoading[this.pageId]) || this.overviewLoading;
    }
  },
  components: {
    ContainerBar
  },
  data() {
    return {
      pageId: this.$route.params.id,
      dataInfo: { ...this.$store.state.workspaceData.dataInfo },
      // 综合数据
      schema: "",
      // 类型
      typeView: "",
      detailDataSource: null,
      detailDataSourceTotal: 0,
      overviewLoading: false,
      overviewRequestId: 0,
      tableFilterName: "",
      detailsObj: {}
    };
  },
  mounted() {
    if (!this.pageId) return;
    this.$emit("openRouteObject", this.$route);
  },
  methods: {
    async restoreOverview(context = {}) {
      const dataSourceId = context.dataSourceId || this.$route.query.dataSourceId;
      const dataSource = this.$store.state.workspace.dataSourceList.find(item => String(item.id) === String(dataSourceId)) ||
        (String(this.dataInfo.dataSource?.id) === String(dataSourceId) ? this.dataInfo.dataSource : null);
      const schema = context.schemaName || this.$route.query.schemaName || this.dataInfo.userName || '';
      if (!dataSource || !schema) {
        this.$message.error('无法恢复模式总览，请在左侧重新选择数据源和模式');
        return;
      }
      const requestId = ++this.overviewRequestId;
      this.dataInfo = { ...this.dataInfo, dataSource, userName: schema };
      this.typeView = 'tables';
      this.schema = schema;
      this.tableFilterName = '';
      this.detailDataSource = [];
      this.detailDataSourceTotal = 0;
      this.overviewLoading = true;
      try {
        const res = await tableServer.getTableList({
          dataSourceId: dataSource.id,
          dataSourceName: dataSource.alias,
          databaseType: dataSource.type,
          databaseName: context.databaseName || this.$route.query.databaseName || '',
          schemaName: schema,
          requestType: 2,
          pageNo: 1,
          pageSize: 200,
          refresh: false,
          isRefreshCache: false
        });
        if (requestId !== this.overviewRequestId) return;
        if (!res.success) throw new Error(res.errorMessage || '读取表列表失败');
        this.detailDataSource = (res.data?.data || []).map(item => {
          const details = item.tableDetails || {};
          return {
            name: item.name, schema: details.schema || schema,
            tableSpace: details.tableSpace, comment: item.comment || details.comment,
            numRows: details.numRows, created: details.created,
            lastDDL: details.lastDDL, type: details.type, path: details.path
          };
        });
        this.detailDataSourceTotal = Number(res.data?.total) || this.detailDataSource.length;
      } catch (error) {
        if (requestId === this.overviewRequestId) this.$message.error(error.message || '模式总览加载失败');
      } finally {
        if (requestId === this.overviewRequestId) this.overviewLoading = false;
      }
    },
    setDetailsObj(val) {
      this.detailsObj = val;
    },
    isType(val, schema) {
      this.dataInfo = { ...this.$store.state.workspaceData.dataInfo };
      this.detailDataSource = [];
      this.detailDataSourceTotal = 0;
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
    tableRefresh(node) {
      this.$emit("tableRefresh", node);
    },
    updateViewNameTwo(val) {
      this.$emit("updateViewNameTwo", val);
    }
  }
};
</script>

<style lang='scss' scoped>
.bodyBox {
  flex: 1;
  display: flex;
  min-width: 0;
  min-height: 0;
  height: 100%;
}
</style>
