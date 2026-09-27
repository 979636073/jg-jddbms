<template>
  <div class="database-result">
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
        <h3>整库表结构对比结果</h3>
        <p>展开发生变化的表，可查看具体字段差异。</p>
      </div>
      <div class="result-tools">
        <el-input
          v-model.trim="keyword"
          clearable
          size="small"
          prefix-icon="el-icon-search"
          placeholder="搜索表名"
        />
        <el-radio-group v-model="filter" size="small">
          <el-radio-button label="all">全部</el-radio-button>
          <el-radio-button label="changed">有变化</el-radio-button>
          <el-radio-button label="source-only">仅源库</el-radio-button>
          <el-radio-button label="target-only">仅目标库</el-radio-button>
          <el-radio-button label="same">一致</el-radio-button>
        </el-radio-group>
      </div>
    </div>

    <el-table
      :data="filteredRows"
      :row-class-name="tableRowClassName"
      row-key="key"
      empty-text="当前条件下没有匹配的数据表"
      class="database-table"
    >
      <el-table-column type="expand" width="46">
        <template slot-scope="scope">
          <div v-if="scope.row.fieldRows.length" class="field-detail">
            <div class="field-detail__title">
              <strong>{{ scope.row.sourceName || scope.row.targetName }}</strong>
              <span>字段差异明细</span>
            </div>
            <el-table :data="scope.row.fieldRows" size="mini" border>
              <el-table-column prop="columnName" label="字段" min-width="150" />
              <el-table-column label="源表结构" min-width="260">
                <template slot-scope="fieldScope">
                  <span v-if="fieldScope.row.source" :class="typeClass(fieldScope.row)">
                    {{ columnTypeText(fieldScope.row.source) }}
                  </span>
                  <span v-else class="missing-value">无</span>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="96" align="center">
                <template slot-scope="fieldScope">
                  <el-tag :type="statusType(fieldScope.row.status)" size="mini" effect="plain">
                    {{ fieldStatusText(fieldScope.row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="目标表结构" min-width="260">
                <template slot-scope="fieldScope">
                  <span v-if="fieldScope.row.target" :class="typeClass(fieldScope.row)">
                    {{ columnTypeText(fieldScope.row.target) }}
                  </span>
                  <span v-else class="missing-value">无</span>
                </template>
              </el-table-column>
              <el-table-column label="变化属性" min-width="220">
                <template slot-scope="fieldScope">
                  {{ changedFieldText(fieldScope.row) }}
                </template>
              </el-table-column>
            </el-table>
          </div>
          <div v-else class="no-field-detail">
            {{ scope.row.status === 'same' ? '两张表字段结构一致' : '另一侧不存在同名表，无字段差异明细' }}
          </div>
        </template>
      </el-table-column>
      <el-table-column label="表名" min-width="220">
        <template slot-scope="scope">
          <span class="table-name">{{ scope.row.sourceName || scope.row.targetName }}</span>
        </template>
      </el-table-column>
      <el-table-column label="源库" min-width="210">
        <template slot-scope="scope">
          <span v-if="scope.row.sourceName">{{ scope.row.sourceName }}</span>
          <span v-else class="missing-value">不存在</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="108" align="center">
        <template slot-scope="scope">
          <el-tag :type="statusType(scope.row.status)" size="mini" effect="plain">
            {{ tableStatusText(scope.row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="差异摘要" min-width="280">
        <template slot-scope="scope">
          <span v-if="scope.row.status === 'changed'" class="difference-summary">
            修改 {{ scope.row.counts.changed }}，仅源表 {{ scope.row.counts.sourceOnly }}，仅目标表 {{ scope.row.counts.targetOnly }}
          </span>
          <span v-else-if="scope.row.status === 'same'" class="same-summary">字段结构一致</span>
          <span v-else class="missing-value">{{ tableStatusText(scope.row.status) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="目标库" min-width="210">
        <template slot-scope="scope">
          <span v-if="scope.row.targetName">{{ scope.row.targetName }}</span>
          <span v-else class="missing-value">不存在</span>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script>
const FIELD_LABELS = {
  dataType: '数据类型',
  columnType: '字段类型',
  columnSize: '长度',
  decimalDigits: '小数位',
  nullable: '可空',
  defaultValue: '默认值',
  primaryKey: '主键',
  autoIncrement: '自增',
  comment: '注释'
}

export default {
  name: 'WholeDatabaseResult',
  props: {
    rows: {
      type: Array,
      default: () => []
    }
  },
  data() {
    return {
      filter: 'all',
      keyword: ''
    }
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
        { key: 'all', label: '数据表总数', value: this.rows.length },
        { key: 'changed', label: '有变化', value: this.summary.changed },
        { key: 'source-only', label: '仅源库', value: this.summary['source-only'] },
        { key: 'target-only', label: '仅目标库', value: this.summary['target-only'] },
        { key: 'same', label: '结构一致', value: this.summary.same }
      ]
    },
    filteredRows() {
      const keyword = this.keyword.toLocaleUpperCase()
      return this.rows.filter(row => {
        const matchesStatus = this.filter === 'all' || row.status === this.filter
        const tableName = row.sourceName || row.targetName || ''
        return matchesStatus && tableName.toLocaleUpperCase().includes(keyword)
      })
    }
  },
  methods: {
    tableStatusText(status) {
      return {
        changed: '有变化',
        'source-only': '仅源库',
        'target-only': '仅目标库',
        same: '一致'
      }[status]
    },
    fieldStatusText(status) {
      return {
        changed: '有变化',
        'source-only': '仅源表',
        'target-only': '仅目标表',
        same: '一致'
      }[status]
    },
    statusType(status) {
      return {
        changed: 'danger',
        'source-only': 'warning',
        'target-only': 'info',
        same: 'success'
      }[status]
    },
    columnTypeText(column) {
      if (column.columnType) return column.columnType
      const type = column.dataType || '-'
      if (!column.columnSize) return type
      const decimal = column.decimalDigits ? `,${column.decimalDigits}` : ''
      return `${type}(${column.columnSize}${decimal})`
    },
    typeClass(row) {
      return row.changedFields.some(item => ['dataType', 'columnType', 'columnSize', 'decimalDigits'].includes(item))
        ? 'is-different'
        : ''
    },
    changedFieldText(row) {
      if (row.status === 'source-only') return '目标表缺少该字段'
      if (row.status === 'target-only') return '源表缺少该字段'
      if (row.status === 'same') return '—'
      return row.changedFields.map(field => FIELD_LABELS[field] || field).join('、')
    },
    tableRowClassName({ row }) {
      return `database-row--${row.status}`
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

  strong {
    color: #303133;
    font-size: 22px;
  }

  &:hover,
  &.active {
    border-color: #006fff;
    box-shadow: 0 2px 8px rgba(0, 111, 255, 0.1);
  }

  &--changed strong,
  &--source-only strong {
    color: #e6a23c;
  }

  &--target-only strong {
    color: #409eff;
  }

  &--same strong {
    color: #67c23a;
  }
}

.result-toolbar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  padding: 20px 24px 14px;

  h3 {
    margin: 0 0 4px;
    font-size: 16px;
  }

  p {
    margin: 0;
    color: #909399;
    font-size: 12px;
  }
}

.result-tools {
  display: flex;
  align-items: center;
  gap: 12px;

  .el-input {
    width: 210px;
  }
}

.database-table {
  border-top: 1px solid #ebeef5;

  ::v-deep th {
    background: #f7f9fc;
    color: #4a5565;
  }

  ::v-deep .database-row--changed td {
    background: #fffaf2;
  }
}

.table-name {
  color: #303133;
  font-family: Consolas, Monaco, monospace;
  font-weight: 600;
}

.field-detail {
  padding: 10px 22px 18px 58px;
}

.field-detail__title {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 10px;

  span {
    color: #909399;
    font-size: 12px;
  }
}

.difference-summary,
.is-different {
  color: #f56c6c;
}

.is-different {
  padding: 1px 4px;
  border-radius: 3px;
  background: #fff1f0;
}

.same-summary {
  color: #67c23a;
}

.missing-value,
.no-field-detail {
  color: #a7b0bc;
  font-size: 12px;
}

.no-field-detail {
  padding: 12px 22px 12px 58px;
}

@media (max-width: 1100px) {
  .summary-row {
    grid-template-columns: repeat(3, 1fr);
  }

  .result-toolbar,
  .result-tools {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
