<script>
import viewServer from "@/api/main/view";
import userServer from "@/api/main/user";
import MonacoEditor from "@/components/MonacoEditor/index.vue";
import MonacoSqlEditor from "@/components/MonacoSqlEditor/index.vue";
import UserInfo from "../renderUserEditor/UserInfo.vue";
import SqlPreview from "@/views/main/workspace/components/DataSource/SqlPreview.vue";
import RoleGrantsList from "@/views/main/workspace/components/renderUserEditor/RoleGrantsList.vue";
import ObjectPermissions from './objectPermissions.vue'
import { RESOURCE_MUTATION_EVENT } from '@/utils/resourceMutation'
export default {
  components: {
    SqlPreview,
    MonacoEditor,
    MonacoSqlEditor,
    UserInfo,
    RoleGrantsList,
    ObjectPermissions
  },
  props: {
    queryResultData: {
      type: Object,
      default: () => {},
    },
  },
  data() {
    return {
      currentConfig: null,
      activeName: "first",
      activeTab: "first",
      // 基本信息
      basicForm: {
        dataSourceId: "",
        databaseName: "",
        schemaName: "",
        tableName: "",
      },
      columnList: [],
      userInfo: null,
      roleGrantsList: [],
      detailRequestVersion: 0,

      databaseSupportField: {
        columnTypes: [],
        charsets: [],
        collations: [],
        indexTypes: [],
        defaultValues: [],
      },

      clickRow: null, // 当前点击的行
      clickCell: null, // 当前点击的列
    };
  },
  computed: {
    openConfigList() {
      return this.$store.state.workspace.workspaceTabList;
    },
    openConfigValue() {
      return this.$store.state.workspace.activeConsoleId;
    },
    currentDataSource() {
      return this.$store.state.workspace.currentConnectionDetails;
    },
  },
  watch: {
    // openConfigValue: {
    //   handler() {
    //     this.configChange();
    //     // this.initData();
    //   },
    //   deep: true,
    //   immediate: true,
    // },
    queryResultData: {
      handler() {
        this.configChange();
        this.initData();
      },
      immediate: true,
      deep: false,
    },
    activeTab:{
      handler(newVal) {
        if(newVal == 'third') {
          this.refreshScript()
        }
      }
    }

  },
  mounted() {
    window.addEventListener(RESOURCE_MUTATION_EVENT, this.handleResourceMutation);
  },
  beforeDestroy() {
    window.removeEventListener(RESOURCE_MUTATION_EVENT, this.handleResourceMutation);
  },
  methods: {
    handleResourceMutation(event) {
      const change = event.detail;
      if (!change?.groups?.includes('users')
          || String(change.dataSourceId) !== String(this.currentConfig?.uniqueData?.dataSourceId)) return;
      if (this.activeTab === 'third') this.refreshScript();
      const permissions = this.$refs.objectPre;
      if (this.activeTab === 'permissions' && permissions?.selectedNames?.length
          && !permissions.hasChanges && !permissions.loading) permissions.loadPermissions();
    },
    async initData() {
      const version = ++this.detailRequestVersion;
      this.currentConfig = this.queryResultData;
      this.basicForm = this.currentConfig?.tableDetails || {};
      this.roleGrantsList = this.currentConfig?.tableUserRoles || [];
      this.$refs.objectPre?.reset();
      if (!this.currentConfig?.uniqueData?.dataSourceId || this.currentConfig.title === '新建用户') return;
      try {
        const res = await userServer.getUserDetails({
          dataSourceId: this.currentConfig.uniqueData.dataSourceId,
          databaseName: this.currentConfig.uniqueData.databaseName,
          userName: this.currentConfig.uniqueData.userName || this.currentConfig.title,
        });
        if (version !== this.detailRequestVersion) return;
        if (!res.success || !res.data) throw new Error(res.errorMessage || '用户详情读取失败');
        this.basicForm = res.data.tableDetails || {};
        this.roleGrantsList = res.data.tableUserRoles || [];
        this.$refs.MonacoEditor?.setValue(res.data.querySql || '');
      } catch (error) {
        if (version === this.detailRequestVersion) this.$message.error(error.message || '用户详情读取失败');
      }
    },
    configChange() {
      if (this.currentConfig) {
        this.currentConfig.basicForm = JSON.parse(
          JSON.stringify(this.basicForm)
        );
        this.currentConfig.columnList = JSON.parse(
          JSON.stringify(this.columnList)
        );
        this.currentConfig.activeName = this.activeName;
      }
    },

    async refreshScript() {
      if (!this.currentConfig || this.currentConfig.title === '新建用户') return;
      try {
        const res = await userServer.getUserDetails({
          dataSourceId: this.currentConfig.uniqueData.dataSourceId,
          databaseName: this.currentConfig.uniqueData.databaseName,
          userName: this.currentConfig.uniqueData.userName || this.currentConfig.title,
        });
        if (res.success) this.$refs.MonacoEditor?.setValue(res.data.querySql || '');
        else this.$message.error(res.errorMessage || '脚本查询失败');
      } catch (error) {
        this.$message.error(error?.message || '脚本查询失败，请重试');
      }
    },

    // 获取依赖关系
    getExecuteSQL() {
      let viewSql = this.$refs.MonacoEditor.getValue();
      if (!viewSql) {
        this.$message.error("请先输入SQL");
        return;
      }

      let send = {
        dataSourceId: this.currentConfig?.uniqueData.dataSourceId,
        databaseName: this.currentConfig?.uniqueData.databaseName,
        schemaName: this.currentConfig?.uniqueData.schemaName,
        tableName: this.basicForm.tableName,
        viewSql: viewSql,
      };
      viewServer.getExecuteSQL(send).then((res) => {
        if (res.success) {
          this.$refs.MonacoSqlEditor.setValue(res.message);

          this.$message.success("获取成功！");
        } else {
          this.$message.error(res.message);
        }
      });
    },

    // 运行
    executeSQL() {
      let viewSql = this.$refs.MonacoEditor.getValue();
      if (!viewSql) {
        this.$message.error("请先输入SQL");
        return;
      }
      console.log("this.currentConfig", this.currentConfig);
      console.log(
        "uniqueData.tableName",
        this.currentConfig?.uniqueData.tableName
      );

      let send = {
        dataSourceId: this.currentConfig?.uniqueData.dataSourceId,
        databaseName: this.currentConfig?.uniqueData.databaseName,
        schemaName: this.currentConfig?.uniqueData.schemaName,
        tableName: this.basicForm.tableName,
        viewSql: viewSql,
      };
      viewServer.getViewColumnList(send).then((res) => {
        if (res.success) {
          this.columnList = res.data.map((item) => {
            return {
              name: item.name,
              comment: "",
            };
          });
          this.$message.success("运行成功！");
        } else {
          this.$message.error(res.errorMessage);
        }
      });
    },
    sqlPreview() {
      let viewSql = this.$refs.MonacoEditor.getValue();
      let send = {
        dataSourceId: this.currentConfig?.uniqueData.dataSourceId,
        databaseName: this.currentConfig?.uniqueData.databaseName,
        schemaName: this.currentConfig?.uniqueData.schemaName,
        tableName: this.basicForm.tableName,
        viewSql: viewSql,
        columnList: this.columnList,
      };
      viewServer.viewShowSql(send).then((res) => {
        if (res.success) {
          this.$refs.sqlPreview.init(res.data.sql);
        } else {
          this.$message.error(res.errorMessage);
        }
      });
    },
    submit() {
      let viewSql = this.$refs.MonacoEditor.getValue();
      let send = {
        dataSourceId: this.currentConfig?.uniqueData.dataSourceId,
        databaseName: this.currentConfig?.uniqueData.databaseName,
        schemaName: this.currentConfig?.uniqueData.schemaName,
        tableName: this.basicForm.tableName,
        viewSql: viewSql,
        columnList: this.columnList,
      };
      viewServer.viewExecute(send).then((res) => {
        if (res.success) {
          this.$message.success("保存成功！");
          this.$emit("tableRefresh", this.currentConfig.uniqueData.node.parent);
        } else {
          this.$message.error(res.errorMessage);
        }
      });
    },
    // 双击单元格修改数据
    celldblclick(row, column, cell, event) {
      if (column.index !== 0) {
        console.log(row, column, "111");
        this.clickRow = row.index;
        this.clickCell = column.index;
        this.$nextTick(() => {
          this.$refs.editInput.focus();
        });
      }
    },
    // 把每一行的索引放进row
    tableRowClassName({ row, rowIndex }) {
      row.index = rowIndex;
      return "";
    },
    // 把每一列的索引放进column
    tableCellClassName({ column, columnIndex }) {
      column.index = columnIndex;
    },
    inputBlur() {
      this.clickRow = null;
      this.clickCell = null;
    },
    saveUser() {
      this.$refs.userInfo.editUser()
    }
  },
};
</script>

<template>
  <div class="view_editor">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="基本信息" name="first">
        <UserInfo
          ref="userInfo"
          :currentConfig="currentConfig"
          :initData='initData'
          :basic-form="basicForm"
        />
      </el-tab-pane>
      <el-tab-pane label="角色授权" name="second">
        <RoleGrantsList
          ref="roleGrantsList"
          :currentConfig="currentConfig"
          :tableData="roleGrantsList"
          :initData='initData'
        />
      </el-tab-pane>
      <el-tab-pane label="对象权限" name="permissions">
        <ObjectPermissions :currentConfig="currentConfig" ref='objectPre' @permissions-changed="refreshScript"></ObjectPermissions>
      </el-tab-pane>
      <el-tab-pane label="脚本" name="third">
        <MonacoEditor
          ref="MonacoEditor"
          dom="editor"
          class="monacoEditor"
        />
      </el-tab-pane>
    </el-tabs>
    <el-button
      size="mini"
      type="text"
      plain
      style="position: absolute; right: 10px; top: 10px"
      @click="saveUser"
      v-show="activeTab == 'first'"
    >
      保存
    </el-button>
  </div>
</template>

<style scoped lang="scss">
.view_editor {
  height: 100%;
  background: #fff;
  padding: 10px 10px 0;
  border-radius: 5px;
  position: relative;
  .btn_box {
    display: flex;
    justify-content: end;
  }
  .monaco_btn {
    display: flex;
    align-items: center;
    padding: 10px 20px;
    ::v-deep .el-button {
      & > span {
        display: flex;
        align-items: center;
        img {
          margin-right: 6px;
        }
      }
    }
    .el-button_before::before {
      content: "";
      position: absolute;
      right: -15px;
      top: 8px;
      width: 1px;
      height: 14px;
      background: #68728c;
    }
  }
}
.el-tabs {
  height: calc(100% - 205px);
  ::v-deep .el-tabs__content {
    height: calc(100% - 55px);
    .el-tab-pane {
      height: 100%;
    }
  }
}
.monacoEditor {
  height: 100%;
}
</style>
