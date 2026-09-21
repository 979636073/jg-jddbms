<template>
  <div>
    <!-- <el-button type="primary" size="small" style="margin-bottom:10px" @click="updateSave">保存</el-button> -->
    <el-table
      size="mini"
      :data="tableData"
      :cell-class-name="getRowCloumn"
      @cell-click="getCell"
      row-key="key"
      border
      height="700"
    >
    <el-table-column prop="name" label="列名称">
      <template slot-scope="scope">
          <template>
            <el-input size="mini" v-model="scope.row.name" @blur="inputBlur(scope.row)" maxlength="50" show-word-limit></el-input>
          </template>
        </template>
       </el-table-column>
        <el-table-column prop="columnType" label="数据类型" sortable>
        </el-table-column>
        <el-table-column prop="nullable" label="是否为空" :formatter="dataFormatter"> </el-table-column>
      <!-- <el-table-column
        v-for="(item, index) in columns"
        :key="index"
        :prop="item.dataIndex"
        :label="item.title"
        :width="item.width"
        :fixed="item.fixed"
        :formatter="item.formatter"
      >
        <template slot-scope="scope">
          <template v-if="scope.row.index == tabRowindex && scope.column.index == tabColumnIndex">
            <el-input size="mini" v-model="scope.row[item.dataIndex]" @blur="inputBlur(scope.row)"></el-input>
          </template>
          <div v-else>{{ scope.row[item.dataIndex] }}</div>
        </template>
      </el-table-column> -->
    </el-table>
  </div>
</template>

<script>
import viewServer from "@/api/main/view";
export default {
  props: {
    tableData: {
      type: Array,
      default: []
    },
    queryResultData: {
      type: Object,
      default: {}
    },
    initData: {
      type: Function
    }
  },
  data() {
    return {
      columns: [
        // {
        //   title: "序号",
        //   dataIndex: "columnType"
        // },
        {
          title: "列名称",
          dataIndex: "name"
        },
        {
          title: "数据类型",
          dataIndex: "columnType"
        },
        {
          title: "是否为空",
          dataIndex: "nullable",
          formatter: this.dataFormatter
        }
      ],
      tabRowindex: null,
      tabColumnIndex: null,
      newRowData: {},
      oldRowData: {}
    };
  },
  methods: {
    dataFormatter(row) {
      return row.nullable === 1 ? "否" : "是";
    },
    getRowCloumn({ row, column, rowIndex, columnIndex }) {
      row.index = rowIndex;
      column.index = columnIndex;
    },
    getCell(row, column, cell, event) {
      this.oldRowData = JSON.parse(JSON.stringify(row.name));

      // if (column.label == "列名称") {
      //   this.tabRowindex = row.index;
      //   this.tabColumnIndex = column.index;
      //   this.newRowData = row.name;
      //   this.oldRowData = JSON.parse(JSON.stringify(row.name));
      // }
    },
    inputBlur(row) {
      this.tabRowindex = null;
      this.tabColumnIndex = null;
      const params = {
        dataSourceId: this.queryResultData.uniqueData.dataSourceId,
        schemaName: this.queryResultData.uniqueData.schemaName,
        oldViewName: this.queryResultData.uniqueData.tableName,
        oldColumns: [this.oldRowData],
        newColumns: [row.name]
      };
      viewServer.updateColumnName(params).then(res => {
        if (res.data) {
          this.initData();
          this.$message.success("修改成功!");
        } else {
          this.$message.error("修改失败!");
        }
      });
    }
  }
};
</script>

<style lang='scss' scoped>
.table_btn_list {
  display: flex;
  align-items: center;
  padding: 10px 0;
  .el-button_before::before {
    content: "";
    position: absolute;
    right: -15px;
    top: 8px;
    width: 1px;
    height: 14px;
    background: #68728c;
  }
}
</style>
