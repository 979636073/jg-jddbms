<template>
  <div class="table_box">
    <div class="user-list-tools">
      <el-input v-model.trim="keyword" size="small" clearable placeholder="搜索用户" @input="page = 1" />
      <span>共 {{ filteredUsers.length }} 个用户</span>
    </div>
    <el-table
      :data="pagedUsers"
      @row-click="handleCurrentChange"
      :header-cell-style="{ background: '#f1f3f9', color: '#68728c' }"
      height="calc(100% - 82px)"
    >
      <el-table-column prop="name" label="用户"> </el-table-column>
      <el-table-column prop="accountStatus" label="状态"> </el-table-column>
      <el-table-column prop="defaultTableSpace" label="默认表空间">
      </el-table-column>
      <el-table-column prop="tempTableSpace" label="临时表空间">
      </el-table-column>
      <el-table-column prop="created" label="创建时间"> </el-table-column>
    </el-table>
    <el-pagination v-if="filteredUsers.length > pageSize" small layout="prev, pager, next" :total="filteredUsers.length" :page-size="pageSize" v-model:current-page="page" />
  </div>
</template>

<script>
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
    return { userDetailRequestId: 0, keyword: '', page: 1, pageSize: 100 };
  },
  computed: {
    filteredUsers() {
      const keyword = this.keyword.toLowerCase();
      return keyword ? this.detailDataSource.filter(item => String(item.name || '').toLowerCase().includes(keyword)) : this.detailDataSource;
    },
    pagedUsers() {
      const start = (this.page - 1) * this.pageSize;
      return this.filteredUsers.slice(start, start + this.pageSize);
    },
    pageId() {
      return this.$route.params.id;
    },
  },
  watch: { detailDataSource() { this.page = 1; } },
  methods: {
    async handleCurrentChange(items) {
      if (!items?.name || !this.dataInfo?.dataSource?.id) return;
      const requestId = ++this.userDetailRequestId;
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
      try {
        const res = await userServer.getUserDetails(send);
        if (requestId !== this.userDetailRequestId) return;
        if (!res?.success || !res.data?.tableDetails) {
          this.$message.error(res?.errorMessage || "用户详情加载失败");
          return;
        }
        this.$router.replace({
          path: this.$route.path,
          query: {
            ...this.$route.query,
            dataSourceId: this.dataInfo.dataSource.id,
            dataSourceName: this.dataInfo.dataSource.alias,
            schemaName: this.dataInfo.userName || this.$route.query.schemaName,
            tableName: items.name,
            objectType: "user",
            viewType: undefined,
            path: undefined,
          },
        }).catch(() => {});
        this.$store.dispatch('jdTagsView/changeView', { title: items.name, id: this.pageId });
        this.$store.dispatch("workspaceData/setDataCurrentData", {
          pageId: this.pageId,
          title: items.name,
          type: "editUser",
          tableDetails: res.data.tableDetails,
          tableUserRoles: res.data.tableUserRoles,
          uniqueData: {
            dataSourceId: this.dataInfo.dataSource.id,
            databaseName: this.dataInfo.dataSource.alias,
            sqlInfo: null,
            databaseType: this.dataInfo.dataSource.type,
            userName: items.name,
          },
        });
      } catch (error) {
        if (requestId === this.userDetailRequestId) {
          this.$message.error(error?.message || "用户详情加载失败");
        }
      }
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
.user-list-tools { display: flex; align-items: center; justify-content: space-between; gap: 12px; height: 42px; }
.user-list-tools .el-input { max-width: 260px; }
::v-deep .el-table--medium .el-table__cell {
  padding: 2px 0px !important;
}
</style>
