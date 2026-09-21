<template>
  <div class="table_box">
    <el-table
      :data="detailDataSource"
      @row-dblclick="handleCurrentChange"
      :header-cell-style="{ background: '#f1f3f9', color: '#68728c' }"
      height="800"
    >
      <el-table-column prop="name" label="用户"> </el-table-column>
      <el-table-column prop="accountStatus" label="状态"> </el-table-column>
      <el-table-column prop="defaultTableSpace" label="默认表空间">
      </el-table-column>
      <el-table-column prop="tempTableSpace" label="临时表空间">
      </el-table-column>
      <el-table-column prop="created" label="创建时间"> </el-table-column>
    </el-table>
  </div>
</template>

<script>
import { v4 as uuidv4 } from "uuid";
import userServer from "@/api/main/user";
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
  },
  data() {
    return {};
  },
  computed: {
    pageId() {
      return this.$route.params.id;
    },
  },
  methods: {
    handleCurrentChange(items) {
      this.$store.dispatch('jdTagsView/changeView', {title:items.name || items.tableDetails.tableSpace,id:this.$route.params.id});
      let send = {
        dataSourceId: this.dataInfo.dataSource.id,
        databaseType: this.dataInfo.dataSource.type,
        databaseName: this.dataInfo.databaseName,
        hasNextPage: true,
        pageNo: 1,
        pageSize: 200,
        schemaName: this.schema,
        tableName: items.name,
        userName: items.name,
        total: 0,
        type: this.dataInfo.dataSource.type,
      };
      userServer.getUserDetails(send).then((res) => {
        this.$store.dispatch("workspaceData/setDataCurrentData", {
          pageId: this.pageId,
          title: items.name,
          type: "editUser",
          tableDetails: res.data.tableDetails,
          uniqueData: {
            dataSourceId: this.dataInfo.dataSource.id,
            databaseName: this.dataInfo.dataSource.alias,
            sqlInfo: null,
            databaseType: this.dataInfo.dataSource.type,
            userName: items.name,
          },
        });
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
}
::v-deep .el-table--medium .el-table__cell {
  padding: 2px 0px !important;
}
</style>
