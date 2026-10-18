<template>
  <el-dialog title="共享连接授权" :model-value="visible" width="960px" :close-on-click-modal="false" @open="load" @close="$emit('close')">
    <div v-loading="loading">
      <el-tabs v-model="activeTab" @tab-click="tabChanged">
      <el-tab-pane label="授权管理" name="grants">
      <p>按操作授权用户或团队使用此连接。目标数据库账号的权限仍会生效。</p>
      <el-form inline size="small" @submit.prevent>
        <el-form-item label="授权类型">
          <el-select v-model="objectType" style="width: 100px" @change="resetSearch">
            <el-option label="用户" value="USER" /><el-option label="团队" value="TEAM" />
          </el-select>
        </el-form-item>
        <el-form-item label="授权对象">
          <el-select v-model="selectedIds" multiple filterable remote clearable :placeholder="objectType === 'USER' ? '搜索登录账号' : '搜索团队'"
                     :remote-method="searchObjects" :loading="searching" style="width: 250px">
            <el-option v-for="item in candidates" :key="item.id" :value="item.id" :label="item.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作权限">
          <el-select v-model="permissionCodes" multiple collapse-tags style="width: 260px">
            <el-option v-for="action in actions" :key="action.code" :label="action.label" :value="action.code" />
          </el-select>
        </el-form-item>
        <el-button type="primary" size="small" :loading="saving" :disabled="!selectedIds.length || !permissionCodes.length" @click="grant">批量授权</el-button>
      </el-form>
      <el-table :data="grants" size="small" max-height="320" empty-text="当前仅连接所有者可使用">
        <el-table-column label="类型" width="80"><template #default="scope">{{ scope.row.objectType === 'USER' ? '用户' : scope.row.objectType === 'TEAM' ? '团队' : scope.row.objectType }}</template></el-table-column>
        <el-table-column prop="name" label="名称" />
        <el-table-column prop="description" label="说明" />
        <el-table-column label="操作权限" min-width="250">
          <template #default="scope">
            <el-select v-model="scope.row.permissionCodes" multiple collapse-tags size="small" style="width: 185px">
              <el-option v-for="action in actions" :key="action.code" :label="action.label" :value="action.code" />
            </el-select>
            <el-button type="text" :disabled="saving || !scope.row.permissionCodes.length" @click="saveGrant(scope.row)">保存</el-button>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="scope">
            <el-button v-if="scope.row.objectType === 'USER'" type="text" @click="previewGrant(scope.row)">有效权限</el-button>
            <el-button type="text" :disabled="saving || !['USER', 'TEAM'].includes(scope.row.objectType)" @click="revoke(scope.row)">撤销</el-button>
          </template>
        </el-table-column>
      </el-table>
      <p class="sharing-hint">{{ kind === 'SHARED' ? '共享连接：按上方授权操作。SQL 执行包含任意语句能力，请谨慎授予。' : '私有连接：仅所有者可使用。所有者的数据操作也需要单独授权。' }}</p>
      </el-tab-pane>
      <el-tab-pane label="有效权限" name="effective">
        <p>选择用户，查看平台角色与此连接授权共同决定的权限。团队权限会合并计算；目标数据库仍会校验实际操作。</p>
        <el-select v-model="previewUserId" filterable remote clearable placeholder="搜索登录账号" :remote-method="searchPreviewUsers"
          :loading="previewSearching" @change="loadPreview">
          <el-option v-for="item in previewCandidates" :key="item.userId" :value="item.userId" :label="item.userName" />
        </el-select>
        <el-button size="small" :disabled="!previewUserId" :loading="previewLoading" @click="loadPreview">刷新权限</el-button>
        <el-alert v-if="previewError" :title="previewError" type="error" :closable="false" />
        <el-table v-loading="previewLoading" :data="preview ? preview.actions : []" size="small" max-height="360" empty-text="请选择用户查看有效权限">
          <el-table-column label="操作" width="155"><template #default="scope">{{ actionLabel(scope.row.code) }}</template></el-table-column>
          <el-table-column label="平台角色" width="85"><template #default="scope">{{ scope.row.platformAllowed ? '允许' : '未允许' }}</template></el-table-column>
          <el-table-column label="连接授权来源" min-width="160"><template #default="scope">{{ scope.row.sources.map(sourceLabel).join('、') || '未授权' }}</template></el-table-column>
          <el-table-column label="最终权限" width="85"><template #default="scope"><el-tag size="small" :type="scope.row.allowed ? 'success' : 'info'">{{ scope.row.allowed ? '允许' : '拒绝' }}</el-tag></template></el-table-column>
          <el-table-column prop="reason" label="原因" min-width="190" />
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="授权记录" name="audit">
        <p>最近 100 条授权与撤权记录，包含失败操作。记录异步写入，操作后可刷新查看。</p>
        <el-button size="small" :loading="auditLoading" @click="loadAudit">刷新记录</el-button>
        <el-alert v-if="auditError" :title="auditError" type="error" :closable="false" />
        <el-table v-loading="auditLoading" :data="auditRecords" size="small" max-height="360" empty-text="暂无授权记录">
          <el-table-column prop="id" label="记录号" width="85" />
          <el-table-column prop="operator" label="操作者" width="100" />
          <el-table-column prop="action" label="操作" width="135" />
          <el-table-column label="时间" width="160"><template #default="scope">{{ parseTime(scope.row.time) }}</template></el-table-column>
          <el-table-column label="结果" width="70"><template #default="scope">{{ scope.row.success ? '成功' : '失败' }}</template></el-table-column>
          <el-table-column label="详情" min-width="160"><template #default="scope"><el-popover trigger="click" width="420"><p>{{ scope.row.url }}</p><pre class="sharing-audit-detail">{{ scope.row.params }}</pre><p>{{ scope.row.error }}</p><template #reference><el-button  type="text">查看对象与权限</el-button></template></el-popover></template></el-table-column>
        </el-table>
      </el-tab-pane>
      </el-tabs>
    </div>
    <template #footer><span ><el-button @click="$emit('close')">关闭</el-button></span></template>
  </el-dialog>
</template>

<script>
import connectionServer from '@/api/main/connection'

export default {
  props: { visible: { type: Boolean, default: false }, dataSourceId: { type: [Number, String], default: null } },
  data() { return { grants: [], kind: 'PRIVATE', candidates: [], objectType: 'USER', selectedIds: [], permissionCodes: ['METADATA_READ', 'DATA_READ'], loading: false, searching: false, saving: false, requestVersion: 0,
    activeTab: 'grants', previewUserId: null, previewCandidates: [], preview: null, previewLoading: false, previewSearching: false,
    previewError: '', previewVersion: 0, previewSearchVersion: 0, auditRecords: [], auditLoading: false, auditError: '', auditVersion: 0,
    actions: [
      { code: 'METADATA_READ', label: '查看结构' }, { code: 'DATA_READ', label: '读取数据' },
      { code: 'DATA_WRITE', label: '修改数据' }, { code: 'SCHEMA_CHANGE', label: '修改结构与数据库用户' },
      { code: 'SQL_EXECUTE', label: '执行任意 SQL' }, { code: 'MONITOR_VIEW', label: '运行监控' },
      { code: 'MIGRATION_RUN', label: '执行数据迁移' }, { code: 'DATASOURCE_MANAGE', label: '管理连接' }
    ] } },
  watch: {
    dataSourceId() { this.resetInspection() },
    visible(value) { if (!value) this.resetInspection() }
  },
  methods: {
    resetInspection() {
      this.previewVersion++; this.previewSearchVersion++; this.auditVersion++
      this.activeTab = 'grants'; this.previewUserId = null; this.previewCandidates = []; this.preview = null
      this.previewLoading = false; this.previewSearching = false; this.previewError = ''
      this.auditRecords = []; this.auditLoading = false; this.auditError = ''
    },
    actionLabel(code) { return this.actions.find(action => action.code === code)?.label || code },
    sourceLabel(source) {
      if (source === 'OWNER') return '连接所有者'
      if (source === 'ADMIN') return '超级管理员'
      return source.replace('USER:', '用户 #').replace('TEAM:', '团队 #')
    },
    tabChanged() {
      if (this.activeTab === 'audit') this.loadAudit()
      if (this.activeTab === 'effective' && this.previewUserId) this.loadPreview()
    },
    previewGrant(row) {
      this.previewCandidates = [{ userId: row.objectId, userName: row.name }]
      this.previewUserId = row.objectId; this.activeTab = 'effective'; this.loadPreview()
    },
    async searchPreviewUsers(keyword) {
      const version = ++this.previewSearchVersion
      this.previewCandidates = []
      this.previewSearching = false
      if (!keyword || !keyword.trim()) return
      this.previewSearching = true
      try {
        const result = await connectionServer.searchSharingUsers(this.dataSourceId, keyword.trim())
        if (!result.success) throw new Error(result.errorMessage || '用户搜索失败')
        if (version === this.previewSearchVersion) this.previewCandidates = result.data || []
      } catch (error) { if (version === this.previewSearchVersion) this.$message.error(error.message || '用户搜索失败') }
      finally { if (version === this.previewSearchVersion) this.previewSearching = false }
    },
    async loadPreview() {
      const version = ++this.previewVersion
      this.preview = null; this.previewError = ''; this.previewLoading = false
      if (!this.previewUserId) return
      this.previewLoading = true
      try {
        const result = await connectionServer.getEffectivePermissions(this.dataSourceId, this.previewUserId)
        if (!result.success) throw new Error(result.errorMessage || '有效权限读取失败')
        if (version === this.previewVersion) this.preview = result.data
      } catch (error) { if (version === this.previewVersion) this.previewError = error.message || '有效权限读取失败' }
      finally { if (version === this.previewVersion) this.previewLoading = false }
    },
    async loadAudit() {
      const version = ++this.auditVersion
      this.auditLoading = true; this.auditError = ''; this.auditRecords = []
      try {
        const result = await connectionServer.getSharingAudit(this.dataSourceId)
        if (!result.success) throw new Error(result.errorMessage || '授权记录读取失败')
        if (version === this.auditVersion) this.auditRecords = result.data || []
      } catch (error) { if (version === this.auditVersion) this.auditError = error.message || '授权记录读取失败' }
      finally { if (version === this.auditVersion) this.auditLoading = false }
    },
    resetSearch() { this.requestVersion++; this.selectedIds = []; this.candidates = [] },
    async load() {
      if (!this.dataSourceId) return
      this.loading = true
      try {
        const result = await connectionServer.getSharing(this.dataSourceId)
        if (!result.success) throw new Error(result.errorMessage || '授权列表读取失败')
        this.kind = result.data.kind
        this.grants = (result.data.grants || []).map(row => ({ ...row, permissionCodes: row.permissionCodes || ['METADATA_READ', 'DATA_READ'] }))
      } catch (error) { this.$message.error(error.message || '授权列表读取失败') }
      finally { this.loading = false }
    },
    async searchObjects(keyword) {
      const version = ++this.requestVersion
      if (!keyword || !keyword.trim()) { this.candidates = []; return }
      this.searching = true
      try {
        const result = this.objectType === 'USER'
          ? await connectionServer.searchSharingUsers(this.dataSourceId, keyword.trim())
          : await connectionServer.searchSharingTeams(this.dataSourceId, keyword.trim())
        if (version === this.requestVersion && result.success) this.candidates = (result.data || []).map(item => this.objectType === 'USER'
          ? { id: item.userId, label: `${item.userName} · ${item.nickName || ''}` }
          : { id: item.teamId, label: `${item.name} · ${item.code || ''}` })
      } catch (error) { if (version === this.requestVersion) this.$message.error(error.message || '授权对象搜索失败') }
      finally { if (version === this.requestVersion) this.searching = false }
    },
    async grant() {
      if (this.saving || !this.selectedIds.length) return
      this.saving = true
      try {
        for (const selectedId of this.selectedIds) {
          const result = this.objectType === 'USER'
            ? await connectionServer.grantSharingUser(this.dataSourceId, selectedId, this.permissionCodes)
            : await connectionServer.grantSharingTeam(this.dataSourceId, selectedId, this.permissionCodes)
          if (!result.success) throw new Error(result.errorMessage || '授权失败')
        }
        this.selectedIds = []
        this.candidates = []
        await this.load()
        this.$emit('changed')
        this.$message.success('授权成功')
      } catch (error) { this.$message.error(error.message || '授权失败') }
      finally { this.saving = false }
    },
    async saveGrant(row) {
      this.saving = true
      try {
        const result = row.objectType === 'USER'
          ? await connectionServer.grantSharingUser(this.dataSourceId, row.objectId, row.permissionCodes)
          : await connectionServer.grantSharingTeam(this.dataSourceId, row.objectId, row.permissionCodes)
        if (!result.success) throw new Error(result.errorMessage || '权限保存失败')
        await this.load()
        this.$emit('changed')
        this.$message.success('权限已更新')
      } catch (error) { this.$message.error(error.message || '权限保存失败') }
      finally { this.saving = false }
    },
    async revoke(row) {
      try {
        await this.$confirm(`撤销 ${row.name} 的连接使用权限？`, '确认撤销', { type: 'warning' })
        this.saving = true
        const result = row.objectType === 'USER'
          ? await connectionServer.revokeSharingUser(this.dataSourceId, row.objectId)
          : await connectionServer.revokeSharingTeam(this.dataSourceId, row.objectId)
        if (!result.success) throw new Error(result.errorMessage || '撤销失败')
        await this.load()
        this.$emit('changed')
        this.$message.success('已撤销')
      } catch (error) { if (error !== 'cancel' && error !== 'close') this.$message.error(error.message || '撤销失败') }
      finally { this.saving = false }
    }
  }
}
</script>

<style scoped>
.sharing-hint { color: #606266; font-size: 12px; margin: 12px 0 0; }
.sharing-audit-detail { white-space: pre-wrap; overflow-wrap: anywhere; }
</style>
