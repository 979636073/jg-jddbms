<script>
export default {
  name: 'BaseInfo',
  props: {
    basicForm: {
      type: Object
    },
    currentConfig: {
      type: Object
    }
  },
}
</script>

<template>
  <el-form ref="basicForm" size="mini" :model="basicForm" label-position="top" label-width="80px">
    <el-form-item label="表名">
      <el-input v-model="basicForm.name"></el-input>
    </el-form-item>
    <el-form-item v-if="currentConfig && currentConfig.uniqueData.databaseType !== 'GBASE'" label="注释">
      <el-input v-model="basicForm.comment" maxlength="50" show-word-limit></el-input>
    </el-form-item>

    <template v-if="currentConfig && currentConfig.uniqueData.databaseType === 'MYSQL'">
      <el-form-item label="字符集">
        <el-input v-model="basicForm.charset"></el-input>
      </el-form-item>
      <el-form-item label="引擎">
        <el-input v-model="basicForm.engine"></el-input>
      </el-form-item>
      <el-form-item label="自增值">
        <el-input v-model="basicForm.incrementValue"></el-input>
      </el-form-item>
    </template>
  </el-form>
</template>

<style scoped lang="scss">

</style>
