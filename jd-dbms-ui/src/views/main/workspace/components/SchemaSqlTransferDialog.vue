<template>
  <el-dialog :model-value="visible" :title="operation === 'import' ? '导入模式 SQL' : '导出模式 SQL'" width="720px" append-to-body :close-on-click-modal="false" @close="$emit('close')">
    <div class="schema-sql-dialog">
      <p class="schema-label">模式：<b>{{ context.schema }}</b> <el-tag size="small">{{ context.type }}</el-tag></p>
      <el-alert v-if="operation === 'import'" type="warning" :closable="false" title="SQL 导入会创建所选表；目标表须不存在。DDL 可能在失败前部分生效，请先在测试库验收。同类型且同名模式可恢复索引、约束、触发器、视图和过程；跨库或跨模式仅导入表及数据。" />
      <el-alert v-else type="info" :closable="false" title="导出始终包含建表 SQL、数据行及相关的索引、约束、触发器、视图和过程。空表只有建表 SQL。文件会在后台生成，到任务中心下载。" />
      <div v-if="operation === 'import'" class="file-row">
        <input type="file" accept=".sql" @change="chooseFile"><span v-if="previewing">正在解析表清单…</span>
      </div>
      <div class="object-title">选择表 <small>已选 {{ selectedAll ? total : selected.length }} 张</small></div>
      <div class="search-row">
        <el-input v-model.trim="search" size="small" clearable placeholder="搜索表名" prefix-icon="el-icon-search" @keyup.enter="searchTables" @clear="searchTables" />
        <el-button size="small" @click="searchTables">搜索</el-button>
        <el-button size="small" @click="selectPage">选中本页</el-button>
        <el-button size="small" type="primary" plain @click="selectAllResults">全选{{ operation === 'import' ? '筛选结果' : '搜索结果' }}</el-button>
        <el-button size="small" @click="clearSelection">清空</el-button>
      </div>
      <div class="table-list" v-loading="loading">
        <el-checkbox v-for="name in pageTables" :key="name" :model-value="selectedAll || selected.includes(name)" :disabled="selectedAll" @change="toggle(name, $event)">{{ name }}</el-checkbox>
        <div v-if="!pageTables.length" class="empty">{{ operation === 'import' && !file ? '请先选择 SQL 文件' : '没有匹配的表' }}</div>
      </div>
      <el-pagination v-if="total > pageSize" small layout="total, prev, pager, next" :current-page="page" :page-size="pageSize" :total="total" @current-change="changePage" />
      <div v-if="operation === 'import'" class="object-title">导入范围</div>
      <el-radio-group v-if="operation === 'import'" v-model="importScope" size="small">
        <el-radio-button label="structure">只导入表</el-radio-button>
        <el-radio-button label="all">导入表和数据</el-radio-button>
      </el-radio-group>
      <p v-if="operation === 'import'" class="file-hint">{{ importScope === 'all' ? '创建所选表并导入数据行。' : '创建所选表，不导入数据行。' }}同类型且同名模式还会恢复文件中的索引、约束、触发器、视图和过程。</p>
      <p v-if="operation === 'import' && preview" class="file-hint">文件来源：{{ preview.sourceType }} · {{ preview.schema }} · {{ preview.tables.length }} 张表。仅接受本系统导出的模式 SQL 包。</p>
    </div>
    <template #footer><span ><el-button size="small" @click="$emit('close')">取消</el-button><el-button type="primary" size="small" :loading="busy" @click="submit">提交后台{{ operation === 'import' ? '导入' : '导出' }}任务</el-button></span></template>
  </el-dialog>
</template>

<script>
import api from '@/api/main/schemaSqlTransfer'

export default {
  name: 'SchemaSqlTransferDialog',
  props: { visible: Boolean, operation: { type: String, default: 'export' }, context: { type: Object, default: () => ({}) } },
  data() { return { file: null, preview: null, previewing: false, search: '', allSearch: '', page: 1, pageSize: 100, total: 0, pageTables: [], loading: false, busy: false, selected: [], selectedAll: false, importScope: 'all', requestNo: 0 } },
  watch: { visible(value) { if (value) this.reset() } },
  methods: {
    reset() { this.file = null; this.preview = null; this.search = ''; this.page = 1; this.total = 0; this.pageTables = []; this.selected = []; this.selectedAll = false; this.importScope = 'all'; if (this.operation === 'export') this.loadTables() },
    async chooseFile(event) {
      this.file = event.target.files && event.target.files[0] || null; this.preview = null; this.clearSelection(); this.pageTables = []; this.total = 0
      if (!this.file) return
      if (!this.file.name.toLowerCase().endsWith('.sql') || this.file.size > 200 * 1024 * 1024) return this.$message.error('请选择不超过 200 MiB 的 .sql 文件')
      this.previewing = true
      try { const response = await api.preview(this.file); if (!response.success) throw new Error(response.errorMessage || '解析失败'); this.preview = response.data; this.searchTables() }
      catch (error) { this.$message.error(error.message || 'SQL 文件解析失败') }
      finally { this.previewing = false }
    },
    searchTables() { this.page = 1; this.clearSelection(); this.loadTables() },
    changePage(page) { this.page = page; this.loadTables() },
    async loadTables() {
      if (this.operation === 'import') {
        const names = (this.preview && this.preview.tables || []).filter(name => name.toLowerCase().includes(this.search.toLowerCase()))
        this.total = names.length; this.pageTables = names.slice((this.page - 1) * this.pageSize, this.page * this.pageSize); return
      }
      const current = ++this.requestNo; this.loading = true
      try {
        const response = await api.tables({ dataSourceId: this.context.id, schemaName: this.context.schema, search: this.search, page: this.page, size: this.pageSize })
        if (!response.success) throw new Error(response.errorMessage || '表清单读取失败')
        if (current === this.requestNo) { this.pageTables = response.data.data || []; this.total = response.data.total || 0 }
      } catch (error) { this.$message.error(error.message || '表清单读取失败') }
      finally { if (current === this.requestNo) this.loading = false }
    },
    toggle(name, checked) {
      if (checked && !this.selected.includes(name)) this.selected.push(name)
      if (!checked) this.selected = this.selected.filter(item => item !== name)
    },
    selectPage() { this.selectedAll = false; this.pageTables.forEach(name => { if (!this.selected.includes(name)) this.selected.push(name) }) },
    selectAllResults() {
      if (!this.total) return this.$message.warning('没有可选择的表')
      if (this.operation === 'export') { this.selectedAll = true; this.allSearch = this.search; this.selected = [] }
      else { this.selectedAll = false; this.selected = this.preview.tables.filter(name => name.toLowerCase().includes(this.search.toLowerCase())) }
    },
    clearSelection() { this.selectedAll = false; this.allSearch = ''; this.selected = [] },
    async submit() {
      if (this.busy) return
      if (!this.selectedAll && !this.selected.length) return this.$message.warning('请选择至少一张表')
      if (this.operation === 'import' && (!this.file || !this.preview)) return this.$message.warning('请先选择并解析 SQL 文件')
      this.busy = true
      try {
        const response = this.operation === 'export'
          ? await api.exportSql({ dataSourceId: this.context.id, schemaName: this.context.schema, tables: this.selected, selectAll: this.selectedAll, search: this.selectedAll ? this.allSearch : this.search })
          : await api.importSql(this.context, this.file, this.selected, this.importScope)
        if (!response.success) throw new Error(response.errorMessage || '任务提交失败')
        this.$message.success('任务已提交，请到任务中心查看进度' + (this.operation === 'export' ? '并下载文件' : ''))
        this.$EventBus.$emit('openTaskCenter'); this.$emit('close')
      } catch (error) { this.$message.error(error.message || '任务提交失败') }
      finally { this.busy = false }
    }
  }
}
</script>

<style scoped>
.schema-sql-dialog { color: #303133; }
.schema-label { margin: 0 0 12px; }
.schema-label .el-tag { margin-left: 8px; }
.file-row { margin-top: 14px; }
.file-row input { max-width: 100%; cursor: pointer; }
.object-title { margin: 18px 0 9px; font-weight: 600; }
.object-title small, .file-hint { color: #69768a; font-size: 12px; font-weight: normal; }
.search-row { display: flex; gap: 7px; margin-bottom: 8px; }
.search-row .el-input { flex: 1; }
.table-list { min-height: 80px; max-height: 210px; overflow: auto; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); align-content: start; gap: 7px; padding: 10px; border: 1px solid #dcdfe6; border-radius: 4px; }
.table-list .el-checkbox { margin-right: 0; overflow: hidden; text-overflow: ellipsis; }
.empty { grid-column: 1 / -1; color: #909399; text-align: center; padding: 20px; }
</style>
