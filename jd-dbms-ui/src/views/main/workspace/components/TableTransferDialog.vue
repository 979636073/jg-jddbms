<template>
  <el-dialog :model-value="visible" :title="operation === 'import' ? '导入表数据' : '导出表'" width="620px" append-to-body :close-on-click-modal="false" @close="$emit('close')">
    <div class="table-transfer-dialog">
      <div class="transfer-table"><i class="el-icon-tickets" /> {{ tableInfo.schema }}.{{ tableInfo.table }} <el-tag size="small">{{ tableInfo.type }}</el-tag></div>
      <el-form label-position="top" size="small">
        <el-form-item label="操作方式">
          <el-radio-group v-model="mode" size="small" @change="resetMode">
            <el-radio-button label="file">{{ operation === 'import' ? '从文件导入' : '导出到文件' }}</el-radio-button>
            <el-radio-button label="database">{{ operation === 'import' ? '从数据库导入' : '导出到数据库' }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <template v-if="mode === 'file'">
          <el-form-item label="文件类型">
            <el-select v-model="format" style="width: 100%" placeholder="请选择格式">
              <el-option v-for="item in formats" :key="item.value" :value="item.value" :label="item.label" />
            </el-select>
          </el-form-item>
          <template v-if="format === 'DMP'">
            <el-alert type="info" :closable="false" show-icon
              title="DMP 为数据库原生格式：达梦与 Oracle 不能互导，Oracle 传统 EXP/IMP 与 Data Pump 也不能互读。任务在后台任务中心执行。" />
            <el-form-item label="客户端格式" class="dmp-field">
              <el-select v-model="dmpFamily" style="width: 100%" @change="selectedToolId = ''">
                <el-option v-for="item in dmpFamilies" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="客户端版本">
              <el-select v-model="selectedToolId" clearable style="width: 100%" placeholder="自动选择已启用的兼容版本">
                <el-option v-for="item in compatibleDmpTools" :key="item.id" :value="item.id"
                  :label="`${item.version} · ${item.os}/${item.arch}${item.checksumVerified ? ' · 已核对摘要' : ''}`" />
              </el-select>
              <div class="transfer-help">留空时自动选择；未上传版本时尝试服务器已配置的客户端。</div>
            </el-form-item>
            <el-form-item v-if="operation === 'import'" label="文件中的源模式名">
              <el-input v-model.trim="sourceSchema" maxlength="128" placeholder="必须与 DMP 内的源模式一致" />
            </el-form-item>
            <el-alert v-if="dmpFamily === 'ORACLE_DATAPUMP'" type="warning" :closable="false" show-icon
              title="Oracle Data Pump 需预先配置数据库 DIRECTORY 与客户端共享目录。" />
          </template>
          <el-form-item v-if="operation === 'import'" label="选择文件">
            <input ref="fileInput" class="transfer-file-input" type="file" :accept="accept" @change="fileChanged">
            <div v-if="format === 'DMP' && uploadPercent > 0" class="transfer-help">上传 {{ uploadPercent }}%</div>
            <div v-if="format === 'DMP'" class="transfer-help">仅导入 DMP 中的 {{ tableInfo.table }}。目标表已存在时会追加数据，请先确认不会产生重复行。</div>
            <div v-else class="transfer-help">导入到当前已有表并追加数据；字段名须与目标表匹配。SQL 文件中的 CREATE TABLE 仅用于核对表名，不会执行建表。CSV/TSV 的空字段表示空文本；要保留 NULL 请使用 JSON 或 XML。单次最多 100,000 行、50 MiB，失败会回滚本次导入。</div>
          </el-form-item>
          <el-alert v-else-if="format !== 'DMP'" type="info" :closable="false" title="SQL 包含基础 CREATE TABLE 和 INSERT；其他格式导出表头与数据。文件可导入达梦或 Oracle 的同名字段表。" />
        </template>
        <template v-else>
          <el-alert type="info" :closable="false" title="数据库直连将创建单表迁移草稿并运行只读预检查；请在审阅页确认后再启动。文件格式仅用于文件导入导出。" />
          <el-form-item :label="operation === 'import' ? '来源数据源' : '目标数据源'">
            <el-select v-model="otherId" filterable style="width: 100%" placeholder="选择达梦或 Oracle 连接" @change="loadOtherConnection">
              <el-option v-for="source in sources" :key="source.id" :value="source.id" :label="source.alias + ' · ' + source.type" />
            </el-select>
          </el-form-item>
          <el-form-item :label="operation === 'import' ? '来源模式' : '目标模式'">
            <el-select v-model="otherSchema" filterable style="width: 100%" placeholder="选择业务模式" :loading="loadingConnection" @change="schemaChanged">
              <el-option v-for="schema in schemas" :key="schema.name" :value="schema.name" :label="schema.name" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="operation === 'import'" label="来源表">
            <el-select v-model="sourceTable" filterable remote :remote-method="searchSourceTables" :loading="loadingTables" style="width: 100%" placeholder="搜索来源表名">
              <el-option v-for="item in sourceTables" :key="item.name" :value="item.name" :label="item.name" />
            </el-select>
          </el-form-item>
          <el-form-item v-else label="目标表名"><el-input v-model.trim="targetTable" maxlength="128" placeholder="输入目标表名" /></el-form-item>
          <el-form-item v-if="operation === 'import'" label="目标表数据处理">
            <el-radio-group v-model="directConflict" size="small">
              <el-radio label="STOP">仅空目标表</el-radio><el-radio label="APPEND">追加</el-radio><el-radio label="CLEAR">清空后写入</el-radio>
            </el-radio-group>
          </el-form-item>
        </template>
      </el-form>
    </div>
    <template #footer><span  class="dialog-footer">
      <el-button size="small" @click="$emit('close')">取消</el-button>
      <el-button type="primary" size="small" :loading="busy" @click="submit">{{ mode === 'database' ? '创建并预检查' : operation === 'import' ? '提交后台导入任务' : '提交后台导出任务' }}</el-button>
    </span></template>
  </el-dialog>
</template>

<script>
import connectionServer from '@/api/main/connection'
import migration from '@/api/main/migration'
import { exportTableFile, importTableFile } from '@/api/main/tableTransfer'
import { listDmpTools } from '@/api/main/dmpTool'
import sqlServer from '@/api/main/sql'
import request from '@/utils/request'

const formats = [
  { value: 'SQL', label: 'SQL 脚本（.sql）' }, { value: 'CSV', label: 'CSV（.csv）' },
  { value: 'TSV', label: 'TSV（.tsv）' }, { value: 'XLS', label: 'Excel 97–2003（.xls）' },
  { value: 'XLSX', label: 'Excel（.xlsx）' }, { value: 'JSON', label: 'JSON（.json）' },
  { value: 'XML', label: 'XML（.xml）' }, { value: 'DMP', label: '数据库原生 DMP（.dmp）' }
]

export default {
  name: 'TableTransferDialog',
  props: { visible: Boolean, operation: { type: String, default: 'export' }, tableInfo: { type: Object, default: () => ({}) } },
  data() { return { formats, mode: 'file', format: 'SQL', file: null, busy: false, sources: [], otherId: null, otherSchema: '', schemas: [], sourceTable: '', sourceTables: [], targetTable: '', directConflict: 'STOP', loadingConnection: false, loadingTables: false, tableRequest: 0,
    dmpFamily: '', selectedToolId: '', dmpTools: [], sourceSchema: '', uploadPercent: 0 } },
  computed: {
    accept() { return this.format ? '.' + this.format.toLowerCase() : '' },
    dmpFamilies() { return String(this.tableInfo.type).toUpperCase() === 'DM'
      ? [{ value: 'DM_DEXP', label: '达梦 DEXP / DIMP' }]
      : [{ value: 'ORACLE_LEGACY', label: 'Oracle 传统 EXP / IMP' }, { value: 'ORACLE_DATAPUMP', label: 'Oracle Data Pump' }] },
    compatibleDmpTools() { return this.dmpTools.filter(item => item.enabled && item.status === 'AVAILABLE' && item.currentPlatformCompatible !== false &&
      item.databaseType === String(this.tableInfo.type).toUpperCase() && item.toolFamily === this.dmpFamily) }
  },
  watch: {
    visible(value) { if (value) { this.reset(); this.loadSources() } },
    format(value) { this.file = null; this.uploadPercent = 0; if (this.$refs.fileInput) this.$refs.fileInput.value = ''; if (value === 'DMP') this.loadDmpTools() }
  },
  methods: {
    reset() { this.mode = 'file'; this.format = 'SQL'; this.file = null; if (this.$refs.fileInput) this.$refs.fileInput.value = ''; this.busy = false; this.otherId = null; this.otherSchema = ''; this.schemas = []; this.sourceTable = ''; this.sourceTables = []; this.targetTable = this.tableInfo.table || ''; this.directConflict = 'STOP'; this.dmpFamily = String(this.tableInfo.type).toUpperCase() === 'DM' ? 'DM_DEXP' : 'ORACLE_LEGACY'; this.selectedToolId = ''; this.dmpTools = []; this.sourceSchema = this.tableInfo.schema || ''; this.uploadPercent = 0 },
    resetMode() { this.file = null; if (this.$refs.fileInput) this.$refs.fileInput.value = '' },
    fileChanged(event) { this.file = event.target.files && event.target.files[0] || null },
    async loadDmpTools() {
      try { const response = await listDmpTools(); this.dmpTools = response.data || [] }
      catch (error) { this.dmpTools = []; this.$message.error(error.message || '客户端版本读取失败') }
    },
    async loadSources() {
      try {
        const response = await connectionServer.getList({ pageNo: 1, pageSize: 1000 })
        if (!response.success) throw new Error(response.errorMessage || '数据源列表读取失败')
        const rows = Array.isArray(response.data) ? response.data : response.data && response.data.data || []
        this.sources = rows.filter(row => ['DM', 'ORACLE'].includes(row.type)).map(row => ({ id: row.id, type: row.type, alias: row.alias || row.user || row.userName || `数据源 ${row.id}` }))
      } catch (error) { this.$message.error(error.message || '数据源列表读取失败') }
    },
    async loadOtherConnection() {
      this.otherSchema = ''; this.schemas = []; this.sourceTable = ''; this.sourceTables = []
      if (!this.otherId) return
      this.loadingConnection = true
      try { const info = await migration.connection(this.otherId); this.schemas = info.schemas || [] }
      catch (error) { this.$message.error(error.message || '连接失败') }
      finally { this.loadingConnection = false }
    },
    schemaChanged() { this.sourceTable = ''; this.sourceTables = []; if (this.operation === 'import') this.searchSourceTables('') },
    async searchSourceTables(search) {
      if (!this.otherId || !this.otherSchema || this.operation !== 'import') return
      const request = ++this.tableRequest; this.loadingTables = true
      try {
        const result = await migration.objects({ id: this.otherId, schema: this.otherSchema, search, page: 1, size: 200 })
        if (request === this.tableRequest) this.sourceTables = result.data.filter(row => row.type === 'TABLE')
      } catch (error) { this.$message.error(error.message || '表列表读取失败') }
      finally { if (request === this.tableRequest) this.loadingTables = false }
    },
    async submit() {
      if (this.busy) return
      this.busy = true
      try { if (this.mode === 'file') await this.submitFile(); else await this.submitDatabase() }
      catch (error) { this.$message.error(error.message || '导入导出失败') }
      finally { this.busy = false }
    },
    async submitFile() {
      const context = this.tableInfo
      if (this.format === 'DMP') return this.submitDmp()
      if (this.operation === 'export') {
        const response = await exportTableFile({ dataSourceId: context.id, schema: context.schema, table: context.table, format: this.format })
        if (!response.success) throw new Error(response.errorMessage || '任务提交失败')
        this.$message.success('导出任务已提交，请在任务中心下载文件'); this.$EventBus.$emit('openTaskCenter'); this.$emit('close')
      } else {
        if (!this.file) throw new Error('请选择导入文件')
        if (!this.file.name.toLowerCase().endsWith('.' + this.format.toLowerCase())) throw new Error('文件扩展名与所选类型不一致')
        if (this.file.size > 50 * 1024 * 1024) throw new Error('文件不能超过 50 MiB')
        const data = new FormData(); data.append('dataSourceId', context.id); data.append('schema', context.schema); data.append('table', context.table); data.append('format', this.format); data.append('file', this.file)
        const response = await importTableFile(data)
        if (!response.success) throw new Error(response.errorMessage || '导入失败')
        this.$message.success('导入任务已提交，请在任务中心查看结果'); this.$EventBus.$emit('openTaskCenter'); this.$emit('close')
      }
    },
    async submitDmp() {
      const context = this.tableInfo
      const common = { dataSourceId: context.id, schemaName: context.schema, dmpToolFamily: this.dmpFamily,
        dmpToolId: this.selectedToolId || undefined, tableList: [context.table] }
      let response
      if (this.operation === 'export') {
        response = await sqlServer.exportDmp(common)
      } else {
        if (!this.file || !/\.dmp$/i.test(this.file.name) || !this.sourceSchema) throw new Error('请选择 DMP 文件并填写源模式名')
        const data = new FormData(); data.append('file', this.file)
        const uploaded = await request({ url: '/common/upload', method: 'post', data,
          headers: { 'Content-Type': 'multipart/form-data', repeatSubmit: false }, timeout: 60 * 60 * 1000,
          onUploadProgress: event => { if (event.total) this.uploadPercent = Math.round(event.loaded / event.total * 100) } })
        if (uploaded.code !== 200) throw new Error(uploaded.msg || 'DMP 文件上传失败')
        response = await sqlServer.importDmp({ ...common, fileName: uploaded.fileName, filePath: uploaded.url,
          fromName: this.sourceSchema, toName: context.schema })
      }
      if (!response.success || !response.data) throw new Error(response.errorMessage || 'DMP 任务提交失败')
      this.$message.success('DMP 任务已提交，请在任务中心查看进度' + (this.operation === 'export' ? '并下载文件' : ''))
      this.$EventBus.$emit('openTaskCenter'); this.$emit('close')
    },
    async submitDatabase() {
      if (!this.otherId || !this.otherSchema || this.operation === 'import' && !this.sourceTable || this.operation === 'export' && !this.targetTable) throw new Error('请完整选择数据源、模式和表')
      const reviewWindow = window.open('about:blank', '_blank')
      const context = this.tableInfo
      const sourceId = this.operation === 'export' ? context.id : this.otherId
      const targetId = this.operation === 'export' ? this.otherId : context.id
      const sourceSchema = this.operation === 'export' ? context.schema : this.otherSchema
      const targetSchema = this.operation === 'export' ? this.otherSchema : context.schema
      const sourceTable = this.operation === 'export' ? context.table : this.sourceTable
      const targetTable = this.operation === 'export' ? this.targetTable : context.table
      const config = { name: `${sourceSchema}.${sourceTable} → ${targetSchema}.${targetTable}`.slice(0, 80), sourceId, targetId, sourceSchema, targetSchema,
        scope: this.operation === 'export' ? 'ALL' : 'DATA', conflict: this.operation === 'export' ? 'STOP' : this.directConflict,
        fetchSize: 100, commitSize: 500, rowsPerSecond: 0, parallelism: 1, targetTablespace: '', errorPolicy: 'STOP',
        tables: [{ source: sourceTable, target: targetTable, sourceSchema, targetSchema: '', type: 'TABLE', filters: [], columns: [], dependencies: [], manualDdl: '', ddlConfirmed: false }] }
      try {
        const task = await migration.save(null, config)
        const checked = await migration.precheck(task.id)
        const route = this.$router.resolve({ path: '/data-migration/index', query: { taskId: String(task.id) } })
        if (reviewWindow) { reviewWindow.opener = null; reviewWindow.location.href = window.location.origin + route.href }
        else window.location.assign(route.href)
        if (checked.status === 'MIG_READY') this.$message.success('单表任务预检查通过，请在审阅页确认后启动')
        else this.$message.warning('单表任务预检查存在阻断项，请在审阅页查看原因')
        this.$emit('close')
      } catch (error) { if (reviewWindow) reviewWindow.close(); throw error }
    }
  }
}
</script>

<style scoped>
.table-transfer-dialog { color: #303133; }
.transfer-table { padding: 10px 12px; margin-bottom: 16px; border: 1px solid #dfe6f0; border-radius: 4px; background: #f5f8fc; font-weight: 600; overflow-wrap: anywhere; }
.transfer-table .el-tag { margin-left: 8px; }
.transfer-file-input { display: block; width: 100%; padding: 8px; box-sizing: border-box; border: 1px solid #dcdfe6; border-radius: 4px; cursor: pointer; }
.transfer-help { margin-top: 6px; line-height: 1.6; color: #68728c; font-size: 12px; }
.dmp-field { margin-top: 16px; }
</style>
