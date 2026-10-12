<template>
  <el-dialog :visible="visible" :title="`${operation === 'import' ? '导入' : '导出'} DMP · ${scope === 'table' ? '表' : '模式'}`"
    width="680px" append-to-body :close-on-click-modal="false" @close="$emit('close')">
    <div class="dmp-transfer-dialog">
      <div class="context-line">{{ context.type === 'DM' ? '达梦' : 'Oracle' }} · {{ context.schema }}<span v-if="scope === 'table'">.{{ context.table }}</span></div>
      <el-alert type="info" :closable="false" show-icon
        title="DMP 为数据库原生格式。达梦 DMP 与 Oracle DMP 不能互导；Oracle 传统 EXP/IMP 与 Data Pump 也不能互读。跨库类型转换请使用 SQL、CSV 或数据迁移。" />
      <el-form label-position="top" size="small" class="transfer-form">
        <el-form-item label="客户端格式">
          <el-select v-model="family" style="width: 100%" @change="selectedToolId = ''">
            <el-option v-for="item in families" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="客户端版本">
          <el-select v-model="selectedToolId" clearable style="width: 100%" placeholder="自动选择已启用的兼容版本">
            <el-option v-for="item in compatibleTools" :key="item.id" :value="item.id"
              :label="`${item.version} · ${item.os}/${item.arch}${item.checksumVerified ? ' · 已核对摘要' : ''}`" />
          </el-select>
          <div class="hint">留空时自动选择；若没有上传版本，后台会尝试使用服务器已配置的官方客户端。</div>
        </el-form-item>
        <el-form-item v-if="operation === 'export' && scope === 'schema'" label="导出范围">
          <el-radio-group v-model="exportRange">
            <el-radio label="schema">整个模式（包含模式内对象和数据）</el-radio>
            <el-radio label="tables">指定表</el-radio>
          </el-radio-group>
        </el-form-item>
        <template v-if="operation === 'export' && scope === 'schema' && exportRange === 'tables'">
          <div class="table-toolbar">
            <el-input v-model.trim="search" clearable placeholder="搜索表名" @keyup.enter.native="searchTables" @clear="searchTables" />
            <el-button @click="searchTables">搜索</el-button>
            <el-button @click="selectPage">选中本页</el-button>
          </div>
          <div class="table-list" v-loading="loadingTables">
            <el-checkbox v-for="name in pageTables" :key="name" :value="selectedTables.includes(name)" @change="toggleTable(name, $event)">{{ name }}</el-checkbox>
            <span v-if="!pageTables.length" class="hint">没有匹配的表</span>
          </div>
          <el-pagination v-if="tableTotal > pageSize" small layout="total, prev, pager, next" :current-page="page"
            :page-size="pageSize" :total="tableTotal" @current-change="changePage" />
          <div class="hint">已选 {{ selectedTables.length }} 张表。要导出全部表及模式对象，请选择“整个模式”。</div>
        </template>
        <el-form-item v-if="scope === 'schema'" label="导入导出内容">
          <div class="content-actions">
            <span>默认全选；建表定义始终包含在 DMP 中</span>
            <el-button type="text" @click="selectAllContent">全选</el-button>
          </div>
          <el-checkbox-group v-model="contentOptions" class="content-grid">
            <el-checkbox v-for="item in contentChoices" :key="item.value" :label="item.value"
              :disabled="exportRange === 'tables' && operation === 'export' && schemaOnlyContent.includes(item.value)">{{ item.label }}</el-checkbox>
          </el-checkbox-group>
          <div v-if="operation === 'export' && exportRange === 'tables'" class="hint">指定表仅包含表及附属对象；视图、过程、包、序列需选择整个模式。</div>
          <div v-if="family === 'ORACLE_LEGACY'" class="hint">Oracle 传统 EXP/IMP 无法单独筛选视图、过程、包、序列，导入时也无法单独筛选触发器；需要这些筛选请切换 Data Pump。</div>
        </el-form-item>
        <template v-if="operation === 'import'">
          <el-form-item label="DMP 文件">
            <input ref="fileInput" type="file" accept=".dmp" @change="onFileChange">
            <div v-if="uploadPercent > 0" class="hint">上传 {{ uploadPercent }}%</div>
          </el-form-item>
          <el-form-item label="文件中的源模式名">
            <el-input v-model.trim="sourceSchema" maxlength="128" placeholder="例如 SYSDBA；必须与 DMP 内的源模式一致" />
          </el-form-item>
          <el-alert v-if="scope === 'table'" type="warning" :closable="false" show-icon
            :title="`仅导入 DMP 中的 ${context.table}，若目标表已存在将追加数据；请先确认数据不会重复。`" />
          <el-alert v-else type="warning" :closable="false" show-icon
            title="将源模式内容导入当前模式。目标对象已存在时按数据库原生工具规则处理；请先用测试库验证。" />
        </template>
        <el-alert v-if="family === 'ORACLE_DATAPUMP'" type="warning" :closable="false" show-icon
          title="Oracle Data Pump 需预先配置数据库 DIRECTORY 与客户端共享目录。" />
      </el-form>
    </div>
    <span slot="footer">
      <el-button size="small" @click="$emit('close')">取消</el-button>
      <el-button type="primary" size="small" :loading="busy" @click="submit">提交后台{{ operation === 'import' ? '导入' : '导出' }}任务</el-button>
    </span>
  </el-dialog>
</template>

<script>
import request from '@/utils/request'
import sqlServer from '@/api/main/sql'
import { listDmpTools } from '@/api/main/dmpTool'
import schemaSql from '@/api/main/schemaSqlTransfer'

export default {
  name: 'DmpTransferDialog',
  props: { visible: Boolean, operation: { type: String, default: 'export' }, scope: { type: String, default: 'table' }, context: { type: Object, default: () => ({}) } },
  data() {
    return { family: '', selectedToolId: '', tools: [], file: null, sourceSchema: '', busy: false, uploadPercent: 0,
      exportRange: 'schema', search: '', page: 1, pageSize: 100, pageTables: [], tableTotal: 0, selectedTables: [], loadingTables: false, tableRequest: 0,
      contentOptions: ['ROWS', 'INDEXES', 'CONSTRAINTS', 'TRIGGERS', 'VIEWS', 'PROCEDURE', 'PACKAGE', 'SEQUENCE', 'GRANTS'],
      contentChoices: [{ value: 'ROWS', label: '数据行' }, { value: 'INDEXES', label: '索引' }, { value: 'CONSTRAINTS', label: '约束' },
        { value: 'TRIGGERS', label: '触发器' }, { value: 'VIEWS', label: '视图' }, { value: 'PROCEDURE', label: '过程（含函数）' },
        { value: 'PACKAGE', label: '包' }, { value: 'SEQUENCE', label: '序列' }, { value: 'GRANTS', label: '权限' }],
      schemaOnlyContent: ['VIEWS', 'PROCEDURE', 'PACKAGE', 'SEQUENCE'] }
  },
  computed: {
    families() {
      return String(this.context.type).toUpperCase() === 'DM'
        ? [{ value: 'DM_DEXP', label: '达梦 DEXP / DIMP' }]
        : [{ value: 'ORACLE_LEGACY', label: 'Oracle 传统 EXP / IMP' }, { value: 'ORACLE_DATAPUMP', label: 'Oracle Data Pump' }]
    },
    compatibleTools() {
      return this.tools.filter(item => item.enabled && item.status === 'AVAILABLE' && item.currentPlatformCompatible !== false &&
        item.databaseType === String(this.context.type).toUpperCase() && item.toolFamily === this.family)
    }
  },
  watch: {
    visible(value) { if (value) this.reset() },
    exportRange(value) { if (value === 'tables') { this.selectAllContent(); this.loadTables() } }
  },
  methods: {
    async reset() {
      this.family = String(this.context.type).toUpperCase() === 'DM' ? 'DM_DEXP' : 'ORACLE_LEGACY'
      this.selectedToolId = ''; this.file = null; this.sourceSchema = this.context.schema || ''
      if (this.$refs.fileInput) this.$refs.fileInput.value = ''
      this.busy = false; this.uploadPercent = 0; this.exportRange = 'schema'; this.search = ''; this.page = 1
      this.pageTables = []; this.tableTotal = 0; this.selectedTables = []
      this.selectAllContent()
      try { const result = await listDmpTools(); this.tools = result.data || [] }
      catch (error) { this.tools = []; this.$message.error(error.message || '客户端版本读取失败') }
    },
    onFileChange(event) { this.file = event.target.files && event.target.files[0] || null },
    searchTables() { this.page = 1; this.loadTables() },
    changePage(page) { this.page = page; this.loadTables() },
    async loadTables() {
      const requestId = ++this.tableRequest; this.loadingTables = true
      try {
        const response = await schemaSql.tables({ dataSourceId: this.context.id, schemaName: this.context.schema, search: this.search, page: this.page, size: this.pageSize })
        if (!response.success) throw new Error(response.errorMessage || '表清单读取失败')
        if (requestId === this.tableRequest) { this.pageTables = response.data.data || []; this.tableTotal = response.data.total || 0 }
      } catch (error) { this.$message.error(error.message || '表清单读取失败') }
      finally { if (requestId === this.tableRequest) this.loadingTables = false }
    },
    toggleTable(name, checked) { this.selectedTables = checked ? [...new Set([...this.selectedTables, name])] : this.selectedTables.filter(item => item !== name) },
    selectPage() { this.selectedTables = [...new Set([...this.selectedTables, ...this.pageTables])] },
    selectAllContent() { this.contentOptions = this.contentChoices.map(item => item.value) },
    async submit() {
      if (this.busy) return
      if (this.operation === 'import' && (!this.file || !/\.dmp$/i.test(this.file.name) || !this.sourceSchema)) return this.$message.warning('请选择 DMP 文件并填写源模式名')
      if (this.operation === 'export' && this.scope === 'schema' && this.exportRange === 'tables' && !this.selectedTables.length) return this.$message.warning('请选择至少一张表')
      if (this.scope === 'schema' && !this.contentOptions.length) return this.$message.warning('请至少选择一项导入导出内容')
      if (this.scope === 'schema' && this.family === 'ORACLE_LEGACY') {
        const unsupported = this.schemaOnlyContent.concat(this.operation === 'import' ? ['TRIGGERS'] : [])
        if (unsupported.some(name => !this.contentOptions.includes(name))) return this.$message.warning('Oracle 传统 EXP/IMP 不支持所选内容筛选，请切换 Data Pump')
      }
      this.busy = true
      try {
        const common = { dataSourceId: this.context.id, schemaName: this.context.schema, dmpToolFamily: this.family,
          dmpToolId: this.selectedToolId || undefined,
          contentOptions: this.scope === 'schema' ? this.contentOptions : undefined }
        let result
        if (this.operation === 'export') {
          result = await sqlServer.exportDmp({ ...common,
            tableList: this.scope === 'table' ? [this.context.table] : this.exportRange === 'tables' ? this.selectedTables : [] })
        } else {
          const data = new FormData(); data.append('file', this.file)
          const uploaded = await request({ url: '/common/upload', method: 'post', data,
            headers: { 'Content-Type': 'multipart/form-data', repeatSubmit: false }, timeout: 60 * 60 * 1000,
            onUploadProgress: event => { if (event.total) this.uploadPercent = Math.round(event.loaded / event.total * 100) } })
          if (uploaded.code !== 200) throw new Error(uploaded.msg || 'DMP 文件上传失败')
          result = await sqlServer.importDmp({ ...common, fileName: uploaded.fileName, filePath: uploaded.url,
            fromName: this.sourceSchema, toName: this.context.schema,
            tableList: this.scope === 'table' ? [this.context.table] : [] })
        }
        if (!result.success || !result.data) throw new Error(result.errorMessage || '任务提交失败')
        this.$message.success('任务已提交，请在任务中心查看进度' + (this.operation === 'export' ? '并下载文件' : ''))
        this.$EventBus.$emit('openTaskCenter'); this.$emit('close')
      } catch (error) { this.$message.error(error.message || 'DMP 任务提交失败') }
      finally { this.busy = false }
    }
  }
}
</script>

<style scoped>
.context-line { font-weight: 600; margin-bottom: 12px; }
.transfer-form { margin-top: 16px; }
.hint { color: #6b7586; font-size: 12px; line-height: 1.5; margin-top: 6px; }
.table-toolbar { display: flex; gap: 8px; margin-bottom: 8px; }
.table-toolbar .el-input { flex: 1; }
.table-list { max-height: 200px; min-height: 70px; overflow: auto; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; padding: 10px; border: 1px solid #dcdfe6; }
.table-list .el-checkbox { margin-right: 0; }
.content-actions { display: flex; justify-content: space-between; align-items: center; color: #6b7586; font-size: 12px; }
.content-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); border: 1px solid #dcdfe6; border-radius: 4px; padding: 8px 12px; }
.content-grid .el-checkbox { margin: 2px 0; }
</style>
