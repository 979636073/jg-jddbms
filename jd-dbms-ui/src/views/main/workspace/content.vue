<template>
    <el-main class="bodyBox" v-loading="loading" element-loading-text="正在打开对象，请稍候…">
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
</template>

<script>
import ContainerBar from "./ContainerBar.vue";

export default {
  props: {
    objectLoading: { type: Object, default: () => ({}) }
  },
  computed: {
    loading() {
      return Boolean(this.objectLoading[this.pageId]);
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
      tableFilterName: "",
      detailsObj: {}
    };
  },
  mounted() {
    if (!this.pageId) return;
    this.$emit("openRouteObject", this.$route);
  },
  methods: {
    setDetailsObj(val) {
      this.detailsObj = val;
    },
    isType(val, schema) {
      this.dataInfo = { ...this.$store.state.workspaceData.dataInfo };
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
