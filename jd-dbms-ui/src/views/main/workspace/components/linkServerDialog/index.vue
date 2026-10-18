<template>
  <el-dialog :title="dialogTitle" :model-value="visibleDialog" width="600px" top="8vh"
    custom-class="datasource-dialog" :close-on-click-modal="false" :before-close="handleClose" @open="initializeForm">
    <div v-loading="loading" class="datasource-content">
      <p class="dialog-hint">{{ dialogHint }}</p>
      <el-form ref="formData" :model="formData" :rules="fromDataRules"
        :disabled="isReadonly || connecting || loading" label-position="top" size="small">
        <el-form-item label="连接名" prop="connectionName">
          <el-input v-model.trim="formData.connectionName" maxlength="50" placeholder="例如：生产库、测试库" />
        </el-form-item>
        <el-form-item label="数据库类型" prop="type">
          <el-radio-group v-model="formData.type" @change="handleTypeChange">
            <el-radio label="DM">达梦 DM</el-radio>
            <el-radio label="ORACLE">Oracle</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="16">
            <el-form-item label="主机地址" prop="host">
              <el-input v-model.trim="formData.host" placeholder="IP 地址或主机名" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="端口" prop="port">
              <el-input v-model.trim="formData.port" placeholder="端口号" />
            </el-form-item>
          </el-col>
        </el-row>
        <template v-if="formData.type === 'ORACLE'">
          <el-form-item label="Oracle 连接方式">
            <el-radio-group v-model="formData.radio">
              <el-radio label="serviceName">Service Name</el-radio>
              <el-radio label="sid">SID</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item :label="formData.radio === 'sid' ? 'SID' : 'Service Name'" prop="input">
            <el-input v-model.trim="formData.input" placeholder="请输入服务名称或 SID" />
          </el-form-item>
        </template>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="用户名" prop="username">
              <el-input v-model.trim="formData.username" placeholder="请输入用户名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="密码" prop="password">
              <el-input v-model="formData.password" type="password" :show-password="!isReadonly"
                :placeholder="isReadonly ? '使用已保存的密码' : mode === 'edit' ? '留空保留原密码' : '请输入密码'" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item v-if="mode === 'create'">
          <el-checkbox v-model="formData.isSave">保存密码</el-checkbox>
        </el-form-item>
      </el-form>
    </div>
    <template #footer><div  class="dialog-footer">
      <el-button :disabled="connecting" @click="handleClose">取消</el-button>
      <el-button v-if="!isReadonly" :disabled="loading || connecting" @click="saveConnection('test')">测试连接</el-button>
      <el-button type="primary" :loading="connecting" :disabled="loading || (mode !== 'create' && !detailsReady)" @click="submit">
        {{ isReadonly ? '连接' : mode === 'edit' ? '保存' : '保存并连接' }}
      </el-button>
    </div></template>
  </el-dialog>
</template>

<script>
import connectionServer from "@/api/main/connection";

function emptyForm() {
  return { id: "", connectionName: "", username: "", password: "", host: "", port: "", type: "", radio: "", input: "", isSave: false };
}

export default {
  props: {
    visibleDialog: { type: Boolean, default: false },
    selectedDataSourceId: { type: [String, Number], default: null },
    mode: { type: String, default: "create" },
  },
  data() {
    const hostValidator = (rule, value, callback) => {
      const ipv4 = /^(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$/;
      const hostname = /^(?=.{1,253}$)(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)*[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?$/;
      if (!value) callback(new Error("主机地址不能为空"));
      else if (!ipv4.test(value) && (!hostname.test(value) || /^[0-9.]+$/.test(value))) callback(new Error("请输入正确的 IP 地址或主机名"));
      else callback();
    };
    const portValidator = (rule, value, callback) => {
      if (!/^\d+$/.test(value) || Number(value) < 1 || Number(value) > 65535) callback(new Error("请输入 1 到 65535 之间的端口号"));
      else callback();
    };
    return {
      formData: emptyForm(),
      originalDataSource: {},
      driverConfig: {},
      loading: false,
      connecting: false,
      detailsReady: false,
      fromDataRules: {
        connectionName: [{ required: true, message: "请输入连接名", trigger: "blur" }],
        type: [{ required: true, message: "请选择数据库类型", trigger: "change" }],
        username: [{ required: true, message: "请输入用户名", trigger: "blur" }],
        password: [{ validator: (rule, value, callback) => {
          if (this.mode === "create" && !value) callback(new Error("请输入密码"));
          else callback();
        }, trigger: "blur" }],
        host: [{ validator: hostValidator, trigger: "blur" }],
        port: [{ validator: portValidator, trigger: "blur" }],
        input: [{ required: true, message: "请输入服务名称或 SID", trigger: "blur" }],
      },
    };
  },
  computed: {
    isReadonly() { return this.mode === "connect"; },
    dialogTitle() { return this.isReadonly ? "连接数据源" : this.mode === "edit" ? "修改数据源" : "新增数据源"; },
    dialogHint() {
      return this.isReadonly ? "确认连接以下数据源，连接成功后加载数据库。连接信息仅供查看。"
        : this.mode === "edit" ? "修改连接配置并保存。密码留空时保留原密码。"
        : "填写新的连接信息，保存并连接后显示在左侧树中。";
    },
  },
  methods: {
    handleClose() {
      if (!this.connecting) this.$emit("close");
    },
    async initializeForm() {
      this.formData = emptyForm();
      this.originalDataSource = {};
      this.driverConfig = {};
      this.detailsReady = false;
      this.$nextTick(() => this.$refs.formData?.clearValidate());
      if (this.mode === "create") return;
      this.loading = true;
      const selectedId = this.selectedDataSourceId;
      try {
        const res = await connectionServer.getDetails(selectedId);
        if (!this.visibleDialog || String(selectedId) !== String(this.selectedDataSourceId)) return;
        if (!res.success || !res.data) throw new Error(res.errorMessage || "数据源信息加载失败");
        const row = res.data;
        this.originalDataSource = row;
        this.driverConfig = row.driverConfig || {};
        this.formData = {
          id: row.id,
          connectionName: row.alias && !row.alias.startsWith("@") ? row.alias : row.user,
          username: row.user,
          password: this.isReadonly ? "********" : "",
          host: row.host,
          port: row.port,
          type: row.type,
          radio: row.sid ? "sid" : "serviceName",
          input: row.sid || row.serviceName || "",
          isSave: Boolean(row.isSave),
        };
        this.detailsReady = true;
      } catch (error) {
        this.$message.error(error.message || "数据源信息加载失败");
      } finally {
        this.loading = false;
      }
    },
    handleTypeChange(type) {
      this.formData.port = type === "ORACLE" ? "1521" : "5236";
      this.formData.radio = type === "ORACLE" ? "serviceName" : "";
      this.formData.input = "";
      this.driverConfig = {};
    },
    async getDriverConfig() {
      if (this.driverConfig.jdbcDriver) return;
      const res = await connectionServer.getDriverList({ dbType: this.formData.type });
      if (!res.success || !res.data?.defaultDriverConfig) throw new Error(res.errorMessage || "驱动配置加载失败");
      this.driverConfig = res.data.defaultDriverConfig;
    },
    submit() {
      if (this.isReadonly) return this.connectDataSource();
      return this.saveConnection(this.mode === "edit" ? "update" : "save");
    },
    async connectDataSource() {
      if (this.connecting || this.loading || !this.detailsReady) return;
      this.connecting = true;
      try {
        const res = await connectionServer.connect({ id: this.selectedDataSourceId });
        if (!res.success) throw new Error(res.errorMessage || "数据库连接失败");
        this.$message.success("连接成功");
        this.$emit("connected", this.originalDataSource);
      } catch (error) {
        this.$message.error(error.message || "数据库连接失败");
      } finally {
        this.connecting = false;
      }
    },
    buildParams() {
      const row = this.originalDataSource;
      const form = this.formData;
      const params = {
        alias: this.formData.connectionName,
        id: form.id || undefined,
        connectionEnvType: row.envType || "DAILY",
        envType: row.envType || "DAILY",
        environmentId: row.environmentId,
        driverConfig: this.driverConfig,
        user: form.username,
        host: form.host,
        port: form.port,
        password: form.password || row.password || "",
        checked: form.isSave,
        type: form.type,
        url: `jdbc:${form.type === "DM" ? "dm://" : "oracle:thin:@"}${form.host}:${form.port}`,
        extendInfo: row.extendInfo || [{ key: "zeroDateTimeBehavior", value: "convertToNull" }],
        ssh: row.ssh || { use: false },
        sid: "",
        serviceName: "",
      };
      if (form.type === "ORACLE") {
        if (form.radio === "sid") {
          params.sid = form.input;
          params.url += ":" + form.input;
          params.serviceType = "0";
        } else {
          params.serviceName = form.input;
          params.url += "/" + form.input;
          params.serviceType = "1";
        }
      }
      return params;
    },
    saveConnection(flag) {
      if (this.connecting || this.loading || this.isReadonly || (this.mode === "edit" && !this.detailsReady)) return;
      this.$refs.formData.validate(async (valid) => {
        if (!valid) return;
        this.connecting = true;
        try {
          await this.getDriverConfig();
          const res = await connectionServer[flag](this.buildParams());
          if (!res.success) throw new Error(res.errorMessage || res.errorCode || "操作失败");
          if (flag === "test") {
            this.$message.success("测试连接成功");
          } else if (flag === "update") {
            this.$message.success("保存成功");
            this.$emit("saved", { id: this.formData.id });
          } else {
            this.$message.success("保存并连接成功");
            this.$emit("saved", res.data);
          }
        } catch (error) {
          this.$message.error(error.message || "操作失败");
        } finally {
          this.connecting = false;
        }
      });
    },
  },
};
</script>

<style lang="scss" scoped>
::v-deep .datasource-dialog {
  max-width: calc(100vw - 32px);
  .el-dialog__body {
    padding: 20px 24px 8px;
    max-height: calc(84vh - 120px);
    overflow-y: auto;
  }
  .el-dialog__footer { padding: 16px 24px; border-top: 1px solid #ebeef5; }
  .el-form-item { margin-bottom: 20px; }
  .el-form-item__label { padding-bottom: 8px; line-height: 20px; color: #68728c; }
  .el-input.is-disabled .el-input__inner { color: #303133; background: #f5f7fa; }
  .el-radio__input.is-disabled + .el-radio__label { color: #68728c; }
}
.dialog-hint { margin: 0 0 20px; color: #68728c; font-size: 13px; line-height: 20px; }
</style>
