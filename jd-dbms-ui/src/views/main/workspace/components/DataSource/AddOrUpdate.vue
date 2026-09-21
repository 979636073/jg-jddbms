<script>
import connectionServer from "@/api/main/connection";
import { database, dataSourceFormConfigs } from "@/utils/database";
import FormItem from "@/views/main/workspace/components/DataSource/FormItem.vue";
import ExtendTable from "@/views/main/workspace/components/DataSource/ExtendTable.vue";

export default {
  name: "DialogAddOrUpdate",
  components: { ExtendTable, FormItem },
  data() {
    return {
      database,
      title: "",
      dialogVisible: false,
      dataSourceInfo: {},
      dataSourceFormConfig: null,
      envList: [],
      // 驱动列表
      driverConfigList: []
    };
  },
  created() {
    connectionServer.getEnvList().then(res => {
      this.envList = res.data;
    });
  },
  watch: {
    // "dataSourceInfo.port": function(newVal) {
    //   if (newVal === undefined) return;
    //   let urlObj = this.urlToObj(this.dataSourceInfo.url);
    //   if (urlObj.port == newVal) return;
    //   urlObj.port  = newVal;
    //   this.dataSourceInfo.url = this.objToUrl(urlObj);
    // },
    "dataSourceInfo.url": function(newVal) {
      if (newVal === undefined) return;
      let urlObj = this.urlToObj(newVal);
      if (urlObj.host != this.dataSourceInfo.host) {
        this.dataSourceInfo.host = urlObj.host;
      }
      if (urlObj.port != this.dataSourceInfo.port) {
        this.dataSourceInfo.port = urlObj.port + urlObj.catalog;
      }
    },
    "dataSourceInfo.host": function(newVal) {
      if (newVal === undefined) return;
      let urlObj = this.urlToObj(this.dataSourceInfo.url);
      if (urlObj.host == newVal) return;
      urlObj.host = newVal;
      this.dataSourceInfo.url = this.objToUrl(urlObj);
    }
  },
  methods: {
    urlToObj(url) {
      let protocol, a, port, catalog;
      if (url.indexOf("://") != -1) {
        protocol = url.split("://")[0];
        a = url.split("://")[1];
        protocol += "://";
      } else if (url.indexOf("@") != -1) {
        protocol = url.indexOf("@") != -1;
        a = url.split("@")[1];
        protocol += "@";
      }
      let host = a.slice(0, a.indexOf(":")); // host
      let b = a.slice(a.indexOf(":") + 1, a.length);

      if (b.indexOf(":") != -1) {
        port = b.slice(0, b.indexOf(":"));
        catalog = b.slice(b.indexOf(":"), b.length);
      } else {
        port = b.slice(0, b.indexOf("/"));
        catalog = b.slice(b.indexOf("/"), b.length);
      }
      return { port, host, protocol, catalog };
    },
    objToUrl(obj) {
      return `${obj.protocol}${obj.host}:${obj.port}${obj.catalog}`;
    },

    init(row) {
      this.title = "选择数据源";
      this.dialogVisible = true;
      if (row) {
        this.dataSourceInfo = {
          id: row.id,
          type: row.type,
          ssh: {},
          extendInfo: [{ key: "zeroDateTimeBehavior", value: "convertToNull" }]
        };
        connectionServer.getDriverList({ dbType: row.type }).then(res => {
          if (!this.dataSourceInfo.driverConfig) {
            this.$set(
              this.dataSourceInfo,
              "driverConfig",
              res.data.defaultDriverConfig
            );
          }
          this.driverConfigList = res.data.driverConfigList;
          this.dataSourceFormConfig = this.getDataSourceFormConfig(row);
        });
      }
    },
    // 点击新增数据源
    handleCreateConnections(database) {
      this.dataSourceInfo = {
        type: database.code,
        ssh: {},
        extendInfo: [{ key: "zeroDateTimeBehavior", value: "convertToNull" }]
      };
      connectionServer
        .getDriverList({ dbType: this.dataSourceInfo.type })
        .then(res => {
          this.$set(
            this.dataSourceInfo,
            "driverConfig",
            res.data.defaultDriverConfig
          );
          this.driverConfigList = res.data.driverConfigList;
          this.dataSourceFormConfig = this.getDataSourceFormConfig();
        });
    },
    getDataSourceFormConfig(row) {
      let data = dataSourceFormConfigs.find(
        item => item.type === this.dataSourceInfo.type
      );

      let that = this;
      function setValue(dataSourceInfo, _newDataItem) {
        _newDataItem &&
          _newDataItem.forEach(item => {
            that.$set(
              dataSourceInfo,
              item.name,
              item.defaultValue,
              // row && row[item.name] ? row[item.name] : item.defaultValue
            );
            if (item.name === "environmentId") {
              item.selects = that.envList.map(item => {
                return {
                  label: item.name,
                  value: item.id
                };
              });
              that.$set(
                dataSourceInfo,
                item.name,
                row && row[item.name] ? row[item.name] : that.envList[0].id
              );
            }
            if (item.inputType === "select" && row && row[item.name]) {
              let selectItemInfo = item.selects.find(
                selectItem => selectItem.value == row[item.name]
              );
              if (selectItemInfo?.items) {
                setValue(dataSourceInfo, selectItemInfo.items);
              }
            }
          });
      }
      if (data) {
        setValue(this.dataSourceInfo, data.baseInfo.items);
        setValue(this.dataSourceInfo.ssh, data.ssh.items);
        return data;
      } else {
        return {};
      }
    },

    // 测试 保存 修改链接
    saveConnection(type) {
      let send = {
        ...this.dataSourceInfo,
        connectionEnvType: "DAILY",
        driverConfig: {
          jdbcDriver: this.dataSourceInfo.driverConfig.jdbcDriver,
          jdbcDriverClass: this.dataSourceInfo.driverConfig.jdbcDriverClass
        }
      };
      connectionServer[type](send).then(res => {
        if (type === "test") {
          if (res === false) {
            this.$notify.error({
              title: "error",
              message: "测试链接 失败",
              position: "bottom-right"
            });
          } else {
            if (res.errorDetail) {
              this.$notify.error({
                title: "error",
                message: "Connection refused: no further information",
                position: "bottom-right"
              });
            } else {
              this.$notify.success({
                title: "success",
                message: "测试链接 成功",
                position: "bottom-right"
              });
            }
          }
        } else {
          this.$notify.success({
            title: "success",
            message: type === "update" ? "修改成功" : "添加成功",
            position: "bottom-right"
          });
          this.dialogClose();
          this.$emit("getConnectionList");
        }
      });
    },
    handleClose(done) {
      this.dataSourceInfo = {};
      done();
    },
    dialogClose() {
      this.dialogVisible = false;
      this.dataSourceInfo = {};
    }
  }
};
</script>

<template>
  <el-dialog :title="title" :visible.sync="dialogVisible" width="800" :before-close="handleClose">
    <el-scrollbar>
      <el-form
        v-if="dataSourceInfo && dataSourceInfo.type"
        size="mini"
        ref="form"
        :model="dataSourceInfo"
        label-width="80px"
        :inline="true"
      >
        <FormItem
          :dataSourceInfo="dataSourceInfo"
          :form-config="dataSourceFormConfig ? dataSourceFormConfig.baseInfo.items : []"
        />
        <div class="paddingLeftAndRight40">
          <el-collapse>
            <el-collapse-item title="驱动" v-if="dataSourceInfo.driverConfig">
              <el-form-item label="驱动">
                <el-select v-model="dataSourceInfo.driverConfig.jdbcDriver" placeholder="请选择">
                  <el-option
                    v-for="item in driverConfigList"
                    :key="item.jdbcDriver"
                    :label="item.jdbcDriver"
                    :value="item.jdbcDriver"
                  ></el-option>
                </el-select>
              </el-form-item>
              <el-form-item label="class">
                <el-input disabled v-model="dataSourceInfo.driverConfig.jdbcDriverClass"></el-input>
              </el-form-item>
            </el-collapse-item>
            <el-collapse-item title="SSH">
              <FormItem
                :dataSourceInfo="dataSourceInfo.ssh"
                :form-config="dataSourceFormConfig ? dataSourceFormConfig.ssh.items : []"
              />
            </el-collapse-item>
            <el-collapse-item title="高级配置">
              <ExtendTable :extendTableData="this.dataSourceInfo.extendInfo" />
            </el-collapse-item>
          </el-collapse>
        </div>
      </el-form>
      <div v-else class="dataBaseList">
        <div
          v-for="item in database"
          :key="item.code"
          @click="handleCreateConnections(item)"
          class="databaseItem"
        >
          <i class="icon iconfont">{{ item.icon }}</i>
          {{ item.name }}
        </div>
      </div>
    </el-scrollbar>
    <span slot="footer" class="dialog-footer">
      <el-button
        v-if="dataSourceInfo && dataSourceInfo.type"
        type="primary"
        plain
        @click="saveConnection('test')"
      >测试链接</el-button>
      <el-button type="primary" plain @click="dialogClose">取 消</el-button>
      <el-button type="primary" @click="saveConnection(dataSourceInfo.id ? 'update' : 'save')">确 定</el-button>
    </span>
  </el-dialog>
</template>

<style scoped lang="scss">
::v-deep {
  .el-form--inline .el-form-item {
    margin-right: 0;
  }
  .el-form--inline .el-form-item__content {
    width: calc(100% - 80px);
  }
  .el-scrollbar__wrap {
    max-height: 60vh;
  }
}
.paddingLeftAndRight40 {
  padding: 0 40px;
}
.dataBaseList {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  .databaseItem {
    min-width: 220px;
    border-radius: 4px;
    height: 50px;
    line-height: 50px;
    margin: 10px 20px;
    padding: 0 16px;
    overflow: hidden;
    box-sizing: border-box;
    border: 1px solid #363b41;
    &:hover {
      color: #1668dc;
      border: 1px solid #1668dc;
      cursor: pointer;

      .databaseItemRight {
        display: block;

        i {
          color: #1668dc;
        }
      }
    }
  }
}
@media (max-width: 1000px) {
  .dataBaseList {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
