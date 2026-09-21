<script>
import sqlServer from "@/api/main/sql";
import { v4 as uuidv4 } from "uuid";
import { downloadFile } from "@/utils/file";
import { getToken } from "@/utils/auth";
export default {
  name: "viewAllTable",
  props: {
    uniqueData: {
      type: Object,
      default: {}
    }
  },
  data() {
    return {
      dialogExportVisible: false,
      dialogImportVisible: false,
      exportProgress: 0,
      timer: null,
      tableLoading: false,
      searchKey: "",
      tableData: [],
      tableDataTotal: 0,
      customHeaders: {
        Authorization: "Bearer " + getToken()
      },
      queryParams: {
        pageNo: 1,
        pageSize: 1000
      },
      multipleSelection: [],
      databaseImportInfo: {
        logFile: "",
        errorNum: 0,
        message: []
      }
    };
  },
  watch: {
    uniqueData: {
      handler(newVal) {
        this.getTable();
      },
      immediate: true,
      deep: true
    }
  },
  methods: {
    getTable(params = {}) {
      this.tableLoading = true;
      sqlServer
        .getTableList({
          ...this.uniqueData,
          ...this.queryParams,
          ...params
        })
        .then(res => {
          this.tableDataTotal = res.data.total;
          const data = res.data.data.map(t => {
            const key = uuidv4();
            return {
              uuid: key,
              name: t.name,
              treeNodeType: "table",
              key: t.name,
              pinned: t.pinned,
              comment: t.comment,
              extraParams: {
                ...this.uniqueData,
                tableName: t.name
              }
            };
          });
          this.tableData = data;
        })
        .finally(() => {
          this.tableLoading = false;
        });
    },
    createTable() {
      this.$store.dispatch("addWorkspaceTab", {
        id: uuidv4(),
        title: "新建表",
        type: "createTable",
        uniqueData: {
          ...this.uniqueData
        }
      });
    },
    refresh() {
      this.queryParams.pageNo = 1;
      this.queryParams.pageSize = 1000;
      this.getTable({ refresh: true });
    },
    onSearch() {
      this.queryParams.pageNo = 1;
      this.queryParams.pageSize = 1000;
      this.getTable({ searchKey: this.searchKey });
    },
    // 导出
    handleExport(command) {
      if (command != "c") {
        this.dialogExportVisible = true;
        let send = {
          dataSourceId: this.uniqueData.dataSourceId,
          databaseType: this.uniqueData.databaseType,
          schemaName: this.uniqueData.schemaName,
          databaseName: this.uniqueData.databaseName,
          containData: command === "b"
        };
        if (this.timer) clearInterval(this.timer);
        this.timer = setInterval(() => {
          sqlServer
            .databaseExport({
              ...send,
              refresh: false,
              tableList: this.multipleSelection.length
                ? this.multipleSelection.map(item => item.name)
                : null
            })
            .then(({ data }) => {
              this.exportProgress = (
                (data.exported / data.total) *
                100
              ).toFixed(2);
              if (data.status === "FINISH") {
                clearInterval(this.timer);
                this.dialogExportVisible = false;
                this.exportProgress = 0;
                downloadFile(
                  process.env.VUE_APP_BASE_API +
                    "/api/rdb/database/export2/download",
                  {
                    ...send,
                    delete: true
                  }
                );
              }
            });
        }, 1000);
      } else {
        let send = {
          tableList: this.multipleSelection.length
            ? this.multipleSelection.map(item => item.name)
            : null,
          dataSourceId: this.uniqueData.dataSourceId,
          schemaName: this.uniqueData.schemaName,
          databaseName: this.uniqueData.databaseName
        };
        sqlServer.exportDmp(send).then(res => {
          downloadFile(
            process.env.VUE_APP_BASE_API +
              "/api/rdb/database/downloadExportDmp",
            {
              ...send
              // delete: true
            }
          );
        });
      }
    },
    // 导入
    handleAvatarSuccess(response, file) {
      let send = {
        dataSourceId: this.uniqueData.dataSourceId,
        schemaName: this.uniqueData.schemaName,
        databaseName: this.uniqueData.databaseName,
        importUrl: response.fileName
      };
      this.dialogImportVisible = true;
      this.timer = setInterval(() => {
        sqlServer.databaseImport(send).then(({ data }) => {
          this.databaseImportInfo = data;
          if (data.status === "FINISH") {
            clearInterval(this.timer);
            this.$message.success("导入成功！");
          }
        });
      }, 1000);
    },
    handleAvatarSuccess2(response) {
      let send = {
        dataSourceId: this.uniqueData.dataSourceId,
        schemaName: this.uniqueData.schemaName,
        databaseName: this.uniqueData.databaseName,
        importUrl: response.fileName,
        dmpUrl: response.fileName,
        tableList: this.multipleSelection.length
          ? this.multipleSelection.map(item => item.name)
          : null
      };
      // this.dialogImportVisible = true;
      sqlServer.importDmp(send).then(res => {
        send.logUrl = res.data.logUrl;
        sqlServer.downloadImportLog(send).then(res => {
          this.getTable();
        });
      });
    },
    download_Logs() {
      if (this.databaseImportInfo.status === "FINISH") {
        if(this.databaseImportInfo.msg != ""&&this.databaseImportInfo.msg != null){
          this.$message.warning(this.databaseImportInfo.msg);
        }else{
          downloadFile(
            process.env.VUE_APP_BASE_API + "/api/rdb/database/import/download",
            {
              dataSourceId: this.uniqueData.dataSourceId,
              databaseType: this.uniqueData.databaseType,
              schemaName: this.uniqueData.schemaName,
              databaseName: this.uniqueData.databaseName,
              importUrl: this.databaseImportInfo.logFile
            }
          );
        }
      } else {
        this.$message.warning("数据表导入未结束！");
      }
    },
    handleClose() {
      this.databaseImportInfo = {
        logFile: "",
        errorNum: 0,
        message: []
      };
      this.dialogImportVisible = false;
    },
    beforeAvatarUpload(file) {
      const isLt50M = file.size / 1024 / 1024 < 50;

      if (!isLt50M) {
        this.$message.error("上传文件大小不能超过 50MB!");
      }
      return isLt50M;
    },
    handleSizeChange(val) {
      this.queryParams.pageNo = 1;
      this.queryParams.pageSize = val;
      this.getTable();
    },
    handleCurrentChange(val) {
      this.queryParams.pageNo = val;
      this.getTable();
    },
    handleSelectionChange(val) {
      this.multipleSelection = val;
    }
  }
};
</script>

<template>
  <div class="table_box">
    <div class="table_btn_list">
      <el-button
        @click="createTable"
        size="mini"
        type="text"
        class="el-button_before"
        style="margin-left: 0"
      >
        <img src="@/assets/main/3-con-ico01.png" alt />
        添加表
      </el-button>
      <el-button
        @click="refresh"
        size="mini"
        type="text"
        class="el-button_before"
      >
        <img src="@/assets/main/5-ico3.png" alt />
        刷新
      </el-button>
      <el-input
        v-model="searchKey"
        size="mini"
        placeholder="搜索"
        style="width: 200px; margin: 0 5px"
      />
      <el-button @click="onSearch" size="mini" type="primary">
        <img src="@/assets/main/5-ico4.png" alt />
        查询
      </el-button>
      <el-dropdown
        @command="handleExport"
        trigger="click"
        style="margin-left: 10px"
      >
        <el-button size="mini" type="primary" plain>
          <img src="@/assets/main/3-con-ico05.png" alt />
          导出
        </el-button>
        <el-dropdown-menu slot="dropdown">
          <el-dropdown-item command="a">导出选中数据模板</el-dropdown-item>
          <el-dropdown-item command="b">导出选中数据</el-dropdown-item>
          <el-dropdown-item command="c">导出DMP</el-dropdown-item>
        </el-dropdown-menu>
      </el-dropdown>
      <el-upload
        accept=".sql"
        class="hide-file-list"
        action="/dev-api/common/upload"
        :headers="customHeaders"
        :on-success="handleAvatarSuccess"
        :before-upload="beforeAvatarUpload"
      >
        <el-button size="mini" type="primary" plain style="margin-left: 10px">
          <img src="@/assets/main/3-con-ico04.png" alt />
          导入SQL
        </el-button>
      </el-upload>
      <el-upload
        accept=".dmp"
        class="hide-file-list"
        action="/dev-api/common/upload"
        :headers="customHeaders"
        :on-success="handleAvatarSuccess2"
        :before-upload="beforeAvatarUpload"
      >
        <el-button size="mini" type="primary" plain style="margin-left: 10px">
          <img src="@/assets/main/3-con-ico04.png" alt />
          导入DMP
        </el-button>
      </el-upload>
    </div>
    <el-table
      :data="tableData"
      v-loading="tableLoading"
      size="mini"
      style="width: 100%"
      height="calc(100% - 80px)"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="55"></el-table-column>
      <el-table-column prop="name" label="表名"></el-table-column>
      <el-table-column prop="comment" label="备注"></el-table-column>
    </el-table>
    <div class="table_bottom_box">
      <el-pagination
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
        :current-page="queryParams.pageNo"
        :page-sizes="[10, 50, 100, 200, 500, 1000]"
        :page-size="queryParams.pageSize"
        layout="total, sizes, prev, pager, next, jumper"
        :total="tableDataTotal"
      ></el-pagination>
    </div>
    <el-dialog width="30%" :visible.sync="dialogExportVisible" title="导出进度">
      <p>
        导出进度:
        <el-progress :percentage="exportProgress"></el-progress>
      </p>
    </el-dialog>
    <el-dialog
      title="批量导入确认"
      :visible.sync="dialogImportVisible"
      :before-close="handleClose"
    >
      <div class="left_right_title">
        <p>请确认导入数据是否正确。</p>
        <p>
          <span style="color: red"
            >当前有问题数据{{ databaseImportInfo.errorNum }}行</span
          >
          ，具体可
          <span style="color: #1890ff; cursor: pointer" @click="download_Logs"
            >下載问题日志查看</span
          >。
        </p>
      </div>
      <ul class="errorMessageList" style="overflow: auto; max-height: 200px">
        <li
          v-for="(item, index) in databaseImportInfo.message"
          :key="index"
          class="infinite-list-item"
        >
          {{ item }}
        </li>
      </ul>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="handleClose">关 闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.table_box {
  height: calc(100% - 58px);
  background: #fff;
  padding: 0 20px;
  border-radius: 5px;
  .table_btn_list {
    display: flex;
    align-items: center;
    padding: 10px 0;
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
  .table_bottom_box {
    display: flex;
    align-items: center;
    justify-content: end;
    .table_bottom_info {
      font-size: 12px;
      color: rgba(35, 36, 41, 0.88);
      & > span {
        margin-right: 16px;
      }
    }
  }
}
.errorMessageList {
  list-style: none;
  line-height: 22px;
  font-size: 14px;
  padding-left: 0;
}
.left_right_title {
  display: flex;
  justify-content: space-between;
}
/* 隐藏上传文件列表 */
.hide-file-list ::v-deep .el-upload-list {
  display: none;
}
</style>
