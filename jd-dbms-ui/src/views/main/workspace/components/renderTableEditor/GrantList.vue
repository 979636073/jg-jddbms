<script>
export default {
  name: "GrantList",
  props: {
    tableData: {
      type: Array,
      default:()=>[]
    },
  },
  data() {
    return {
      //  当前修改行
      tableRowEditKey: "",
      columns: [
        {
          title: "被授权者",
          dataIndex: "grantee",
        },
        {
          title: "授权表",
          dataIndex: "tableName",
        },
        {
          title: "权限",
          dataIndex: "privilege",
        },
        {
          title: "是否可转让授权",
          dataIndex: "grantAble",
        },
        {
          title: "权限拥有者",
          dataIndex: "grantor",
        },
      ],
    };
  },
  methods: {},
};
</script>

<template>
  <div class="draggable">
    <el-table size="mini" :data="tableData" row-key="key">
      <el-table-column
        sortable
        v-for="(item, index) in columns"
        :key="index"
        :prop="item.dataIndex"
        :label="item.title"
        :width="item.width"
        :fixed="item.fixed"
        :formatter="item.formatter"
      >
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped lang="scss">
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
::v-deep {
  .el-table .el-button--text {
    margin: 0;
  }
  .el-table .el-button {
    & > span {
      display: flex;
      align-items: center;
      img {
        margin-right: 6px;
      }
    }
  }
}
</style>