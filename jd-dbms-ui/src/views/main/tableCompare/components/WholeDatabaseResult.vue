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
        <p>展开数据表查看字段、索引和约束差异；读取不完整时显示“无法判定”。</p>
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
          <el-radio-button label="unknown">无法判定</el-radio-button>
        </el-radio-group>
      </div>
    </div>

    <el-table
      :data="pageRows"
      :row-class-name="tableRowClassName"
      row-key="key"
      empty-text="当前条件下没有匹配的数据表"
      class="database-table"
    >
      <el-table-column type="expand" width="46">
        <template #default="scope">
          <div v-if="scope.row.fieldRows.length" class="field-detail">
            <div class="field-detail__title">
              <strong>{{ scope.row.sourceName || scope.row.targetName }}</strong>
              <span>字段差异明细</span>
            </div>
            <field-compare-result :rows="scope.row.fieldRows" />
          </div>
          <div v-else class="no-field-detail">
            {{ scope.row.status === 'same' ? '两张表字段结构一致' : '另一侧不存在同名表，无字段差异明细' }}
          </div>
          <schema-object-result v-if="scope.row.objectRows && scope.row.objectRows.length" :rows="scope.row.objectRows" />
        </template>
      </el-table-column>
      <el-table-column label="表名" min-width="220">
        <template #default="scope">
          <span class="table-name">{{ scope.row.sourceName || scope.row.targetName }}</span>
        </template>
      </el-table-column>
      <el-table-column label="源库" min-width="210">
        <template #default="scope">
          <span v-if="scope.row.sourceName">{{ scope.row.sourceName }}</span>
          <span v-else class="missing-value">不存在</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="108" align="center">
        <template #default="scope">
          <el-tag :type="statusType(scope.row.status)" size="small" effect="plain">
            {{ tableStatusText(scope.row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="差异摘要" min-width="280">
        <template #default="scope">
          <span v-if="scope.row.status === 'changed'" class="difference-summary">
            字段修改 {{ scope.row.counts.changed }}，仅源表 {{ scope.row.counts.sourceOnly }}，仅目标表 {{ scope.row.counts.targetOnly }}；结构对象差异 {{ scope.row.objectDifferenceCount || 0 }}
          </span>
          <span v-else-if="scope.row.status === 'same'" class="same-summary">字段及可见结构对象一致</span>
          <span v-else class="missing-value">{{ tableStatusText(scope.row.status) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="目标库" min-width="210">
        <template #default="scope">
          <span v-if="scope.row.targetName">{{ scope.row.targetName }}</span>
          <span v-else class="missing-value">不存在</span>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-if="filteredRows.length > pageSize" small layout="total, prev, pager, next"
                   :total="filteredRows.length" :page-size="pageSize" v-model:current-page="page" />
  </div>
</template>

<script>
import FieldCompareResult from './FieldCompareResult'
import SchemaObjectResult from './SchemaObjectResult'

export default {
  name: 'WholeDatabaseResult',
  components: { FieldCompareResult, SchemaObjectResult },
  props: {
    rows: {
      type: Array,
      default: () => []
    }
  },
  data() {
    return {
      filter: 'all',
      keyword: '',
      page: 1,
      pageSize: 50
    }
  },
  computed: {
    summary() {
      return this.rows.reduce((result, row) => {
        result[row.status] += 1
        return result
      }, { changed: 0, 'source-only': 0, 'target-only': 0, same: 0, unknown: 0 })
    },
    summaryItems() {
      return [
        { key: 'all', label: '数据表总数', value: this.rows.length },
        { key: 'changed', label: '有变化', value: this.summary.changed },
        { key: 'source-only', label: '仅源库', value: this.summary['source-only'] },
        { key: 'target-only', label: '仅目标库', value: this.summary['target-only'] },
        { key: 'same', label: '结构一致', value: this.summary.same },
        { key: 'unknown', label: '无法判定', value: this.summary.unknown }
      ]
    },
    filteredRows() {
      const keyword = this.keyword.toLocaleUpperCase()
      return this.rows.filter(row => {
        const matchesStatus = this.filter === 'all' || row.status === this.filter
        const tableName = row.sourceName || row.targetName || ''
        return matchesStatus && tableName.toLocaleUpperCase().includes(keyword)
      })
    },
    pageRows() {
      const start = (this.page - 1) * this.pageSize
      return this.filteredRows.slice(start, start + this.pageSize)
    }
  },
  watch: {
    filter() { this.page = 1 },
    keyword() { this.page = 1 },
    rows() { this.page = 1 }
  },
  methods: {
    tableStatusText(status) {
      return {
        changed: '有变化',
        'source-only': '仅源库',
        'target-only': '仅目标库',
        same: '一致',
        unknown: '无法判定'
      }[status]
    },
    statusType(status) {
      return {
        changed: 'danger',
        'source-only': 'warning',
        'target-only': 'info',
        same: 'success',
        unknown: 'info'
      }[status]
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
  grid-template-columns: repeat(6, minmax(110px, 1fr));
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
