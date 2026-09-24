<script>
import tableServer from "@/api/main/table";
import viewServer from "@/api/main/view";
import GrantList from "../renderTableEditor/GrantList.vue";

export default {
  name: "ViewGrantPanel",
  components: { GrantList },
  props: {
    queryResultData: {
      type: Object,
      required: true
    },
    disabled: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      grantList: [],
      grantLoading: false,
      grantDialogVisible: false,
      grantUser: "",
      grantSubmitting: false
    };
  },
  mounted() {
    this.queryGrantList();
  },
  methods: {
    queryGrantList() {
      const target = this.queryResultData.uniqueData;
      if (!target?.tableName || this.grantLoading) return;
      this.grantLoading = true;
      tableServer.getGrantList({
        dataSourceId: target.dataSourceId,
        schemaName: target.schemaName,
        tableName: target.tableName
      }).then(res => {
        if (res.success) {
          this.grantList = res.data || [];
        } else {
          this.$message.error(res.errorMessage || "获取授权信息失败");
        }
      }).catch(() => this.$message.error("获取授权信息失败"))
        .finally(() => { this.grantLoading = false; });
    },
    grantParams(user) {
      const target = this.queryResultData.uniqueData;
      return {
        dataSourceId: target.dataSourceId,
        databaseName: target.databaseName,
        schemaName: target.schemaName,
        tableName: target.tableName,
        toGrantUser: user
      };
    },
    async grantViewSelect() {
      const user = this.grantUser.trim();
      if (!user) {
        this.$message.warning("请输入被授权用户");
        return;
      }
      this.grantSubmitting = true;
      try {
        const res = await viewServer.grantViewSelect(this.grantParams(user));
        if (!res.success) {
          this.$message.error(res.errorMessage || "授权失败");
          return;
        }
        this.$message.success("SELECT 授权成功");
        this.grantDialogVisible = false;
        this.grantUser = "";
        this.queryGrantList();
      } catch (e) {
        this.$message.error("授权失败，请稍后重试");
      } finally {
        this.grantSubmitting = false;
      }
    },
    async revokeViewSelect(row) {
      try {
        await this.$confirm(`确定撤销 ${row.grantee} 的 SELECT 权限吗？`, "撤销授权", {
          type: "warning"
        });
      } catch (e) {
        return;
      }
      this.grantSubmitting = true;
      try {
        const res = await viewServer.revokeViewSelect(this.grantParams(row.grantee));
        if (!res.success) {
          this.$message.error(res.errorMessage || "撤销授权失败");
          return;
        }
        this.$message.success("SELECT 权限已撤销");
        this.queryGrantList();
      } catch (e) {
        this.$message.error("撤销授权失败，请稍后重试");
      } finally {
        this.grantSubmitting = false;
      }
    }
  }
};
</script>

<template>
  <div>
    <el-button type="primary" size="mini" style="margin-bottom:10px" :disabled="disabled || grantSubmitting" @click="grantDialogVisible = true">授予 SELECT</el-button>
    <GrantList v-loading="grantLoading" :tableData="grantList" :allow-revoke="!disabled" :revoke-disabled="grantSubmitting" @revoke="revokeViewSelect" />
    <el-dialog title="授予视图 SELECT 权限" :visible.sync="grantDialogVisible" width="420px" append-to-body>
      <el-input v-model="grantUser" placeholder="被授权用户名" maxlength="128" />
      <span slot="footer">
        <el-button @click="grantDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="grantSubmitting" @click="grantViewSelect">确定</el-button>
      </span>
    </el-dialog>
  </div>
</template>
