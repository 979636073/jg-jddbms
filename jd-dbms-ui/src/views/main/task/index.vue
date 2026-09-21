<template>
  <div class="app-container">
    <el-form :inline="true" size="small" @submit.native.prevent="handleQuery">
      <el-form-item label="任务状态">
        <el-select v-model="queryParams.taskStatus" clearable placeholder="全部状态" @change="handleQuery">
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
      <el-table-column prop="taskType" label="任务类型" min-width="150" show-overflow-tooltip />
      <el-table-column prop="taskStatus" label="状态" width="100">
        <template slot-scope="scope">
          <el-tag :type="statusType(scope.row.taskStatus)">{{ statusText(scope.row.taskStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="taskProgress" label="进度" width="160">
        <template slot-scope="scope">
          <el-progress :percentage="progress(scope.row.taskProgress)" :status="scope.row.taskStatus === 'ERROR' ? 'exception' : undefined" />
          <div v-if="taskMessage(scope.row)" class="task-progress-text">{{ taskMessage(scope.row) }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="gmtCreate" label="创建时间" width="180">
        <template slot-scope="scope">{{ parseTime(scope.row.gmtCreate) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="130" fixed="right">
        <template slot-scope="scope">
          <el-button v-if="scope.row.taskStatus === 'FINISH' && scope.row.downloadUrl" type="text" icon="el-icon-download" @click="download(scope.row)">下载</el-button>
          <el-button v-if="!isRunning(scope.row.taskStatus)" type="text" class="task-clear" @click="clearTask(scope.row)">清除</el-button>
          <el-button v-if="canCancel(scope.row)" type="text" @click="cancelTask(scope.row)">取消</el-button>
          <span v-if="isRunning(scope.row.taskStatus) && !canCancel(scope.row)" class="muted">—</span>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNo" :limit.sync="queryParams.pageSize" @pagination="getList" />
  </div>
</template>

<script>
import taskApi, { downloadTaskFile } from '@/api/main/task'

export default {
  name: 'TaskCenter',
  data() {
    return {
      loading: false,
      list: [],
      total: 0,
      refreshTimer: null,
      queryParams: { pageNo: 1, pageSize: 10, taskStatus: undefined }
    }
  },
  created() { this.getList() },
  beforeDestroy() { this.stopRefresh() },
  methods: {
    getList() {
      this.loading = true
      taskApi.getTaskList(this.queryParams).then(res => {
        const page = res.data || {}
        this.list = page.data || []
        this.total = page.total || 0
        this.syncRefresh()
      }).finally(() => { this.loading = false })
    },
    syncRefresh() {
      const hasRunningTask = this.list.some(item => item.taskStatus === 'INIT' || item.taskStatus === 'PROCESSING')
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
    download(row) { downloadTaskFile(row.id, row.taskType) },
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
.muted { color: #909399; }
.task-progress-text { margin-top: 4px; color: #909399; font-size: 12px; white-space: pre-line; }
</style>
