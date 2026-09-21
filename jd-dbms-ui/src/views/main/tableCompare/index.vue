<template>
  <div class="app-container">
    <el-form :inline="true" size="small" @submit.native.prevent="compare">
      <el-form-item label="数据源ID"><el-input v-model="form.dataSourceId" placeholder="请输入数据源ID" /></el-form-item>
      <el-form-item label="数据库"><el-input v-model="form.databaseName" /></el-form-item>
      <el-form-item label="模式"><el-input v-model="form.schemaName" /></el-form-item>
      <el-form-item label="源表"><el-input v-model="form.sourceTableName" /></el-form-item>
      <el-form-item label="目标表"><el-input v-model="form.targetTableName" /></el-form-item>
      <el-form-item><el-button type="primary" icon="el-icon-search" @click="compare">开始对比</el-button></el-form-item>
    </el-form>
    <el-alert v-if="result" :title="result.same ? '两张表结构一致' : '发现结构差异'" :type="result.same ? 'success' : 'warning'" show-icon />
    <el-table v-if="result" v-loading="loading" :data="result.differences" stripe class="compare-table">
      <el-table-column prop="columnName" label="字段" width="220" />
      <el-table-column prop="type" label="差异类型" width="220" />
      <el-table-column prop="detail" label="说明" />
    </el-table>
  </div>
</template>

<script>
import tableCompareApi from '@/api/main/tableCompare'

export default {
  name: 'TableCompare',
  data() {
    return { loading: false, result: null, form: { dataSourceId: '', databaseName: '', schemaName: '', sourceTableName: '', targetTableName: '' } }
  },
  methods: {
    compare() {
      if (!this.form.dataSourceId || !this.form.sourceTableName || !this.form.targetTableName) {
        this.$message.error('请填写数据源ID、源表和目标表')
        return
      }
      this.loading = true
      tableCompareApi.compareTables(this.form).then(res => { this.result = res.data }).finally(() => { this.loading = false })
    }
  }
}
</script>

<style scoped>
.compare-table { margin-top: 16px; }
</style>
