<template>
  <div>
    <el-dialog title="新增用户" :visible.sync="userDialogVisible" :before-close="handleClose" width="800px">
      <span>
        <el-form
          label-position="right"
          label-width="90px"
          :model="userFrom"
          ref="userFrom"
          :rules="rules"
        >
          <el-row>
            <el-col :span="8">
              <el-form-item label="用户名称" class="is-required" prop="userName">
                <el-input
                  placeholder="请输入用户名称"
                  size="mini"
                  v-model="userFrom.userName"
                >
                </el-input>
              </el-form-item>
            </el-col>

            <el-col :span="8"
              ><el-form-item label="密码" class="is-required" prop="newPassWord">
                <el-input
                  placeholder="请输入密码"
                  size="mini"
                  v-model="userFrom.newPassWord"
                  type="password"
                >
                </el-input>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="确认密码" class="is-required" prop="verifyPassword">
                <el-input
                  placeholder="请输入确认密码"
                  size="mini"
                  v-model="userFrom.verifyPassword"
                  type="password"
                >
                </el-input>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row>
            <el-col :span="8">
              <el-form-item label="默认表空间" class="is-required" prop="defaultTableSpace">
                <el-select
                  v-model="userFrom.defaultTableSpace"
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
            </el-col>
            <el-col :span="8">
              <el-form-item label="临时表空间" class="is-required" prop="tempTableSpace">
                <el-select
                  v-model="userFrom.tempTableSpace"
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
            </el-col>
          </el-row>
        </el-form>
      </span>
      <span>
        <el-table
          :data="userRoleList"
          highlight-current-row
          style="width: 100%"
          height="500"
        >
          <el-table-column
            prop="role"
            label="角色名称"
            width="180"
            show-overflow-tooltip
          >
          </el-table-column>
          <el-table-column prop="isGranted" label="Granted">
            <template slot-scope="scope">
              <el-checkbox v-model="scope.row.isGranted" />
            </template>
          </el-table-column>
          <el-table-column prop="isAdmin" label="Admin">
            <template slot-scope="scope">
              <el-checkbox v-model="scope.row.isAdmin" />
            </template>
          </el-table-column>
          <el-table-column prop="isDefault" label="Default">
            <template slot-scope="scope">
              <el-checkbox v-model="scope.row.isDefault" />
            </template>
          </el-table-column>
        </el-table>
        <!-- <pagination
          v-show="total > 0"
          :total="total"
          :page.sync="queryParams.pageNo"
          :limit.sync="queryParams.pageSize"
          @pagination="getUserRoleList"
        /> -->
      </span>
      <span slot="footer" class="dialog-footer">
        <el-button @click="userHandleCloseFn(false)" size="mini"
          >取 消</el-button
        >
        <el-button type="primary" @click="saveUser" size="mini"
          >确 定</el-button
        >
      </span>
    </el-dialog>
  </div>
</template>

<script>
import userServer from "@/api/main/user";
import tableSpaceServer from "@/api/main/tableSpace";
export default {
  // dicts: ["data_byte_type"],
  props: {
    userDialogVisible: {
      type: Boolean,
      default: false,
    },
    schema: {
      type: String,
      default: "",
    },
    getTableDataList: {
      type: Function,
    },
    getDataBaseTreeData:{
      type:Function
    },
    dataBaseInfo: {
      type: Object,
      default: {},
    },
    userInfo: {
      type: Object,
      default: {},
    },
  },
  data() {
    var userName = (rule, value, callback) => {
      var regex = /^\d+$/;
      if (value == "") {
        callback(new Error("用户名不能为空"));
      } else if (regex.test(value)) {
        callback(new Error("用户名不可以纯数字"));
      } else {
        callback();
      }
    };
    var validatePass2 = (rule, value, callback) => {
      if (value === "") {
        callback(new Error("请再次输入密码"));
      } else if (value !== this.userFrom.newPassWord) {
        callback(new Error("两次输入密码不一致!"));
      } else {
        callback();
      }
    };
    return {
      userFrom: {
        userName: "",
        newPassWord: "",
        verifyPassword: "",
        defaultTableSpace: "",
        tempTableSpace: "",
      },
      defaultTableSpaceList: [],
      tempTableSpaceList: [],
      userRoleList: [],
      total: 1,
      checked: false,
      autoCheck: false,
      selectTable: {},
      queryParams: {
        pageNo: 1,
        pageSize: 10,
      },
      rules: {
        verifyPassword: [{ validator: validatePass2, trigger: "blur" }],
        userName: [{ validator: userName, trigger: "blur" }],
        newPassWord:[{ min: 9, message: '密码长度不少于 9 个字符', trigger: 'blur' }],
        defaultTableSpace:[{ required: true, message: '请选择默认表空间', trigger: 'blur' }],
        tempTableSpace:[{ required: true, message: '请选择临时表空间', trigger: 'blur' }]
      },
    };
  },
  watch: {
    userDialogVisible(newVal) {
      if(newVal) {
       this.getTableSpaceList();
       this.getUserRoleList();
      }
    },
  },
  methods: {
    getTableSpaceList() {
      console.log(this.dataBaseInfo)
      const params = {
        dataSourceId: this.dataBaseInfo.id,
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
    getUserRoleList() {
      this.queryParams.dataSourceId = this.dataBaseInfo.id;
      userServer.getUserRoleList(this.queryParams).then((res) => {
        if (res.success) {
          this.userRoleList = res.data;
          // this.total = res.data.total;
        }
      });
    },
    userHandleCloseFn(val) {
      this.$refs.userFrom.resetFields();
      this.userFrom.userName = "";
      this.userFrom.newPassWord = "";
      this.userFrom.verifyPassword = "";
      this.userFrom.defaultTableSpace = "";
      this.userFrom.tempTableSpace = "";
      this.userRoleList = [];
      this.$emit("userHandleCloseFn", val);
    },
    handleClose() {
      this.$refs.userFrom.resetFields();
      this.userFrom.userName = "";
      this.userFrom.newPassWord = "";
      this.userFrom.verifyPassword = "";
      this.userFrom.defaultTableSpace = "";
      this.userFrom.tempTableSpace = "";
      this.userRoleList = [];
      this.$emit("userHandleCloseFn", false);
    },
    addSpace() {
      this.userDialogVisible = true;
      this.getList();
    },
    submit() {
      this.userInfo.fileName = this.selectTable.path;
      this.dialogSpace = false;
    },
    saveUser() {
      this.$refs.userFrom.validate((valid) => {
        if (valid) {
          const params = {
            userName: this.userFrom.userName,
            newPassWord: this.userFrom.newPassWord,
            defaultTableSpace: this.userFrom.defaultTableSpace,
            tempTableSpace: this.userFrom.tempTableSpace,
            roleList: this.userInfo.userRoleList,
            dataSourceId: this.dataBaseInfo.id,
          };
          userServer.addUserRole(params).then((res) => {
            if (res.code == 200) {
              this.getTableDataList(this.schema);
              this.userHandleCloseFn();
              this.getDataBaseTreeData()
              this.$message.success('新建用户成功!');
            } else {
              this.$message.error(res.errorCode);
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
  margin-bottom: 22px;
}
</style>
