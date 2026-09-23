<script>
import viewServer from "@/api/main/view"
import {v4 as uuidv4} from "uuid";
import {downloadFile} from "@/utils/file";
import { getToken } from '@/utils/auth'
export default {
  name: 'viewAllView',
  props: {
    uniqueData: {
      type: Object,
      default: {}
    }
  },
  data() {
    return {
      timer: null,
      tableLoading: false,
      searchKey: '',
      tableData: [],
      tableDataTotal: 0,
      databaseName: '',
      schemaName: '',
      dataSourceId: '',
      queryParams: {
        pageNo: 1,
        pageSize: 1000,
      },
      multipleSelection: [],
    }
  },
  watch: {
    uniqueData: {
      handler(newVal) {
        this.getTable()
        this.databaseName= newVal.databaseName
        this.schemaName= newVal.schemaName
        this.dataSourceId = newVal.dataSourceId
      },
      immediate: true,
      deep: true
    }
  },
  methods: {
    getTable(params = {}) {
      this.tableLoading = true
      viewServer.getViewList({
        ...this.uniqueData,
        ...this.queryParams,
        ...params,
      })
        .then((res) => {
          this.tableDataTotal = res.data.total
          const data = res.data.data.map((t) => {
            const key = uuidv4();
            return {
              uuid: key,
              name: t.name,
              treeNodeType: 'table',
              key: t.name,
              pinned: t.pinned,
              comment: t.comment,
              extraParams: {
                ...this.uniqueData,
                tableName: t.name,
              },
            };
          });
          this.tableData = data
        })
        .finally(() => {
          this.tableLoading = false
        });
    },
    createView() {
      this.$store.dispatch('addWorkspaceTab', {
        id: uuidv4(),
        title: '新建视图',
        type: 'createView',
        uniqueData: {
          ...this.uniqueData,
        }
      })
    },

    submit() {
      if (this.multipleSelection.length>0){
        let send = {
          dataSourceId: this.dataSourceId,
          databaseName: this.databaseName,
          schemaName: this.schemaName,
          tableName: this.multipleSelection.map(item => item.name).join(',')
        }
        viewServer.allExecute(send)
          .then(res => {
            (res.data || []).forEach(result => {
              const row = this.tableData.find(item => item.name === result.tableName)
              if (row) row.comment = result.success ? '编译成功' : '编译失败'
              this.$notify({
                title: result.success ? '成功' : '失败',
                message: result.message,
                type: result.success ? 'success' : 'warning'
              })
            })
          })
      }else{
        this.$message.error("未选择编译数据")
      }

    },


    refresh() {
      this.queryParams.pageNo = 1
      this.queryParams.pageSize = 1000
      this.getTable({refresh: true})
    },
    onSearch() {
      this.queryParams.pageNo = 1
      this.queryParams.pageSize = 1000
      this.getTable({searchKey: this.searchKey})
    },
    handleSizeChange(val) {
      this.queryParams.pageNo = 1
      this.queryParams.pageSize = val
      this.getTable()
    },
    handleCurrentChange(val) {
      this.queryParams.pageNo = val
      this.getTable()
    },
    handleSelectionChange(val) {
      this.multipleSelection = val;
    },

    addClass({ row, column, rowIndex, columnIndex }) {
      // console.log(row.czjd);
      let style = "";
      switch (row.comment) {
        case "编译成功":
            style = "background: #67C23A;";
          break;
        case "编译失败":
          style = "background: #F56C6C;";
          break;
      }
      return style;
    }
    // 多选框选中数据
    // handleSelectionChange(selection) {
    //   this.ids = selection.map(item => item.wtbh);
    //   this.single1 = selection.length<=0
    //   this.single = selection.length !== 1;
    //   this.multiple = !selection.length;
    // },
  }
}
</script>

<template>
<div class="table_box">
  <div class="table_btn_list">
    <el-button @click="createView" size="mini" type="text" class="el-button_before" style="margin-left: 0">
      <img src="@/assets/main/3-con-ico01.png" alt="">
      添加视图
    </el-button>
    <el-button @click="refresh" size="mini" type="text">
      <img src="@/assets/main/5-ico3.png" alt="">
      刷新
    </el-button>
    <el-button @click="submit"  icon="el-icon-caret-right" size="mini" type="text">
      编译
    </el-button>
<!--    <el-input-->
<!--      v-model="searchKey"-->
<!--      size="mini"-->
<!--      placeholder="搜索"-->
<!--      style="width: 200px;margin: 0 5px"-->
<!--    />-->
<!--    <el-button @click="onSearch" size="mini" type="primary">-->
<!--      <img src="@/assets/main/5-ico4.png" alt="">-->
<!--      查询-->
<!--    </el-button>-->
  </div>
  <el-table
    :data="tableData"
    v-loading="tableLoading"
    size="mini"
    style="width: 100%"
    height="calc(100% - 80px)"
    @selection-change="handleSelectionChange"
    :cell-style="addClass">
    <el-table-column
      type="selection"
      width="55">
    </el-table-column>
    <el-table-column prop="name" label="视图名称">
    </el-table-column>
    <el-table-column prop="comment" label="备注">
    </el-table-column>
  </el-table>
  <div class="table_bottom_box">
    <el-pagination
      @size-change="handleSizeChange"
      @current-change="handleCurrentChange"
      :current-page="queryParams.pageNo"
      :page-sizes="[10, 50, 100, 200, 500, 1000]"
      :page-size="queryParams.pageSize"
      layout="total, sizes, prev, pager, next, jumper"
      :total="tableDataTotal">
    </el-pagination>
  </div>
</div>
</template>

<style scoped lang="scss">
.table_box {
  height: calc(100% - 58px);
  background: #fff;
  padding: 0 20px;
  border-radius: 5px;
  .table_btn_list {
    display: flex;
    align-items: center;
    padding: 10px 0;
    ::v-deep .el-button {
      & > span{
        display: flex;
        align-items: center;
        img {
          margin-right: 6px;
        }
      }
    }
    .el-button_before::before {
      content: '';
      position: absolute;
      right: -15px;
      top: 8px;
      width: 1px;
      height: 14px;
      background: #68728C;
    }
  }
  .table_bottom_box {
    display: flex;
    align-items: center;
    justify-content: end;
    .table_bottom_info {
      font-size: 12px;
      color: rgba(35, 36, 41, 0.88);
      & > span {
        margin-right: 16px;
      }
    }
  }
}
.errorMessageList {
  list-style: none;
  line-height: 22px;
  font-size: 14px;
  padding-left: 0;
}
.left_right_title {
  display: flex;
  justify-content: space-between;
}
/* 隐藏上传文件列表 */
.hide-file-list ::v-deep .el-upload-list { display: none; }
</style>
