<template>
  <div class="field-result">
    <div class="summary-row">
      <button
        v-for="item in summaryItems"
        :key="item.key"
        type="button"
        class="summary-card"
        :class="[{ active: filter === item.key }, 'summary-card--' + item.key]"
        @click="filter = item.key"
      >
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
      </button>
    </div>

    <div class="result-toolbar">
      <div>
        <h3>字段对比结果</h3>
        <p>先看“变化内容”中的源值与目标值；展开两侧属性可核对完整定义。</p>
      </div>
      <el-radio-group v-model="filter" size="small">
        <el-radio-button label="all">全部</el-radio-button>
        <el-radio-button label="changed">有变化</el-radio-button>
        <el-radio-button label="source-only">仅源表</el-radio-button>
        <el-radio-button label="target-only">仅目标表</el-radio-button>
        <el-radio-button label="same">一致</el-radio-button>
      </el-radio-group>
    </div>

    <el-table
      :data="pageRows"
      :row-class-name="tableRowClassName"
      row-key="columnName"
      empty-text="当前筛选条件下没有字段"
      class="difference-table"
    >
      <el-table-column label="字段" min-width="150" fixed="left">
        <template #default="scope">
          <span class="column-name">{{ scope.row.columnName }}</span>
        </template>
      </el-table-column>
      <el-table-column label="结果" width="100" align="center">
        <template #default="scope">
          <el-tag :type="statusType(scope.row.status)" size="small" effect="plain">
            {{ statusText(scope.row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="变化内容（源 → 目标）" min-width="330">
        <template #default="scope">
          <div v-if="scope.row.status === 'changed'" class="change-list">
            <div v-for="detail in scope.row.changeDetails" :key="detail.field" class="change-line">
              <strong>{{ detail.label }}</strong>
              <span class="change-line__before">{{ detail.source }}</span>
              <i class="el-icon-right" />
              <span class="change-line__after">{{ detail.target }}</span>
            </div>
          </div>
          <span v-else-if="scope.row.status === 'source-only'" class="missing-value">目标表缺少该字段</span>
          <span v-else-if="scope.row.status === 'target-only'" class="missing-value">源表缺少该字段</span>
          <span v-else class="same-value">所有参与比较的属性一致</span>
        </template>
      </el-table-column>
      <el-table-column label="源表完整属性" min-width="310">
        <template #default="scope">
          <column-structure :row="scope.row" side="source" />
        </template>
      </el-table-column>
      <el-table-column label="目标表完整属性" min-width="310">
        <template #default="scope">
          <column-structure :row="scope.row" side="target" />
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-if="filteredRows.length > pageSize" small layout="total, prev, pager, next"
                   :total="filteredRows.length" :page-size="pageSize" v-model:current-page="page" />
  </div>
</template>

<script>
const ColumnStructure = {
  props: {
    row: { type: Object, required: true },
    side: { type: String, required: true }
  },
  methods: {
    propertyClass(field) {
      if (this.row.status !== 'changed') return ''
      if (field === 'type') {
        return this.row.changedFields.some(item => ['dataType', 'columnType', 'columnSize', 'decimalDigits'].includes(item))
          ? 'is-different'
          : ''
      }
      return this.row.changedFields.includes(field) ? 'is-different' : ''
    },
    columnTypeText(column) {
      if (column.columnType) return column.columnType
      const type = column.dataType || '-'
      if (!column.columnSize) return type
      const decimal = column.decimalDigits ? `,${column.decimalDigits}` : ''
      return `${type}(${column.columnSize}${decimal})`
    },
    nullableText(value) {
      return value === true || value === 1 || ['1', 'TRUE', 'Y', 'YES'].includes(String(value).toUpperCase()) ? '可为空' : '不可为空'
    },
    booleanText(value) {
      return value === true || value === 1 || ['1', 'TRUE', 'Y', 'YES'].includes(String(value).toUpperCase()) ? '是' : '否'
    },
    numberText(value) {
      return value === null || value === undefined || value === '' || Number(value) === 0 ? '—' : value
    },
    valueText(value) {
      return value === null || value === undefined || value === '' ? '—' : value
    }
  },
  render(h) {
    const column = this.row[this.side]
    if (!column) {
      return h('span', { class: 'missing-value' }, `${this.side === 'source' ? '源表' : '目标表'}无此字段`)
    }
    const property = (field, text) => h('span', { class: this.propertyClass(field) }, text)
    return h('div', { class: 'column-structure' }, [
      h('div', { class: ['type-line', this.propertyClass('type')] }, `完整类型：${this.columnTypeText(column)}`),
      h('div', { class: 'property-list' }, [
        property('dataType', `数据类型：${this.valueText(column.dataType)}`),
        property('columnSize', `长度：${this.numberText(column.columnSize)}`),
        property('decimalDigits', `小数位：${this.numberText(column.decimalDigits)}`),
        property('nullable', `是否可空：${this.nullableText(column.nullable)}`),
        property('defaultValue', `默认值：${this.valueText(column.defaultValue)}`),
        property('primaryKey', `主键：${this.booleanText(column.primaryKey)}`),
        property('autoIncrement', `自增：${this.booleanText(column.autoIncrement)}`),
        property('ordinalPosition', `字段顺序：${this.valueText(column.ordinalPosition)}`),
        property('comment', `注释：${this.valueText(column.comment)}`)
      ])
    ])
  }
}

export default {
  name: 'FieldCompareResult',
  components: { ColumnStructure },
  props: {
    rows: {
      type: Array,
      default: () => []
    }
  },
  data() {
    return { filter: 'all', page: 1, pageSize: 50 }
  },
  computed: {
    summary() {
      return this.rows.reduce((result, row) => {
        result[row.status] += 1
        return result
      }, { changed: 0, 'source-only': 0, 'target-only': 0, same: 0 })
    },
    summaryItems() {
      return [
        { key: 'all', label: '字段总数', value: this.rows.length },
        { key: 'changed', label: '有变化', value: this.summary.changed },
        { key: 'source-only', label: '仅源表', value: this.summary['source-only'] },
        { key: 'target-only', label: '仅目标表', value: this.summary['target-only'] },
        { key: 'same', label: '结构一致', value: this.summary.same }
      ]
    },
    filteredRows() {
      if (this.filter === 'all') return this.rows
      return this.rows.filter(row => row.status === this.filter)
    },
    pageRows() {
      return this.filteredRows.slice((this.page - 1) * this.pageSize, this.page * this.pageSize)
    }
  },
  watch: {
    filter() { this.page = 1 },
    rows() { this.page = 1 }
  },
  methods: {
    statusText(status) {
      return { changed: '有变化', 'source-only': '仅源表', 'target-only': '仅目标表', same: '一致' }[status]
    },
    statusType(status) {
      return { changed: 'danger', 'source-only': 'warning', 'target-only': 'info', same: 'success' }[status]
    },
    tableRowClassName({ row }) {
      return `compare-row--${row.status}`
    }
  }
}
</script>

<style lang="scss" scoped>
.summary-row {
  display: grid;
  grid-template-columns: repeat(5, minmax(110px, 1fr));
  gap: 12px;
  padding: 18px 24px 0;
}

.summary-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 60px;
  padding: 12px 14px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  background: #fff;
  color: #606266;
  cursor: pointer;

  strong { color: #303133; font-size: 22px; }
  &:hover, &.active { border-color: #006fff; box-shadow: 0 2px 8px rgba(0, 111, 255, 0.1); }
  &--changed strong, &--source-only strong { color: #e6a23c; }
  &--target-only strong { color: #409eff; }
  &--same strong { color: #67c23a; }
}

.result-toolbar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  padding: 20px 24px 14px;

  h3 { margin: 0 0 4px; font-size: 16px; }
  p { margin: 0; color: #909399; font-size: 12px; }
}

.difference-table {
  border-top: 1px solid #ebeef5;
  ::v-deep th { background: #f7f9fc; color: #4a5565; }
  ::v-deep .compare-row--changed td { background: #fffaf2; }
  ::v-deep .compare-row--source-only td,
  ::v-deep .compare-row--target-only td { background: #fafcff; }
}

.column-name { color: #303133; font-family: Consolas, Monaco, monospace; font-weight: 600; }
.change-list { display: flex; flex-direction: column; gap: 6px; padding: 4px 0; }
.change-line { display: flex; flex-wrap: wrap; align-items: baseline; gap: 5px; line-height: 1.5; font-size: 12px; }
.change-line strong { min-width: 70px; color: #b54708; }
.change-line__before { color: #5b6470; overflow-wrap: anywhere; }
.change-line__after { color: #b42318; font-weight: 600; overflow-wrap: anywhere; }
.change-line i { color: #909399; }
.same-value { color: #5b8b45; font-size: 12px; }

::v-deep .column-structure { padding: 5px 0; }
::v-deep .type-line { display: inline-block; margin-bottom: 7px; color: #303133; font-family: Consolas, Monaco, monospace; font-weight: 600; }
::v-deep .property-list { display: flex; flex-wrap: wrap; gap: 5px 12px; color: #7b8794; font-size: 12px; line-height: 20px; }
::v-deep .is-different { padding: 0 4px; border-radius: 3px; background: #fff1f0; color: #f56c6c !important; }
::v-deep .missing-value { color: #b6bec8; font-size: 12px; }

@media (max-width: 1100px) {
  .summary-row { grid-template-columns: repeat(3, 1fr); }
  .result-toolbar { align-items: flex-start; flex-direction: column; }
}
</style>
