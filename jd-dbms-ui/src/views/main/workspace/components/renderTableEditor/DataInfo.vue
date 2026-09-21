<script>
export default {
  props: {
    queryResultData: {
      type: Object,
      default: {},
    },
    dataTable: {
      type: Array,
      default: [],
    },
  },
  name: "DataInfo",
  data() {
    return {
      // 当前修改行
      tableRowEditKey: "",
      multipleSelection:[],
    };
  },
  mounted(){
    //  this.$EventBus.$on('type',(val)=>{
    //    console.log(val,'val');
    //  })
  },
  methods: {
    handleSelectionChange(val) {
      this.multipleSelection = val;
      console.log(this.multipleSelection)
    },
    delRow(row) {

    }
  },
};
</script>

<template>
  <div class="draggable">
    <div class="table_btn_list">
      <el-button
        size="mini"
        type="text"
        class="el-button_before"
        style="margin-left: 0"
      >
        <img src="@/assets/main/3-con-ico01.png" alt="" />
        添加
      </el-button>
      <el-button size="mini" type="text" :disabled="!multipleSelection.length" @click="delRow">
        <img src="@/assets/main/3-con-ico02.png" alt="" />
        删除
      </el-button>
      <el-button size="mini" type="text">
        <img src="@/assets/main/5-ico3.png" alt="" />
        提交
      </el-button>
      <el-button size="mini" type="text">
        <img src="@/assets/main/5-ico3.png" alt="" />
        回退
      </el-button>
      <el-button size="mini" type="text" :disabled="!multipleSelection.length">
        <img src="@/assets/main/3-con-ico05.png" alt="" />
        导入
      </el-button>
      <el-button size="mini" type="text" :disabled="!multipleSelection.length">
        <img src="@/assets/main/3-con-ico05.png" alt="" />
        导出
      </el-button>
    </div>
    <el-table
      size="mini"
      :data="dataTable"
      row-key="key"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="55"></el-table-column>
      <el-table-column
        width="180px"
        v-for="header in queryResultData.headerList"
        :key="header.name"
        :prop="header.name"
        :label="header.name === '行号' ? '' : header.name"
        show-overflow-tooltip
        :sortable="header.name !== '行号'"
      ></el-table-column>
    </el-table>
  </div>
</template>

<style scoped lang="scss">
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
.draggable {
  height: 100%;
}
.keyBox {
  width: 26px;
  height: 26px;
  display: flex;
  justify-content: center;
  align-items: center;
  cursor: pointer;
  position: relative;
  i {
    color: #d89614;
  }
  span {
    position: absolute;
    font-weight: bold;
    right: 4px;
    bottom: -5px;
    transform: scale(0.8);
  }
}
</style>
