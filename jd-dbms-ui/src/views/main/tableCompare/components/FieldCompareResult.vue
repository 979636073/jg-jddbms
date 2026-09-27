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
        <p>高亮内容表示该属性在两张表中不一致。</p>
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
      :data="filteredRows"
      :row-class-name="tableRowClassName"
      empty-text="当前筛选条件下没有字段"
      class="difference-table"
    >
      <el-table-column label="字段" min-width="150" fixed="left">
        <template slot-scope="scope">
          <span class="column-name">{{ scope.row.columnName }}</span>
        </template>
      </el-table-column>
      <el-table-column label="源表结构" min-width="340">
        <template slot-scope="scope">
          <column-structure :row="scope.row" side="source" />
        </template>
      </el-table-column>
      <el-table-column label="状态" width="108" align="center">
        <template slot-scope="scope">
          <el-tag :type="statusType(scope.row.status)" size="mini" effect="plain">
            {{ statusText(scope.row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="目标表结构" min-width="340">
        <template slot-scope="scope">
          <column-structure :row="scope.row" side="target" />
        </template>
      </el-table-column>
    </el-table>
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
      return Number(value) === 1 || value === true || value === 'true' ? '可为空' : '不可为空'
    },
    booleanText(value) {
      return value === true || value === 1 || value === '1' || value === 'true' ? '是' : '否'
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
      h('div', { class: ['type-line', this.propertyClass('type')] }, this.columnTypeText(column)),
      h('div', { class: 'property-list' }, [
        property('nullable', this.nullableText(column.nullable)),
        property('defaultValue', `默认值：${this.valueText(column.defaultValue)}`),
        property('primaryKey', `主键：${this.booleanText(column.primaryKey)}`),
        property('autoIncrement', `自增：${this.booleanText(column.autoIncrement)}`),
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
    return { filter: 'all' }
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
    }
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
