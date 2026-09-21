<template>
  <div>
    <el-dialog title="视图导入" :visible.sync="dialogVisible" width="40%" :before-close="handleClose">
      <span>
        <div class="download-box">
          <el-upload
            class="upload-demo"
            ref="commonUpload"
            accept=".sql"
            :action="uploadImgUrl + '/common/upload'"
            :show-file-list="false"
            :headers="customHeaders"
            :on-success="handleAvatarSuccess"
            :before-upload="beforeAvatarUpload"
            :limit="1"
          >
            <div class="uploadBox">
              <div class="background">
                <i class="el-icon-upload"></i>
              </div>
              <div style="margin-left: 10px">
                <div style="font-size: 16px;">上传</div>
                <div style="color: #9da4b4; font-size: 12px; margin-top: 5px;">SQL</div>
              </div>
            </div>
          </el-upload>
          <div class="fileBox" v-show="isFile">
            <div class="background">
              <img src="@/assets/main/file_01.png" alt />
            </div>
            <div style="margin-left: 10px">
              <div style="font-size: 16px">{{ filterObj.originalFilename }}</div>
              <div style="color: #9da4b4; font-size: 12px; margin-top: 5px">{{ filterObj.fileSize }}</div>
            </div>
            <div class="deleteBox" @click="deleteFile">
              <i class="el-icon-close"></i>
            </div>
          </div>
        </div>
      </span>
      <span slot="footer" class="dialog-footer">
        <el-button @click="handleClose" size="small">取 消</el-button>
        <el-button type="primary" @click="save" size="small">确 定</el-button>
      </span>
    </el-dialog>
     <addView
      :viewDialogVisible="viewDialogVisible"
      @viewCloseFn="handleClose"
      :dataBaseInfo="dataBaseInfo"
      :getTableDataList="getTableDataList"
      :uploadFormInfo="uploadFormInfo"
      :key="key"
    ></addView>
  </div>
</template>

<script>
import { getToken } from "@/utils/auth";
import viewServer from "@/api/main/view";
import addView from "./../siedBarDialog/addView.vue";
export default {
  components:{
    addView
  },
  props: {
    dataInfo: {
      type: Object,
      default: {}
    },
    getTableDataList: {
      type: Function
    },
    schema: {
      type: String,
      default: ""
    }
  },
  data() {
    return {
      key:0,
      dataBaseInfo:{},
      uploadFormInfo:{},
      viewDialogVisible:false,
      dialogVisible: false,
      uploadImgUrl: process.env.VUE_APP_BASE_API,
      customHeaders: {
        Authorization: "Bearer " + getToken()
      },
      isFile: false,
      filterObj: {}
    };
  },
  methods: {
    handleClose() {
      this.viewDialogVisible = false
      this.dialogVisible = false;
      this.deleteFile();
    },
    deleteFile() {
      this.isFile = false;
      this.$refs.commonUpload.clearFiles();
      this.filterObj = {};
    },
    handleAvatarSuccess(res) {
      this.filterObj = res;
      if (this.filterObj) {
        this.isFile = true;
      }
    },
    beforeAvatarUpload(file) {
      const isLt50M = file.size / 1024 / 1024 < 50;
      if (!isLt50M) {
        this.$message.error("上传文件大小不能超过 50MB!");
      }
      return isLt50M;
    },
   async save() {
      if (JSON.stringify(this.filterObj) == "{}") {
        return this.$message.error("请先上传文件!");
      }
        // 
      const params = {
        dataSourceId: this.dataInfo.dateSourceId,
        fileName: this.filterObj.fileName
      };
      // 获取解析后的sql
     await viewServer.importViewSQL(params)
      .then(res=>{
        if(res.data){
          this.dataBaseInfo.id = this.dataInfo.dateSourceId
          this.uploadFormInfo.textarea = res.data.trim() || ''
          this.viewDialogVisible = true
          this.key++
        }
      })
      .catch(e=>{
        console.log(e);
      })

      // viewServer.importSqlOptiate(params).then(res => {
      //   if (res.success) {
      //     this.$message.success("上传成功!");
        
      //     this.deleteFile();
      //     this.dialogVisible = false;
      //     this.getTableDataList(this.schema)
      //   } else {
      //     this.$message.error(res.errorCode);
      //   }
      // })
    }
  }
};
</script>

<style lang='scss' scoped>
.download-box {
  display: flex;
}
.uploadBox {
  width: 200px;
  height: 65px;
  border: 1px dashed #e1e6ec;
  background: #f6f9fc;
  border-radius: 3px;
  display: flex;
  align-items: center;
  .background {
    width: 50px;
    height: 50px;
    background: #006fff;
    border-radius: 6px;
    display: flex;
    justify-content: center;
    align-items: center;
    margin-left: 10px;
    .el-icon-upload {
      color: #fff;
      font-size: 30px;
    }
  }
}
.fileBox {
  width: 600px;
  height: 65px;
  border: 1px solid #e1e6ec;
  margin-left: 20px;
  display: flex;
  align-items: center;
  border-radius: 3px;
  position: relative;
  .background {
    width: 50px;
    height: 50px;
    background: #d4e3f6;
    border-radius: 6px;
    display: flex;
    justify-content: center;
    align-items: center;
    margin-left: 10px;
  }
  .deleteBox {
    width: 18px;
    height: 18px;
    background: #ff5733;
    border-radius: 50%;
    position: absolute;
    right: 5px;
    top: 5px;
    cursor: pointer;
    > i {
      display: flex;
      align-items: center;
      justify-content: center;
      color: #fff;
      font-weight: bold;
      margin-top: 2px;
    }
  }
}
</style>