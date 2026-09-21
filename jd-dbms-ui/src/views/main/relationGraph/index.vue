<template>
  <div class="app-container">
    <el-form :inline="true" size="small" @submit.native.prevent="loadRelations">
      <el-form-item label="数据源ID"><el-input v-model="form.dataSourceId" placeholder="请输入数据源ID" /></el-form-item>
      <el-form-item label="数据库"><el-input v-model="form.databaseName" /></el-form-item>
      <el-form-item label="模式"><el-input v-model="form.schemaName" /></el-form-item>
      <el-form-item label="表名"><el-input v-model="form.tableName" /></el-form-item>
      <el-form-item label="关联类型">
        <el-select v-model="form.relationType" style="width: 130px">
          <el-option label="外键关联" value="table" />
          <el-option label="视图依赖" value="view" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-share" @click="loadRelations">查询关联</el-button>
      </el-form-item>
    </el-form>
    <el-alert :title="form.relationType === 'view' ? '当前展示视图依赖关系树，数据只读，不会修改数据库。' : '当前展示该表的外键关联树，数据只读，不会修改数据库。'" type="info" show-icon :closable="false" />
    <el-card v-loading="loading" class="relation-card">
      <el-empty v-if="!treeData" description="请输入表信息查询关联关系" />
      <el-tree v-else :data="[treeData]" node-key="id" default-expand-all :props="treeProps">
        <span slot-scope="{ node, data }" class="tree-node">
          <i :class="data.type === 'TABLE' ? 'el-icon-tickets' : 'el-icon-connection'" />
          <span>{{ data.schemaName ? data.schemaName + '.' : '' }}{{ data.tableName }}</span>
          <el-tag v-if="data.status" size="mini" type="info">{{ data.status }}</el-tag>
        </span>
      </el-tree>
    </el-card>
  </div>
</template>

<script>
import relationApi from '@/api/main/relation'

export default {
  name: 'RelationGraph',
  data() {
    return {
      loading: false,
      treeData: null,
      treeProps: { children: 'children', label: 'tableName' },
      form: { dataSourceId: '', databaseName: '', schemaName: '', tableName: '', isRefreshCache: false, relationType: 'table' }
    }
  },
  methods: {
    loadRelations() {
      if (!this.form.dataSourceId || !this.form.schemaName || !this.form.tableName) {
        this.$message.error('请填写数据源ID、模式和表名')
        return
      }
      this.loading = true
      const params = Object.assign({}, this.form)
      if (params.relationType === 'view') params.check = '1'
      delete params.relationType
      const query = this.form.relationType === 'view'
        ? relationApi.queryViewRelations(params)
        : relationApi.queryTableRelations(params)
      query.then(res => {
        this.treeData = res.data || null
        if (!this.treeData) this.$message.info('未查询到外键关联')
      }).finally(() => { this.loading = false })
    }
  }
}
</script>

<style scoped>
.relation-card { margin-top: 16px; min-height: 360px; }
.tree-node { display: inline-flex; align-items: center; gap: 6px; }
</style>
