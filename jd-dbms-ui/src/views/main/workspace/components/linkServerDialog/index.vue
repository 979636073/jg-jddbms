<template>
  <el-dialog
    title="连接数据源"
    :visible.sync="visibleDialog"
    width="60%"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :before-close="handleClose"
  >
    <div class="dialogBox">
      <div class="tableData">
        <el-table
          :data="tableData"
          style="width: 100%"
          height="350"
          highlight-current-row
          @row-click="rowClick"
          :header-cell-style="{ background: '#f1f3f9', color: '#68728c' }"
        >
          <el-table-column prop="user" label="用户" sortable></el-table-column>
          <el-table-column
            prop="url"
            label="数据源"
            width="300"
            show-overflow-tooltip
          ></el-table-column>
          <el-table-column prop="status" label="状态" align="center">
            <template slot-scope="scope">
              <div v-if="scope.row.status" class="iconTitle">
                <i class="dotClass"></i>
                <span style="color: springgreen">已连接</span>
              </div>
              <div v-else class="iconTitle">
                <i class="notDotClass"></i>
                <span style="color: #ff5733">未连接</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200" align="center">
            <template slot-scope="scope">
              <el-button
                size="mini"
                type="warning"
                v-if="scope.row.status"
                @click.stop="closeDataSource(scope.row, scope.$index)"
              >
                <span style="font-size: 12px">关闭连接</span>
              </el-button>
              <el-button
                size="mini"
                type="danger"
                @click.stop="deleteDataSource(scope.row, scope.$index)"
              >
                <span style="font-size: 12px">删除</span>
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <div class="formData">
        <el-form
          label-position="top"
          label-width="80px"
          size="mini"
          :model="formData"
          :rules="fromDataRules"
          ref="formData"
        >
          <el-form-item prop="radio">
            <el-row :gutter="20">
              <el-col :span="12">
                <el-radio v-model="formData.type" label="DM">DM</el-radio>
              </el-col>
              <el-col :span="12">
                <el-radio v-model="formData.type" label="ORACLE"
                  >Oracle</el-radio
                >
              </el-col>
            </el-row>
          </el-form-item>
          <el-form-item label="用户名/模式名" prop="username">
            <el-input
              v-model="formData.username"
              placeholder="请输入用户"
            ></el-input>
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="formData.password"
              show-password
              placeholder="请输入密码"
            ></el-input>
          </el-form-item>
          <div style="width: 100%">
            <el-form-item prop="isSave">
              <el-checkbox v-model="formData.isSave">保存密码</el-checkbox>
            </el-form-item>
          </div>
          <el-row :gutter="20">
            <el-col :span="16">
              <el-form-item label="主机号" prop="host" class="is-required">
                <el-input
                  v-model="formData.host"
                  placeholder="请输入服务地址"
                ></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="端口" prop="port" class="is-required">
                <el-input v-model="formData.port"></el-input>
              </el-form-item>
            </el-col>
          </el-row>
          <el-alert
            v-if="isLoopbackHost"
            class="host-warning"
            title="Docker 部署提示"
            description="127.0.0.1 或 localhost 指向后端容器；连接宿主机数据库请使用 host.docker.internal。"
            type="warning"
            show-icon
            :closable="false"
          ></el-alert>
          <template v-if="formData.type == 'ORACLE'">
            <el-row :gutter="20">
              <el-col :span="12">
                <span style="color: red">*</span>
                <el-radio
                  style="margin-left: 5px"
                  v-model="formData.radio"
                  label="serviceName"
                  >Server Name</el-radio
                >
              </el-col>
              <el-col :span="12">
                <el-radio v-model="formData.radio" label="sid">SID</el-radio>
              </el-col>
            </el-row>
            <el-form-item style="margin-top: 5px" prop="input">
              <el-input v-model="formData.input"></el-input>
            </el-form-item>
          </template>
          <!-- <el-form-item label="状态">
            <el-tag type="success" v-if="isStatus">已连接</el-tag>
            <el-tag type="danger" v-else>未连接</el-tag>
          </el-form-item> -->
        </el-form>
      </div>
    </div>
    <div slot="footer" class="dialog-footer">
      <el-button @click="clearForm">清空</el-button>
      <el-button @click="saveConnection('test')">测试连接</el-button>
      <el-button type="primary" @click="saveConnection('save')">连接</el-button>
    </div>
  </el-dialog>
</template>

<script>
import connectionServer from "@/api/main/connection";
import { v4 as uuidv4 } from "uuid";

export default {
  props: {
    visibleDialog: {
      type: Boolean,
      default: true,
    },
  },
  computed: {
    isLoopbackHost() {
      const host = (this.formData.host || "").trim().toLowerCase();
      return host === "127.0.0.1" || host === "localhost";
    },
  },
  watch: {
    "formData.type"(val) {
      if (val == "ORACLE") {
        this.formData.port = "1521";
      } else {
        this.formData.port = "5236";
      }
      this.getDriverList();
    },
    "formData.radio"(val) {
      if (val == "sid") {
        this.formData.serverName = "";
      } else {
        this.formData.sid = "";
      }
    },
  },
  data() {
    var hostValidator = (rule, value, callback) => {
      const ipv4Regex =
        /^(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$/;
      const hostnameRegex =
        /^(?=.{1,253}$)(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)*[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?$/;
      if (!value) {
        return callback(new Error("主机地址不能为空!"));
      } else if (
        !ipv4Regex.test(value) &&
        (!hostnameRegex.test(value) || /^[0-9.]+$/.test(value))
      ) {
        callback(new Error("请输入正确的IP地址或主机名!"));
      } else {
        callback();
      }
    };
    var postRegex = (rule, value, callback) => {
      const port = parseInt(value);
      if (!value) {
        return callback(new Error("端口号不能为空!"));
      } else if (isNaN(port)) {
        return callback(new Error("端口号应为纯数字!"));
      } else if (port < 1 || port > 65535) {
        return callback(new Error("请输入正确的端口!"));
      } else {
        callback();
      }
    };
    return {
      localKey: "AllCreateData",
      formData: {
        id: "",
        username: "",
        password: "",
        port: "5236",
        host: "",
        type: "DM",
        input: "",
        radio: "",
        isSave: "",
        sid: "",
        serverName: "",
      },
      fromDataRules: {
        password: [{ required: true, message: "请输入密码", trigger: "blur" }],
        username: [
          { required: true, message: "请输入用户名", trigger: "blur" },
        ],
        host: [{ validator: hostValidator, trigger: "blur" }],
        port: [{ validator: postRegex, trigger: "blur" }],
        input: [{ required: true, message: "请输入", trigger: "blur" }],
      },
      tableData: [],
      driverConfig: { jdbcDriver: "", jdbcDriverClass: "" },
      connecting: false,
    };
  },
  mounted() {
    this.queryDataSoureList();
    this.getDriverList();
  },
  methods: {
    handleClose() {
      this.visibleDialog = false;
    },
    queryDataSoureList() {
      const params = {
        pageNo: 1,
        pageSize: 10000,
        refresh: true,
        searchKey: "",
      };
      connectionServer.getList(params).then((res) => {
        this.tableData = res.data.data;
        this.$store.commit("SET_DATASOURCELIST", res.data.data);
      });
    },
    closeDataSource(row) {
      this.$confirm("确定要关闭该数据源吗?", "提示", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      })
        .then(() => {
          connectionServer.closeDataSource({ id: row.id }).then((res) => {
            if (res.success) {
              this.queryDataSoureList();
            }
          });
        })
        .catch(() => {});
    },
    deleteDataSource(row) {
      this.$confirm("确定要删除该数据源吗?", "提示", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      })
        .then(() => {
          connectionServer.deleteDataSource(row.id).then((res) => {
            if (res.success) {
              this.queryDataSoureList();
            }
          });
        })
        .catch(() => {});
    },
    // 行点击事件
    rowClick(row) {
      let {
        user: username,
        host,
        port,
        password,
        isSave,
        type,
        sid,
        serviceName,
        id,
      } = row;

      this.formData.username = username;
      this.formData.host = host;
      this.formData.port = port;
      this.formData.password = password;
      this.formData.isSave = isSave;
      this.formData.type = type;
      this.formData.id = id;
      // this.isStatus = row.status ? true:false;
      if (type != "DM") {
        if (sid) {
          this.formData.radio = "sid";
          this.formData.input = sid;
        } else {
          this.formData.radio = "serviceName";
          this.formData.input = serviceName;
        }
      }
    },

    getDriverList() {
      // 获取驱动列表
      connectionServer
        .getDriverList({ dbType: this.formData.type })
        .then((res) => {
          let { jdbcDriver, jdbcDriverClass } = res.data.defaultDriverConfig;
          this.driverConfig.jdbcDriver = jdbcDriver;
          this.driverConfig.jdbcDriverClass = jdbcDriverClass;
        });
    },

    clearForm() {
      this.$refs.formData.resetFields();
      // this.isStatus = false;
      this.formData.id = "";
      if (this.formData.type == "ORACLE") {
        this.formData.port = "1521";
      } else {
        this.formData.port = "5236";
      }
    },

    getCache() {
      let cache = localStorage.getItem(this.localKey);
      return cache ? JSON.parse(cache) : [];
    },

    setCache(arr) {
      localStorage.setItem(this.localKey, JSON.stringify(arr));
    },

    updateCache(newObj) {
      let cache = this.getCache();
      let exist = cache.some((item) => item.dateSourceId == newObj.dateSourceId);
      console.log(cache,exist,'cache');
      if (!exist) {
        cache.push(newObj);
        this.setCache(cache);
      }
    },

    saveConnection(flag) {
      if (this.connecting) return;
      this.$refs.formData.validate((valid) => {
        if (valid) {
          if (flag === "save") this.connecting = true;
          const params = {
            alias: this.formData.port,
            connectionEnvType: "DAILY",
            driverConfig: {
              ...this.driverConfig,
            },
            user: this.formData.username,
            host: this.formData.host,
            port: this.formData.port,
            password: this.formData.password,
            checked: this.formData.isSave,
            id: this.formData.id,
            type: this.formData.type,
            url: `jdbc:${
              this.formData.type == "DM" ? "dm://" : "oracle:thin:@"
            }${this.formData.host}:${this.formData.port}`,
            extendInfo: [
              { key: "zeroDateTimeBehavior", value: "convertToNull" },
            ],
            ssh: {
              authenticationType: "1",
              hostName: "",
              keyFile: null,
              localPort: "",
              passphrase: "",
              password: "",
              port: this.formData.port,
              rhost: null,
              rport: null,
              use: false,
              userName: "",
            },
          };
          if (this.formData.type != "DM") {
            if (this.formData.radio === "sid") {
              params.sid = this.formData.input;
              params.url += ":" + params.sid;
            } else {
              params.serviceName = this.formData.input;
              params.url += "/" + params.serviceName;
            }
          }

          connectionServer[flag](params).then((res) => {
            if (res.success) {
           
              sessionStorage.setItem("createData", JSON.stringify(res.data));
              if (flag == "test") {
                this.$message({
                  message: "测试连接成功",
                  type: "success",
                });
              } else {
                let pageId = uuidv4();
                this.updateCache(res.data) // 存储所有新建连接
                this.$parent.visibleDialog = false;
                this.dataBaseInfo = res.data;
                this.$store.commit(
                  "workspaceData/SET_DATA_BASE_INFO",
                  res.data.dataSource
                );
                this.$store.commit("workspaceData/SET_DATA_INFO", res.data);
                localStorage.removeItem("saveVisitedViews2");
                this.$store.dispatch("jdTagsView/delAllViews", {});
                this.$router.replace({ path: "/workspace/look/" + pageId });
              }
            } else {
              this.$message.error(
                res.errorMessage || res.errorCode || "数据库连接失败"
              );
            }
          }).finally(() => {
            if (flag === "save") this.connecting = false;
          });
        }
      });
    },
  },
};
</script>

<style lang='scss' scoped>
::v-deep .el-dialog {
  .el-dialog__body {
    padding: 10px;
    .dialogBox {
      display: flex;
      width: 100%;
      .tableData {
        flex: 1;
        margin-right: 10px;
      }

      .iconTitle {
        display: flex;
        justify-content: center;
        align-items: center;
        .dotClass {
          background: springgreen;
          width: 10px;
          height: 10px;
          border-radius: 50%;
          display: block;
          margin-right: 10px;
        }
        .notDotClass {
          background: #ff5733;
          width: 10px;
          height: 10px;
          border-radius: 50%;
          display: block;
          margin-right: 10px;
        }
      }

      .formData {
        width: 350px;
      }
    }
  }
}
</style>
