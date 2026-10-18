<template>
  <div class="app-container">
    <el-form :inline="true" size="small" @submit.prevent="handleQuery">
      <el-form-item label="任务状态">
        <el-select v-model="queryParams.taskStatus" style="width: 160px" clearable placeholder="全部状态" @change="handleQuery">
          <el-option label="初始化" value="INIT" />
          <el-option label="处理中" value="PROCESSING" />
          <el-option label="完成" value="FINISH" />
          <el-option label="失败" value="ERROR" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" @click="resetQuery">重置</el-button>
        <el-button :disabled="!hasFinishedTask()" type="danger" plain icon="el-icon-delete" @click="clearFinished">清除已结束</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="taskName" label="任务名称" min-width="180" show-overflow-tooltip />
      <el-table-column label="任务类型" min-width="150" show-overflow-tooltip><template #default="scope">{{ typeText(scope.row.taskType) }}</template></el-table-column>
      <el-table-column prop="taskStatus" label="状态" width="100">
        <template #default="scope">
          <el-tag :type="statusType(scope.row.taskStatus)">{{ statusText(scope.row.taskStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="taskProgress" label="进度" width="160">
        <template #default="scope">
          <el-progress :percentage="progress(scope.row.taskProgress)" :status="scope.row.taskStatus === 'ERROR' ? 'exception' : undefined" />
          <el-tooltip v-if="taskMessage(scope.row)" :content="taskMessage(scope.row)" placement="top" popper-class="task-message-tooltip">
            <div class="task-progress-text">{{ taskMessage(scope.row) }}</div>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="gmtCreate" label="创建时间" width="180">
        <template #default="scope">{{ parseTime(scope.row.gmtCreate) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="scope">
          <el-button v-if="scope.row.taskStatus === 'FINISH' && scope.row.downloadUrl" type="text" icon="el-icon-download" @click="download(scope.row)">下载</el-button>
          <el-button v-if="!isRunning(scope.row.taskStatus)" type="text" class="task-clear" @click="clearTask(scope.row)">清除</el-button>
          <el-button v-if="canCancel(scope.row)" type="text" @click="cancelTask(scope.row)">取消</el-button>
          <span v-if="isRunning(scope.row.taskStatus) && !canCancel(scope.row)" class="muted">—</span>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNo" v-model:limit="queryParams.pageSize" @pagination="getList" />
    <div class="migration-section">
      <h3>跨库数据迁移 <el-button type="text" size="small" @click="openMigrationList">查看全部迁移任务</el-button></h3>
      <el-table :data="migrationTasks" size="small" stripe empty-text="暂无跨库迁移任务">
        <el-table-column label="任务名称" min-width="200"><template #default="scope"><el-button type="text" @click="openMigration(scope.row)">{{ scope.row.config && scope.row.config.name }}</el-button></template></el-table-column>
        <el-table-column prop="direction" label="方向" width="125" />
        <el-table-column label="状态" width="130"><template #default="scope"><el-tag size="small" :type="migrationStatusType(scope.row.status)">{{ migrationStatus(scope.row.status) }}</el-tag></template></el-table-column>
        <el-table-column prop="committedRows" label="已提交行数" width="140" />
        <el-table-column prop="message" label="执行信息" min-width="220" show-overflow-tooltip />
      </el-table>
    </div>
  </div>
</template>

<script>
import taskApi, { downloadTaskFile } from '@/api/main/task'
import migration from '@/api/main/migration'
import { notifyResourceTaskStatus } from '@/utils/resourceMutation'

export default {
  name: 'TaskCenter',
  data() {
    return {
      loading: false,
      list: [],
      migrationTasks: [],
      total: 0,
      refreshTimer: null,
      queryParams: { pageNo: 1, pageSize: 10, taskStatus: undefined }
    }
  },
  created() { this.getList() },
  beforeUnmount() { this.stopRefresh() },
  methods: {
    getList() {
      this.loading = true
      this.loadMigrations()
      taskApi.getTaskList(this.queryParams).then(res => {
        const page = res.data || {}
        this.list = page.data || []
        this.list.forEach(notifyResourceTaskStatus)
        this.total = page.total || 0
        this.syncRefresh()
      }).finally(() => { this.loading = false })
    },
    syncRefresh() {
      const hasRunningTask = this.list.some(item => item.taskStatus === 'INIT' || item.taskStatus === 'PROCESSING') || this.migrationTasks.some(item => ['MIG_RUNNING', 'MIG_CANCEL_REQUESTED'].includes(item.status))
      if (hasRunningTask && !this.refreshTimer) {
        this.refreshTimer = setInterval(() => this.getList(), 1000)
      } else if (!hasRunningTask) {
        this.stopRefresh()
      }
    },
    stopRefresh() {
      if (this.refreshTimer) {
        clearInterval(this.refreshTimer)
        this.refreshTimer = null
      }
    },
    isRunning(status) { return status === 'INIT' || status === 'PROCESSING' },
    canCancel(row) { return row.taskType === 'UPLOAD_TABLE_STRUCTURE' && this.isRunning(row.taskStatus) },
    hasFinishedTask() { return this.list.some(item => !this.isRunning(item.taskStatus)) },
    handleQuery() { this.queryParams.pageNo = 1; this.getList() },
    resetQuery() { this.queryParams.taskStatus = undefined; this.handleQuery() },
    progress(value) {
      const number = Number(value)
      return Number.isFinite(number) ? Math.max(0, Math.min(100, number <= 1 ? number * 100 : number)) : 0
    },
    taskMessage(row) {
      if (!row.content) return ''
      if (typeof row.content === 'string') {
        try {
          return decodeURIComponent(Array.prototype.map.call(atob(row.content), char => `%${(`00${char.charCodeAt(0).toString(16)}`).slice(-2)}`).join(''))
        } catch (e) {
          return row.content
        }
      }
      if (Array.isArray(row.content) && window.TextDecoder) {
        return new TextDecoder('utf-8').decode(new Uint8Array(row.content))
      }
      return ''
    },
    statusText(status) { return { INIT: '初始化', PROCESSING: '处理中', FINISH: '完成', ERROR: '失败' }[status] || status || '未知' },
    statusType(status) { return { FINISH: 'success', ERROR: 'danger', PROCESSING: 'warning' }[status] || 'info' },
    download(row) { downloadTaskFile(row.id, row.taskType, row) },
    typeText(type) { return ({ TABLE_EXPORT: '表文件导出', TABLE_IMPORT: '表文件导入', SCHEMA_SQL_EXPORT: '模式 SQL 导出', SCHEMA_SQL_IMPORT: '模式 SQL 导入', DOWNLOAD_TABLE_STRUCTURE: 'DMP 导出', UPLOAD_TABLE_STRUCTURE: 'DMP 导入' })[type] || type || '后台任务' },
    async loadMigrations() {
      try { const result = await migration.list({ page: 1, size: 10 }); this.migrationTasks = result.data || []; this.syncRefresh() }
      catch (_) { this.migrationTasks = [] }
    },
    migrationStatus(status) { return ({ MIG_DRAFT: '待配置', MIG_READY: '待确认', MIG_BLOCKED: '预检查阻断', MIG_RUNNING: '执行中', MIG_COMPLETED: '已完成', MIG_FAILED: '失败', MIG_PARTIAL: '部分完成', MIG_CANCELLED: '已取消' })[status] || status },
    migrationStatusType(status) { return status === 'MIG_COMPLETED' ? 'success' : ['MIG_FAILED', 'MIG_BLOCKED'].includes(status) ? 'danger' : 'info' },
    openMigration(task) { window.open(`/data-migration/index?taskId=${encodeURIComponent(task.id)}`, '_blank') },
    openMigrationList() { window.open('/data-migration/index', '_blank') },
    clearTask(row) {
      this.$confirm('仅清除任务记录，不会删除已导出的文件。确定继续吗？', '清除任务', { type: 'warning' }).then(() => taskApi.clearTask(row.id)).then(res => {
        if (res.success) {
          this.$message.success('任务已清除')
          this.getList()
        } else this.$message.error(res.errorMessage || '清除任务失败')
      }).catch(() => {})
    },
    cancelTask(row) {
      this.$confirm('将立即终止正在执行的 DMP 导入，已导入的数据不会自动回滚。确定继续吗？', '取消导入', { type: 'warning' }).then(() => taskApi.cancelTask(row.id)).then(res => {
        if (res.success) {
          this.$message.success('导入任务已取消')
          this.getList()
        } else this.$message.error(res.errorMessage || '取消任务失败')
      }).catch(() => {})
    },
    clearFinished() {
      this.$confirm('将清除全部已结束任务记录，不会删除已导出的文件。确定继续吗？', '清除任务', { type: 'warning' }).then(() => taskApi.clearFinishedTasks()).then(res => {
        if (res.success) {
          this.$message.success('已结束任务已清除')
          this.getList()
        } else this.$message.error(res.errorMessage || '清除任务失败')
      }).catch(() => {})
    }
  }
}
</script>

<style scoped>
.migration-section { margin-top: 26px; }
.migration-section h3 { display: flex; align-items: center; gap: 12px; margin: 0 0 10px; font-size: 16px; }
.muted { color: #909399; }
.task-progress-text { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; overflow-wrap: anywhere; margin-top: 4px; color: #909399; font-size: 12px; line-height: 1.5; }
</style>

<style>
.task-message-tooltip { max-width: 480px; max-height: 240px; overflow: auto; overflow-wrap: anywhere; white-space: pre-wrap; }
</style>
