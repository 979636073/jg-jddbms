<template>
  <div>
    <el-dialog
      title="新增视图"
      :visible.sync="viewDialogVisible"
      :before-close="handleClose"
      width="600px"
    >
      <span>
        <el-form
          label-position="left"
          label-width="70px"
          :rules="rules"
          ref="searchParams"
          :model="searchParams"
        >
          <!-- <el-form-item label="视图名称" prop="name">
            <el-input
              placeholder="请输入内容"
              size="mini"
              v-model="searchParams.name"
            >
            </el-input>
          </el-form-item> -->
          <el-form-item label="SQL语句" prop="textarea">
            <el-input
              type="textarea"
              :rows="8"
              placeholder="请输入内容"
              v-model="searchParams.textarea"
            >
            </el-input>
          </el-form-item>
        </el-form>
      </span>
      <span slot="footer" class="dialog-footer">
        <el-button size="mini" @click="viewCloseFn(false)">取 消</el-button>
        <el-button type="primary" size="mini" @click="addViewFn"
          >确 定</el-button
        >
      </span>
    </el-dialog>
  </div>
</template>

<script>
import viewServer from "@/api/main/view";
export default {
  props: {
    viewDialogVisible: {
      type: Boolean,
      default: false,
    },
    dataBaseInfo: {
      type: Object,
      default: {},
    },
    schema: {
      type: String,
      default: "",
    },
    getTableDataList: {
      type: Function,
    },
    uploadFormInfo: {
      type: Object,
    },
  },
  data() {
    return {
      searchParams: {
        // name: "",
        textarea: "",
      },
      rules: {
        // name: [{ required: true, message: "请输入视图名称", trigger: "blur" }],
        textarea: [{ required: true, message: "请输入SQL", trigger: "blur" }],
      },
    };
  },
  watch: {
    uploadFormInfo: {
      handler(val) {
        this.searchParams = val;
      },
    },
    deep: true,
  },
  mounted() {
    if (this.uploadFormInfo && Object.keys(this.uploadFormInfo).length) {
      this.searchParams = this.uploadFormInfo;
    }
  },
  methods: {
    viewCloseFn(val) {
      this.searchParams.name = "";
      this.searchParams.textarea = "";
      this.$emit("viewCloseFn", val);
    },
    handleClose() {
      this.searchParams.name = "";
      this.searchParams.textarea = "";
      this.$emit("viewCloseFn", false);
    },
    addViewFn() {
      this.$refs.searchParams.validate((valid) => {
        if (valid) {
          const params = {
            dataSourceId: this.dataBaseInfo.id,
            schemaName: this.schema,
            // tableName: this.searchParams.name,
            viewSql: this.searchParams.textarea,
          };
          viewServer.addViewFn(params).then((res) => {
            if (res.success) {
              if (
                this.uploadFormInfo &&
                Object.keys(this.uploadFormInfo).length
              ) {
                this.$message.success("导入成功");
              }
              if (res.data[0].success) {
                this.$message.success("新建成功");
                this.getTableDataList(this.schema);
                this.viewCloseFn(false);
              } else {
                this.$message.error("新建失败");
              }
            } else {
              this.$message.error(res.errorMessage);
            }
          });
        }
      });
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
  margin-bottom: 19px;
}
</style>
