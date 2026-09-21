<template>
  <div>
    <el-table :data="detailDataSource" height="890" style="width: 100%">
      <el-table-column prop="name" label="用户"> </el-table-column>
      <el-table-column prop="accountStatus" label="状态"> </el-table-column>
      <el-table-column prop="defaultTableSpace" label="默认表空间"> </el-table-column>
      <el-table-column prop="tempTableSpace" label="临时表空间">
      </el-table-column>
      <el-table-column prop="created" label="创建时间"> </el-table-column>
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
  },
  data() {
    return {};
  },
};
</script>

<style>
</style>