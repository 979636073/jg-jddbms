<script>
import Hamburger from "@/components/Hamburger/index.vue";
import { mapGetters } from "vuex";
import connectionServer from "@/api/main/connection";
import sqlServer from "@/api/main/sql";
import tableServer from "@/api/main/table";
import { downloadFile } from "@/utils/file";
import { getToken } from "@/utils/auth";
import { getUserProfile } from "@/api/system/user";
import { v4 as uuidv4 } from "uuid";
import { TASK_STATUS } from "@/constants/task";
import TaskDrawer from "@/views/main/task/TaskDrawer.vue";

export default {
  name: "databaseHeader",
  dicts: ["role_user"],
  components: { Hamburger, TaskDrawer },
  props: {
    opened: {
      type: Boolean,
      default: true,
    },
    collapsed: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      contextMenu_top: 0,
      contextMenu_left: 0,
      contextMenuVisible: false,
      taskDrawerVisible: false,
      runningTaskCount: 0,
      exportProgress: "0%",
      loading2: false,
      navType: "editor",
      ExportDialog: false,
      ImportDialog: false,
      radio: "user",
      radioImp: "user",
      isDialog: "",
      scheName: "",
      checked: false,
      checkedImp: false,
      radioType: "all",
      schemaData: [],
      importArchive: "",
      exportArchive: [],
      filterObj: {},
      archiveObj: [],
      isFile: false,
      count: 0,
      multipleSelection: [],
      formData: {
        user: "",
        toUser: "",
      },
      searchFrom: {
        user: "",
        toUser: "",
      },
      optionImp: [],
      option: [
        {
          id: 1,
          label: "SYSTEM",
        },
        {
          id: 2,
          label: "TEXT",
        },
      ],
      obj: {},
      tableData: [],
      uploadInput: "",
      userInfo: {},
      uploadImport: "",
      fileName: "",
      url: "",
      seatchTable: "",
      timer: 0,
      timeCount: 0,
      loading: false,
      loadingTime: false,
      transferValue: [],
      transferData: [],
      transferVal: [],
      transfer: [],
      selectTableData: [],
      fromUser: [],
      fromToUser: [],
      exportAddress: 6,
      serverPath: "",
      serverArray: [],
      isSearchTable: false,
      searchTableData: [],
      selectArray: [],
      label: [],
      maxSize: "5",
      // 存储归档信息
      acrhive: {},
      // 存储表名
      acrhiveArray: [],
      headers: [],
      page: {
        pageSize: 100,
        pageNum: 1,
        total: 0,
      },
      form: {
        savePath: "",
      },
      customHeaders: {
        Authorization: "Bearer " + getToken(),
      },
      defaultProps: {
        children: "children",
        label: "label",
      },
      importCopyTable: [], //导入复制对应表选中数组
      exportUser: [],
      navList: [
        {
          name: "新建窗口",
          type: "editor",
          icon: require("@/assets/main/top-ico3.png"),
          iconHover: require("@/assets/main/top-ico3-hover.png"),
        },
        {
          name: "新建查询",
          type: "search",
          icon: require("@/assets/main/top-ico2.png"),
          iconHover: require("@/assets/main/top-ico2-hover.png"),
        },
        {
          name: "导入文件",
          type: "import",
          icon: require("@/assets/main/top-ico4.png"),
          iconHover: require("@/assets/main/top-ico4-hover.png"),
          hasRole: true,
        },
        {
          name: "导出文件",
          type: "export",
          icon: require("@/assets/main/top-ico5.png"),
          iconHover: require("@/assets/main/top-ico5-hover.png"),
          hasRole: true,
        },
        {
          name: "新建连接",
          type: "datasource",
          icon: require("@/assets/main/top-ico6.png"),
          iconHover: require("@/assets/main/top-ico6-hover.png"),
        },
      ],
    };
  },
  computed: {
    ...mapGetters(["avatar", "name", "dataInfo"]),
    openConfigList() {
      return this.$store.state.workspace.workspaceTabList;
    },
    userNameHasRole() {
      let roleUser = this.dict.type.role_user;
      return roleUser.some((item) => item.value == this.dataInfo.userName);
    },
    pageId() {
      return this.$route.params.id;
    },
    filterTableData() {
      if (this.seatchTable) {
        return this.selectTableData.filter((item) => {
          return (
            item.name.toLowerCase().indexOf(this.seatchTable.toLowerCase()) !=
            -1
          );
        });
      } else {
        return this.selectTableData;
      }
    },
  },
  watch: {
    $route: {
      immediate: true,
      handler(val) {
        const matched = this.navList.find((item) => item.route === val.path);
        this.navType = val.path.includes("/workspace/query/")
          ? "search"
          : (matched ? matched.type : "editor");
      },
    },
    contextMenuVisible(value) {
      if (value) {
        document.body.addEventListener("click", this.closeMenu);
      } else {
        document.body.removeEventListener("click", this.closeMenu);
      }
    },
    radio(newVal) {
      if (newVal == "user") {
        this.getDataBaseTree();
      }
    },
    // 导入 切换radio时清除上传文件
    radioImp(newVal) {
      this.deleteFile();
    },
    // 导入如果user输入为空 不走过滤
    "formData.user": {
      handler(newVal) {
        if (newVal == "" || newVal == null) {
          this.getDataBaseTree();
        }
      },
    },
    isSearchTable(newVal) {
      if (newVal) {
        this.querySearchTableData();
      }
    },
  },
  created() {
    this.getUser();
  },
  mounted() {
    this.loading2 = false;
    this.$EventBus.$on("openLookPage", (data) => {
      this.sqlDetailTableData(data);
      // this.navClick({ name: "新建窗口", type: "editor" }, data);
      console.log("自动查询");
    });
    this.getServerPath();
    this.$EventBus.$on("openTaskCenter", this.openTaskCenter);
  },
  beforeDestroy() {
    this.$EventBus.$off("openLookPage");
    this.$EventBus.$off("openTaskCenter", this.openTaskCenter);
  },
  methods: {
    closeAllnewSearch() {
      this.$confirm("是否确认关闭所有新建查询窗口?", "提示", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      }).then(() => {
        this.$store.commit("SET_ISCLOSEALL", true);
      });
    },
    closeMenu() {
      this.contextMenuVisible = false;
    },
    creatRightMenu(item, e) {
      // console.log(item, e);
      if (item.name !== "新建查询") return;
      e.preventDefault();
      this.contextMenu_top = e.clientY + 10;
      this.contextMenu_left = e.clientX;
      this.contextMenuVisible = true;
    },
    // 从sql控制台跳转到表数据
    sqlDetailTableData(data) {
      let tableName = data.tableName.replaceAll('"', "");
      let pageId = uuidv4();
      this.$router.push({
        path: "/workspace/look/" + pageId,
        query: { schemaName: data.databaseName, tableName: tableName },
      });
    },
    // 获取服务器地址
    getServerPath() {
      sqlServer.queryServerPath().then((res) => {
        if (res.data) {
          this.serverArray = res.data;
        }
      });
    },
    queryArchiveTitleFn() {
      sqlServer.queryArchiveTitle().then((res) => {
        if (res.code == 200) {
          this.archiveObj = res.data;
        }
      });
    },
    // 获取用户
    getDataBaseTree() {
      const params = {
        dataSourceId: this.dataInfo.dataSource.id,
        dataSourceName: this.dataInfo.dataSource.alias,
        refresh: true,
      };
      connectionServer.getSchemaList(params).then((res) => {
        if (res.data.length > 0) {
          this.schemaData = res.data;
          this.transferData = res.data;
          this.optionImp = res.data;
        }
      });
    },
    // 过滤导入文件筛选条件
    filterData() {
      if (this.formData.user == "" || this.formData.user == null) {
        this.getDataBaseTree();
        return;
      }
      let list = this.optionImp.filter(
        (item) => item.name.indexOf(this.formData.user) == -1
      );
      this.optionImp = list;
    },
    //搜索
    // seatchTableFn() {
    //   this.getTableDataList();
    // },
    // 获取表数据
    getTableDataList() {
      const params = {
        dataSourceId: this.dataInfo.dataSource.id,
        dataSourceName: this.dataInfo.dataSource.alias,
        databaseType: this.dataInfo.dataSource.type,
        schemaName: this.scheName || this.dataInfo.userName,
        refresh: true,
        pageNo: 1,
        pageSize: 1000,
        requestType: 1,
      };
      tableServer.getTableList(params).then((res) => {
        if (res.data.data.length > 0) {
          this.selectTableData = res.data.data;
          // this.transfer = res.data.data;
        } else {
          this.selectTableData = [];
          this.transfer = [];
        }
      });
    },
    queryTransferData() {
      const params = {
        dataSourceId: this.dataInfo.dataSource.id,
        dataSourceName: this.dataInfo.dataSource.alias,
        databaseType: this.dataInfo.dataSource.type,
        schemaName: this.formData.toUser,
        refresh: true,
        pageNo: 1,
        pageSize: 1000,
        requestType: 1,
      };
      tableServer.getTableList(params).then((res) => {
        if (res.data.data.length > 0) {
          this.transfer = res.data.data;
        } else {
          this.selectTableData = [];
          this.transfer = [];
        }
      });
    },
    // 点击导航
    async navClick(item, params) {
      this.navType = item.type;
      if (item.route) {
        this.$router.push({ path: item.route });
      } else if (item.name === "新建连接") {
        window.open("/workspace?isShow=true");
      } else if (item.name === "新建查询") {
        let pageId = uuidv4();
        this.$store.commit("SET_ISCLOSEALL", false);
        await this.$store
          .dispatch("workspaceData/createConsole", {
            pageId,
            dataSourceId: this.dataInfo.dataSource.id,
            dataSourceName: this.dataInfo.dataSource.alias,
            databaseType: this.dataInfo.dataSource.type,
          })
          .then((res) => {
            this.$router.push({
              path: "/workspace/query/" + pageId,
            });
          });
      } else if (item.name == "新建窗口") {
        // this.$store.dispatch("addWorkspaceTab", false);
        let pageId = uuidv4();
        this.$router.push({
          path: "/workspace/look/" + pageId,
          query: { ...params },
        });
      } else {
        this.queryArchiveTitleFn();
      }
    },
    logManagement() {
      this.$router.push({ path: "/logManagement" });
    },
    toggleSideBar() {
      this.$emit("toggleSideBar");
    },
    async logout() {
      this.$confirm("确定注销并退出系统吗？", "提示", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      })
        .then(() => {
          this.$store.dispatch("LogOut").then(() => {
            location.href = "/index";
          });
        })
        .catch(() => {});
    },
    // 导出关闭操作
    handleClose() {
      this.ExportDialog = false;
      this.selectTableData = [];
      this.multipleSelection = [];
      this.schemaData = [];
      this.scheName = "";
      this.transferValue = [];
      this.transferData = [];
      this.loading = false;
      this.exportArchive = [];
      this.serverPath = "";
      this.maxSize = "5";
      this.seatchTable = "";
      this.exportUser = [];
      if (this.isDialog !== "exportDMP") {
        this.deleteFile();
      }

      clearInterval(this.timer);
    },
    handleCloseSearch() {
      this.isSearchTable = false;
      this.searchTableData = [];
      this.headers = [];
      this.page.total = 0;
      this.page.pageSize = 100;
      this.page.pageNum = 1;
    },
    // 导出提交
    exportSubmit() {
      let params = {};
      if (this.isDialog == "exportArchive") {
        if (!this.maxSize) {
          return this.$message.error("请设置最大值!");
        } else if (!this.serverPath && this.exportAddress == 3) {
          return this.$message.error("请选择服务器路径!");
        } else if (!this.exportArchive.length) {
          return this.$message.error("请选择查询条件!");
        }
        this.loading = true;
        const params = {
          filterValue: this.exportArchive,
          dataSourceId: this.dataInfo.dataSource.id,
          pigenholeId: this.acrhive.id,
          maxSize: this.maxSize,
          uploadUrl: this.exportAddress == 3 ? this.serverPath : undefined,
        };
        sqlServer.queryRule(params).then((res) => {
          if (res.success && res.data) {
            this.$message.success(res.data);
            this.loading = false;
          } else if (res.success && res.data == undefined) {
            downloadFile(
              process.env.VUE_APP_BASE_API + "/api/rdb/pigenhole/exportTable",
              { ...params }
            );
            this.loading = false;
          } else {
            this.$message.error("导出失败!");
            this.loading = false;
          }
        });
      } else {
        if (this.radio == "table" && this.isDialog == "exportDMP") {
          if (!this.scheName) {
            return this.$message.error("请选择数据源!");
          } else if (!this.multipleSelection.length) {
            return this.$message.error("请选择表!");
          }
          params = {
            tableList: this.multipleSelection.length
              ? this.multipleSelection.map((item) => item.name)
              : null,
            dataSourceId: this.dataInfo.dataSource.id,
            schemaName: this.scheName,
            dataBaseName: this.dataInfo.dataSource.alias,
          };
        } else if (this.radio == "user" && this.isDialog == "exportDMP") {
          if (!this.transferValue.length) {
            return this.$message.error("请选择可用用户!");
          }
          params = {
            dataSourceId: this.dataInfo.dataSource.id,
            schemaName: this.scheName,
            dataBaseName: this.dataInfo.dataSource.alias,
            schemaList: this.transferValue,
          };
        } else if (this.radio == "database" && this.isDialog == "exportDMP") {
          params = {
            dataSourceId: this.dataInfo.dataSource.id,
            dataBaseName: this.dataInfo.dataSource.alias,
          };
        }
        this.loading = true;
        sqlServer.exportDmp(params).then((res) => {
          if (res.success && res.data) {
            this.$message.success("导出任务已提交，请到任务中心查看进度和下载文件");
            this.taskDrawerVisible = true;
            this.handleClose();
          } else {
            this.$message.error(res.errorMessage || "导出任务提交失败");
          }
        }).finally(() => { this.loading = false; });
      }
    },
    // 获取导出表格选中的数据
    handleSelectionChange(val) {
      this.multipleSelection = val;
    },
    handleExportSchemaChange() {
      this.multipleSelection = [];
      this.seatchTable = "";
      this.selectTableData = [];
      this.getTableDataList();
    },
    submitImport() {
      if (JSON.stringify(this.filterObj) == "{}") {
        return this.$message.error("请先上传文件!");
      }
      this.$confirm("是否确认导入?", "提示", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
        customClass: "submitImport",
      })
        .then(() => {
          if (this.isDialog == "importDMP") {
            return this.submitDmpImportTask();
          } else if (this.isDialog == "importArchive") {
            let fileName = this.filterObj.fileName;
            let pos = fileName.lastIndexOf(".");
            let lastName = fileName.substring(pos, fileName.length);
            if (lastName.toLowerCase() !== ".xlsx") {
              return this.$message.error("必须为.XLSX结尾的文件！");
            }
            const params = {
              id: this.importArchive,
              dataSourceId: this.dataInfo.dataSource.id,
              filePath: this.url,
              pigenholeId: this.acrhive.id,
            };
            this.loadingTime = true;
            sqlServer.importArchive(params).then((res) => {
              if (res.success) {
                this.loadingTime = false;
                this.handleCloseFn();
                this.$message.success("导入成功");
              } else if (!res.success) {
                this.$message.error(res.errorCode);
                this.loadingTime = false;
              }
            });
          }
        })
        .catch(() => {
          this.$message({
            type: "info",
            message: "已取消导入",
          });
        });
    },
    submitDmpImportTask() {
      const fileName = this.filterObj.fileName || '';
      if (!/\.dmp$/i.test(fileName)) {
        return this.$message.error("必须为.DMP结尾的文件！");
      }
      const params = {
        fileName: this.fileName,
        filePath: this.url,
        dataSourceId: this.dataInfo.dataSource.id
      };
      if (this.radioImp === "table") {
        if (!this.formData.user || !this.formData.toUser) {
          return this.$message.error("请输入源用户或目的用户");
        }
        params.fromName = this.formData.user;
        params.toName = this.formData.toUser;
        if (this.radioType === "copyTable") {
          if (!this.importCopyTable.length) {
            return this.$message.error("请选择可用表!");
          }
          params.tableList = this.importCopyTable;
          params.schemaName = this.formData.user;
        }
      } else if (this.radioImp === "user") {
        if (!this.searchFrom.user || !this.searchFrom.toUser) {
          return this.$message.error("请输入源用户和目的用户!");
        }
        params.fromUser = [this.searchFrom.user];
        params.toUser = [this.searchFrom.toUser];
      }
      this.loadingTime = true;
      const submit = this.radioImp === "user" ? sqlServer.importDmpUser(params) : sqlServer.importDmp(params);
      submit.then((res) => {
        if (res.success && res.data) {
          this.$message.success("导入任务已提交，请到任务中心查看进度");
          this.handleCloseFn(true);
        } else {
          this.$message.error(res.errorMessage || "导入任务提交失败");
        }
      }).finally(() => { this.loadingTime = false; });
    },
    handleCloseFn(keepUpload = false) {
      this.ImportDialog = false;
      this.radioType = "all";
      this.radioImp = "user";
      this.formData.user = "";
      this.formData.toUser = "";
      this.url = "";
      this.fileName = "";
      this.transferVal = [];
      this.searchFrom.user = "";
      this.searchFrom.toUser = "";
      this.tableData = [];
      this.importArchive = "";
      this.loadingTime = false;
      this.$refs.commonUpload.clearFiles();
      this.importCopyTable = [];
      clearInterval(this.timeCount);
      if (!keepUpload) {
        this.deleteFile();
      }
    },
    // 最大值只支持正整数
    changeValue(val) {
      this.maxSize = /^[0-9]*$/.test(parseInt(val))
        ? String(parseInt(val)).replace(".", "")
        : "";
    },
    //导入新增操作
    addTable() {
      if (!this.searchFrom.user || !this.searchFrom.toUser) {
        return this.$message.error("请输入源用户或选择目的用户!");
      }
      this.tableData.push({
        user: this.searchFrom.user,
        touser: this.obj.name,
      });
      this.fromUser = [];
      this.fromToUser = [];
      this.tableData.forEach((item) => {
        this.fromUser.push(item.user);
        this.fromToUser.push(item.touser);
      });
      this.searchFrom.user = "";
      this.searchFrom.toUser = "";
    },
    //前端导入删除表操作
    deleteData(row) {
      let index = this.tableData.findIndex((item) => {
        return row.id == item.id;
      });
      if (index !== -1) {
        this.tableData.splice(index, 1);
      }
      this.fromUser = [];
      this.fromToUser = [];
      this.tableData.forEach((item) => {
        this.fromUser.push(item.user);
        this.fromToUser.push(item.touser);
      });
    },
    updataValue(row) {
      this.obj = this.optionImp.find((item) => {
        return item.name == row;
      });
    },
    handleBeforeUpload(file) {
      this.loading2 = true;
      let maxSize = 20 * 1024 * 1024 * 1024; // 4G
      console.log(file.size, maxSize);
      if (file.size > maxSize) {
        this.loading2 = false;
        this.$message.error("上传文件过大");
        this.deleteFile();
        return false;
      }
    },
    hanldeUploadError(err, file, fileList) {
      console.log(err, file, fileList, "err, file, fileList)");
    },
    // 文件上传时 如果点击取消 终止上传、loading
    handleProgress(event, file, fileList) {
      if (this.loading2) {
        if (!file) {
          this.loading2 = false; // 关闭loading
          this.$message.error("文件上传中断！！！");
          this.$refs.commonUpload.abort(); // 取消文件上传
        }
      }
    },
    handleAvatarSuccess(response) {
      this.loading2 = false;
      const uploadResult = response.data || response;
      this.filterObj = uploadResult;
      this.fileName = uploadResult.fileName;
      this.url = uploadResult.url;
      if (this.filterObj) {
        this.isFile = true;
      }
    },
    handleCommand(command) {
      this.isDialog = command;
      if (command == "importDMP") {
        this.ImportDialog = true;
        this.getTableDataList();
        this.getDataBaseTree();
      } else if (command == "exportDMP") {
        this.selectTableData = [];
        this.multipleSelection = [];
        this.transferValue = [];
        this.scheName = "";
        this.ExportDialog = true;
        this.radio = "table";
        this.getDataBaseTree();
      }
    },
    handleCommandFn(command) {
      this.isDialog = command;
      if (command == "exportArchive") {
        this.ExportDialog = true;
      } else if (command == "importArchive") {
        this.ImportDialog = true;
      }
    },
    itemDown(type) {
      console.log(type, "2");
      this.acrhive = type;
      this.acrhiveArray = this.acrhive.tables.split(",");
    },
    getUser() {
      getUserProfile().then((response) => {
        this.userInfo = response.data;
      });
    },
    deleteFile() {
      this.isFile = false;
      this.$refs.commonUpload.clearFiles();
      this.url = "";
      this.fileName = "";
      this.filterObj = {};
    },
    searchDialog() {
      this.isSearchTable = true;
    },
    querySearchTableData() {
      this.loading = true;
      const params = {
        dataSourceId: this.dataInfo.dataSource.id,
        databaseType: this.dataInfo.dataSource.type,
        databaseName: "",
        hasNextPage: true,
        pageNo: this.page.pageNum,
        pageSize: this.page.pageSize,
        schemaName: this.acrhive.userName,
        sql: `select * from ${
          this.acrhive.userName || this.dataInfo.dataSource.alias
        }.${this.acrhiveArray[0]}`,
        tableName: this.acrhiveArray[0],
        total: 0,
        type: this.dataInfo.dataSource.type,
      };
      sqlServer.viewTable(params).then((res) => {
        if (res.success) {
          if (res.data.length && res.data[0].success) {
            this.headers = res.data[0].headerList;
            this.page.total = Number(res.data[0].fuzzyTotal);
            let tableData = res.data[0].dataList?.map((item) => {
              let array = {};
              for (const dataKey in res.data[0].headerList) {
                array[res.data[0].headerList[dataKey].name] = item[dataKey];
              }
              return array;
            });
            this.searchTableData = tableData;
            this.loading = false;
          } else {
            this.loading = false;
          }
        } else {
          this.$message.error(res.errorMessage);
          this.loading = false;
        }
      });
    },
    handleSelection(val) {
      this.selectArray = val;
      this.label = [];
      this.selectArray.forEach((item) => {
        for (let key in item) {
          if (this.acrhive.filter == key) {
            this.label.push(item[key]);
          }
        }
      });
    },
    saveSelect() {
      this.searchTableData = [];
      this.exportArchive = this.label;
      this.isSearchTable = false;
    },
    handleSizeChange(val) {
      this.page.pageSize = val;
      this.querySearchTableData();
    },
    handleCurrentChange(val) {
      this.page.pageNum = val;
      this.querySearchTableData();
    },
    // 导入复制表穿梭框
    handleChange(val) {
      this.importCopyTable = val;
    },
    // 导出用户穿梭框
    exportHandleChange(val) {
      this.exportUser = this.transferValue;
    },
    // 下载帮助文档
    downLoadHelp() {
      downloadFile(
        process.env.VUE_APP_BASE_API + "/api/rdb/database/helpDocument"
      );
    },
    updateRunningTaskCount(count) {
      this.runningTaskCount = count;
    },
    openTaskCenter() {
      this.taskDrawerVisible = true;
    },
  },
};
</script>

<template>
  <div class="header" :class="{ 'header-collapsed': collapsed }">
    <!-- <hamburger
      id="hamburger-container"
      :is-active="opened"
      class="hamburger-container"
      @toggleClick="toggleSideBar"
    />-->
    <div class="brand-area">
      <div class="imgBox">
        <img src="../../../../src/assets/images/logo.png" alt />
        <span class="title">数据库管理软件</span>
      </div>
    </div>
    <el-button
      class="header-toggle"
      type="text"
      :title="collapsed ? '展开标题栏' : '收起标题栏'"
      @click="$emit('toggleHeader')"
    >
      <i :class="collapsed ? 'el-icon-arrow-down' : 'el-icon-arrow-up'"></i>
    </el-button>
    <div class="nav_list">
      <template v-for="item in navList">
        <div
          v-if="!item.hasRole || (item.hasRole && userNameHasRole)"
          :key="item.type"
          :class="{ nav_item: true, nav_item_action: navType === item.type }"
          @click="navClick(item)"
          @contextmenu="creatRightMenu(item, $event)"
        >
          <el-dropdown
            trigger="click"
            @command="handleCommand"
            :hide-on-click="true"
          >
            <div class="dropdown">
              <img
                :src="navType === item.type ? item.iconHover : item.icon"
                alt
              />
              <span>{{ item.name }}</span>
            </div>
            <el-dropdown-menu slot="dropdown" v-if="item.type == 'import'">
              <el-dropdown-item command="importDMP">导入DMP</el-dropdown-item>
              <el-dropdown placement="right" @command="handleCommandFn">
                <el-dropdown-item>
                  归档导入
                  <i class="el-icon-arrow-right"></i>
                </el-dropdown-item>
                <el-dropdown-menu slot="dropdown">
                  <el-dropdown-item
                    v-for="item in archiveObj"
                    :key="item.id"
                    @click.native="itemDown(item)"
                    command="importArchive"
                    >{{ item.taskName }}</el-dropdown-item
                  >
                </el-dropdown-menu>
              </el-dropdown>
            </el-dropdown-menu>
            <el-dropdown-menu slot="dropdown" v-else></el-dropdown-menu>
            <el-dropdown-menu slot="dropdown" v-if="item.type == 'export'">
              <el-dropdown-item command="exportDMP">导出DMP</el-dropdown-item>
              <!-- 三层 -->
              <el-dropdown placement="right" @command="handleCommandFn">
                <el-dropdown-item>
                  归档导出
                  <i class="el-icon-arrow-right"></i>
                </el-dropdown-item>
                <el-dropdown-menu slot="dropdown">
                  <el-dropdown-item
                    v-for="item in archiveObj"
                    :key="item.id"
                    @click.native="itemDown(item)"
                    command="exportArchive"
                    >{{ item.taskName }}</el-dropdown-item
                  >
                </el-dropdown-menu>
              </el-dropdown>
            </el-dropdown-menu>
          </el-dropdown>
        </div>
      </template>
    </div>

    <div
      class="rightMenu"
      v-show="contextMenuVisible"
      :style="{ left: contextMenu_left + 'px', top: contextMenu_top + 'px' }"
    >
      <el-button type="text" @click="closeAllnewSearch" size="mini">关闭所有</el-button>
    </div>

    <el-tooltip
      class="item"
      effect="dark"
      content="帮助文档"
      placement="top-start"
    >
      <span class="help" @click="downLoadHelp">
        <!-- <i class="el-icon-question"></i> -->
      </span>
    </el-tooltip>
    <el-badge
      :value="runningTaskCount"
      :hidden="runningTaskCount === 0"
      class="task-shortcut"
    >
      <el-button
        type="text"
        icon="el-icon-bell"
        title="任务中心"
        @click="taskDrawerVisible = true"
      />
    </el-badge>
    <el-dropdown
      class="avatar-container right-menu-item hover-effect"
      trigger="click"
    >
      <div class="avatar-wrapper">
        <!-- <img :src="avatar" class="user-avatar" /> -->
        <span>{{ userInfo.userName }}</span>
        <i class="el-icon-caret-bottom" />
      </div>
      <el-dropdown-menu slot="dropdown">
        <router-link to="/user/profile">
          <el-dropdown-item>个人中心</el-dropdown-item>
        </router-link>
        <el-dropdown-item @click.native="logManagement">
          <span>日志管理</span>
        </el-dropdown-item>
        <el-dropdown-item divided @click.native="logout">
          <span>退出登录</span>
        </el-dropdown-item>
      </el-dropdown-menu>
    </el-dropdown>
    <task-drawer
      :visible.sync="taskDrawerVisible"
      @running-count="updateRunningTaskCount"
    />
    <!-- 导出 -->
    <el-dialog
      :title="
        isDialog == 'exportArchive' ? acrhive.taskName || '归档导出' : '导出'
      "
      :visible.sync="ExportDialog"
      width="870px"
      :before-close="handleClose"
    >
      <span v-if="isDialog == 'exportArchive'">
        <div>
          表：<el-tag
            style="margin-left: 10px; margin-bottom: 10px"
            v-for="item in acrhiveArray"
            :key="item"
            >{{ item }}</el-tag
          >
        </div>
        <div style="margin-bottom: 10px">
          <el-radio-group v-model="exportAddress">
            <el-radio :label="6">导出到本地</el-radio>
            <el-radio :label="3">导出到服务器</el-radio>
          </el-radio-group>
        </div>
        <div style="margin-bottom: 10px" v-if="exportAddress == 3">
          <span>服务器路径</span>
          <el-select
            v-model="serverPath"
            size="mini"
            placeholder="请选择服务器路径"
            style="width: 100%"
          >
            <el-option
              v-for="item in serverArray"
              :key="item"
              :label="item"
              :value="item"
            >
            </el-option>
          </el-select>
        </div>
        <span>文件最大值(MB)</span>
        <el-input
          v-model="maxSize"
          style="margin-bottom: 10px"
          placeholder="请输入大小"
          size="mini"
          @input="changeValue"
        ></el-input>
        <span>{{ acrhive.filter }}</span>
        <el-input v-model="exportArchive" disabled size="mini"
          ><el-button
            slot="append"
            icon="el-icon-search"
            @click="searchDialog"
          ></el-button
        ></el-input>
      </span>
      <span v-else>
        <div class="exportTitle">
          <el-radio-group
            v-model="radio"
            size="small"
            class="exportBox"
            fill="#006fff"
          >
            <el-radio-button label="table">导出表</el-radio-button>
            <el-radio-button label="user">导出用户</el-radio-button>
            <el-radio-button label="database">导出整库</el-radio-button>
          </el-radio-group>
        </div>
        <div style="margin-top: 20px" v-if="this.radio == 'table'">
          <div class="titleFrom">
            <span style="color: #313745; font-size: 14px">源用户</span>
            <el-select
              style="margin-left: 14px"
              v-model="scheName"
              size="small"
              @change="handleExportSchemaChange"
              @focus="getDataBaseTree"
              placeholder="请选择数据源"
              filterable
            >
              <el-option
                v-for="(item, index) in schemaData"
                :key="index"
                :label="item.name"
                :value="item.name"
              ></el-option>
            </el-select>
            <el-input
              v-model="seatchTable"
              placeholder="搜索表名"
              size="small"
              class="searchInput"
            ></el-input>
          </div>
          <div class="treeClass">
            <el-table
              ref="multipleTable"
              :data="filterTableData"
              :header-cell-style="{ background: '#f1f3f9', color: '#68728c' }"
              tooltip-effect="dark"
              style="width: 100%"
              height="400"
              @selection-change="handleSelectionChange"
            >
              <el-table-column type="selection" width="55"></el-table-column>
              <el-table-column prop="name" label="表名称"></el-table-column>
            </el-table>
          </div>
        </div>
        <div
          style="margin-top: 10px; display: flex; justify-content: center"
          v-if="this.radio == 'user'"
        >
          <el-transfer
            v-model="transferValue"
            :titles="['可用', '已选']"
            :data="transferData"
            :props="{ key: 'name', label: 'name' }"
            @right-check-change="exportHandleChange"
            :right-default-checked="transferValue"
          ></el-transfer>
        </div>
        <!-- <el-checkbox v-model="checked" v-if="radio != 'table'"
          >Table rows</el-checkbox
        >-->
      </span>
      <span slot="footer" class="dialog-footer">
        <el-button
          @click="handleClose"
          style="color: #006fff; border: 1px solid #006fff"
          size="small"
          >取 消</el-button
        >
        <el-button
          type="primary"
          @click="exportSubmit"
          :loading="this.loading"
          size="small"
          >{{ this.loading ? `文件下载中` : "确定" }}</el-button
        >
      </span>
    </el-dialog>
    <!-- 导入 -->
    <el-dialog
      :title="
        isDialog == 'importArchive' ? acrhive.taskName || '归档导入' : '导入'
      "
      :visible.sync="ImportDialog"
      width="870px"
      :before-close="handleCloseFn"
    >
      <span
        v-if="isDialog == 'importDMP'"
        v-loading="loading2"
        element-loading-text="正在上传中..."
        element-loading-spinner="el-icon-loading"
        element-loading-background="rgba(255, 255, 255, 0.5)"
      >
        <div class="exportTitle">
          <el-radio-group
            v-model="radioImp"
            size="small"
            class="exportBox"
            fill="#006fff"
          >
            <!-- <el-radio-button label="table">导入表</el-radio-button> -->
            <el-radio-button label="user">导入用户</el-radio-button>
            <el-radio-button label="database">导入整库</el-radio-button>
          </el-radio-group>
        </div>
        <div style="margin-top: 10px" v-if="radioImp == 'table'">
          <el-form
            :inline="true"
            size="mini"
            :model="formData"
            class="demo-form-inline"
          >
            <el-row>
              <el-col :span="12">
                <el-form-item label="源用户" class="is-required" prop="user">
                  <el-input
                    v-model="formData.user"
                    @change="filterData"
                    style="width: 300px"
                    size="small"
                    placeholder="请输入"
                  ></el-input>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item
                  label="目的用户"
                  prop="toUser"
                  style="margin-left: 15px"
                  class="is-required"
                >
                  <el-select
                    v-model="formData.toUser"
                    @focus="getDataBaseTree"
                    @change="queryTransferData"
                    filterable
                    size="small"
                    style="width: 300px"
                  >
                    <el-option
                      v-for="item in optionImp"
                      :key="item.name"
                      :label="item.name"
                      :value="item.name"
                    ></el-option>
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
          <div style="margin-top: 5px">
            <span style="color: #7c7f86; margin-left: 30px">选择分类</span>
            <span style="margin-left: 30px">
              <el-radio v-model="radioType" label="all">导入所有表</el-radio>
              <!-- <el-radio v-model="radioType" label="copyTable"
                >复制对应表</el-radio> -->
            </span>
          </div>
          <div style="margin-top: 20px" v-if="radioType == 'copyTable'">
            <el-transfer
              v-model="transferVal"
              :titles="['可用', '已选']"
              :data="transfer"
              :props="{ key: 'name', label: 'name' }"
              @right-check-change="handleChange"
            ></el-transfer>
          </div>
        </div>
        <div style="margin-top: 10px" v-if="radioImp == 'user'">
          <el-form
            :inline="true"
            size="mini"
            :model="searchFrom"
            class="demo-form-inline"
          >
            <el-form-item label="源用户" class="is-required">
              <el-input
                v-model="searchFrom.user"
                placeholder="请输入"
                size="small"
                style="width: 300px"
              ></el-input>
            </el-form-item>
            <el-form-item label="目的用户" class="is-required">
              <el-select
                v-model="searchFrom.toUser"
                style="width: 300px"
                @change="updataValue"
                @focus="getDataBaseTree"
                filterable
                size="small"
              >
                <el-option
                  v-for="item in optionImp"
                  :key="item.name"
                  :label="item.name"
                  :value="item.name"
                ></el-option>
              </el-select>
            </el-form-item>
            <!-- <el-form-item>
              <el-button
                type="primary"
                @click="addTable"
                :disabled="tableData.length >= 1"
                >添加</el-button
              >
            </el-form-item> -->
          </el-form>
          <!-- <el-table
            size="mini"
            :data="tableData"
            :header-cell-style="{ background: '#f1f3f9', color: '#68728c' }"
            row-key="key"
          >
            <el-table-column prop="user" label="源用户"></el-table-column>
            <el-table-column prop="touser" label="目的用户"></el-table-column>
            <el-table-column width="120" label="操作">
              <template slot-scope="scope">
                <el-button
                  @click="deleteData(scope.row)"
                  size="mini"
                  type="text"
                  plain
                >
                  <img src="@/assets/main/3-con-ico02.png" alt />
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table> -->
        </div>
        <div style="margin-top: 10px; display: flex; flex-direction: column">
          <el-upload
            class="upload-demo"
            ref="commonUpload"
            accept=".dmp"
            action="/dev-api/common/upload"
            :show-file-list="false"
            :headers="customHeaders"
            :on-success="handleAvatarSuccess"
            :on-error="hanldeUploadError"
            :on-progress="handleProgress"
            :before-upload="handleBeforeUpload"
            :limit="1"
            :disabled="JSON.stringify(filterObj) != '{}'"
          >
            <div class="uploadBox">
              <div class="background">
                <i class="el-icon-upload"></i>
              </div>
              <div style="margin-left: 10px">
                <div style="font-size: 16px">上传</div>
                <div style="color: #9da4b4; font-size: 12px; margin-top: 5px">
                  DMP
                </div>
              </div>
            </div>
          </el-upload>
          <div style="font-size: 12px; color: #999">
            仅支持上传DMP格式文件,文件大小为20G
          </div>
          <div class="fileBox" v-show="isFile">
            <div class="background">
              <img src="@/assets/main/file_01.png" alt />
            </div>
            <div style="margin-left: 10px">
              <div style="font-size: 16px">
                {{ filterObj.originalFilename }}
              </div>
              <div style="color: #9da4b4; font-size: 12px; margin-top: 5px">
                {{ filterObj.fileSize }}
                <span style="margin-left: 10px"
                  >上传状态:{{ filterObj.code == 200 ? "成功" : "失败" }}</span
                >
              </div>
            </div>
            <div class="deleteBox" @click="deleteFile">
              <i class="el-icon-close"></i>
            </div>
          </div>
        </div>
      </span>
      <span v-else>
        <div style="margin-top: 10px; display: flex">
          <el-upload
            class="upload-demo"
            ref="commonUpload"
            :show-file-list="false"
            action="/dev-api/common/upload"
            :headers="customHeaders"
            :on-success="handleAvatarSuccess"
            :limit="1"
            accept=".xlsx"
          >
            <div class="uploadBox">
              <div class="background">
                <i class="el-icon-upload"></i>
              </div>
              <div style="margin-left: 10px">
                <div style="font-size: 16px">上传</div>
                <div style="color: #9da4b4; font-size: 12px; margin-top: 5px">
                  XLSX
                </div>
              </div>
            </div>
            <div class="fileBox" v-show="isFile">
              <div class="background">
                <img src="@/assets/main/file_01.png" alt />
              </div>
              <div style="margin-left: 10px">
                <div style="font-size: 16px">
                  {{ filterObj.originalFilename }}
                </div>
                <div style="color: #9da4b4; font-size: 12px; margin-top: 5px">
                  {{ filterObj.fileSize
                  }}<span style="margin-left: 10px"
                    >上传状态:{{
                      filterObj.code == 200 ? "成功" : "失败"
                    }}</span
                  >
                </div>
              </div>
              <div class="deleteBox" @click.stop="deleteFile">
                <i class="el-icon-close"></i>
              </div>
            </div>
          </el-upload>
        </div>
      </span>
      <span slot="footer" class="dialog-footer">
        <el-button
          @click="handleCloseFn"
          style="color: #006fff; border: 1px solid #006fff"
          size="small"
          >取 消</el-button
        >
        <el-button
          type="primary"
          @click="submitImport"
          size="small"
          :disabled="loading2"
          :loading="this.loadingTime"
          >{{ this.loadingTime ? "文件导入中" : "确定" }}</el-button
        >
      </span>
    </el-dialog>

    <el-dialog
      title="选择"
      :visible.sync="isSearchTable"
      width="70%"
      :before-close="handleCloseSearch"
    >
      <span>
        <el-table
          ref="isSearchTable"
          :data="searchTableData"
          tooltip-effect="dark"
          style="width: 100%"
          @selection-change="handleSelection"
          height="500"
          border
          v-loading="loading"
        >
          <el-table-column type="selection" width="55"> </el-table-column>
          <el-table-column
            v-for="item in headers"
            show-overflow-tooltip
            min-width="150px"
            :key="item.name"
            :prop="item.name"
            :label="item.name"
          >
          </el-table-column>
        </el-table>
        <el-pagination
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
          :current-page="page.pageNum"
          :page-sizes="[100, 200, 500]"
          :page-size="page.pageSize"
          layout="total, sizes, prev, pager, next, jumper"
          :total="page.total"
        >
        </el-pagination>
      </span>
      <span slot="footer" class="dialog-footer">
        <el-button
          style="color: #006fff; border: 1px solid #006fff"
          size="small"
          @click="handleCloseSearch"
          >取 消</el-button
        >
        <el-button type="primary" size="small" @click="saveSelect"
          >确定</el-button
        >
      </span>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.treeClass {
  width: 100%;
  margin-top: 10px;
}
::v-deep .el-upload {
  display: flex !important;
}
.imgBox {
  height: 100%;
  display: flex;
  align-items: center;
  margin-left: 15px;
}
.title {
  color: #006fff;
  font-weight: bold;
  font-size: 20px;
  margin-left: 10px;
}
::v-deep .el-transfer-panel {
  width: 318px !important;
}
::v-deep .el-form-item--mini .el-form-item__label {
  line-height: 30px !important;
}
::v-deep .el-form-item__label {
  color: #7c7f86;
}
.dropdown {
  display: flex;
  align-items: center;
  position: relative;
  > span {
    margin-left: 5px;
  }
}
.rightMenu {
  position: absolute;
  // height: 30px;
  border: 1px solid #ccc;
  color: #000;
  font-size: 15px;
  text-align: center;
  line-height: 30px;
  padding: 0 5px;
  background: #fff;
  z-index: 99;
}
.header {
  height: 60px;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: relative;
  transition: height 0.2s ease;
  .brand-area {
    transition: opacity 0.2s ease;
  }
  .header-toggle {
    position: absolute;
    left: 50%;
    bottom: -14px;
    transform: translateX(-50%);
    z-index: 20;
    width: 28px;
    height: 22px;
    padding: 0;
    border-radius: 0 0 4px 4px;
    background: #fff;
    box-shadow: 0 2px 5px rgba(0, 0, 0, 0.12);
  }
  &.header-collapsed {
    height: 20px;
    .brand-area,
    .nav_list,
    .help,
    .task-shortcut,
    .avatar-container {
      opacity: 0;
      pointer-events: none;
    }
  }
  .nav_list {
    display: flex;
    align-items: center;
    .nav_item {
      display: flex;
      align-items: center;
      margin-right: 30px;
      height: 36px;
      line-height: 36px;
      color: #4a5565;
      padding: 0 16px;
      cursor: pointer;
      img {
        width: 15px;
        height: 15px;
        margin-right: 6px;
      }
    }
    .nav_item_action {
      .el-dropdown {
        color: #006fff !important;
        font-weight: bold;
      }
      background: #eef3fa;
      border-radius: 5px;
    }
  }
  .avatar-container {
    margin-right: 30px;

    .avatar-wrapper {
      display: flex;
      width: 100px;
      align-items: center;
      justify-content: center;
      height: 30px;
      background: #e9edf4;
      border-radius: 20px;
      cursor: pointer;
      .el-icon-caret-bottom {
        cursor: pointer;
        margin-left: 10px;
        font-size: 12px;
      }
    }
  }
  .task-shortcut {
    margin-left: auto;
    margin-right: 12px;
    .el-button {
      padding: 6px;
      color: #606266;
      font-size: 19px;
    }
  }
}
.help {
  position: absolute;
  right: 10%;
  color: #006fff;
  cursor: pointer;
  i {
    font-size: 20px;
  }
}
.uploadBox {
  width: 200px;
  height: 65px;
  border: 1px dashed #e1e6ec;
  background: #f6f9fc;
  border-radius: 3px;
  display: flex;
  align-items: center;
  .background {
    width: 50px;
    height: 50px;
    background: #006fff;
    border-radius: 6px;
    display: flex;
    justify-content: center;
    align-items: center;
    margin-left: 10px;
    .el-icon-upload {
      color: #fff;
      font-size: 30px;
    }
  }
}
.fileBox {
  width: 600px;
  height: 65px;
  border: 1px solid #e1e6ec;
  margin-top: 10px;
  display: flex;
  align-items: center;
  border-radius: 3px;
  position: relative;
  .background {
    width: 50px;
    height: 50px;
    background: #d4e3f6;
    border-radius: 6px;
    display: flex;
    justify-content: center;
    align-items: center;
    margin-left: 10px;
  }
  .deleteBox {
    width: 18px;
    height: 18px;
    background: #ff5733;
    border-radius: 50%;
    position: absolute;
    right: 5px;
    top: 5px;
    cursor: pointer;
    > i {
      display: flex;
      align-items: center;
      justify-content: center;
      color: #fff;
      font-weight: bold;
      margin-top: 2px;
    }
  }
}
::v-deep .el-dialog .el-dialog__header {
  background: #dfe3f0 !important;
  height: 40px !important;
  .el-dialog__title {
    color: #242f57 !important;
    line-height: 40px !important;
    font-size: 16px !important;
  }
}
::v-deep .el-dialog .el-dialog__body {
  padding: 15px 20px;
}
::v-deep .el-table--medium .el-table__cell {
  padding: 3px;
}
::v-deep .el-dialog__footer {
  // padding: 40px !important;
}
.exportTitle {
  display: flex;
  justify-content: center;
  .exportBox {
    border-radius: 20px !important;
    background: #dfe3f0;
    border: 1px solid #dfe3f0;
    ::v-deep .el-radio-button__inner {
      border-radius: 20px;
      background: #dfe3f0;
    }
  }
  // border: 1px solid #ccc;
}
.titleFrom {
  display: flex;
  align-items: center;
  .searchInput {
    width: 250px;
    margin-left: 30px;
  }
}
::v-deep .el-table__empty-block {
  width: 100% !important;
}
</style>

<style>
.submitImport {
  top: 20%;
  position: absolute;
  left: 30%;
}
</style>
