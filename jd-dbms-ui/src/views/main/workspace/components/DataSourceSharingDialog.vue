<template>
  <el-dialog title="共享连接授权" :visible="visible" width="620px" :close-on-click-modal="false" @open="load" @close="$emit('close')">
    <div v-loading="loading">
      <p>按操作授权用户或团队使用此连接。目标数据库账号的权限仍会生效。</p>
      <el-form inline size="small" @submit.native.prevent>
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
        <el-table-column label="类型" width="80"><template slot-scope="scope">{{ scope.row.objectType === 'USER' ? '用户' : scope.row.objectType === 'TEAM' ? '团队' : scope.row.objectType }}</template></el-table-column>
        <el-table-column prop="name" label="名称" />
        <el-table-column prop="description" label="说明" />
        <el-table-column label="操作权限" min-width="250">
          <template slot-scope="scope">
            <el-select v-model="scope.row.permissionCodes" multiple collapse-tags size="mini" style="width: 185px">
              <el-option v-for="action in actions" :key="action.code" :label="action.label" :value="action.code" />
            </el-select>
            <el-button type="text" :disabled="saving || !scope.row.permissionCodes.length" @click="saveGrant(scope.row)">保存</el-button>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70">
          <template slot-scope="scope">
            <el-button type="text" :disabled="saving || !['USER', 'TEAM'].includes(scope.row.objectType)" @click="revoke(scope.row)">撤销</el-button>
          </template>
        </el-table-column>
      </el-table>
      <p class="sharing-hint">{{ kind === 'SHARED' ? '共享连接：按上方授权操作。SQL 执行包含任意语句能力，请谨慎授予。' : '私有连接：仅所有者可使用。所有者的数据操作也需要单独授权。' }}</p>
    </div>
    <span slot="footer"><el-button @click="$emit('close')">关闭</el-button></span>
  </el-dialog>
</template>

<script>
import connectionServer from '@/api/main/connection'

export default {
  props: { visible: { type: Boolean, default: false }, dataSourceId: { type: [Number, String], default: null } },
  data() { return { grants: [], kind: 'PRIVATE', candidates: [], objectType: 'USER', selectedIds: [], permissionCodes: ['METADATA_READ', 'DATA_READ'], loading: false, searching: false, saving: false, requestVersion: 0,
    actions: [
      { code: 'METADATA_READ', label: '查看结构' }, { code: 'DATA_READ', label: '读取数据' },
      { code: 'DATA_WRITE', label: '修改数据' }, { code: 'SCHEMA_CHANGE', label: '修改结构与数据库用户' },
      { code: 'SQL_EXECUTE', label: '执行任意 SQL' }, { code: 'MONITOR_VIEW', label: '运行监控' },
      { code: 'MIGRATION_RUN', label: '执行数据迁移' }, { code: 'DATASOURCE_MANAGE', label: '管理连接' }
    ] } },
  methods: {
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

<style scoped>.sharing-hint { color: #606266; font-size: 12px; margin: 12px 0 0; }</style>
