<template>
  <div>
    <div class="isRadioBox">
      <el-table
        :data="viewDependentData"
        style="width: 100%; margin-bottom: 20px"
        :row-key="getKey"
        default-expand-all
        :tree-props="{ children: 'children'}"
      >
        <el-table-column prop="schemaName" label="所属模式" sortable>
        </el-table-column>
        <el-table-column prop="tableName" label="名称"> </el-table-column>
        <el-table-column prop="type" label="类型" sortable> </el-table-column>
        <el-table-column prop="status" label="状态"> </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script>
export default {
  props: {
    viewDependentData: {
      type: Array,
      default: ()=>[],
    },
  },
  data() {
    return {
      radio: "tree",
    };
  },
  methods:{
    getKey(row) {
      return row.id + Math.random()
    }
  }
};
</script>

<style lang='scss' scoped>
</style>