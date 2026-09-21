<template>
  <div>
    <el-dialog title="授权用户" :visible.sync="userDialogVisible" :before-close="handleClose"  width="800px">
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
          <el-table-column prop="isDefault" label="Default" v-if="currentConfig.uniqueData.databaseType == 'ORACLE'">
            <template slot-scope="scope">
              <el-checkbox v-model="scope.row.isDefault" />
            </template>
          </el-table-column>
        </el-table>
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
export default {
  props: {
    userDialogVisible: {
      type: Boolean,
      default: false,
    },
    currentConfig: {
      type: Object,
      default: {},
    },
    initData: {
      type: Function,
    },
  },
  data() {
    return {
      userRoleList: [],
      total: 1,
      checked: false,
      autoCheck: false,
      selectTable: {},
      oldData: [],
    };
  },
  watch: {
    userDialogVisible(newVal) {
      if (newVal) {
        this.getUserRoleList();
      }
    },
  },
  methods: {
    getUserRoleList() {
      const params = {
        dataSourceId: this.currentConfig?.uniqueData.dataSourceId,
        userName: this.currentConfig?.uniqueData.userName,
      };
      userServer.getUserRoleList(params).then((res) => {
        if (res.success) {
          this.userRoleList = res.data;
          this.oldData = JSON.parse(JSON.stringify(this.userRoleList));
          // this.total = res.data.total;
        }
      });
    },
    userHandleCloseFn(val) {
      this.$emit("userHandleCloseFn", val);
    },
    handleClose() {
      this.$emit("userHandleCloseFn", false);
    },
    saveUser() {
      const params = {
        userName: this.currentConfig?.uniqueData.userName,
        dataSourceId: this.currentConfig?.uniqueData.dataSourceId,
        oldRoles: this.oldData,
        newRoles: this.userRoleList,
      };
      userServer.editUserRole(params).then((res) => {
        if (res.code == 200) {
          this.initData();
          this.userHandleCloseFn(false);
          this.$message.success('操作成功!')
        } else {
          this.$message.error(res.errorCode);
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
  margin-bottom: 15px;
}
</style>
