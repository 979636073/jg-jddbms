<script>
import connectionServer from "@/api/main/connection";
import AddOrUpdate from "@/views/main/workspace/components/DataSource/AddOrUpdate.vue";

export default {
  name: 'databaseConfig',
  components: {AddOrUpdate},
  data() {
    return {
      dataSourceList: [],
      dataSourceLoading: false,
      searchKey: '',
      queryParams: {
        pageNum: 1,
        pageSize: 50
      },
      total: 0,
    }
  },
  watch: {
    dataSourceList: {
      handler(newVal) {
        this.$store.dispatch('getDataSourceList')
      },
      deep: true
    }
  },
  created() {
    this.getConnectionList()
  },
  methods: {
    // 获取数据源列表
    getConnectionList() {
      this.dataSourceLoading = true
      let send = {
        pageNo: this.queryParams.pageNum,
        pageSize: this.queryParams.pageSize,
        refresh: true,
        searchKey: this.searchKey
      }
      connectionServer.getList(send).then((res) => {
        this.dataSourceList = res.data.data;
        this.total = res.data.total
        this.dataSourceLoading = false
      });
    },
    // 添加 修改数据源
    addOrUpdateDataSource(row) {
      this.$nextTick(() => {
        this.$refs.AddOrUpdate.init(row)
      })
    },
    // 复制数据源
    copyConnection(item) {
      connectionServer.clone({ id: item.id }).then((res) => {
        this.$message.success('复制成功')
        this.getConnectionList();
      })
    },
    // 删除数据源
    handelDelete(item) {
      this.$confirm('是否确定删除该数据源?', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        connectionServer.remove(item.id).then(() => {
          this.$message.success('删除成功')
          this.getConnectionList();
        });
      }).catch(() => {
        this.$message({
          type: 'info',
          message: '已取消删除'
        });
      });
    },
    handleSizeChange(val) {
      this.queryParams.pageNum = 1
      this.queryParams.pageSize = val
      this.getConnectionList()
    },
    handleCurrentChange(val) {
      this.queryParams.pageNum = val
      this.getConnectionList()
    },
  }
}
</script>

<template>
  <div class="databaseConfig">
    <div class="table_btn_list">
      <el-button @click="addOrUpdateDataSource()" size="mini" type="text" class="el-button_before" style="margin-left: 0">
        <img src="@/assets/main/3-con-ico01.png" alt="">
        添加
      </el-button>
      <el-button @click="getConnectionList" size="mini" type="text" class="el-button_before">
        <img src="@/assets/main/5-ico3.png" alt="">
        刷新
      </el-button>
      <el-input
        v-model="searchKey"
        size="mini"
        placeholder="数据源"
        style="width: 200px;margin: 0 5px"
      />
      <el-button @click="getConnectionList" size="mini" type="primary">
        <img src="@/assets/main/5-ico4.png" alt="">
        查询
      </el-button>
    </div>

    <el-table
      :data="dataSourceList"
      style="width: 100%"
      v-loading="dataSourceLoading"
      size="mini"
      height="calc(100% - 100px)"
    >
      <el-table-column label="操作" width="240">
        <template slot-scope="scope">
          <el-button @click="addOrUpdateDataSource(scope.row)" size="mini" type="text" plain>
            <img src="@/assets/main/3-con-ico03.png" alt="">
            修改
          </el-button>
          <el-button @click="copyConnection(scope.row)" size="mini" type="text" plain>
            <img src="@/assets/main/3-con-ico06.png" alt="">
            复制
          </el-button>
          <el-button @click="handelDelete(scope.row)" size="mini" type="text" plain>
            <img src="@/assets/main/3-con-ico02.png" alt="">
            删除
          </el-button>
        </template>
      </el-table-column>
      <el-table-column prop="alias" label="配置名称"></el-table-column>
      <el-table-column prop="type" label="数据库类型"></el-table-column>
      <el-table-column prop="host" label="数据库ip"></el-table-column>
      <el-table-column prop="port" label="数据库端口"></el-table-column>
      <el-table-column prop="user" label="数据库用户名"></el-table-column>
    </el-table>

    <el-pagination
      @size-change="handleSizeChange"
      @current-change="handleCurrentChange"
      :current-page="queryParams.pageNum"
      :page-sizes="[10, 50, 100, 200, 500, 1000]"
      :page-size="queryParams.pageSize"
      layout="total, sizes, prev, pager, next, jumper"
      :total="total">
    </el-pagination>
    <AddOrUpdate ref="AddOrUpdate" @getConnectionList="getConnectionList" />
  </div>
</template>

<style scoped lang="scss">
::v-deep {
  .el-table .el-button--text {
    margin: 0;
  }
  .el-button {
    & > span{
      display: flex;
      align-items: center;
      img {
        margin-right: 6px;
      }
    }
  }
}
.databaseConfig {
  height: calc(100% - 58px);
  background: #fff;
  border-radius: 5px;
  padding: 0 20px;
  .table_btn_list {
    display: flex;
    align-items: center;
    padding: 10px 0;
    .el-button_before::before {
      content: '';
      position: absolute;
      right: -15px;
      top: 8px;
      width: 1px;
      height: 14px;
      background: #68728C;
    }
  }
}
</style>
