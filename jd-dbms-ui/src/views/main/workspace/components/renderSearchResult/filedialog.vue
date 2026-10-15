<template>
  <el-dialog
    title="文件"
    :visible.sync="dialogVisible"
    width="50%"
    :before-close="handleClose"
  >
    <span>
      <p>文件名：{{ fromData.name }}{{ fromData.type }}</p>
      <p v-if="loading"><i class="el-icon-loading" /> 正在读取预览…</p>
      <el-alert v-else-if="error" :title="error" type="error" :closable="false" />
      <el-alert v-else-if="tooLarge" title="文件超过 8 MiB，已停止预览读取，请下载后查看" type="info" :closable="false" />
      <el-tag v-else-if="nullValue">NULL，没有文件</el-tag>
      <el-image
        v-else-if="isShow"
        :src="imageSrc"
        fit="contain"
        style="width: 100%; height: 40vh; max-height: 420px"
        :preview-src-list="[imageSrc]"
     />
     <el-tag v-else>{{ blobStr === '' ? '空文件（0 字节）' : '请下载后预览' }}</el-tag>
     <p v-if="downloadLoading">已下载 {{ (downloadedBytes / 1024 / 1024).toFixed(2) }} MiB</p>
    </span>
    <span slot="footer" class="dialog-footer">
      <el-button @click="handleClose">{{ loading || downloadLoading ? '取消读取' : '关闭' }}</el-button>
      <el-button type="primary" :loading="downloadLoading" :disabled="loading || !!error || nullValue" @click="downLoad">下 载</el-button>
    </span> 
  </el-dialog>
</template>

<script>
import { downloadFile, downloadBinaryFile, binaryFileType } from "@/utils/file";
import tableServer from "@/api/main/table";
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
      loading: false,
      downloadLoading: false,
      downloadedBytes: 0,
      tooLarge: false,
      nullValue: false,
      error: "",
      readRequest: null,
    };
  },
  computed: {
    imageSrc() {
      const mime = { ".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".gif": "image/gif", ".webp": "image/webp", ".bmp": "image/bmp" }[this.fromData.type] || "image/png";
      return `data:${mime};base64,${this.blobStr}`;
    },
  },
  beforeDestroy() { this.cancelRead(); },
  methods: {
    cancelRead() {
      if (this._readController) {
        this._readController.abort();
        tableServer.cancelBlob(this._activeRequest).catch(() => {});
        this._readController = null;
      }
      this.loading = false;
      this.downloadLoading = false;
    },
    async open(request, name, localBase64) {
      this.cancelRead();
      this.readRequest = localBase64 === undefined ? request : null;
      this.fromData = { name, type: ".bin" };
      this.blobStr = ""; this.isShow = false; this.tooLarge = false; this.nullValue = false; this.error = "";
      this.dialogVisible = true; this.loading = true;
      const controller = new AbortController();
      this._readController = controller;
      const active = { ...request, readId: crypto.randomUUID() };
      this._activeRequest = active;
      try {
        const result = localBase64 === undefined ? await tableServer.previewBlob(active, controller.signal) :
          { success: true, data: { base64: localBase64 } };
        if (controller.signal.aborted) return;
        if (!result.success) throw new Error(result.errorMessage || "文件预览失败");
        const preview = result.data;
        this.nullValue = !!preview.nullValue;
        this.tooLarge = !!preview.tooLarge;
        this.blobStr = preview.base64 || "";
        this.fromData.type = binaryFileType(preview.prefix || preview.base64);
        this.isShow = !this.tooLarge && !this.nullValue && this.blobStr !== "" &&
          [".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp"].includes(this.fromData.type);
      } catch (error) {
        if (!controller.signal.aborted) this.error = error.message || "文件预览失败";
      } finally {
        if (this._readController === controller) { this._readController = null; this.loading = false; }
      }
    },
    handleClose() {
      this.cancelRead();
      this.dialogVisible = false;
      this.fromData.name = "";
      this.fromData.type = "";
      this.blobStr = "";
      this.readRequest = null;
    },
    async downLoad() {
      if (this.downloadLoading) return;
      const controller = new AbortController();
      const request = { ...this.readRequest, readId: crypto.randomUUID(), fileName: this.fromData.name + this.fromData.type };
      this.downloadLoading = true; this.downloadedBytes = 0;
      this._readController = controller; this._activeRequest = request;
      try {
        if (this.readRequest) {
          const fileHandle = typeof window.showSaveFilePicker === "function" ?
            await window.showSaveFilePicker({ suggestedName: request.fileName }) : undefined;
          if (controller.signal.aborted) return;
          await downloadBinaryFile(request, { signal: controller.signal, fileHandle,
            onProgress: count => { this.downloadedBytes = count; } });
        } else await downloadFile(
          process.env.VUE_APP_BASE_API + "/api/rdb/dml/exportFile",
          { blobStr: this.blobStr, fileName: this.fromData.name, prefix: this.fromData.type }
        );
        if (!controller.signal.aborted) this.$message.success("下载完成");
      } catch (error) {
        if (!controller.signal.aborted && error.name !== "AbortError") this.$message.error(error.message || "文件下载失败");
      } finally {
        if (this._readController === controller) { this._readController = null; this.downloadLoading = false; }
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
