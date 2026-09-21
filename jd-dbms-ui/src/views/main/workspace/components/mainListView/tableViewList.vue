<template>
  <div class="table_box">
    <el-table
      :data="pagedDetailDataSource"
      @row-dblclick="handleCurrentChange"
      :header-cell-style="{ background: '#f1f3f9', color: '#68728c' }"
      style="width: 100%; font-size: 12px"
      height="calc(100% - 60px)"
      fit
      :row-style="overviewRowStyle"
    >
      <el-table-column
        prop="name"
        label="表"
        :show-overflow-tooltip="true"
      ></el-table-column>
      <el-table-column prop="schema" label="模式"></el-table-column>
      <el-table-column prop="tableSpace" label="表空间"></el-table-column>
      <el-table-column
        prop="comment"
        label="注释"
        :show-overflow-tooltip="true"
      ></el-table-column>
      <!-- <el-table-column prop="lastAnalyzed" label="最后分析时间"></el-table-column> -->
      <el-table-column prop="numRows" label="记录行数"></el-table-column>
      <el-table-column
        prop="created"
        label="创建时间"
        :show-overflow-tooltip="true"
      ></el-table-column>
      <el-table-column
        prop="lastDDL"
        label="最后结构变化时间"
        :show-overflow-tooltip="true"
      ></el-table-column>
    </el-table>
    <div class="table_bottom_box">
      <el-pagination
        @size-change="handleSizeChange"
        @current-change="handleCurrentChangePage"
        :current-page="queryParams.pageNum"
        :page-sizes="[20, 50, 100, 200]"
        :page-size="queryParams.pageSize"
        layout="total, sizes, prev, pager, next, jumper"
        :total="queryParams.total"
        background
      ></el-pagination>
    </div>
  </div>
</template>

<script>
import { v4 as uuidv4 } from "uuid";
import sqlServer from "@/api/main/sql";
export default {
  props: {
    detailDataSource: {
      type: Array,
      default: () => [],
    },
    dataInfo: {
      type: Object,
      default: {},
    },
    detailDataSourceTotal: {
      type: Number,
      default: 0,
    },
    filterName: {
      type: String,
      default: "",
    },
  },
  data() {
    return {
      queryParams: {
        pageNum: 1,
        pageSize: 20,
        total: 0,
      },
    };
  },
  computed: {
    pageId() {
      return this.$route.params.id;
    },
    pagedDetailDataSource() {
      const start = (this.queryParams.pageNum - 1) * this.queryParams.pageSize;
      return this.filteredDetailDataSource.slice(start, start + this.queryParams.pageSize);
    },
    filteredDetailDataSource() {
      const keyword = String(this.filterName || "").trim().toLowerCase();
      if (!keyword) return this.detailDataSource || [];
      return (this.detailDataSource || []).filter((item) => {
        return [item.name, item.comment, item.schema, item.tableSpace]
          .filter(Boolean)
          .some((value) => String(value).toLowerCase().includes(keyword));
      });
    },
  },
  watch: {
    detailDataSource: {
      immediate: true,
      handler(value) {
        this.queryParams.total = this.filteredDetailDataSource.length;
        const maxPage = Math.max(1, Math.ceil(this.queryParams.total / this.queryParams.pageSize));
        if (this.queryParams.pageNum > maxPage) this.queryParams.pageNum = 1;
      },
    },
    filterName() {
      this.queryParams.total = this.filteredDetailDataSource.length;
      this.queryParams.pageNum = 1;
    },
  },
  methods: {
    handleSizeChange(val) {
      this.queryParams.pageSize = val;
      this.queryParams.pageNum = 1;
    },
    handleCurrentChangePage(val) {
      this.queryParams.pageNum = val;
    },
    overviewRowStyle() {
      const tableHeight = this.$el ? this.$el.clientHeight - 60 : 600;
      // Element 表格还会占用约 40px 表头高度，行高需从主体高度中扣除。
      const bodyHeight = Math.max(0, tableHeight - 40);
      const rowHeight = Math.max(24, Math.floor(bodyHeight / Math.max(1, this.queryParams.pageSize)));
      return { height: `${rowHeight}px` };
    },
    handleCurrentChange(item) {
      console.log(item, "item");
      this.$store.dispatch("jdTagsView/changeView", {
        title: item.name || item.tableDetails.tableSpace,
        id: this.$route.params.id,
      });
      let id = uuidv4();
      let send = {
        dataSourceId: this.dataInfo.dataSource.id,
        databaseType: this.dataInfo.dataSource.type,
        databaseName: this.dataInfo.dataSource.databaseName,
        hasNextPage: true,
        pageNo: 1,
        pageSize: 200,
        schemaName: item.schema,
        sql: `select * from "${item.schema}"."${item.name}"`,
        tableName: item.name,
        total: 0,
        type: this.dataInfo.dataSource.type,
      };
      sqlServer.executeSql(send).then((res) => {
        if (res.data[0]?.success) {
          this.$store.dispatch("workspaceData/setDataCurrentData", {
            pageId: this.pageId,
            title: item.name,
            type: "editTable",
            ...res.data[0],
            params: send,
            uniqueData: {
              dataSourceId: this.dataInfo.dataSource.id,
              dataSourceName: this.dataInfo.dataSource.alias,
              databaseName: "",
              sqlInfo: null,
              databaseType: this.dataInfo.dataSource.type,
              schemaName: item.schema,
              tableName: item.name,
              isLoading: true,
              dataType: "tables",
            },
          });
        } else {
          this.$message.error(res.data[0].message);
        }
      });
    },
  },
};
</script>

<style scoped lang="scss">
.table_box {
  background: #fff;
  padding: 5px 10px 0px 10px;
  height: 100%;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
::v-deep .el-table--medium .el-table__cell {
  padding: 2px 0px !important;
}
</style>
