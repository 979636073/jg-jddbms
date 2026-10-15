<template>
  <div class="app-container dmp-tools-page">
    <div class="page-header">
      <div>
        <h2>导入导出客户端</h2>
        <p>管理达梦 DEXP / DIMP、Oracle EXP / IMP 与 Data Pump 的官方客户端版本。任务会按数据库类型、格式和服务端运行平台选择已启用版本。</p>
      </div>
      <div class="header-actions">
        <el-button size="small" icon="el-icon-refresh" @click="loadTools">刷新</el-button>
        <el-button type="primary" size="small" icon="el-icon-upload2" @click="openUpload">上传客户端</el-button>
      </div>
    </div>

    <el-alert type="warning" :closable="false" show-icon
      title="请从数据库厂商的官方渠道取得完整客户端，并在上传时选择其运行平台。系统会优先自动识别文件平台，无法识别时使用所选平台。" />

    <div class="filter-bar">
      <el-select v-model="databaseFilter" size="small" placeholder="全部数据库" clearable>
        <el-option label="达梦" value="DM" />
        <el-option label="Oracle" value="ORACLE" />
      </el-select>
      <el-input v-model.trim="keyword" size="small" clearable placeholder="搜索版本或文件名" prefix-icon="el-icon-search" />
      <span class="count">共 {{ filteredTools.length }} 个版本</span>
    </div>

    <el-table v-loading="loading" :data="filteredTools" border size="small" empty-text="尚未上传客户端工具">
      <el-table-column label="数据库 / 工具" min-width="150">
        <template slot-scope="scope">
          <strong>{{ scope.row.databaseType === 'DM' ? '达梦' : 'Oracle' }}</strong>
          <div class="muted">{{ familyLabel(scope.row.toolFamily) }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="version" label="版本" min-width="85" />
      <el-table-column label="运行平台" min-width="125">
        <template slot-scope="scope">
          {{ scope.row.os }} / {{ scope.row.arch }}
          <div v-if="scope.row.currentPlatformCompatible === false" class="warning">与当前服务器不兼容</div>
        </template>
      </el-table-column>
      <el-table-column prop="originalFileName" label="上传文件" min-width="125" show-overflow-tooltip />
      <el-table-column label="状态" min-width="190">
        <template slot-scope="scope">
          <el-tag size="mini" :title="scope.row.statusMessage" :type="scope.row.enabled && scope.row.status === 'AVAILABLE' ? 'success' : 'info'">
            {{ scope.row.status !== 'AVAILABLE' ? '检测失败' : scope.row.enabled ? '已启用' : '已停用' }}
          </el-tag>
          <div v-if="scope.row.status !== 'AVAILABLE'" class="status-reason" :title="scope.row.statusMessage">
            {{ scope.row.statusMessage || '未识别客户端版本' }}
          </div>
        </template>
      </el-table-column>
      <el-table-column label="启用" width="80" fixed="right" align="center">
        <template slot-scope="scope">
          <el-switch :value="scope.row.enabled" :disabled="scope.row.status !== 'AVAILABLE' || changingId === scope.row.id"
            @change="value => changeEnabled(scope.row, value)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="170" fixed="right" align="center">
        <template slot-scope="scope">
          <el-button type="text" size="small" @click="openEdit(scope.row)">修改</el-button>
          <el-button type="text" size="small"
            :loading="redetectingId === scope.row.id" @click="redetectTool(scope.row)">重新检测</el-button>
          <el-button type="text" size="small" class="delete-action" :disabled="scope.row.enabled"
            :title="scope.row.enabled ? '请先停用，再删除该版本' : ''" @click="removeTool(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog title="上传客户端工具包" :visible.sync="uploadVisible" width="520px" append-to-body class="dmp-client-dialog">
      <el-form label-position="top" size="small">
        <el-form-item label="官方客户端 ZIP">
          <el-upload drag action="" accept=".zip" :auto-upload="false" :limit="1" :file-list="uploadFiles"
            :on-change="onFileChange" :on-remove="onFileRemove">
            <i class="el-icon-upload" />
            <div class="el-upload__text">将 ZIP 拖到这里，或<em>点击选择</em></div>
          </el-upload>
        </el-form-item>
        <el-form-item label="备用版本（可选）">
          <el-input v-model.trim="fallbackVersion" placeholder="仅自动检测失败时填写，例如 8.1.3" />
        </el-form-item>
        <el-form-item label="运行平台">
          <el-select v-model="selectedPlatform" placeholder="请选择工具包的运行平台" style="width: 100%">
            <el-option v-for="item in platforms" :key="item.value" :value="item.value" :label="item.label" />
          </el-select>
          <div class="platform-help">能识别文件头时以自动检测结果为准；无法识别时使用这里选择的平台。</div>
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button size="small" @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" size="small" :loading="uploading" @click="submitUpload">上传并检测</el-button>
      </span>
    </el-dialog>
    <el-dialog title="修改客户端信息" :visible.sync="editVisible" width="480px" append-to-body class="dmp-client-dialog">
      <el-form label-position="top" size="small">
        <el-form-item label="版本号">
          <el-input v-model.trim="editVersion" placeholder="例如 8.1.4.200" maxlength="60" />
        </el-form-item>
        <el-form-item label="运行平台">
          <el-select v-model="editPlatform" style="width: 100%">
            <el-option v-for="item in platforms" :key="item.value" :value="item.value" :label="item.label" />
          </el-select>
          <div class="platform-help">文件头能够识别运行平台时，只能使用识别到的平台。</div>
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button size="small" @click="editVisible = false">取消</el-button>
        <el-button type="primary" size="small" :loading="editing" @click="submitEdit">保存修改</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import { listDmpTools, uploadDmpTool, setDmpToolEnabled, redetectDmpTool, updateDmpTool, deleteDmpTool } from '@/api/main/dmpTool'

export default {
  name: 'DmpToolsAdmin',
  data() {
    return { tools: [], loading: false, databaseFilter: '', keyword: '', uploadVisible: false, uploading: false,
      uploadFiles: [], selectedFile: null, fallbackVersion: '', selectedPlatform: '', changingId: '', redetectingId: '',
      editVisible: false, editing: false, editId: '', editVersion: '', editPlatform: '',
      platforms: [
        { value: 'WINDOWS:X86_64', label: 'Windows x64' },
        { value: 'LINUX:X86_64', label: 'Linux x64' },
        { value: 'LINUX:X86', label: 'Linux x86' },
        { value: 'LINUX:ARM64', label: 'Linux ARM64' }
      ] }
  },
  computed: {
    filteredTools() {
      const keyword = this.keyword.toLowerCase()
      return this.tools.filter(item =>
        (!this.databaseFilter || item.databaseType === this.databaseFilter) &&
        (!keyword || [item.version, item.originalFileName, item.toolFamily].some(value => String(value || '').toLowerCase().includes(keyword)))
      )
    }
  },
  created() { this.loadTools() },
  methods: {
    familyLabel(family) {
      return { DM_DEXP: 'DEXP / DIMP', ORACLE_LEGACY: 'EXP / IMP', ORACLE_DATAPUMP: 'Data Pump' }[family] || family
    },
    async loadTools() {
      this.loading = true
      try {
        const response = await listDmpTools()
        this.tools = response.data || []
      } finally {
        this.loading = false
      }
    },
    openUpload() {
      this.uploadFiles = []; this.selectedFile = null; this.fallbackVersion = ''
      this.selectedPlatform = ''; this.uploadVisible = true
    },
    onFileChange(file, files) { this.selectedFile = file.raw; this.uploadFiles = files.slice(-1) },
    onFileRemove() { this.selectedFile = null; this.uploadFiles = [] },
    async submitUpload() {
      if (!this.selectedFile) return this.$message.warning('请选择 ZIP 工具包')
      if (!this.selectedPlatform) return this.$message.warning('请选择运行平台')
      const data = new FormData(); data.append('file', this.selectedFile)
      if (this.fallbackVersion) data.append('version', this.fallbackVersion)
      data.append('platform', this.selectedPlatform)
      this.uploading = true
      try {
        const response = await uploadDmpTool(data)
        if (!response.success || !response.data) throw new Error(response.errorMessage || '客户端上传失败')
        this.$message.success(response.data && response.data.status === 'AVAILABLE' ? '客户端检测通过' : '工具包已保存，请检查检测结果')
        this.uploadVisible = false
        await this.loadTools()
      } catch (error) {
        this.$message.error(error.message || '客户端上传失败')
      } finally { this.uploading = false }
    },
    async changeEnabled(row, enabled) {
      this.changingId = row.id
      try { await setDmpToolEnabled(row.id, enabled); row.enabled = enabled; await this.loadTools() }
      finally { this.changingId = '' }
    },
    async redetectTool(row) {
      this.redetectingId = row.id
      try {
        const response = await redetectDmpTool(row.id)
        if (!response.success || !response.data) throw new Error(response.errorMessage || '重新检测失败')
        this.$message[response.data.status === 'AVAILABLE' ? 'success' : 'warning'](
          response.data.status === 'AVAILABLE' ? `客户端检测通过：${response.data.version}` : (response.data.statusMessage || '仍未识别客户端版本'))
        await this.loadTools()
      } catch (error) {
        this.$message.error(error.message || '重新检测失败')
      } finally { this.redetectingId = '' }
    },
    openEdit(row) {
      this.editId = row.id
      this.editVersion = row.version === 'UNKNOWN' ? '' : row.version
      this.editPlatform = `${row.os}:${row.arch}`
      this.editVisible = true
    },
    async submitEdit() {
      if (!this.editVersion) return this.$message.warning('请输入版本号')
      if (!this.editPlatform) return this.$message.warning('请选择运行平台')
      this.editing = true
      try {
        const response = await updateDmpTool(this.editId, this.editVersion, this.editPlatform)
        if (!response.success || !response.data) throw new Error(response.errorMessage || response.message || '修改失败')
        this.$message.success('客户端信息已更新')
        this.editVisible = false
        await this.loadTools()
      } catch (error) {
        this.$message.error(error.message || '修改失败')
      } finally { this.editing = false }
    },
    async removeTool(row) {
      try {
        await this.$confirm(`删除 ${this.familyLabel(row.toolFamily)} ${row.version}？删除后无法恢复。`, '删除客户端工具', { type: 'warning' })
        await deleteDmpTool(row.id)
        this.$message.success('客户端工具已删除')
        await this.loadTools()
      } catch (error) { if (error !== 'cancel') this.$message.error(error.message || '删除失败') }
    }
  }
}
</script>

<style scoped>
.page-header, .filter-bar { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.page-header { margin-bottom: 16px; }
.page-header h2 { margin: 0 0 8px; }
.page-header p, .muted, .count { color: #606b7b; }
.page-header p { margin: 0; line-height: 1.5; }
.header-actions { display: flex; gap: 8px; flex-shrink: 0; }
.filter-bar { justify-content: flex-start; margin: 18px 0 12px; }
.filter-bar .el-select { width: 160px; }
.filter-bar .el-input { width: 250px; }
.count { margin-left: auto; font-size: 13px; }
.warning { color: #b05a00; font-size: 12px; }
.platform-help { margin-top: 5px; color: #606b7b; font-size: 12px; line-height: 1.5; }
.delete-action { color: #f56c6c; }
.status-reason { margin-top: 4px; color: #a64b00; font-size: 12px; line-height: 1.4; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
::v-deep .el-alert--warning .el-alert__title { color: #704700; }
::v-deep .el-alert--warning .el-alert__icon { color: #9b6500; }
::v-deep .el-upload, ::v-deep .el-upload-dragger { width: 100%; }
.dmp-client-dialog ::v-deep .el-dialog {
  display: flex;
  flex-direction: column;
  max-height: 88vh;
  max-width: calc(100vw - 32px);
  margin-bottom: 0;
}
.dmp-client-dialog ::v-deep .el-dialog__body {
  min-height: 0;
  overflow-y: auto;
}
.dmp-client-dialog ::v-deep .el-dialog__header,
.dmp-client-dialog ::v-deep .el-dialog__footer { flex-shrink: 0; }
@media (max-width: 900px) { .page-header { align-items: flex-start; flex-direction: column; } }
</style>
