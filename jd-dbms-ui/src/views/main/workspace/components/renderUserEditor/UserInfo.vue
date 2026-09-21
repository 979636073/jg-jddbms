<script>
import tableSpaceServer from "@/api/main/tableSpace";
import userServer from "@/api/main/user";
import SqlPreview from "@/views/main/workspace/components/DataSource/SqlPreview.vue";
import sqlServer from "@/api/main/sql";

export default {
  name: "UserInfo",
  props: {
    basicForm: {
      type: Object,
    },
    currentConfig: {
      type: Object,
    },
    initData: {
      type: Function,
    },
  },
  components: {
    SqlPreview,
  },
  data() {
    return {
      isLock:false,
      passwordType: "password",
      passwordType2: "password",
      defaultTableSpaceList: [],
      tempTableSpaceList: [],
      dialogVisible: false,
      updatePassword: "",
      fromData: {
        password: "",
        secondPassword: "",
      },
    };
  },
  mounted() {
    this.getTableSpaceList();
    this.isLock = this.basicForm.accountStatus == 'OPEN'
  },
  methods: {
    showPwd(id) {
      if (id == 1) {
        if (this.passwordType === "password") {
          this.passwordType = "";
        } else {
          this.passwordType = "password";
        }
        this.$nextTick(() => {
          this.$refs.password.focus();
        });
      } else {
        if (this.passwordType2 === "password") {
          this.passwordType2 = "";
        } else {
          this.passwordType2 = "password";
        }
        this.$nextTick(() => {
          this.$refs.password2.focus();
        });
      }
    },
    getTableSpaceList() {
      const params = {
        dataSourceId: this.currentConfig?.uniqueData.dataSourceId,
        requestType: 0,
      };

      tableSpaceServer.getTableSpaceNameList(params).then((res) => {
        if (res.success) {
          this.defaultTableSpaceList = res.data;
        }
      });
      params.requestType = 1;
      tableSpaceServer.getTableSpaceNameList(params).then((res) => {
        if (res.success) {
          this.tempTableSpaceList = res.data;
        }
      });
    },
    editUser() {
      const parmas = {
        dataSourceId: this.currentConfig?.uniqueData.dataSourceId,
        userName: this.basicForm.name,
        defaultTableSpace: this.basicForm.defaultTableSpace,
        tempTableSpace: this.basicForm.tempTableSpace,
      };
      userServer.editUsermodify(parmas).then((res) => {
        if (res.code == 200) {
          this.initData();
          this.$message.success("修改成功");
        } else {
          this.$message.error(res.errorCode);
        }
      });
    },
    lockUser() {
      const parmas = {
        dataSourceId: this.currentConfig?.uniqueData.dataSourceId,
        lockName: this.basicForm.name,
        isLock:this.isLock
      };
      userServer.lockUser(parmas).then((res) => {
        if (res.success) {
          this.initData();
          this.$message.success("操作成功");
        } else {
          this.$message.error(res.errorCode);
        }
      });
    },
    editPassword() {
      this.dialogVisible = true;
    },
    submitSave() {
      if (!this.fromData.password) {
        return this.$message.error("请输入新的密码!");
      }else if (!this.fromData.secondPassword) {
        return this.$message.error("请输入确认密码!");
      }
      if(JSON.stringify(this.fromData.password) !== JSON.stringify(this.fromData.secondPassword) ){
        this.showPwd(1)
        this.showPwd(2)
        return this.$message.error("两次输入的密码不一致!");
      }
      const params = {
        dataSourceId: this.currentConfig?.uniqueData.dataSourceId,
        userName: this.basicForm.name,
        newPassWord: this.fromData.password,
      };
      userServer.upatePassword(params).then((res) => {
        if (res.success) {
          this.$message.success("修改成功!");
          this.cancel();
        } else {
          this.$message.error("修改失败!");
        }
      });
    },
    cancel() {
      this.dialogVisible = false;
      this.fromData = {
         password: "",
        secondPassword: "",
      };
    },
    // executeUpdateDataSql() {
    //   let sql = this.$refs.sqlPreview.getValue();
    //   const params = {
    //     dataSourceId: this.currentConfig?.uniqueData.dataSourceId,
    //     databaseName: this.currentConfig?.uniqueData.databaseName,
    //     databaseType: this.currentConfig?.uniqueData.databaseType,
    //     sql: sql
    //   };
    //   sqlServer.executeDDL(params).then(res => {
    //     if (res.success) {
    //       if (res.data.success) {
    //         this.$message.success("执行成功");
    //         this.$refs.sqlPreview.dialogClose();
    //         this.cancel()
    //       } else {
    //         this.$notify.error({
    //           title: "错误",
    //           message: res.data.message
    //         });
    //       }
    //     } else {
    //       this.$message.error("系统发生异常!");
    //     }
    //   })
    // }
  },
};
</script>

<template>
  <div style="continer">
    <div class="row_box">
      <div class="item_box">
        <img src="@/assets/main/1-7-01.png" alt="" />
        <div class="title_box">
          <span style="color: #68728c">用户ID</span>
          <div>{{ basicForm.userId }}</div>
        </div>
      </div>
      <span style="color: #c4cddf">|</span>
      <div class="item_box">
        <img src="@/assets/main/1-7-03.png" alt="" />
        <div class="title_box">
          <span style="color: #68728c">创建时间</span>
          <div>{{ basicForm.created }}</div>
        </div>
      </div>
      <span style="color: #c4cddf">|</span>
      <div class="item_box">
        <img src="@/assets/main/1-7-04.png" alt="" />
        <div class="title_box">
          <span>账号状态</span>
          <div>{{ basicForm.accountStatus }}</div>
        </div>
      </div>
    </div>
    <el-form
      ref="basicForm"
      size="mini"
      :model="basicForm"
      label-width="100px"
      class="basicForm"
    >
      <el-form-item label="用户名">
        <el-input v-model="basicForm.name" disabled></el-input>
      </el-form-item>
      <el-form-item label="默认表空间" style="margin-left: 15px">
        <el-select
          v-model="basicForm.defaultTableSpace"
          size="mini"
          placeholder="请选择表空间"
        >
          <el-option
            v-for="item in defaultTableSpaceList"
            :key="item.id"
            :label="item.name"
            :value="item.name"
          ></el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="临时表空间" style="margin-left: 15px">
        <el-select
          v-model="basicForm.tempTableSpace"
          size="mini"
          placeholder="请选择表空间"
        >
          <el-option
            v-for="item in tempTableSpaceList"
            :key="item.id"
            :label="item.name"
            :value="item.name"
          ></el-option>
        </el-select>
      </el-form-item>
      <el-form-item style="margin-left: 15px">
       <el-switch
       @change="lockUser"
          style="display: block"
          v-model="isLock"
          active-color="#13ce66"
          inactive-color="#ff4949"
          active-text="解锁"
          inactive-text="加锁">
        </el-switch>
      </el-form-item>
       <el-form-item style="margin-left: 15px">
        <el-button type="primary" size="mini" @click="editPassword"
          >修改密码</el-button
        >
      </el-form-item>
    </el-form>
    <el-dialog
      title="修改密码"
      :visible.sync="dialogVisible"
      width="30%"
      :before-close="cancel"
    >
      <span>
        <el-form
          ref="fromData"
          size="mini"
          :model="fromData"
          label-width="80px"
        >
          <el-form-item label="密     码">
            <el-input
              style="width: 280px"
              placeholder="密码"
              :key="passwordType"
              :type="passwordType"
              auto-complete="off"
              name="password"
              ref="password"
              v-model="fromData.password"
            >
              <span class="show-pwd" @click="showPwd(1)" slot="suffix">
                <svg-icon
                  :icon-class="passwordType === 'password' ? 'eye' : 'eye-open'"
                />
              </span>
            </el-input>
          </el-form-item>
          <el-form-item label="确认密码">
            <el-input
              style="width: 280px"
              placeholder="确认密码"
              :key="passwordType2"
              :type="passwordType2"
              auto-complete="off"
              name="password"
              ref="password2"
              v-model="fromData.secondPassword"
            >
              <span class="show-pwd" @click="showPwd(2)" slot="suffix">
                <svg-icon
                  :icon-class="passwordType2 === 'password' ? 'eye' : 'eye-open'"
                />
              </span>
            </el-input>
          </el-form-item>
        </el-form>
      </span>
      <span slot="footer" class="dialog-footer">
        <el-button size="small" @click="cancel">取 消</el-button>
        <el-button size="small" type="primary" @click="submitSave"
          >确 定</el-button
        >
      </span>
    </el-dialog>
    <!-- <SqlPreview ref="sqlPreview" title="sql预览">
      <el-button
        style="position: absolute; right: 60px; top: 12px"
        @click="executeUpdateDataSql"
        size="mini"
        type="primary"
        plain
      >
        <img src="@/assets/main/1-con-ico22.png" alt />
        执行
      </el-button>
    </SqlPreview> -->
  </div>
</template>

<style scoped lang="scss">
.item_box {
  width: 200px;
  height: 120px;
  display: flex;
  align-items: center;
  .title_box {
    width: 100%;
    text-align: center;
    margin-left: 15px;
    > span {
      font-size: 12px;
    }
    > div {
      white-space: nowrap;
      margin-top: 5px;
      color: #222427;
      font-weight: bold;
    }
  }
}
.row_box {
  width: 100%;
  height: 100px;
  display: flex;
  align-items: center;
  justify-content: space-around;
  background: #f2f3fa;
}
.basicForm {
  width: 100%;
  height: 100px;
  display: flex;
  align-items: center;
}
::v-deep .el-form-item__label {
  margin-top: 15px;
  color: #828a9e;
  font-weight: normal;
  font-size: 12px;
}
</style>
