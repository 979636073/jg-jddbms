<template>
  <div>
    <div class="isRadioBox">
      <el-button type="primary" @click="roleGrant" size="small" style="margin-bottom:5px">授权</el-button>
      <el-table
        :data="tableData"
        style="width: 100%; margin-bottom: 20px"
        row-key="id"
        default-expand-all
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
        :header-cell-style="{ background: '#f1f3f9', color: '#68728c' }"
      >
        <el-table-column prop="grantee" label="被授权用户"> </el-table-column>
        <el-table-column prop="role" label="角色" sortable>
          <template slot-scope="scope">
            <el-select
              size="mini"
              @change="selectNewRole"
              @blur="inputBlur"
              v-if="scope.row.index == tabRowindex && scope.column.index == tabColumnIndex"
              v-model="scope.row.role"
              placeholder="请选择"
            >
              <el-option
                v-for="item in roleData"
                :key="item.role"
                :label="item.role"
                :value="item.role"
              >
              </el-option>
            </el-select>
            <div v-else>
              {{ scope.row.role }}
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="isAdmin" label="是否管理员" sortable>
        </el-table-column>
        <!-- <el-table-column prop="isDefault" label="是否默认"> </el-table-column> -->
      </el-table>
      <addUserDialog :userDialogVisible='userDialogVisible' @userHandleCloseFn='userHandleCloseFn' :currentConfig='currentConfig' :initData='initData'></addUserDialog>
    </div>
  </div>
</template>

<script>
import userServer from "@/api/main/user";
import addUserDialog from './component/addUserDialog.vue'
export default {
  name: "",
  props: {
    tableData: {
      type: Array,
      default: [],
    },
    currentConfig: {
      type: Object,
      default: {},
    },
    initData: {
      type: Function,
    },
  },
  components:{addUserDialog},
  data() {
    return {
      radio: "tree",
      tabRowindex: "",
      tabColumnIndex: "",
      roleData: [],
      oldRole: [],
      newRole: [],
      srr: "",
      ss: "",
      userDialogVisible:false
    };
  },
  watch: {
    tableData() {
      this.getUserRole();
    },
  },
  methods: {
    getUserRole() {
      const params = {
        dataSourceId: this.currentConfig?.uniqueData.dataSourceId,
      };
      userServer.getUserRoleList(params).then((res) => {
        if (res.success) {
          this.roleData = res.data;
        }
      });
    },
    roleGrant() {
      this.userDialogVisible = true
    },
    // selectNewRole(row) {
    //   this.srr = row;
    //   console.log(this.srr);
    // },
    // getCell(row, column, cell, event) {
    //   this.tabRowindex = row.index;
    //   this.tabColumnIndex = column.index;
    //   this.ss = row.role;
    //   console.log(this.ss);
    //   // this.oldRole = [];
    //   // this.oldRole.push(row.role);
    // },
    // getRowCloumn({ row, column, rowIndex, columnIndex }) {
    //   row.index = rowIndex;
    //   column.index = columnIndex;
    // },
    // inputBlur() {
    //   setTimeout(() => {
    //     const params = {
    //       userName: this.tableData[0].grantee,
    //       dataSourceId: this.currentConfig?.uniqueData.dataSourceId,
    //       delRole: [this.ss],
    //       addRole: [this.srr],
    //     };
    //     userServer.editUserRole(params).then((res) => {
    //       if (res.code == 200) {
    //         this.tabRowindex = null;
    //         this.tabColumnIndex = "";
    //         this.initData()
    //       }else {
    //         this.$message.error(res.errorCode)
    //       }
    //     });
    //   }, 100);
    // },
    userHandleCloseFn(val) {
      this.userDialogVisible = val;
    },
  },
};
</script>

<style lang='scss' scoped>
::v-deep .el-table--medium .el-table__cell {
  padding: 3px 0;
}
</style>