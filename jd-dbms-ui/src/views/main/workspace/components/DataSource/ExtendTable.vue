<script>
export default {
  props: {
    extendTableData: {
      type: Array,
      default: []
    }
  },
  data() {
    return {
      clickRow: null, // 当前点击的行
      clickCell: null, // 当前点击的列
      extendConfig: [
        { label: '名称', value: 'key' },
        { label: '值', value: 'value' }
      ]
    };
  },
  methods: {
    cellclick(row, column) {
      this.clickRow = row.index;
      this.clickCell = column.index;
      this.$nextTick(() => {
        this.$refs.editInput[0].focus();
      })
    },
    inputBlur(row, event, column) {
      this.clickRow = null;
      this.clickCell = null;
    },
    // 把每一行的索引放进row
    tableRowClassName({ row, rowIndex }) {
      row.index = rowIndex;
      return '';
    },

    // 把每一列的索引放进column
    tableCellClassName({ column, columnIndex }) {
      column.index = columnIndex;
    },
  }
};
</script>

<template>
  <el-table
    :data="extendTableData"
    style="width: 100%"
    :row-class-name="tableRowClassName"
    :cell-class-name="tableCellClassName"
    @cell-click="cellclick"
  >
    <el-table-column
      v-for="item in extendConfig"
      :key="item.value"
      :prop="item.value"
      :label="item.label">
      <template slot-scope="scope">
        <div v-if="scope.row.index === clickRow && scope.column.index === clickCell">
          <el-input
            ref="editInput"
            v-model="scope.row[item.value]"
            maxlength="300"
            size="mini"
            @blur="inputBlur(scope.row)"
          />
        </div>
        <span v-else>{{ scope.row[item.value] }}</span>
      </template>
    </el-table-column>
  </el-table>
</template>
