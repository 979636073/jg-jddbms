<template>
  <div class="table-compare-page">
    <div class="page-heading">
      <div>
        <h2>表结构对比</h2>
        <p>{{ compareMode === 'table' ? '选择任意两个数据源中的表，快速定位字段结构差异。' : '选择两个数据库或模式，批量检查其中所有数据表的字段结构。' }}</p>
      </div>
      <el-radio-group v-model="compareMode" size="small" :disabled="comparing" @change="handleModeChange">
        <el-radio-button label="table">单表对比</el-radio-button>
        <el-radio-button label="database">整库对比</el-radio-button>
      </el-radio-group>
    </div>

    <section class="compare-panel">
      <div class="selection-grid">
        <div class="object-card source-card">
          <div class="object-card__header">
            <span class="side-mark">源</span>
            <div>
              <h3>{{ compareMode === 'table' ? '源表' : '源库' }}</h3>
              <p>{{ compareMode === 'table' ? '作为结构对比基准' : '作为整库结构基准' }}</p>
            </div>
          </div>
          <el-form label-position="top" size="small">
            <el-form-item label="数据源">
              <el-select
                v-model="source.dataSourceId"
                filterable
                clearable
                :loading="dataSourceLoading"
                :disabled="comparing"
                placeholder="请选择数据源"
                @change="handleDataSourceChange('source')"
              >
                <el-option
                  v-for="item in dataSources"
                  :key="item.id"
                  :label="dataSourceLabel(item)"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item :label="namespaceLabel('source')">
              <el-select
                v-model="source.namespaceName"
                filterable
                clearable
                :loading="source.loadingNamespaces"
                :disabled="!source.dataSourceId || comparing"
                :placeholder="namespacePlaceholder('source')"
                @change="handleNamespaceChange('source')"
              >
                <el-option
                  v-for="item in source.namespaces"
                  :key="item.name"
                  :label="item.name"
                  :value="item.name"
                />
              </el-select>
            </el-form-item>
            <el-form-item v-if="compareMode === 'table'" label="数据表">
              <el-select
                v-model="source.tableName"
                filterable
                remote
                clearable
                reserve-keyword
                :remote-method="searchSourceTables"
                :loading="source.loadingTables"
                :disabled="!source.namespaceName || comparing"
                placeholder="输入表名可远程搜索"
                @change="resetResult"
              >
                <el-option
                  v-for="item in source.tables"
                  :key="item.name"
                  :label="tableLabel(item)"
                  :value="item.name"
                />
              </el-select>
            </el-form-item>
          </el-form>
        </div>

        <div class="swap-column">
          <el-button
            class="swap-button"
            circle
            icon="el-icon-sort"
            title="交换源表和目标表"
            :disabled="comparing"
            @click="swapSides"
          />
          <span>交换</span>
        </div>

        <div class="object-card target-card">
          <div class="object-card__header">
            <span class="side-mark">目</span>
            <div>
              <h3>{{ compareMode === 'table' ? '目标表' : '目标库' }}</h3>
              <p>{{ compareMode === 'table' ? '与源表逐项比较' : '与源库逐表比较' }}</p>
            </div>
          </div>
          <el-form label-position="top" size="small">
            <el-form-item label="数据源">
              <el-select
                v-model="target.dataSourceId"
                filterable
                clearable
                :loading="dataSourceLoading"
                :disabled="comparing"
                placeholder="请选择数据源"
                @change="handleDataSourceChange('target')"
              >
                <el-option
                  v-for="item in dataSources"
                  :key="item.id"
                  :label="dataSourceLabel(item)"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item :label="namespaceLabel('target')">
              <el-select
                v-model="target.namespaceName"
                filterable
                clearable
                :loading="target.loadingNamespaces"
                :disabled="!target.dataSourceId || comparing"
                :placeholder="namespacePlaceholder('target')"
                @change="handleNamespaceChange('target')"
              >
                <el-option
                  v-for="item in target.namespaces"
                  :key="item.name"
                  :label="item.name"
                  :value="item.name"
                />
              </el-select>
            </el-form-item>
            <el-form-item v-if="compareMode === 'table'" label="数据表">
              <el-select
                v-model="target.tableName"
                filterable
                remote
                clearable
                reserve-keyword
                :remote-method="searchTargetTables"
                :loading="target.loadingTables"
                :disabled="!target.namespaceName || comparing"
                placeholder="输入表名可远程搜索"
                @change="resetResult"
              >
                <el-option
                  v-for="item in target.tables"
                  :key="item.name"
                  :label="tableLabel(item)"
                  :value="item.name"
                />
              </el-select>
            </el-form-item>
          </el-form>
        </div>
      </div>

      <div class="compare-actions">
        <div v-if="compareMode === 'database' && comparing" class="database-progress">
          <div class="database-progress__text">
            <span>{{ wholeProgress.stage === 'tables' ? '正在读取数据表清单' : '正在对比表结构' }}</span>
            <strong v-if="wholeProgress.total">{{ wholeProgress.completed }} / {{ wholeProgress.total }}</strong>
          </div>
          <el-progress :percentage="wholeProgressPercentage" :show-text="false" />
        </div>
        <div v-else class="selection-paths">
          <span>{{ selectionPath('source') }}</span>
          <i class="el-icon-right" />
          <span>{{ selectionPath('target') }}</span>
        </div>
        <el-button
          type="primary"
          icon="el-icon-data-analysis"
          :loading="comparing"
          :disabled="!canCompare"
          @click="compare"
        >{{ compareMode === 'table' ? '开始对比' : '开始整库对比' }}</el-button>
      </div>
    </section>

    <section class="result-panel">
      <whole-database-result
        v-if="hasCompared && compareMode === 'database'"
        :rows="databaseRows"
      />
      <field-compare-result v-else-if="hasCompared" :rows="resultRows" />

      <div v-else class="empty-state">
        <i class="el-icon-document-copy" />
        <h3>{{ compareMode === 'table' ? '等待选择需要对比的表' : '等待选择需要对比的数据库或模式' }}</h3>
        <p>{{ compareMode === 'table' ? '完成上方选择并点击“开始对比”，字段差异会显示在这里。' : '整库对比会逐表读取字段结构，并汇总所有表级差异。' }}</p>
      </div>
    </section>
  </div>
</template>

<script>
import connectionServer from '@/api/main/connection'
import tableServer from '@/api/main/table'
import FieldCompareResult from './components/FieldCompareResult'
import WholeDatabaseResult from './components/WholeDatabaseResult'

function createSide() {
  return {
    dataSourceId: '',
    namespaceName: '',
    tableName: '',
    namespaces: [],
    tables: [],
    loadingNamespaces: false,
    loadingTables: false,
    searchTimer: null,
    tableRequestVersion: 0
  }
}

const COMPARE_FIELDS = [
  'dataType',
  'columnType',
  'columnSize',
  'decimalDigits',
  'nullable',
  'defaultValue',
  'primaryKey',
  'autoIncrement',
  'comment'
]

export default {
  name: 'TableCompare',
  components: { FieldCompareResult, WholeDatabaseResult },
  data() {
    return {
      compareMode: 'table',
      dataSources: [],
      dataSourceLoading: false,
      source: createSide(),
      target: createSide(),
      resultRows: [],
      databaseRows: [],
      comparing: false,
      hasCompared: false,
      wholeProgress: {
        stage: 'tables',
        completed: 0,
        total: 0
      }
    }
  },
  computed: {
    canCompare() {
      return ['source', 'target'].every(side => {
        const item = this[side]
        return item.dataSourceId && item.namespaceName && (this.compareMode === 'database' || item.tableName)
      })
    },
    wholeProgressPercentage() {
      if (!this.wholeProgress.total) return 0
      return Math.round(this.wholeProgress.completed / this.wholeProgress.total * 100)
    }
  },
  created() {
    this.loadDataSources()
  },
  beforeDestroy() {
    clearTimeout(this.source.searchTimer)
    clearTimeout(this.target.searchTimer)
  },
  methods: {
    async loadDataSources() {
      this.dataSourceLoading = true
      try {
        const res = await connectionServer.getList({
          pageNo: 1,
          pageSize: 10000,
          refresh: false,
          searchKey: ''
        })
        this.dataSources = (res.data && res.data.data) || []
      } finally {
        this.dataSourceLoading = false
      }
    },
    getDataSource(side) {
      return this.dataSources.find(item => item.id === this[side].dataSourceId)
    },
    dataSourceLabel(item) {
      const name = item.alias || item.name || item.id
      return item.type ? `${name} · ${item.type}` : name
    },
    namespaceLabel(side) {
      const dataSource = this.getDataSource(side)
      return dataSource && dataSource.supportDatabase ? '数据库' : '模式'
    },
    namespacePlaceholder(side) {
      return `请选择${this.namespaceLabel(side)}`
    },
    tableLabel(item) {
      return item.comment ? `${item.name} · ${item.comment}` : item.name
    },
    handleModeChange() {
      this.resetResult()
      if (this.compareMode === 'table') {
        if (this.source.namespaceName) this.loadTables('source', '')
        if (this.target.namespaceName) this.loadTables('target', '')
      }
    },
    async handleDataSourceChange(side) {
      const selection = this[side]
      selection.namespaceName = ''
      selection.tableName = ''
      selection.namespaces = []
      selection.tables = []
      this.resetResult()
      if (!selection.dataSourceId) return
      await this.loadNamespaces(side)
    },
    async loadNamespaces(side) {
      const selection = this[side]
      const dataSource = this.getDataSource(side)
      if (!dataSource) return
      selection.loadingNamespaces = true
      try {
        const request = dataSource.supportDatabase
          ? connectionServer.getDatabaseList
          : connectionServer.getSchemaList
        const res = await request({
          dataSourceId: dataSource.id,
          dataSourceName: dataSource.alias,
          refresh: false
        })
        selection.namespaces = Array.isArray(res.data) ? res.data : []
        if (selection.namespaces.length === 1) {
          selection.namespaceName = selection.namespaces[0].name
          if (this.compareMode === 'table') await this.loadTables(side, '')
        }
      } catch (error) {
        selection.namespaces = []
        this.$message.error('数据库或模式加载失败，请稍后重试')
      } finally {
        selection.loadingNamespaces = false
      }
    },
    handleNamespaceChange(side) {
      const selection = this[side]
      selection.tableName = ''
      selection.tables = []
      this.resetResult()
      if (selection.namespaceName && this.compareMode === 'table') this.loadTables(side, '')
    },
    searchSourceTables(keyword) {
      this.scheduleTableSearch('source', keyword)
    },
    searchTargetTables(keyword) {
      this.scheduleTableSearch('target', keyword)
    },
    scheduleTableSearch(side, keyword) {
      clearTimeout(this[side].searchTimer)
      this[side].searchTimer = setTimeout(() => this.loadTables(side, keyword), 250)
    },
    buildRequest(side) {
      const selection = this[side]
      const dataSource = this.getDataSource(side)
      return {
        dataSourceId: selection.dataSourceId,
        dataSourceName: dataSource && dataSource.alias,
        databaseType: dataSource && dataSource.type,
        databaseName: dataSource && dataSource.supportDatabase ? selection.namespaceName : '',
        schemaName: dataSource && dataSource.supportSchema ? selection.namespaceName : '',
        tableName: selection.tableName
      }
    },
    async loadTables(side, keyword) {
      const selection = this[side]
      if (!selection.dataSourceId || !selection.namespaceName) return
      const requestVersion = ++selection.tableRequestVersion
      selection.loadingTables = true
      try {
        const res = await tableServer.getTableList({
          ...this.buildRequest(side),
          refresh: false,
          pageNo: 1,
          pageSize: 200,
          requestType: 1,
          searchKey: keyword || ''
        })
        if (requestVersion === selection.tableRequestVersion) {
          selection.tables = (res.data && res.data.data) || []
        }
      } catch (error) {
        if (requestVersion === selection.tableRequestVersion) {
          selection.tables = []
          this.$message.error('数据表加载失败，请稍后重试')
        }
      } finally {
        if (requestVersion === selection.tableRequestVersion) {
          selection.loadingTables = false
        }
      }
    },
    swapSides() {
      const source = this.source
      this.source = this.target
      this.target = source
      this.resetResult()
    },
    resetResult() {
      this.resultRows = []
      this.databaseRows = []
      this.hasCompared = false
      this.wholeProgress = { stage: 'tables', completed: 0, total: 0 }
    },
    async compare() {
      if (!this.canCompare) return
      this.comparing = true
      try {
        if (this.compareMode === 'database') {
          await this.compareWholeDatabase()
        } else {
          await this.compareSingleTable()
        }
        this.hasCompared = true
      } catch (error) {
        this.resetResult()
        this.$message.error(this.compareMode === 'database'
          ? '整库结构读取失败，请检查连接后重试'
          : '表结构读取失败，请检查连接后重试')
      } finally {
        this.comparing = false
      }
    },
    async compareSingleTable() {
      const [sourceRes, targetRes] = await Promise.all([
        tableServer.getAllFieldByTable(this.buildRequest('source')),
        tableServer.getAllFieldByTable(this.buildRequest('target'))
      ])
      const sourceColumns = Array.isArray(sourceRes.data) ? sourceRes.data : []
      const targetColumns = Array.isArray(targetRes.data) ? targetRes.data : []
      this.resultRows = this.buildRows(sourceColumns, targetColumns)
    },
    async compareWholeDatabase() {
      this.wholeProgress = { stage: 'tables', completed: 0, total: 0 }
      const [sourceRes, targetRes] = await Promise.all([
        tableServer.getAllTableList({ ...this.buildRequest('source'), tableName: undefined, refresh: false }),
        tableServer.getAllTableList({ ...this.buildRequest('target'), tableName: undefined, refresh: false })
      ])
      const sourceTables = Array.isArray(sourceRes.data) ? sourceRes.data : []
      const targetTables = Array.isArray(targetRes.data) ? targetRes.data : []
      const rows = this.buildDatabaseRows(sourceTables, targetTables)
      const matchedRows = rows.filter(row => row.sourceName && row.targetName)
      this.wholeProgress = { stage: 'columns', completed: 0, total: matchedRows.length }
      await this.runWithConcurrency(matchedRows, 4, async row => {
        const [sourceColumnsRes, targetColumnsRes] = await Promise.all([
          tableServer.getAllFieldByTable({ ...this.buildRequest('source'), tableName: row.sourceName }),
          tableServer.getAllFieldByTable({ ...this.buildRequest('target'), tableName: row.targetName })
        ])
        const sourceColumns = Array.isArray(sourceColumnsRes.data) ? sourceColumnsRes.data : []
        const targetColumns = Array.isArray(targetColumnsRes.data) ? targetColumnsRes.data : []
        row.fieldRows = this.buildRows(sourceColumns, targetColumns, true)
        row.counts = this.countFieldDifferences(row.fieldRows)
        row.status = row.counts.changed || row.counts.sourceOnly || row.counts.targetOnly ? 'changed' : 'same'
        this.wholeProgress.completed += 1
      })
      this.databaseRows = rows
    },
    buildDatabaseRows(sourceTables, targetTables) {
      const normalizeName = name => String(name || '').toLocaleUpperCase()
      const targetMap = new Map(targetTables.map(table => [normalizeName(table.name), table]))
      const sourceNames = new Set(sourceTables.map(table => normalizeName(table.name)))
      const rows = sourceTables.map(sourceTable => {
        const targetTable = targetMap.get(normalizeName(sourceTable.name))
        return {
          key: normalizeName(sourceTable.name),
          sourceName: sourceTable.name,
          targetName: targetTable ? targetTable.name : '',
          status: targetTable ? 'same' : 'source-only',
          fieldRows: [],
          counts: { changed: 0, sourceOnly: 0, targetOnly: 0 }
        }
      })
      targetTables.forEach(targetTable => {
        if (!sourceNames.has(normalizeName(targetTable.name))) {
          rows.push({
            key: normalizeName(targetTable.name),
            sourceName: '',
            targetName: targetTable.name,
            status: 'target-only',
            fieldRows: [],
            counts: { changed: 0, sourceOnly: 0, targetOnly: 0 }
          })
        }
      })
      return rows
    },
    countFieldDifferences(rows) {
      return rows.reduce((counts, row) => {
        if (row.status === 'changed') counts.changed += 1
        if (row.status === 'source-only') counts.sourceOnly += 1
        if (row.status === 'target-only') counts.targetOnly += 1
        return counts
      }, { changed: 0, sourceOnly: 0, targetOnly: 0 })
    },
    async runWithConcurrency(items, limit, worker) {
      let nextIndex = 0
      const runNext = async () => {
        while (nextIndex < items.length) {
          const currentIndex = nextIndex
          nextIndex += 1
          await worker(items[currentIndex], currentIndex)
        }
      }
      const workerCount = Math.min(limit, items.length)
      await Promise.all(Array.from({ length: workerCount }, () => runNext()))
    },
    buildRows(sourceColumns, targetColumns, ignoreCase = false) {
      const normalizeName = name => ignoreCase ? String(name || '').toLocaleUpperCase() : name
      const targetMap = new Map(targetColumns.map(column => [normalizeName(column.name), column]))
      const sourceNames = new Set(sourceColumns.map(column => normalizeName(column.name)))
      const rows = sourceColumns.map(source => {
        const target = targetMap.get(normalizeName(source.name))
        if (!target) {
          return { columnName: source.name, source, target: null, status: 'source-only', changedFields: [] }
        }
        const changedFields = COMPARE_FIELDS.filter(field => !this.sameValue(source[field], target[field]))
        return {
          columnName: source.name,
          source,
          target,
          status: changedFields.length ? 'changed' : 'same',
          changedFields
        }
      })
      targetColumns.forEach(target => {
        if (!sourceNames.has(normalizeName(target.name))) {
          rows.push({ columnName: target.name, source: null, target, status: 'target-only', changedFields: [] })
        }
      })
      return rows
    },
    sameValue(left, right) {
      const normalize = value => value === null || value === undefined ? '' : String(value).trim()
      return normalize(left) === normalize(right)
    },
    selectionPath(side) {
      const selection = this[side]
      const dataSource = this.getDataSource(side)
      const path = [dataSource && (dataSource.alias || dataSource.name), selection.namespaceName]
      if (this.compareMode === 'table') {
        if (!selection.tableName) return side === 'source' ? '尚未选择源表' : '尚未选择目标表'
        path.push(selection.tableName)
      } else if (!selection.namespaceName) {
        return side === 'source' ? '尚未选择源库' : '尚未选择目标库'
      }
      return path
        .filter(Boolean)
        .join(' / ')
    }
  }
}
</script>

<style lang="scss" scoped>
.table-compare-page {
  min-height: calc(100vh - 84px);
  padding: 24px;
  background: #f5f7fa;
  color: #1f2d3d;
}

.page-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  max-width: 1440px;
  margin: 0 auto 16px;

  h2 {
    margin: 0 0 6px;
    font-size: 22px;
    line-height: 30px;
    font-weight: 600;
  }

  p {
    margin: 0;
    color: #8492a6;
    font-size: 13px;
  }
}

.compare-panel,
.result-panel {
  max-width: 1440px;
  margin: 0 auto;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  background: #fff;
}

.selection-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 56px minmax(0, 1fr);
  gap: 20px;
  padding: 20px 24px;
}

.object-card {
  padding: 18px 20px 4px;
  border: 1px solid #dfe6ee;
  border-top: 3px solid #006fff;
  border-radius: 6px;
  background: #fbfcfe;

  &.target-card {
    border-top-color: #5b8def;
  }

  ::v-deep .el-select {
    width: 100%;
  }

  ::v-deep .el-form-item {
    margin-bottom: 16px;
  }

  ::v-deep .el-form-item__label {
    padding-bottom: 6px;
    line-height: 20px;
    color: #606266;
  }
}

.object-card__header {
  display: flex;
  align-items: center;
  margin-bottom: 16px;

  .side-mark {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 32px;
    height: 32px;
    margin-right: 10px;
    border-radius: 6px;
    background: #006fff;
    color: #fff;
    font-size: 14px;
    font-weight: 600;
  }

  h3 {
    margin: 0;
    font-size: 15px;
    line-height: 22px;
  }

  p {
    margin: 1px 0 0;
    color: #909399;
    font-size: 12px;
  }
}

.target-card .side-mark {
  background: #5b8def;
}

.swap-column {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 7px;
  color: #909399;
  font-size: 12px;

  .swap-button {
    border-color: #b8c7d9;
    color: #006fff;

    i {
      transform: rotate(90deg);
    }
  }
}

.compare-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 62px;
  padding: 0 24px;
  border-top: 1px solid #ebeef5;

  .selection-paths {
    display: flex;
    align-items: center;
    min-width: 0;
    color: #606266;
    font-size: 12px;

    span {
      max-width: 360px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    i {
      margin: 0 12px;
      color: #c0c4cc;
    }
  }
}

.database-progress {
  width: min(520px, 60%);

  .database-progress__text {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 6px;
    color: #606266;
    font-size: 12px;
  }

  ::v-deep .el-progress-bar__inner {
    background: #006fff;
  }
}

.result-panel {
  min-height: 300px;
  margin-top: 16px;
  overflow: hidden;
}

.empty-state {
  display: flex;
  min-height: 300px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #909399;

  i {
    margin-bottom: 14px;
    color: #c8d2df;
    font-size: 46px;
  }

  h3 {
    margin: 0 0 7px;
    color: #606266;
    font-size: 15px;
  }

  p {
    margin: 0;
    font-size: 12px;
  }
}

@media (max-width: 1100px) {
  .page-heading {
    align-items: flex-start;
  }

  .selection-grid {
    grid-template-columns: 1fr;
  }

  .swap-column {
    flex-direction: row;
    padding: 0;
  }

}
</style>
