<template>
  <div>
    <el-dialog
      :title="title"
      :visible.sync="editDialogVisible"
      :before-close="handleClose"
      width="600px"
    >
      <span>
        <el-form
          label-position="left"
          label-width="110px"
          :model="form"
          ref="form"
          :rules='rules'
        >
          <el-form-item label="表空间名称" prop="tableSpace">
            <el-input
              placeholder="请输入表空间名称"
              size="mini"
              v-model="form.tableSpace"
            >
            </el-input>
          </el-form-item>
          <el-form-item label="Datafile name" prop="path">
            <el-input
              placeholder="请输入内容"
              size="mini"
              style="width: 350px"
              v-model="form.path"
            >
            </el-input>
            <el-button
              type="primary"
              style="margin-left: 12px"
              size="mini"
              @click="addSpace"
              >Find/Copy</el-button
            >
          </el-form-item>

          <el-form-item label="Datafile Size" prop="totalSize">
            <el-input
              v-model="form.totalSize"
              size="mini"
              class="input-with-select"
              type="number"
              min="1"
              oninput="if(value<1){value=1}"
            >
              <el-select
                v-model="form.sizeUnit"
                slot="append"
                placeholder="请选择"
                style="width: 100px"
              >
                <el-option
                  v-for="item in dict.type.data_byte_type"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                >
                </el-option>
              </el-select>
            </el-input>
          </el-form-item>
          <el-form-item>
            <el-checkbox v-model="autoCheck">Auto Extend</el-checkbox>
          </el-form-item>
          <el-form-item label="Additional Space">
            <el-input
              :disabled="autoCheck"
              v-model="form.autoSize"
              size="mini"
              class="input-with-select"
              type="number"
              min="0"
              max="2048"
              oninput="if(value<0){value=0};if(value>2048){value=2048}"
            >
              <el-select
                :disabled="autoCheck"
                v-model="form.autoSizeUnit"
                slot="append"
                placeholder="请选择"
                style="width: 100px"
              >
                <el-option
                  v-for="item in dict.type.data_byte_type"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                >
                </el-option>
              </el-select>
            </el-input>
          </el-form-item>
          <el-form-item>
            <el-checkbox v-model="checked" :disabled="autoCheck"
              >Maximum size unlimited</el-checkbox
            >
          </el-form-item>
          <el-form-item label="Maximum Size">
            <el-input
              v-model="form.expandUpperLimit"
              :disabled="autoCheck || checked"
              size="mini"
              class="input-with-select"
              type="number"
              min="128"
              
            >
              <el-select
                v-model="form.maxSizeUnit"
                :disabled="autoCheck || checked"
                slot="append"
                placeholder="请选择"
                style="width: 100px"
              >
                <el-option
                  v-for="item in dict.type.data_byte_type"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                >
                </el-option>
              </el-select>
            </el-input>
          </el-form-item>
        </el-form>
      </span>
      <span slot="footer" class="dialog-footer">
        <el-button @click="editHandleCloseFn(false)" size="mini"
          >取 消</el-button
        >
        <el-button type="primary" @click="saveTableSpace" size="mini"
          >确 定</el-button
        >
      </span>
    </el-dialog>
    <!-- 文件选择 -->
    <el-dialog
      title="Select a file name"
      :visible.sync="dialogSpace"
      width="800px"
      style="width: 100%"
      height="500"
    >
      <span>
        <el-table
          :data="tableData"
          highlight-current-row
          @current-change="handleCurrentChange"
          style="width: 100%"
          height="410"
          border
        >
          <el-table-column
            prop="name"
            label="表空间名称"
            width="180"
            show-overflow-tooltip
          >
          </el-table-column>
          <el-table-column prop="path" label="文件名称" show-overflow-tooltip>
          </el-table-column>
        </el-table>
        <!-- <div style="margin-top: 10px; float: right"> -->
        <pagination
          v-show="total > 0"
          :total="total"
          :page.sync="queryParams.pageNo"
          :limit.sync="queryParams.pageSize"
          @pagination="getList"
        />
        <!-- </div> -->
      </span>
      <span slot="footer" class="dialog-footer">
        <el-button size="mini" @click="dialogSpace = false">取 消</el-button>
        <el-button type="primary" size="mini" @click="submit">确 定</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import tableSpaceServer from "@/api/main/tableSpace";
export default {
  dicts: ["data_byte_type"],
  props: {
    editDialogVisible: {
      type: Boolean,
      default: false,
    },
    schema: {
      type: String,
      default: "",
    },
    saveType: {
      type: Number,
      default: "1",
    },
    getTableDataList: {
      type: Function,
    },
    dataBaseInfo: {
      type: Object,
      default: {},
    },
    spaceInfo: {
      type: Object,
      default: {},
    },
  },
  data() {
    return {
      title: "新增表空间",
      rules: {
        tableSpace: [{ required: true, message: "请输入表空间名称", trigger: "blur" }],
        path:[{ required: true, message: "请选择路径", trigger: "blur" }],
        totalSize:[{ required: true, message: "请输入大小", trigger: "blur" }]
      },
      form: {
        path: "",
        tableSpace: "",
        totalSize: "128",
        sizeUnit: "",
        autoSize: "",
        autoSizeUnit: "",
        expandUpperLimit: "",
        maxSizeUnit: "",
        isUpdate: false,
      },
      oldTableSpace: {},
      tableData: [],
      total: 1,
      checked: false,
      autoCheck: false,
      dialogSpace: false,
      selectTable: {},
      queryParams: {
        pageNo: 1,
        pageSize: 10,
      },
      dataSourceId: "",
    };
  },
  watch: {
    editDialogVisible(newVal) {
      if (newVal) {
        if (this.saveType == 2) {
          this.form = JSON.parse(JSON.stringify(this.spaceInfo));
          this.oldTableSpace = JSON.parse(JSON.stringify(this.spaceInfo));
          this.form.path = this.oldTableSpace.path;
          this.form.tableSpace = this.oldTableSpace.tableSpace;
          this.form.totalSize = this.oldTableSpace.totalSize;
          this.form.sizeUnit = this.oldTableSpace.sizeUnit;
          this.form.autoSize = this.oldTableSpace.autoSize;
          this.form.autoSizeUnit = this.oldTableSpace.autoSizeUnit;
          this.form.expandUpperLimit = this.oldTableSpace.expandUpperLimit;
          this.form.maxSizeUnit = this.oldTableSpace.maxSizeUnit;
          this.checked = this.form.checked;
          this.autoCheck = this.form.autoCheck;
          this.dataSourceId = this.dataBaseInfo.dataSourceId;
          this.form.isUpdate = true;
          if(this.form.expandUpperLimit == '67108863') {
            this.checked = true;
          } else {
            this.checked = false
          }
        } else {
          this.dataSourceId = this.dataBaseInfo.dataSourceId;
        }
        this.title = this.form.title;
      }
    },
  },
  created() {
    this.form.sizeUnit = "MB";
    this.form.autoSizeUnit = "GB";
    this.form.maxSizeUnit = "MB";
  },
  methods: {
    editHandleCloseFn(val) {
      this.form.path = "";
      this.form.tableSpace = "";
      this.form.totalSize = "";
      this.form.sizeUnit = "";
      this.form.autoSize = "";
      this.form.autoSizeUnit = "";
      this.form.expandUpperLimit = "";
      this.form.maxSizeUnit = "";
      this.form.isUpdate = false;
      this.$emit("editHandleCloseFn", val);
    },
    handleClose() {
      this.form.path = "";
      this.form.tableSpace = "";
      this.form.totalSize = "";
      this.form.sizeUnit = "";
      this.form.autoSize = "";
      this.form.autoSizeUnit = "";
      this.form.expandUpperLimit = "";
      this.form.maxSizeUnit = "";
      this.form.isUpdate = false;
      this.$emit("editHandleCloseFn", false);
    },
    addSpace() {
      this.dialogSpace = true;
      this.getList();
    },
    getList() {
      this.queryParams.dataSourceId = this.dataBaseInfo.id || this.dataSourceId;
      tableSpaceServer.getTableSpaceFileName(this.queryParams).then((res) => {
        if (res.success) {
          this.tableData = res.data.data;
          this.total = res.data.total;
        }
      });
    },
    handleCurrentChange(val) {
      console.log(val);
      this.selectTable = val;
    },
    submit() {
      this.form.path = this.selectTable.path;
      this.dialogSpace = false;
    },
    saveTableSpace: function () {
      this.$refs.form.validate((valid) => {
        if (valid) {
          if (
            (this.expandUpperLimit != undefined ||
              this.expandUpperLimit != "" ||
              this.expandUpperLimit != "") &&
            this.expandUpperLimit < this.totalSize
          ) {
            this.$modal.msgError("最大值需大于表空间大小！");
            return;
          }
          this.form.checked = this.checked;
          this.form.autoCheck = this.autoCheck;
          // if (this.form.autoCheck) {
          //   this.form.autoSize = "0";
          // }
          if (this.form.checked) {
            this.form.expandUpperLimit = "0";
          }
          if (this.form.isUpdate) {
            const params = {
              dataSourceId: this.dataSourceId,
              newTableSpace: this.form,
              oldTableSpace: this.oldTableSpace,
            };
            tableSpaceServer.updateTableSpace(params).then((res) => {
              if (res.success) {
                this.$modal.msgSuccess("修改成功");
                this.$emit('refshTableData',this.form.path);
                this.editHandleCloseFn(false);
              } else {
                this.$message.error(res.errorCode)
              }
            });
          } else {
            this.form.dataSourceId = this.dataSourceId || this.dataBaseInfo.id;
            tableSpaceServer.createTableSpace(this.form).then((res) => {
              if (res.success) {
                this.$modal.msgSuccess("新增成功");
                this.getTableDataList(this.schema);
                this.editHandleCloseFn(false);
              } else {
                this.$message.error(res.errorCode);
              }
            });
          }
        }
      });
    },
    changeFn() {
      console.log(this.memorySize);
    },
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
  margin-bottom: 18px;
}
</style>
