<template>
  <div class="db-monitor">
    <div class="monitor-heading">
      <div><h2>数据库运行监控</h2><p>按连接查看会话、阻塞和表空间；仅查询，不会结束会话。</p></div>
      <div class="monitor-actions">
        <el-select v-model="dataSourceId" filterable placeholder="选择数据源" size="small" @change="refresh">
          <el-option v-for="item in sources" :key="item.id" :value="item.id" :label="`${item.alias || item.user} · ${item.type}`" />
        </el-select>
        <el-button size="small" :loading="loading" @click="refresh">刷新</el-button>
      </div>
    </div>
    <el-alert v-if="loadError" :title="loadError" type="error" show-icon :closable="false" />
    <template v-if="snapshot">
      <div class="monitor-meta">采集时间：{{ captureTime }} · 每 30 秒自动刷新</div>
      <div class="monitor-cards">
        <div><span>总会话</span><strong>{{ display(snapshot.sessionCount) }}</strong></div>
        <div><span>活动会话</span><strong>{{ display(snapshot.activeCount) }}</strong></div>
        <div><span>阻塞等待</span><strong>{{ display(snapshot.blockedCount) }}</strong></div>
        <div><span>表空间文件</span><strong>{{ snapshot.tablespaceFiles ? snapshot.tablespaceFiles.length : '—' }}</strong></div>
      </div>
      <div class="monitor-thresholds">
        <strong>告警阈值</strong>
        <label>活动会话 ≥ <el-input-number v-model="thresholds.active" :min="1" :max="100000" size="mini" @change="saveThresholds" /></label>
        <label>表空间使用率 ≥ <el-input-number v-model="thresholds.space" :min="1" :max="100" size="mini" @change="saveThresholds" /> %</label>
        <span>阻塞等待出现即告警；阈值保存在当前浏览器。</span>
      </div>
      <section class="monitor-section">
        <h3>当前告警 <el-tag size="mini" :type="alerts.length ? 'danger' : 'success'">{{ alerts.length }}</el-tag></h3>
        <el-alert v-for="alert in alerts" :key="alert.key" :title="alert.message" type="warning" show-icon :closable="false" class="monitor-alert" />
        <p v-if="!alerts.length && !hasSectionErrors" class="monitor-muted">当前采集数据未触发阈值。</p>
        <p v-if="hasSectionErrors" class="monitor-muted">部分指标无法读取；不可将缺失数据判断为正常。请检查数据库账号的动态视图和表空间权限。</p>
      </section>
      <section class="monitor-section">
        <h3>会话 <small>最多显示 50 条</small></h3>
        <el-alert v-if="snapshot.errors && snapshot.errors.sessions" :title="`会话读取失败：${snapshot.errors.sessions}`" type="error" :closable="false" />
        <el-table v-else :data="snapshot.sessions || []" size="small" max-height="360" empty-text="没有可见会话">
          <el-table-column prop="id" label="会话 ID" min-width="140" />
          <el-table-column prop="user" label="用户" min-width="140" />
          <el-table-column prop="state" label="状态" min-width="110" />
          <el-table-column prop="client" label="客户端" min-width="210" />
        </el-table>
      </section>
      <section class="monitor-section">
        <h3>表空间文件</h3>
        <el-alert v-if="snapshot.errors && snapshot.errors.tablespaces" :title="`表空间读取失败：${snapshot.errors.tablespaces}`" type="error" :closable="false" />
        <el-table v-else :data="snapshot.tablespaceFiles || []" size="small" max-height="360" empty-text="没有可见表空间文件">
          <el-table-column prop="name" label="表空间" min-width="150" />
          <el-table-column prop="path" label="文件路径" min-width="240" show-overflow-tooltip />
          <el-table-column label="使用率" width="115"><template slot-scope="scope">{{ scope.row.usagePercent == null ? '—' : `${scope.row.usagePercent}%` }}</template></el-table-column>
          <el-table-column prop="status" label="状态" width="110" />
        </el-table>
      </section>
    </template>
  </div>
</template>

<script>
import connectionServer from '@/api/main/connection'
import { getDatabaseSnapshot } from '@/api/main/monitor'

export default {
  name: 'DatabaseMonitor',
  data() { return { sources: [], dataSourceId: null, snapshot: null, loading: false, loadError: '', timer: null, thresholds: { active: 30, space: 85 } } },
  computed: {
    captureTime() { return this.snapshot ? new Date(this.snapshot.capturedAt).toLocaleString() : '—' },
    hasSectionErrors() { return Boolean(this.snapshot && Object.keys(this.snapshot.errors || {}).length) },
    alerts() {
      const item = this.snapshot
      if (!item) return []
      const alerts = []
      if (item.activeCount != null && item.activeCount >= this.thresholds.active) alerts.push({ key: 'active', message: `活动会话达到 ${item.activeCount}，阈值 ${this.thresholds.active}` })
      if (item.blockedCount > 0) alerts.push({ key: 'blocked', message: `存在 ${item.blockedCount} 个阻塞等待，请检查相关事务` })
      ;(item.tablespaceFiles || []).forEach(file => {
        if (file.usagePercent != null && file.usagePercent >= this.thresholds.space) alerts.push({ key: `space-${file.path}`, message: `${file.name} 使用率 ${file.usagePercent}%，阈值 ${this.thresholds.space}%` })
      })
      return alerts
    }
  },
  async created() {
    try {
      const saved = JSON.parse(localStorage.getItem('jddbms-monitor-thresholds') || '{}')
      if (Number.isFinite(saved.active) && saved.active >= 1) this.thresholds.active = saved.active
      if (Number.isFinite(saved.space) && saved.space >= 1 && saved.space <= 100) this.thresholds.space = saved.space
    } catch (error) { /* keep defaults */ }
    try {
      const result = await connectionServer.getList({ pageNo: 1, pageSize: 10000 })
      if (!result.success) throw new Error(result.errorMessage || '数据源加载失败')
      this.sources = (result.data && result.data.data) || []
      if (this.sources.length) { this.dataSourceId = this.sources[0].id; await this.refresh() }
    } catch (error) { this.loadError = error.message || '数据源加载失败' }
    this.timer = setInterval(() => { if (this.dataSourceId && !document.hidden) this.refresh() }, 30000)
  },
  beforeDestroy() { clearInterval(this.timer) },
  methods: {
    display(value) { return value == null ? '—' : value },
    saveThresholds() { localStorage.setItem('jddbms-monitor-thresholds', JSON.stringify(this.thresholds)) },
    async refresh() {
      if (!this.dataSourceId || this.loading) return
      this.loading = true
      this.loadError = ''
      try {
        const result = await getDatabaseSnapshot(this.dataSourceId)
        if (!result.success) throw new Error(result.errorMessage || '监控采集失败')
        this.snapshot = result.data
      } catch (error) { this.loadError = error.message || '监控采集失败' }
      finally { this.loading = false }
    }
  }
}
</script>

<style scoped lang="scss">
.db-monitor { width: 100%; height: 100%; min-height: 0; padding: 20px; max-width: 1500px; margin: auto; overflow-y: auto; overflow-x: hidden; overscroll-behavior: contain; color: #303133; }
.monitor-heading, .monitor-actions, .monitor-thresholds { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.monitor-heading { justify-content: space-between; } .monitor-heading h2 { margin: 0 0 6px; } .monitor-heading p, .monitor-meta, .monitor-muted { color: #606266; font-size: 13px; }
.monitor-actions .el-select { width: 260px; } .monitor-meta { margin: 14px 0; }
.monitor-cards { display: grid; grid-template-columns: repeat(4, minmax(150px, 1fr)); gap: 12px; }
.monitor-cards > div, .monitor-section, .monitor-thresholds { border: 1px solid #e4e7ed; border-radius: 6px; background: #fff; padding: 15px; }
.monitor-cards span { display: block; color: #606266; font-size: 13px; } .monitor-cards strong { display: block; margin-top: 8px; font-size: 24px; }
.monitor-thresholds { margin-top: 14px; font-size: 13px; } .monitor-thresholds label { display: flex; align-items: center; gap: 5px; } .monitor-thresholds span { color: #909399; }
.monitor-section { margin-top: 14px; } .monitor-section h3 { margin: 0 0 12px; } .monitor-section h3 small { color: #909399; font-weight: normal; } .monitor-alert + .monitor-alert { margin-top: 8px; }
@media(max-width: 800px) { .monitor-cards { grid-template-columns: repeat(2, minmax(0, 1fr)); } .monitor-actions .el-select { width: 200px; } }
</style>
