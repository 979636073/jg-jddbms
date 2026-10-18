<template>
  <div class="app-container">
    <h2>数据源控制</h2>
    <p class="summary">集中查看连接、所有者和共享授权。授权按操作生效，目标数据库账号仍执行最终权限检查。</p>
    <el-form inline size="small" @submit.prevent>
      <el-form-item label="连接名称">
        <el-input v-model="keyword" clearable placeholder="搜索连接名称或地址" @keyup.enter="reload" />
      </el-form-item>
      <el-button type="primary" size="small" icon="el-icon-search" @click="reload">查询</el-button>
      <el-button size="small" icon="el-icon-refresh" @click="reload">刷新</el-button>
    </el-form>
    <el-table v-loading="loading" :data="rows" size="small" border empty-text="暂无可管理的数据源">
      <el-table-column prop="alias" label="连接名称" min-width="170" />
      <el-table-column prop="type" label="数据库类型" width="120" />
      <el-table-column prop="host" label="主机" min-width="170" />
      <el-table-column label="共享状态" width="110">
        <template #default="scope">{{ scope.row.kind === 'SHARED' ? '已共享' : '私有' }}</template>
      </el-table-column>
      <el-table-column prop="userId" label="所有者 ID" width="110" />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="scope">
          <el-button v-if="canManage(scope.row)" type="text" @click="openSharing(scope.row)">共享授权</el-button>
          <span v-else>仅可查看</span>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" v-model:page="pageNo" v-model:limit="pageSize" @pagination="reload" />
    <DataSourceSharingDialog :visible="sharingVisible" :data-source-id="selectedId"
      @close="sharingVisible = false" @changed="reload" />
  </div>
</template>

<script>
import connectionServer from '@/api/main/connection'
import DataSourceSharingDialog from '@/views/main/workspace/components/DataSourceSharingDialog.vue'

export default {
  name: 'DataSourceControl',
  components: { DataSourceSharingDialog },
  data() {
    return { rows: [], total: 0, pageNo: 1, pageSize: 20, keyword: '', loading: false,
      sharingVisible: false, selectedId: null }
  },
  created() { this.reload() },
  methods: {
    canManage(row) {
      return row.canManage === true
    },
    openSharing(row) { this.selectedId = row.id; this.sharingVisible = true },
    async reload() {
      this.loading = true
      try {
        const result = await connectionServer.getList({ pageNo: this.pageNo, pageSize: this.pageSize, searchKey: this.keyword })
        if (!result.success) throw new Error(result.errorMessage || '读取数据源失败')
        this.rows = result.data?.data || []
        this.total = Number(result.data?.total || 0)
      } catch (error) { this.$message.error(error.message || '读取数据源失败') }
      finally { this.loading = false }
    }
  }
}
</script>

<style scoped>
.summary { color: #606266; margin-bottom: 18px; }
</style>
