<template>
  <el-dialog
    title="文件"
    :visible.sync="dialogVisible"
    width="50%"
    :before-close="handleClose"
  >
    <span>
      <el-form
        :model="fromData"
        :rules="rules"
        ref="ruleForm"
        label-width="120px"
        class="demo-ruleForm"
        :inline="true"
      >
        <el-form-item label="文件名称" prop="name">
          <el-input
            v-model="fromData.name"
            placeholder="请输入文件名称"
            size="small"
          ></el-input>
        </el-form-item>
        <el-form-item label="文件类型后缀" prop="type">
          <el-input
            v-model="fromData.type"
            size="small"
            placeholder="例:.txt,.sql,.docx,.xlsx等等"
          ></el-input>
        </el-form-item>
      </el-form>
      <el-image
        v-if="isShow"
        :src="'data:image/png;base64,' + blobStr"
        fit="cover"
        :style="`width:${500};height:${600};`"
        :preview-src-list="['data:image/png;base64,' + blobStr]"
     />
     <el-tag v-else>请下载后预览</el-tag>
    </span>
    <span slot="footer" class="dialog-footer">
      <el-button @click="handleClose">取 消</el-button>
      <el-button type="primary" @click="downLoad">下 载</el-button>
    </span> 
  </el-dialog>
</template>

<script>
import { downloadFile } from "@/utils/file";
export default {
  data() {
    return {
      dialogVisible: false,
      fromData: {
        name: "",
        type: "",
      },
      blobStr: "",
      isShow:true,
      rules: {
        name: [{ required: true, message: "请输入文件名称", trigger: "blur" }],
        type: [
          { required: true, message: "请输入后缀", trigger: "blur" },
        ],
      },
    };
  },
  methods: {
    handleClose() {
      this.dialogVisible = false;
      this.fromData.name = "";
      this.fromData.type = "";
    },
    downLoad() {
      this.$refs.ruleForm.validate((valid) => {
        if (valid) {
          const params = {
            blobStr: this.blobStr,
            fileName: this.fromData.name,
            prefix: this.fromData.type,
          };
          console.log(params,'params');
          downloadFile(
            process.env.VUE_APP_BASE_API + "/api/rdb/dml/exportFile",
            params
          );
          this.handleClose();
        }
      });
    },
  },
};
</script>

<style  scoped lang="scss">
::v-deep .el-image {
  display: flex !important;
  justify-content: center
}
</style>