<template>
  <div>
    <el-table :data="detailDataSource" height="890" style="width: 100%">
      <el-table-column prop="name" label="TableSpace"> </el-table-column>
      <el-table-column prop="" label="Contents"> </el-table-column>
      <el-table-column prop="status" label="Status"> </el-table-column>
      <el-table-column prop="date" label="Extent Management"> </el-table-column>
      <el-table-column prop="totalSize" label="Size"> </el-table-column>
      <el-table-column prop="freeSize" label="Free"> </el-table-column>
      <el-table-column prop="useSize" label="Used"> </el-table-column>
      <el-table-column prop="usageRate" label="% Used"> </el-table-column>
      <el-table-column prop="expandUpperLimit" label="Max Size"> </el-table-column>
      <el-table-column prop="address" label="% Used of Max"> </el-table-column>
      <el-table-column prop="address" label="Bigfile"> </el-table-column>
    </el-table>
  </div>
</template>

<script>
export default {
  props: {
    detailDataSource: {
      type: Array,
      default: () => [],
    },
    dataInfo: {
      type: Object,
      default: {},
    },
  },
  data() {
    return {
      
    };
  },
};
</script>

<style>
</style>