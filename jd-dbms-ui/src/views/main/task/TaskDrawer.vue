<template>
  <el-drawer
    title="后台任务中心"
    :visible="visible"
    direction="rtl"
    size="480px"
    :before-close="close"
  >
    <div class="task-drawer">
      <div class="task-drawer__toolbar">
        <span><i class="el-icon-info" /> 导入、导出等任务会在后台执行</span>
        <div>
          <el-button type="text" size="mini" icon="el-icon-refresh" :loading="loading" @click="getList">刷新</el-button>
          <el-button type="text" size="mini" :disabled="!hasFinishedTask()" @click="clearFinished">清除已结束</el-button>
          <el-button type="text" size="mini" @click="openTaskCenter">完整列表</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="list" size="small" border :empty-text="'暂无后台任务'">
        <el-table-column label="任务详情与实时进度" min-width="270">
          <template slot-scope="scope">
            <div class="task-name">{{ scope.row.taskName || scope.row.taskType || '未命名任务' }}</div>
            <div class="task-type">{{ scope.row.taskType || '后台任务' }}</div>
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
          <template slot-scope="scope">
            <el-tag :type="statusType(scope.row.taskStatus)" size="mini">{{ statusText(scope.row.taskStatus) }}</el-tag>
            <div class="task-time">{{ parseTime(scope.row.gmtCreate) }}</div>
            <el-button
              v-if="scope.row.taskStatus === 'FINISH' && scope.row.downloadUrl"
              type="text"
              size="mini"
              icon="el-icon-download"
              @click="download(scope.row)"
            >下载</el-button>
            <el-button
              v-if="!isRunning(scope.row.taskStatus)"
              type="text"
              size="mini"
              class="task-clear"
              @click="clearTask(scope.row)"
            >清除</el-button>
            <el-button
              v-if="canCancel(scope.row)"
              type="text"
              size="mini"
              class="task-clear"
              @click="cancelTask(scope.row)"
            >取消</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </el-drawer>
</template>

<script>
import taskApi, { downloadTaskFile } from '@/api/main/task'

export default {
  name: 'TaskDrawer',
  props: {
    visible: { type: Boolean, default: false }
  },
  data() {
    return {
      loading: false,
      list: [],
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
  beforeDestroy() {
    this.stopRefresh()
  },
  methods: {
    close() {
      this.$emit('update:visible', false)
    },
    getList() {
      this.loading = true
      taskApi.getTaskList({ pageNo: 1, pageSize: 10 }).then(res => {
        const page = res.data || {}
        this.list = page.data || []
        this.$emit('running-count', this.list.filter(item => this.isRunning(item.taskStatus)).length)
        this.syncRefresh()
      }).finally(() => {
        this.loading = false
      })
    },
    syncRefresh() {
      if (this.list.some(item => this.isRunning(item.taskStatus))) {
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
      downloadTaskFile(row.id, row.taskType)
    },
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
.task-drawer {
  padding: 0 15px 15px;

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
