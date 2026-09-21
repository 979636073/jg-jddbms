<template>
  <div>
    <div class="isRadioBox">
      <el-table
        :data="tableData"
        style="width: 100%; margin-bottom: 20px"
        row-key="id"
        default-expand-all
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
      >
        <el-table-column prop="path" label="表空间" show-overflow-tooltip></el-table-column>
        <el-table-column prop="totalSize" label="大小" sortable>
          <template slot-scope="scope">
          <span>{{ scope.row.totalSize + "MB" }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="freeSize" label="空闲" sortable>
          <template slot-scope="scope">
          <span>{{ scope.row.freeSize + "MB" }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="useSize" label="占用">
          <template slot-scope="scope">
          <span>{{ scope.row.useSize + "MB" }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="expandUpperLimit" label="最大值">
          <template slot-scope="scope">
          <span>{{ scope.row.expandUpperLimit + "MB" }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="usageRate" label="最大占用比">
          <template slot-scope="scope">
          <span>{{ scope.row.usageRate + "%" }}</span>
          </template>
        </el-table-column>
        <el-table-column width="250" label="操作">
          <template slot-scope="scope">
            <el-button
              @click.native="editData(scope.row)"
              size="mini"
              type="text"
              plain
            >
              <img src="@/assets/main/3-con-ico03.png" alt="" />
              修改
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <addTableSpaceVue
        :editDialogVisible="editDialogVisible"
        @editHandleCloseFn="editHandleCloseFn"
        @refshTableData='refshTableData'
        :dataBaseInfo="dataBaseInfo"
        :schema="schema"
        :spaceInfo="spaceInfo"
        :saveType="2"
      ></addTableSpaceVue>
    </div>
  </div>
</template>

<script>
import addTableSpaceVue from "@/views/main/workspace/components/siedBarDialog/addTableSpace.vue";
import tableSpaceServer from "@/api/main/tableSpace";
export default {
  name: "",
  components: { addTableSpaceVue },
  props: {
    tableData: {
      type: Array,
      default: [],
    },
    currentConfig: {
      type: Object,
      default: {},
    },
  },
  data() {
    return {
      editDialogVisible: false,
      searchParams: {
        name: "",
        size: "",
        space: "",
        maxSize: "",
      },
      memorySize: "",
      checked: "",
      dataBaseInfo: {},
      schema: "",
      spaceInfo: {},
    };
  },
  methods: {
    batteryFormatter(row) {
      return row.totalSize + "%";
    },
    editData(row) {
      console.log(row);
      row.sizeUnit = "MB";
      row.autoSizeUnit = "GB";
      row.maxSizeUnit = "MB";
      row.title = '修改表空间';
      this.dataBaseInfo.dataSourceId = this.currentConfig?.uniqueData.dataSourceId;
      this.spaceInfo = row;
      this.editDialogVisible = true;
    },
    editHandleCloseFn() {
      this.editDialogVisible = false;
    },
    editFn() {
      this.editDialogVisible = true;
    },
    getTableDataList(path) {
      let send = {
          dataSourceId: this.currentConfig?.uniqueData.dataSourceId,
          databaseName: this.currentConfig?.uniqueData.databaseName,
          path: path,
        };
        tableSpaceServer.getTableSpaceDetails(send).then((res) => {
          this.tableData = [];
          this.tableData.push(res.data.tableDetails);
        });
    },
    refshTableData(val) {
      let send = {
          dataSourceId: this.currentConfig?.uniqueData.dataSourceId,
          databaseName: this.currentConfig?.uniqueData.databaseName,
          path: val,
        };
        tableSpaceServer.getTableSpaceDetails(send).then((res) => {
          this.tableData = [];
          this.tableData.push(res.data.tableDetails);
        });
    }
  },
};
</script>

<style lang='scss' scoped>
::v-deep .el-table--medium .el-table__cell {
  padding: 5px 0;
}
::v-deep .el-dialog .el-dialog__header {
  background: #dfe3f0 !important;
  .el-dialog__title {
    color: #242f57 !important;
  }
}
::v-deep .el-dialog .el-dialog__body {
  padding: 15px 20px;
}
::v-deep .el-form-item__label {
  font-size: 12px;
}
::v-deep .el-form-item {
  margin-bottom: 10px;
}
</style>
