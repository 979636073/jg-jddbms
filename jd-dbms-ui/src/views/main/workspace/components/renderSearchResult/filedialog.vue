<template>
  <el-dialog
    title="文件"
    :visible.sync="dialogVisible"
    width="50%"
    :before-close="handleClose"
  >
    <span>
      <p>文件名：{{ fromData.name }}{{ fromData.type }}</p>
      <el-image
        v-if="isShow"
        :src="imageSrc"
        fit="contain"
        style="width: 100%; height: 40vh; max-height: 420px"
        :preview-src-list="[imageSrc]"
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
      isShow:false,
    };
  },
  computed: {
    imageSrc() {
      const mime = { ".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".gif": "image/gif", ".webp": "image/webp", ".bmp": "image/bmp" }[this.fromData.type] || "image/png";
      return `data:${mime};base64,${this.blobStr}`;
    },
  },
  methods: {
    handleClose() {
      this.dialogVisible = false;
      this.fromData.name = "";
      this.fromData.type = "";
    },
    async downLoad() {
      try {
        await downloadFile(
          process.env.VUE_APP_BASE_API + "/api/rdb/dml/exportFile",
          { blobStr: this.blobStr, fileName: this.fromData.name, prefix: this.fromData.type }
        );
        this.handleClose();
      } catch (error) {
        this.$message.error(error.message || "文件下载失败");
      }
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
