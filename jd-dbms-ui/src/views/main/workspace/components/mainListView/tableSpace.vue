<template>
  <div class="table_box">
    <el-table
      :data="detailDataSource"
      @row-dblclick="handleCurrentChange"
      :header-cell-style="{ background: '#f1f3f9', color: '#68728c' }"
      height="800"
      style="width: 100%"
    >
      <el-table-column prop="tableSpace" label="TableSpace"></el-table-column>
      <el-table-column prop="status" label="Status"></el-table-column>
      <el-table-column prop="totalSize" label="Size">
        <template slot-scope="scope">
          <span>{{ scope.row.totalSize + "MB" }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="freeSize" label="Free">
        <template slot-scope="scope">
          <span>{{ scope.row.freeSize + "MB" }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="useSize" label="Used">
        <template slot-scope="scope">
          <span>{{ scope.row.useSize + "MB" }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="usageRate" label="% Used" >
        <template slot-scope="scope">
          <el-progress :text-inside="true" :stroke-width="16" :percentage="Number(scope.row.usageRate) > 100 ? 100 :Number(scope.row.usageRate)"></el-progress>
        </template>
      </el-table-column>
      <el-table-column prop="expandUpperLimit" label="Max Size">
        <template slot-scope="scope">
          <span>{{ scope.row.expandUpperLimit + "MB" }}</span>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script>
import tableSpaceServer from "@/api/main/tableSpace";
export default {
  props: {
    detailDataSource: {
      type: Array,
      default: () => []
    },
    dataInfo: {
      type: Object,
      default: {}
    }
  },
  data() {
    return {};
  },
  computed: {
    pageId() {
      return this.$route.params.id;
    }
  },
  methods: {
    handleCurrentChange(item) {
      let send = {
        dataSourceId: this.dataInfo.dataSource.id,
        path: item.path
      };
      tableSpaceServer.getTableSpaceDetails(send).then(res => {
        this.$store.dispatch("workspaceData/setDataCurrentData", {
          pageId: this.pageId,
          title: item.name,
          type: "editTableSpace",
          uniqueData: {
            dataSourceId: this.dataInfo.dataSource.id,
            databaseName: this.dataInfo.dataSource.alias,
            path: item.path
          }
        });
      });
    }
  }
};
</script>

<style scoped lang="scss">
.table_box {
  background: #fff;
  padding: 5px 10px 0px 10px;
  height: 100%;
}
::v-deep .el-table--medium .el-table__cell {
  padding: 2px 0px !important;
}
</style>
