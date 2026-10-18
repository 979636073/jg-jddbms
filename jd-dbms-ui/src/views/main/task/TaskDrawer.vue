<template>
  <el-drawer
    title="后台任务中心"
    :model-value="visible"
    direction="rtl"
    size="480px"
    :before-close="close"
  >
    <div class="task-drawer">
      <div class="task-drawer__toolbar">
        <span><i class="el-icon-info" /> 导入、导出等任务会在后台执行</span>
        <div>
          <el-button type="text" size="small" icon="el-icon-refresh" :loading="loading" @click="getList">刷新</el-button>
          <el-button type="text" size="small" :disabled="!hasFinishedTask()" @click="clearFinished">清除已结束</el-button>
          <el-button type="text" size="small" @click="openTaskCenter">完整列表</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="list" size="small" border :empty-text="'暂无后台任务'">
        <el-table-column label="任务详情与实时进度" min-width="270">
          <template #default="scope">
            <div class="task-name">{{ scope.row.taskName || scope.row.taskType || '未命名任务' }}</div>
            <div class="task-type">{{ typeText(scope.row.taskType) }}</div>
            <template v-if="isRunning(scope.row.taskStatus)">
              <el-progress :percentage="progress(scope.row.taskProgress)" :stroke-width="14" :text-inside="true" status="success" />
              <div class="task-running"><i class="el-icon-loading" /> {{ taskMessage(scope.row) || statusText(scope.row.taskStatus) }}</div>
            </template>
            <div v-else-if="scope.row.taskStatus === 'ERROR'" class="task-error">
              <i class="el-icon-error" /> <b>任务失败</b>
              <div v-if="taskMessage(scope.row)">{{ taskMessage(scope.row) }}</div>
            </div>
            <div v-else-if="scope.row.taskStatus === 'FINISH'" class="task-success">
              <i class="el-icon-circle-check" /> 已完成
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="92" align="center">
          <template #default="scope">
            <el-tag :type="statusType(scope.row.taskStatus)" size="small">{{ statusText(scope.row.taskStatus) }}</el-tag>
            <div class="task-time">{{ parseTime(scope.row.gmtCreate) }}</div>
            <el-button
              v-if="scope.row.taskStatus === 'FINISH' && scope.row.downloadUrl"
              type="text"
              size="small"
              icon="el-icon-download"
              @click="download(scope.row)"
            >下载</el-button>
            <el-button
              v-if="!isRunning(scope.row.taskStatus)"
              type="text"
              size="small"
              class="task-clear"
              @click="clearTask(scope.row)"
            >清除</el-button>
            <el-button
              v-if="canCancel(scope.row)"
              type="text"
              size="small"
              class="task-clear"
              @click="cancelTask(scope.row)"
            >取消</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="migration-heading">跨库数据迁移 <el-button type="text" size="small" @click="openMigrationList">查看全部</el-button></div>
      <div v-for="task in migrationTasks" :key="task.id" class="migration-task">
        <el-button type="text" size="small" @click="openMigration(task)">{{ task.config && task.config.name }}</el-button>
        <el-tag size="small" :type="migrationStatusType(task.status)">{{ migrationStatus(task.status) }}</el-tag>
        <span v-if="task.status === 'MIG_RUNNING'">已提交 {{ task.committedRows || 0 }} 行</span>
      </div>
    </div>
  </el-drawer>
</template>

<script>
import taskApi, { downloadTaskFile } from '@/api/main/task'
import migration from '@/api/main/migration'
import { notifyResourceTaskStatus } from '@/utils/resourceMutation'

export default {
  name: 'TaskDrawer',
  props: {
    visible: { type: Boolean, default: false }
  },
  data() {
    return {
      loading: false,
      list: [],
      migrationTasks: [],
      refreshTimer: null
    }
  },
  watch: {
    visible(value) {
      if (value) {
        this.getList()
      }
    }
  },
  created() {
    this.getList()
  },
  beforeUnmount() {
    this.stopRefresh()
  },
  methods: {
    close() {
      this.$emit('update:visible', false)
    },
    getList() {
      this.loading = true
      this.loadMigrations()
      taskApi.getTaskList({ pageNo: 1, pageSize: 10 }).then(res => {
        const page = res.data || {}
        this.list = page.data || []
        this.list.forEach(notifyResourceTaskStatus)
        this.$emit('running-count', this.list.filter(item => this.isRunning(item.taskStatus)).length)
        this.syncRefresh()
      }).finally(() => {
        this.loading = false
      })
    },
    syncRefresh() {
      if (this.list.some(item => this.isRunning(item.taskStatus)) || this.migrationTasks.some(item => ['MIG_RUNNING', 'MIG_CANCEL_REQUESTED'].includes(item.status))) {
        if (!this.refreshTimer) {
          this.refreshTimer = setInterval(() => this.getList(), 1000)
        }
      } else {
        this.stopRefresh()
      }
    },
    stopRefresh() {
      if (this.refreshTimer) {
        clearInterval(this.refreshTimer)
        this.refreshTimer = null
      }
    },
    isRunning(status) {
      return status === 'INIT' || status === 'PROCESSING'
    },
    canCancel(row) {
      return row.taskType === 'UPLOAD_TABLE_STRUCTURE' && this.isRunning(row.taskStatus)
    },
    hasFinishedTask() {
      return this.list.some(item => !this.isRunning(item.taskStatus))
    },
    progress(value) {
      const number = Number(value)
      return Number.isFinite(number) ? Math.max(0, Math.min(100, number <= 1 ? number * 100 : number)) : 0
    },
    statusText(status) {
      return { INIT: '等待中', PROCESSING: '执行中', FINISH: '已完成', ERROR: '失败' }[status] || status || '未知'
    },
    statusType(status) {
      return { FINISH: 'success', ERROR: 'danger', PROCESSING: 'warning' }[status] || 'info'
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
    download(row) {
      downloadTaskFile(row.id, row.taskType, row)
    },
    async loadMigrations() {
      try {
        const result = await migration.list({ page: 1, size: 5 })
        this.migrationTasks = result.data || []
        this.$emit('running-count', this.list.filter(item => this.isRunning(item.taskStatus)).length + this.migrationTasks.filter(item => ['MIG_RUNNING', 'MIG_CANCEL_REQUESTED'].includes(item.status)).length)
        this.syncRefresh()
      } catch (_) { this.migrationTasks = [] }
    },
    migrationStatus(status) { return ({ MIG_DRAFT: '待配置', MIG_READY: '待确认', MIG_BLOCKED: '预检查阻断', MIG_RUNNING: '执行中', MIG_COMPLETED: '已完成', MIG_FAILED: '失败', MIG_PARTIAL: '部分完成', MIG_CANCELLED: '已取消' })[status] || status },
    migrationStatusType(status) { return status === 'MIG_COMPLETED' ? 'success' : ['MIG_FAILED', 'MIG_BLOCKED'].includes(status) ? 'danger' : 'info' },
    openMigration(task) { window.open(`/data-migration/index?taskId=${encodeURIComponent(task.id)}`, '_blank') },
    openMigrationList() { window.open('/data-migration/index', '_blank') },
    typeText(type) { return ({ TABLE_EXPORT: '表文件导出', TABLE_IMPORT: '表文件导入', SCHEMA_SQL_EXPORT: '模式 SQL 导出', SCHEMA_SQL_IMPORT: '模式 SQL 导入', DOWNLOAD_TABLE_STRUCTURE: 'DMP 导出', UPLOAD_TABLE_STRUCTURE: 'DMP 导入' })[type] || type || '后台任务' },
    clearTask(row) {
      this.$confirm('仅清除任务记录，不会删除已导出的文件。确定继续吗？', '清除任务', { type: 'warning' }).then(() => {
        return taskApi.clearTask(row.id)
      }).then(res => {
        if (res.success) {
          this.$message.success('任务已清除')
          this.getList()
        } else {
          this.$message.error(res.errorMessage || '清除任务失败')
        }
      }).catch(() => {})
    },
    cancelTask(row) {
      this.$confirm('将立即终止正在执行的 DMP 导入，已导入的数据不会自动回滚。确定继续吗？', '取消导入', { type: 'warning' }).then(() => {
        return taskApi.cancelTask(row.id)
      }).then(res => {
        if (res.success) {
          this.$message.success('导入任务已取消')
          this.getList()
        } else {
          this.$message.error(res.errorMessage || '取消任务失败')
        }
      }).catch(() => {})
    },
    clearFinished() {
      this.$confirm('将清除全部已结束任务记录，不会删除已导出的文件。确定继续吗？', '清除任务', { type: 'warning' }).then(() => {
        return taskApi.clearFinishedTasks()
      }).then(res => {
        if (res.success) {
          this.$message.success('已结束任务已清除')
          this.getList()
        } else {
          this.$message.error(res.errorMessage || '清除任务失败')
        }
      }).catch(() => {})
    },
    openTaskCenter() {
      this.close()
      this.$router.push('/task-center/index')
    }
  }
}
</script>

<style scoped lang="scss">
.migration-heading { display: flex; justify-content: space-between; align-items: center; margin: 16px 0 7px; font-weight: 600; }
.migration-task { display: flex; align-items: center; gap: 8px; padding: 6px 0; border-bottom: 1px solid #edf0f4; }
.migration-task > span:last-child { color: #6d7788; font-size: 12px; }
.task-drawer {
  padding: 0 15px 15px;
  max-height: calc(100vh - 70px);
  overflow-y: auto;

  &__toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 10px;
    color: #909399;
    font-size: 12px;
  }
}

.task-name {
  margin-bottom: 3px;
  color: #303133;
  font-weight: 600;
  word-break: break-word;
}

.task-type,
.task-time {
  color: #909399;
  font-size: 11px;
}

.task-type {
  margin-bottom: 6px;
}

.task-time {
  margin-top: 6px;
  line-height: 1.35;
}

.task-running,
.task-success,
.task-error {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.45;
  word-break: break-word;
}

.task-running { color: #e6a23c; }
.task-success { color: #67c23a; }
.task-error {
  padding: 6px;
  border: 1px solid #fbc4c4;
  border-radius: 4px;
  background: #fef0f0;
  color: #f56c6c;
  white-space: pre-line;
}
</style>
