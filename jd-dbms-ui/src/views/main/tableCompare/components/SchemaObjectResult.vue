<template>
  <div class="schema-object-result">
    <h3>其他结构对象对比</h3>
    <p>比较索引、约束、视图、触发器、程序、序列和同义词的可见定义；对象名称按数据库大小写精确匹配。</p>
    <el-alert v-for="issue in issues" :key="issue" :title="issue" type="warning" :closable="false" class="issue" />
    <div class="object-tools">
      <el-input v-model.trim="keyword" clearable size="small" prefix-icon="el-icon-search" placeholder="搜索对象名" />
      <el-select v-model="filter" size="small">
        <el-option value="all" label="全部" /><el-option value="changed" label="有变化" />
        <el-option value="source-only" label="仅源库" /><el-option value="target-only" label="仅目标库" />
        <el-option value="same" label="一致" /><el-option value="unknown" label="无法判定" />
      </el-select>
    </div>
    <el-table :data="pageRows" size="small" row-key="key" empty-text="没有匹配的结构对象">
      <el-table-column type="expand" width="44"><template slot-scope="scope">
        <div class="definition-grid">
          <div><strong>源定义摘要</strong><pre>{{ scope.row.source ? scope.row.source.definition : '不存在' }}</pre></div>
          <div><strong>目标定义摘要</strong><pre>{{ scope.row.target ? scope.row.target.definition : '不存在' }}</pre></div>
        </div>
      </template></el-table-column>
      <el-table-column label="类型" width="110"><template slot-scope="scope">{{ kindLabel(scope.row.kind) }}</template></el-table-column>
      <el-table-column prop="name" label="对象名" min-width="260" show-overflow-tooltip />
      <el-table-column label="结果" width="110"><template slot-scope="scope"><el-tag size="mini" :type="statusType(scope.row.status)">{{ statusLabel(scope.row.status) }}</el-tag></template></el-table-column>
    </el-table>
    <el-pagination v-if="filteredRows.length > pageSize" small layout="total, prev, pager, next"
                   :total="filteredRows.length" :page-size="pageSize" :current-page.sync="page" />
  </div>
</template>

<script>
export default {
  name: 'SchemaObjectResult',
  props: { rows: { type: Array, default: () => [] }, issues: { type: Array, default: () => [] } },
  data() { return { keyword: '', filter: 'all', page: 1, pageSize: 50 } },
  computed: {
    filteredRows() {
      const keyword = this.keyword.toLocaleUpperCase()
      return this.rows.filter(row => (this.filter === 'all' || row.status === this.filter) &&
        row.name.toLocaleUpperCase().includes(keyword))
    },
    pageRows() { return this.filteredRows.slice((this.page - 1) * this.pageSize, this.page * this.pageSize) }
  },
  watch: { filter() { this.page = 1 }, keyword() { this.page = 1 }, rows() { this.page = 1 } },
  methods: {
    kindLabel(kind) { return ({ INDEX: '索引', CONSTRAINT: '约束', VIEW: '视图', TRIGGER: '触发器', PROGRAM: '程序', SEQUENCE: '序列', SYNONYM: '同义词' })[kind] || kind },
    statusLabel(status) { return ({ changed: '有变化', 'source-only': '仅源库', 'target-only': '仅目标库', same: '一致', unknown: '无法判定' })[status] || status },
    statusType(status) { return ({ changed: 'danger', 'source-only': 'warning', 'target-only': 'info', same: 'success', unknown: 'info' })[status] || 'info' }
  }
}
</script>

<style scoped lang="scss">
.schema-object-result { border-top: 1px solid #e4e7ed; padding: 20px 24px; }
.schema-object-result h3 { margin: 0 0 6px; } .schema-object-result p { color: #606266; font-size: 13px; }
.issue { margin: 8px 0; } .object-tools { display: flex; gap: 10px; margin: 14px 0; }
.object-tools .el-input { max-width: 300px; } .object-tools .el-select { width: 150px; }
.definition-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.definition-grid pre { white-space: pre-wrap; overflow-wrap: anywhere; max-height: 240px; overflow: auto; font-size: 12px; }
@media(max-width: 750px) { .definition-grid { grid-template-columns: 1fr; } }
</style>
