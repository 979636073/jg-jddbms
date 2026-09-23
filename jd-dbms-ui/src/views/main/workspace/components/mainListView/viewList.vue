<template>
  <div class="table_box">
    <el-table
      :data="detailDataSource"
      @row-dblclick="handleCurrentChange"
      height="800"
      :header-cell-style="{ background: '#f1f3f9', color: '#68728c' }"
      style="width: 100%"
    >
      <el-table-column prop="name" label="View"></el-table-column>
      <el-table-column label="类型" width="90">
        <template slot-scope="scope">
          <el-tag
            size="mini"
            :type="isMaterializedView(scope.row) ? 'primary' : 'info'"
          >{{ isMaterializedView(scope.row) ? '物化视图' : '普通视图' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="schema" label="Schema"></el-table-column>
      <el-table-column prop="valid" label="Valid"></el-table-column>
      <el-table-column prop="created" label="created"></el-table-column>
      <el-table-column prop="lastDDL" label="Last DDL"></el-table-column>
    </el-table>
  </div>
</template>

<script>
import sqlServer from "@/api/main/sql";
import { v4 as uuidv4 } from "uuid";
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
    isMaterializedView(item) {
      return String(item?.type || item?.tableDetails?.type || "").toUpperCase() === "MATERIALIZED VIEW";
    },
    quoteIdentifier(name) {
      return `"${String(name || "").replace(/"/g, '""')}"`;
    },
    handleCurrentChange(item) {
      this.$store.dispatch("jdTagsView/changeView", {
        title: item.name || item.tableDetails.tableSpace,
        id: this.$route.params.id
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
        skipCount: false,
        sql: `select * from ${this.quoteIdentifier(item.schema)}.${this.quoteIdentifier(item.name)}`,
        tableName: item.name,
        total: 0,
        type: this.dataInfo.dataSource.type
      };
      sqlServer.executeSql(send).then(res => {
        const result = res?.data?.[0];
        if (!res?.success || !result || !result.success) {
          this.$message.error(result?.message || res?.errorMessage || "打开视图失败");
          return;
        }
        const materializedView = this.isMaterializedView(item);
        this.$store.dispatch("workspaceData/setDataCurrentData", {
          pageId: this.pageId,
          title: item.name,
          type: materializedView ? "editTableData" : "editView",
          ...result,
          canEdit: materializedView ? false : result.canEdit,
          params: send,
          uniqueData: {
            dataSourceId: this.dataInfo.dataSource.id,
            dataSourceName: this.dataInfo.dataSource.alias,
            databaseName: "",
            sqlInfo: null,
            databaseType: this.dataInfo.dataSource.type,
            schemaName: item.schema,
            tableName: item.name,
            viewType: item.type,
            isLoading: true,
            dataType: "views"
          }
        });
      }).catch(() => this.$message.error("打开视图失败，请稍后重试"));
    }
  }
};
</script>

<style  scoped lang="scss">
.table_box {
  background: #fff;
  padding: 5px 10px 0px 10px;
  height: 100%;
}
::v-deep .el-table--medium .el-table__cell {
  padding: 2px 0px !important;
}
</style>
