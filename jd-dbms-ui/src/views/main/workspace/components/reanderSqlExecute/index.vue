<script>
import renderSearchResult from "@/views/main/workspace/components/renderSearchResult/index.vue";
import MonacoEditor from "@/components/MonacoEditor/index.vue";
import sqlServer from "@/api/main/sql";
import historyServer from "@/api/main/history";
import tableServer from "@/api/main/table";
import { downloadFile } from "@/utils/file";
import { copyText } from "@/utils";
import draggable from "vuedraggable";
export default {
  components: { MonacoEditor, renderSearchResult, draggable },
  props: {
    currentConfig: {
      type: Object,
    },
    detailsObj: {
      default: {},
    },
  },
  data() {
    return {
      Allmessage: false,
      messageData: "",
      disableCommit: false,
      disableRollBack: false,
      flag: true,
      isCommit: false,
      sessionId: "",
      sqlContent: null,
      sqlLoading: false,
      currentExecutionId: "",
      cancelRequested: false,
      queryTimeoutSeconds: 60,
      // 当前展示查詢結果sql下标
      successCurrentIndex: 0,
      errorCurrentIndex: 0,
      executeRes: "",
      heightStyle: 300,
      updateId: "",
      isShow: false,
      errorShow: false,
      isSuccessShow: false,
      errorMsg: "",
      tableData: [],
      visible2: false,
      inputSqlName: "",
      radioData: "1",
      dialogTableVisible: false,
      gridData: [],
      dateRange: [],
      logSearchKey: "",
      logStatus: "",
      logLoading: false,
      page: {
        pageNum: 1,
        pageSize: 20,
        total: 0,
      },
      search_data: [],
      dialog: false,
      filterConditions: [
        "LIKE",
        "NOT LIKE",
        "=",
        ">=",
        "<=",
        "IS NULL",
        "IS NOT NULL",
      ],
      filterArray: "",
      searchDialogVisible: false,
      searchDialogForm: {
        columnTitle: "",
        sql: "",
        contentTest: "",
      },

      searchSql: "",

      selectedRow: {},
      selectedColumn: {},
      sqlConsoleKeydown: null,
      importingSqlFile: false,
      recentSqlScripts: [],
      recentScriptsVisible: false,
      lastExecutionDuration: null,
      lastResultCount: 0,
      lastOperationStatus: "",
      resultTab: "success",
    };
  },
  computed: {
    lineTexts() {
      return this.messageData
        .split("\n")
        .map((line) => {
          if (line.includes("成功"))
            return `<div style="color:blue">${line}</div>`;
          if (line.includes("失败"))
            return `<div style="color:red">${line}</div>`;
          return line;
        })
        .join("<br>");
    },
    // activeConsoleId() {
    //   return this.$store.state.workspace.activeConsoleId;
    // },
    currentDataSource() {
      return this.$store.state.workspace.currentConnectionDetails;
    },
    successList() {
      if (this.currentConfig.sqlData && this.currentConfig.sqlData.length) {
        return this.currentConfig.sqlData.filter((item) => {
          return item.success;
        });
      } else {
        return [];
      }
    },
    errorList() {
      if (this.currentConfig.sqlData && this.currentConfig.sqlData.length) {
        return this.currentConfig.sqlData.filter((item) => {
          return !item.success;
        });
      } else {
        return [];
      }
    },
    headerName() {
      return this.search_data.filter((item) => {
        return item.name != "行号";
      });
    },
    consoleStatus() {
      if (this.cancelRequested) return "正在取消";
      if (this.sqlLoading) return "执行中";
      if (this.lastOperationStatus) return this.lastOperationStatus;
      if (this.lastExecutionDuration !== null) return `最近执行 ${this.lastExecutionDuration}ms`;
      return "就绪";
    },
    affectedRowCount() {
      return this.successList.reduce((total, result) => {
        return this.hasUpdateCount(result)
          ? total + Number(result.updateCount)
          : total;
      }, 0);
    },
    hasAffectedRows() {
      return this.successList.some((result) => this.hasUpdateCount(result));
    },
    hasPendingTransaction() {
      return this.successList.some((result) => Boolean(result.sign));
    },
  },
  watch: {
    sessionId(val) {
      console.log(val, "val");
      if (val) {
        let { uniqueData } = this.currentConfig;
        let sql =
          this.$refs.MonacoEditor.getSelectValue() ||
          this.$refs.MonacoEditor.getValue();
        if (!sql) {
          return;
        }
        let send = {
          consoleId: this.currentConfig.id,
          dataSourceId: uniqueData.dataSourceId,
          dataSourceName: uniqueData.dataSourceName,
          databaseType: uniqueData.databaseType,
          hasNextPage: true,
          pageNo: 1,
          pageSize: 200,
          schemaName: uniqueData.schemaName,
          sql: sql,
          status: "DRAFT",
          supportDatabase: false,
          supportSchema: true,
          total: 0,
          type: uniqueData.type,
          isErrorExecute: this.radioData == "1" ? false : true,
          isLog: true,
          queryTemplate: true,
          isCommit: true,
          sessionId: this.sessionId,
        };
        this.$store.dispatch("getSessionId", send);
      } else {
        this.$store.dispatch("getSessionId", {});
      }
    },
    visible2(newVal) {
      if (newVal) {
        this.inputSqlName = "";
      }
    },
    dialogTableVisible(newVal) {
      if (newVal) {
        this.logData();
      }
    },
    detailsObj: {
      handler: function (newValue) {
        let str = this.$refs.MonacoEditor.getValue();
        if (str !== "") {
          this.$refs.MonacoEditor.setValue(
            str + "\n" + "select * from " + newValue.name + ";"
          );
        } else {
          this.$refs.MonacoEditor.setValue(
            str + "select * from " + newValue.name + ";"
          );
        }
      },
      deep: true,
    },
    // activeConsoleId() {
    //   this.successCurrentIndex = 0;
    //   this.errorCurrentIndex = 0;
    // }
  },
  mounted() {
    this.loadRecentSqlScripts();
    console.log(this.currentConfig.sqlData, "this.currentConfig.sqlData");
    this.sqlContent =
      this.$refs.MonacoEditor.getSelectValue() ||
      this.$refs.MonacoEditor.getValue();
    // console.log(
    //   this.sqlContent,
    //   // this.currentConfig,
    //   // this.successList,
    //   // this.errorList,
    //   "currentConfig1111111"
    // );
    if (this.currentConfig.uniqueData.sql) {
      this.$refs.MonacoEditor.setValue(this.currentConfig.uniqueData.sql);
    }
    this.sqlConsoleKeydown = (event) => {
      if (event.key === "F9" || (event.ctrlKey && event.key === "Enter")) {
        event.preventDefault();
        this.executeSQL("run");
      } else if (event.ctrlKey && event.shiftKey && event.key.toLowerCase() === "f") {
        event.preventDefault();
        this.formatSql();
      } else if (event.ctrlKey && event.shiftKey && event.key.toLowerCase() === "c") {
        event.preventDefault();
        if (this.disableCommit) this.handleCommit();
      } else if (event.ctrlKey && event.shiftKey && event.key.toLowerCase() === "r") {
        event.preventDefault();
        if (this.disableRollBack) this.handleBackRoll();
      }
    };
    document.addEventListener("keydown", this.sqlConsoleKeydown);
  },
  beforeDestroy() {
    if (this.sqlConsoleKeydown) {
      document.removeEventListener("keydown", this.sqlConsoleKeydown);
    }
  },
  methods: {
    hasUpdateCount(result) {
      if (
        !result ||
        result.updateCount === null ||
        result.updateCount === undefined
      ) {
        return false;
      }
      const updateCount = Number(result.updateCount);
      return (
        Number.isFinite(updateCount) &&
        updateCount >= 0 &&
        (updateCount > 0 || Boolean(result.sign))
      );
    },
    formatExecutionResult(result) {
      if (!result) return "";
      const parts = [result.message || result.description || "执行成功"];
      if (this.hasUpdateCount(result)) {
        parts.push(`影响 ${result.updateCount} 行`);
      }
      if (result.sign) {
        parts.push("事务待提交");
      }
      if (result.duration !== null && result.duration !== undefined) {
        parts.push(`${result.duration}ms`);
      }
      return parts.join(" · ");
    },
    buildExecutionSummary(results) {
      return results
        .map((result, index) => {
          const status = result.success
            ? this.formatExecutionResult(result)
            : result.message || "执行失败";
          return `第${index + 1}条：${status}`;
        })
        .join("\n");
    },
    loadRecentSqlScripts() {
      try {
        const stored = JSON.parse(localStorage.getItem("jd-dbms-recent-sql") || "[]");
        this.recentSqlScripts = Array.isArray(stored) ? stored.slice(0, 10) : [];
      } catch (e) {
        this.recentSqlScripts = [];
      }
    },
    saveRecentSqlScript(sql) {
      const content = String(sql || "").trim();
      if (!content || content.length > 200000) return;
      const item = {
        id: Date.now(),
        preview: content.replace(/\s+/g, " ").slice(0, 80),
        sql: content,
        time: new Date().toLocaleString(),
      };
      this.recentSqlScripts = [item, ...this.recentSqlScripts.filter((script) => script.sql !== content)].slice(0, 10);
      localStorage.setItem("jd-dbms-recent-sql", JSON.stringify(this.recentSqlScripts));
    },
    restoreRecentSqlScript(script) {
      if (!script || !script.sql) return;
      this.$refs.MonacoEditor.setValue(script.sql);
      this.recentScriptsVisible = false;
      this.$message.success("已恢复最近脚本");
    },
    removeRecentSqlScript(script) {
      this.recentSqlScripts = this.recentSqlScripts.filter((item) => item.id !== script.id);
      localStorage.setItem("jd-dbms-recent-sql", JSON.stringify(this.recentSqlScripts));
    },
    upAndDown(){
      this.flag = !this.flag
      this.flag ? this.heightStyle = 700 : this.heightStyle = 300
    },
    handleBackRoll() {
      this.disableCommit = false;
      this.disableRollBack = false;
      let { uniqueData } = this.currentConfig;
      let sql =
        this.$refs.MonacoEditor.getSelectValue() ||
        this.$refs.MonacoEditor.getValue();
      if (!sql) {
        return;
      }
      this.sqlLoading = true;
      let send = {
        consoleId: this.currentConfig.id,
        dataSourceId: uniqueData.dataSourceId,
        dataSourceName: uniqueData.dataSourceName,
        databaseType: uniqueData.databaseType,
        hasNextPage: true,
        pageNo: 1,
        pageSize: 200,
        schemaName: uniqueData.schemaName,
        sql: sql,
        status: "DRAFT",
        supportDatabase: false,
        supportSchema: true,
        total: 0,
        type: uniqueData.type,
        isErrorExecute: this.radioData == "1" ? false : true,
        isLog: true,
        queryTemplate: true,
        isRollback: true,
        sessionId: this.sessionId,
      };
      sqlServer
        .rollBackSession(send)
        .then((res) => {
          const results = Array.isArray(res.data) ? res.data : [];
          if (results[0]) {
            this.disableCommit = results[0].sign;
            this.disableRollBack = results[0].sign;
            this.messageData = results[0].allMessage || "";
          }
          this.resultTab = results.some((item) => item.success) ? "success" : "error";
          this.isShow = results.length > 0;
          this.$emit("setCurrentConfig", "sqlData", results);
          this.lastExecutionDuration = null;
          this.lastResultCount = results.length;
          this.lastOperationStatus = results[0] && results[0].success
            ? "事务已回滚"
            : "回滚失败";
          this.sqlLoading = false;
          this.isSuccessShow = results.some((item) => item.success);
          this.errorShow = false;
        })
        .catch(() => {
          this.sqlLoading = false;
        });
    },
    handleCommit() {
      this.disableCommit = false;
      this.disableRollBack = false;
      let { uniqueData } = this.currentConfig;
      let sql =
        this.$refs.MonacoEditor.getSelectValue() ||
        this.$refs.MonacoEditor.getValue();
      if (!sql) {
        return;
      }
      this.sqlLoading = true;
      let send = {
        consoleId: this.currentConfig.id,
        dataSourceId: uniqueData.dataSourceId,
        dataSourceName: uniqueData.dataSourceName,
        databaseType: uniqueData.databaseType,
        hasNextPage: true,
        pageNo: 1,
        pageSize: 200,
        schemaName: uniqueData.schemaName,
        sql: sql,
        status: "DRAFT",
        supportDatabase: false,
        supportSchema: true,
        total: 0,
        type: uniqueData.type,
        isErrorExecute: this.radioData == "1" ? false : true,
        isLog: true,
        queryTemplate: true,
        isCommit: true,
        sessionId: this.sessionId,
      };
      sqlServer
        .commitSession(send)
        .then((res) => {
          const results = Array.isArray(res.data) ? res.data : [];
          if (results[0]) {
            this.disableCommit = results[0].sign;
            this.disableRollBack = results[0].sign;
            this.messageData = results[0].allMessage || "";
          }
          this.resultTab = results.some((item) => item.success) ? "success" : "error";
          this.isShow = results.length > 0;
          this.$emit("setCurrentConfig", "sqlData", results);
          this.lastExecutionDuration = null;
          this.lastResultCount = results.length;
          this.lastOperationStatus = results[0] && results[0].success
            ? "事务已提交"
            : "提交失败";
          this.sqlLoading = false;
          this.isSuccessShow = results.some((item) => item.success);
          this.errorShow = false;
        })
        .catch(() => {
          this.sqlLoading = false;
        });
    },
    logData() {
      const uniqueData = this.currentConfig.uniqueData || {};
      const params = {
        pageSize: this.page.pageSize,
        pageNo: this.page.pageNum,
        dataSourceId: uniqueData.dataSourceId,
        databaseName: uniqueData.databaseName,
        schemaName: uniqueData.schemaName,
        searchKey: this.logSearchKey || undefined,
        status: this.logStatus || undefined,
      };
      this.logLoading = true;
      sqlServer
        .sqlLog(this.addDateRange(params, this.dateRange))
        .then((res) => {
          if (res.success) {
            this.gridData = res.data.data || [];
            this.page.total = res.data.total || 0;
          }
          this.logLoading = false;
        })
        .catch(() => {
          this.logLoading = false;
        });
    },
    execute() {
      let sql =
        this.$refs.MonacoEditor.getSelectValue() ||
        this.$refs.MonacoEditor.getValue();
      const params = {
        dataSourceId: this.currentConfig.uniqueData.dataSourceId,
        sql: sql,
        consoleId: this.currentConfig.id,
      };
      sqlServer.implementationPlan(params).then((res) => {
        if (res.success) {
          this.$emit("setCurrentConfig", "sqlData", res.data);
          this.$forceUpdate();
          this.$nextTick(() => {
            this.isShow = true;
            this.sqlLoading = false;
            this.isSuccessShow = true;
            this.errorShow = false;
          });
        } else {
          this.sqlLoading = false;
          this.isSuccessShow = false;
          this.errorShow = true;
        }
      });
    },
    defaultDestroy() {
      let ddl = this.$refs.MonacoEditor.getValue();
      this.currentConfig.uniqueData.ddl = ddl;
    },
    // 执行sql
    executeSQL(type, status) {
      // 防止重复点击导致同一 SQL 并发执行，表现为窗口长时间无响应。
      if (this.sqlLoading) {
        return;
      }
      let { uniqueData } = this.currentConfig;
      let sql =
        this.$refs.MonacoEditor.getSelectValue() ||
        this.$refs.MonacoEditor.getValue();
      if (!sql) {
        return;
      }
      this.saveRecentSqlScript(sql);
      this.successCurrentIndex = 0;
      this.errorCurrentIndex = 0;
      this.lastOperationStatus = "";
      this.cancelRequested = false;
      this.sqlLoading = true;

      if (type == "run") {
        let send = {
          consoleId: this.currentConfig.id,
          dataSourceId: uniqueData.dataSourceId,
          dataSourceName: uniqueData.dataSourceName,
          databaseType: uniqueData.databaseType,
          hasNextPage: true,
          pageNo: 1,
          pageSize: 200,
          schemaName: uniqueData.schemaName,
          sql: sql,
          status: "DRAFT",
          supportDatabase: false,
          supportSchema: true,
          total: 0,
          type: uniqueData.type,
          isErrorExecute: this.radioData == "1" ? false : true,
          isLog: true,
          queryTemplate: true,
          isCommit: this.isCommit,
          executionId: this.createExecutionId(),
          queryTimeoutSeconds: this.queryTimeoutSeconds,
          // 控制台只展示当前结果页，跳过达梦上代价较高的全量 count(*)。
          skipCount: true,
        };
        this.currentExecutionId = send.executionId;
        if (this.flag) {
          this.flag = false;
        } else {
          send.sessionId = this.sessionId;
        }
        status ? (send.isExecuteCompile = true) : "";
        this.requestSqlExecution(send);
      } else {
        this.execute();
      }
    },
    requestSqlExecution(send) {
      sqlServer
        .executeSql(send)
        .then((res) => {
          if (
            res.errorCode === "sql.confirmRequired" &&
            !send.confirmDangerousSql
          ) {
            this.sqlLoading = false;
            const target = [
              send.databaseType || send.type,
              send.dataSourceName,
              send.schemaName,
            ]
              .filter(Boolean)
              .join(" / ");
            this.$confirm(
              `${res.errorMessage}。目标：${target || "当前连接"}。确认继续执行吗？`,
              "高风险 SQL 确认",
              {
                confirmButtonText: "确认执行",
                cancelButtonText: "取消",
                type: "warning",
              }
            )
              .then(() => {
                send.confirmDangerousSql = true;
                this.sqlLoading = true;
                this.requestSqlExecution(send);
              })
              .catch(() => {});
            return;
          }
          const results = Array.isArray(res.data) ? res.data : [];
          const durations = results
            .filter((item) => item.duration !== null && item.duration !== undefined)
            .map((item) => Number(item.duration));
          this.lastExecutionDuration = durations.length
            ? durations.reduce((total, duration) => total + duration, 0)
            : null;
          this.lastResultCount = results.length;
          this.lastOperationStatus = results.some((item) => item.cancelled)
            ? "执行已取消"
            : results.some((item) => item.timedOut)
            ? "执行超时"
            : "";
          this.resultTab = results.some((item) => item.success) ? "success" : "error";
          this.isShow = results.length > 0;
          console.log(res.data, "(res.data");
          // if (res.data[0].success) {

          if (results.length < 2) {
            if (results[0]) {
              results[0].params = { ...send, executionId: undefined };
              this.sessionId = results[0].sessionId;
              this.disableCommit = results[0].sign;
              this.disableRollBack = results[0].sign;
            }
            this.$emit("setCurrentConfig", "sqlData", results);
          } else {
            if (results[0]) {
              this.disableCommit = results[0].sign;
              this.disableRollBack = results[0].sign;
              this.sessionId = results[0].sessionId;
            }
            this.tableData = results;
            this.$emit("setCurrentConfig", "sqlData", this.tableData);
          }
          this.messageData = results[0] && results[0].allMessage
            ? results[0].allMessage
            : this.buildExecutionSummary(results);
          this.Allmessage = true;
          this.successCurrentIndex = null;
          this.sqlLoading = false;
          this.currentExecutionId = "";
          this.cancelRequested = false;
          this.isSuccessShow = results.some((item) => item.success);
          // this.errorShow = false;
          // } else {
          //   this.sqlLoading = false;
          //   this.$message.error(res.data[0].message);
          //   this.isSuccessShow = false;
          //   this.errorShow = true;
          //   this.errorMsg = res.data[0].message;
          // }
        })
        .catch(() => {
          this.sqlLoading = false;
          this.currentExecutionId = "";
          this.cancelRequested = false;
        });
    },
    createExecutionId() {
      if (window.crypto && typeof window.crypto.randomUUID === "function") {
        return window.crypto.randomUUID();
      }
      return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
    },
    cancelSqlExecution() {
      if (!this.sqlLoading || !this.currentExecutionId || this.cancelRequested) {
        return;
      }
      const uniqueData = this.currentConfig.uniqueData;
      this.cancelRequested = true;
      sqlServer
        .cancelSql({
          executionId: this.currentExecutionId,
          consoleId: this.currentConfig.id,
          dataSourceId: uniqueData.dataSourceId,
          databaseName: uniqueData.databaseName,
          schemaName: uniqueData.schemaName,
        })
        .then((res) => {
          if (res.success) {
            this.$message.success("已发送停止请求");
          } else {
            this.cancelRequested = false;
            this.$message.warning(res.errorMessage || "SQL 已结束");
          }
        })
        .catch(() => {
          this.cancelRequested = false;
        });
    },
    // 清空
    emptySql() {
      this.$refs.MonacoEditor.setValue(" ");
      this.isShow = false;
    },
    clearResults() {
      this.$emit("setCurrentConfig", "sqlData", []);
      this.isShow = false;
      this.resultTab = "success";
      this.Allmessage = false;
      this.messageData = "";
      this.lastExecutionDuration = null;
      this.lastResultCount = 0;
      this.lastOperationStatus = "";
    },
    importSqlFile() {
      if (this.importingSqlFile) return;
      this.$refs.sqlFileInput && this.$refs.sqlFileInput.click();
    },
    handleSqlFileChange(event) {
      const file = event.target.files && event.target.files[0];
      event.target.value = "";
      if (!file) return;
      if (!/\.(sql|txt)$/i.test(file.name)) {
        this.$message.error("仅支持导入 .sql 或 .txt 文件");
        return;
      }
      if (file.size > 10 * 1024 * 1024) {
        this.$message.error("SQL 文件不能超过 10MB");
        return;
      }
      this.importingSqlFile = true;
      const reader = new FileReader();
      reader.onload = () => {
        this.$refs.MonacoEditor.setValue(String(reader.result || ""));
        this.importingSqlFile = false;
        this.$message.success(`已导入 ${file.name}`);
      };
      reader.onerror = () => {
        this.importingSqlFile = false;
        this.$message.error("SQL 文件读取失败");
      };
      reader.readAsText(file, "UTF-8");
    },
    // 格式化sql代码
    formatSql() {
      this.$refs.MonacoEditor.formatSql(this.$refs.MonacoEditor.getValue());
    },
    close() {
      this.inputSqlName = "";
      this.visible2 = false;
    },
    // 保存
    saveConsole() {
      let sql =
        this.$refs.MonacoEditor.getSelectValue() ||
        this.$refs.MonacoEditor.getValue();
      if (!this.inputSqlName) {
        return this.$message.error("请填写下载名称");
      } else if (!sql) {
        return this.$message.error("请填写SQL语句");
      }
      const byteArray = new TextEncoder().encode(sql);
      const blob = new Blob([byteArray], { type: "text/plain;charset=utf-8" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      // 名称后面追加时间戳
      a.download = `${this.inputSqlName}.sql`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
      this.inputSqlName = "";
      this.visible2 = false;
      // let sql = this.$refs.MonacoEditor.getValue();
      // let send = {
      //   id:  this.currentConfig.id,
      //   status: "RELEASE",
      //   ddl: sql
      // };
      // historyServer.updateSavedConsole(send).then(res => {
      //   if (res.success) {
      //     this.$message.success("保存成功！");
      //   } else {
      //     this.$message.error(res.errorMessage);
      //   }
      // });
    },
    // 选择数据库
    changeDataSource(val) {
      let send = {
        // id: this.activeConsoleId,
        schemaName: val,
      };
      historyServer.updateSavedConsole(send);
    },
    setViewSql(sql) {
      this.$refs.MonacoEditor.setValue(sql);
    },
    equipment() {
      const params = {
        dataSourceId: this.currentConfig.uniqueData.dataSourceId,
      };
      tableServer.getDebugSql(params).then((res) => {
        if (res.success) {
          this.$refs.MonacoEditor.setValue(res.data);
        } else {
          this.$message.error("调测失败!");
        }
      });
    },

    handleMouseTop(event) {
      event.preventDefault();
      let that = this,
        startY = null,
        isFlag = true;

      startY = event.clientY;

      function handleMouseMove(event) {
        if (isFlag) {
          let initialHeight = that.heightStyle;
          let endY = event.clientY;
          if (endY > startY) {
            // Y轴越来越大 = 往下移动  = 原高度-移动量
            initialHeight = initialHeight - (endY - startY);
          } else {
            // Y轴越来越小 = 往上移动  = 原高度+移动量
            initialHeight = initialHeight + (startY - endY);
          }

          if (initialHeight > 700) {
            initialHeight = 700;
          } else if (initialHeight < 300) {
            initialHeight = 300;
          }
          startY = endY;
          that.heightStyle = initialHeight;
        }
      }

      function handleMouseUp(event) {
        isFlag = false;
        document.onmousemove = null;
        document.onmouseup = null;
      }

      document.addEventListener("mousemove", handleMouseMove);
      document.addEventListener("mouseup", handleMouseUp);
    },
    exportSql() {
      let sql = this.$refs.MonacoEditor.getValue();
      const params = {
        dataSourceId: this.currentConfig.uniqueData.dataSourceId,
        sql: sql,
        exportType: "EXCEL",
      };
      this.$confirm(
        "为避免大结果集占满服务内存，单次最多导出 1000 行。是否继续？",
        "导出限制",
        {
          confirmButtonText: "继续导出",
          cancelButtonText: "取消",
          type: "warning",
        }
      )
        .then(() => {
          downloadFile(
            process.env.VUE_APP_BASE_API + "/api/rdb/table/customExport",
            { ...params }
          );
        })
        .catch(() => {});
    },
    currentClick(item, index) {
      this.Allmessage = false;
      this.successCurrentIndex = index;
      let { uniqueData } = this.currentConfig;
      let send = {
        consoleId: this.currentConfig.id,
        dataSourceId: uniqueData.dataSourceId,
        dataSourceName: uniqueData.dataSourceName,
        databaseType: uniqueData.databaseType,
        hasNextPage: true,
        pageNo: 1,
        pageSize: 200,
        schemaName: uniqueData.schemaName,
        sql: item.originalSql,
        status: "DRAFT",
        supportDatabase: false,
        supportSchema: true,
        total: 0,
        type: uniqueData.type,
      };
      item.params = send;
    },
    handleInput(val) {
      this.inputSqlName = val.replace(/[@!=]/g, "");
    },
    viewLog() {
      this.page.pageNum = 1;
      this.dialogTableVisible = true;
      this.logData();
    },
    handleClose() {
      this.page.pageNum = 1;
      this.dialogTableVisible = false;
      this.dateRange = [];
      this.logSearchKey = "";
      this.logStatus = "";
    },
    handleSizeChange(val) {
      this.page.pageSize = val;
      this.logData();
    },
    handleCurrentChange(val) {
      this.page.pageNum = val;
      this.logData();
    },
    searchKey() {
      this.page.pageNum = 1;
      this.logData();
    },
    searchData() {
      const currentResult = this.successList[this.successCurrentIndex];
      if (!currentResult || !currentResult.headerList) {
        this.$message.info("请选择一个结果集后再配置过滤条件");
        return;
      }
      this.dialog = true;
      this.search_data = [];
      this.search_data = currentResult.headerList;
    },
    closeDialog() {
      this.search_data = [];
      this.dialog = false;
    },
    filterTitle() {
      this.filterArray = this.headerName.filter((item) => {
        return item.name == this.searchDialogForm.columnTitle;
      })[0];
      this.searchDialogForm.sql = "";
      this.searchDialogForm.contentTest = "";
    },
    clearValue() {
      this.filterArray = "";
    },
    addSearchForm(str) {
      let sql = "";
      if (
        this.searchDialogForm.sql == "LIKE" ||
        this.searchDialogForm.sql == "NOT LIKE"
      ) {
        sql = `${this.searchDialogForm.columnTitle} ${this.searchDialogForm.sql} '%${this.searchDialogForm.contentTest}%' `;
      } else {
        sql = `${this.searchDialogForm.columnTitle} ${this.searchDialogForm.sql} '${this.searchDialogForm.contentTest}' `;
      }

      if (this.searchSql != "") {
        this.searchSql += `\n${str} ${sql}`;
      } else {
        this.searchSql = sql;
      }
    },
    addSearchForm2(str) {
      if (str == "(") {
        this.searchSql += `\n${str} `;
      } else {
        this.searchSql += ` ${str} `;
      }
    },
    saveFilterData() {
      let currentSql = this.successList[this.successCurrentIndex].originalSql;
      let { uniqueData } = this.currentConfig;
      let send = {
        consoleId: this.currentConfig.id,
        dataSourceId: uniqueData.dataSourceId,
        dataSourceName: uniqueData.dataSourceName,
        databaseType: uniqueData.databaseType,
        hasNextPage: true,
        pageNo: 1,
        pageSize: 200,
        schemaName: uniqueData.schemaName,
        sql: currentSql,
        status: "DRAFT",
        supportDatabase: false,
        supportSchema: true,
        total: 0,
        type: uniqueData.type,
        isErrorExecute: this.radioData == "1" ? false : true,
      };

      if (this.searchSql) {
        send.sql = `SELECT * FROM (${currentSql}) WHERE ${this.searchSql}`;
      } else if (
        currentSql.indexOf("(") != -1 &&
        currentSql.indexOf(")") != -1
      ) {
        let i1 = currentSql.indexOf("(") + 1;
        let i2 = currentSql.indexOf(")");
        send.sql = currentSql.slice(i1, i2);
      }
      sqlServer.executeSql(send).then((res) => {
        if (res.data[0].success) {
          res.data[this.successCurrentIndex].params = send;
          this.$emit("setCurrentConfig", "sqlData", res.data);
        }
        this.sqlLoading = false;
        this.closeDialog();
      });
    },

    async tableCellClick(row, column, cell, event) {
      this.selectedRow = row;
      this.selectedColumn = column;
      if (column.property == "ddl") {
        copyText(row["ddl"]);
      }
    },

    cellStyle({ row, column, rowIndex, columnIndex }) {
      if (
        row === this.selectedRow &&
        column === this.selectedColumn &&
        column.property == "ddl"
      ) {
        return {
          backgroundColor: "#1890ff",
          color: "#fff",
        };
      }
    },
  },
};
</script>

<template>
  <div class="sql_execute">
    <div class="sql_search_box">
      <div class="monaco_btn">
        <input ref="sqlFileInput" type="file" accept=".sql,.txt" style="display:none" @change="handleSqlFileChange" />
        <el-button size="mini" type="text" @click="importSqlFile" :loading="importingSqlFile" title="导入 SQL 文件">
          <i class="el-icon-upload2"></i>
          导入
        </el-button>
        <el-popover placement="bottom-start" width="360" trigger="click" v-model="recentScriptsVisible">
          <div class="recent-sql-title">最近脚本</div>
          <el-empty v-if="!recentSqlScripts.length" description="暂无最近脚本" :image-size="50" />
          <div v-else class="recent-sql-list">
            <div v-for="script in recentSqlScripts" :key="script.id" class="recent-sql-item">
              <div class="recent-sql-preview" :title="script.sql" @click="restoreRecentSqlScript(script)">{{ script.preview }}</div>
              <span class="recent-sql-time">{{ script.time }}</span>
              <i class="el-icon-delete recent-sql-delete" title="删除" @click="removeRecentSqlScript(script)"></i>
            </div>
          </div>
          <el-button slot="reference" size="mini" type="text" title="恢复最近脚本">
            <i class="el-icon-time"></i>
            最近脚本
          </el-button>
        </el-popover>
        <el-button
          v-if="!sqlLoading"
          size="mini"
          @click="executeSQL('run')"
          type="text"
          style="margin-left: 0"
          title="执行（F9 / Ctrl+Enter）"
        >
          <img src="@/assets/main/1-con-ico01.png" alt />
          运行
        </el-button>
        <el-button
          v-else
          size="mini"
          @click="cancelSqlExecution"
          type="danger"
          plain
          :disabled="cancelRequested"
          title="停止当前 SQL"
        >
          <i :class="cancelRequested ? 'el-icon-loading' : 'el-icon-video-pause'"></i>
          {{ cancelRequested ? "正在停止" : "停止" }}
        </el-button>
        <el-select
          v-model="queryTimeoutSeconds"
          size="mini"
          style="width: 92px; margin-left: 6px"
          title="单条 SQL 超时时间"
        >
          <el-option label="5秒超时" :value="5"></el-option>
          <el-option label="30秒超时" :value="30"></el-option>
          <el-option label="60秒超时" :value="60"></el-option>
          <el-option label="120秒超时" :value="120"></el-option>
        </el-select>
        <el-button size="mini" @click="emptySql" type="text">
          <img src="@/assets/main/1-con-ico03.png" alt />
          清空
        </el-button>
        <el-button size="mini" @click="formatSql" type="text">
          <img src="@/assets/main/1-con-ico04.png" alt />
          美化
        </el-button>
        <el-button size="mini" type="text" @click="clearResults" :disabled="!isShow || sqlLoading">
          <i class="el-icon-delete"></i>
          清除结果
        </el-button>
        <el-button
          size="mini"
          type="primary"
          plain
          @click="handleCommit"
          :disabled="!disableCommit"
        >
          <i class="el-icon-check el-icon--left"></i>
          提交
        </el-button>
        <el-button
          size="mini"
          type="primary"
          plain
          @click="handleBackRoll"
          :disabled="!disableRollBack"
        >
          <i class="el-icon-refresh-left el-icon--left"></i>
          回滚
        </el-button>
        <el-popover
          style="margin-left: 15px"
          placement="bottom"
          width="300"
          v-model="visible2"
        >
          <el-input
            v-model="inputSqlName"
            @input="handleInput"
            size="small"
            maxlength="50"
            placeholder="请输入文件名称"
          ></el-input>
          <div style="text-align: right; margin-top: 10px">
            <el-button size="mini" type="text" @click="close">取消</el-button>
            <el-button type="primary" size="mini" @click="saveConsole"
              >确定</el-button
            >
          </div>
          <el-button size="mini" slot="reference" type="primary" plain>
            <img src="@/assets/main/1-con-ico22.png" alt />
            保存
          </el-button>
        </el-popover>

        <el-button
          size="mini"
          @click="executeSQL('run', true)"
          type="primary"
          style="margin-left: 10px"
          plain
        >
          <img src="@/assets/main/1-con-ico22.png" alt />
          过程编译
        </el-button>
        <el-button size="mini" @click="executeSQL('plan')" type="primary" plain>
          <img src="@/assets/main/1-con-ico22.png" alt />
          调测
        </el-button>
        <el-button
          size="mini"
          @click="exportSql"
          type="primary"
          plain
          :disabled="!this.isSuccessShow"
        >
          <i id="icon" class="el-icon-upload2"></i>
          导出
        </el-button>
        <el-button size="mini" @click="viewLog" type="primary" plain>
          <i id="icon" class="el-icon-view"></i>
          查看日志
        </el-button>
        <el-radio-group
          style="margin-left: 10px"
          v-model="radioData"
          size="mini"
        >
          <el-radio label="1" border style="margin-right: 0">错误执行</el-radio>
          <el-radio label="2" border>错误不执行</el-radio>
        </el-radio-group>
        <span class="sql-console-status" :class="{ 'is-running': sqlLoading }">
          <i :class="sqlLoading ? 'el-icon-loading' : 'el-icon-time'"></i>
          {{ consoleStatus }}<template v-if="lastResultCount"> · {{ lastResultCount }} 个结果集</template>
          <template v-if="hasAffectedRows"> · 影响 {{ affectedRowCount }} 行</template>
          <template v-if="hasPendingTransaction"> · <strong>事务待提交</strong></template>
        </span>
      </div>
      <draggable class="draggble2" :group="{ name: 'componentsGroup' }">
        <MonacoEditor
          ref="MonacoEditor"
          dom="editor"
          style="width: 100%; height: 47vh"
        />
      </draggable>
    </div>

    <!-- v-if="currentConfig.sqlData && currentConfig.sqlData.length" -->
    <div
      class="tableBox"
      id="changeHeightDiv"
      :style="{ height: heightStyle + 'px' }"
      v-show="this.isShow"
    >
      <div class="tableBox_top_border" @mousedown="handleMouseTop"></div>
      <div class="sql_result_box">
        <el-tabs v-model="resultTab" type="border-card" v-loading="sqlLoading">
          <!-- （' + successList.length + '） -->
          <el-tab-pane :label="`结果合计（${successList.length}）`" name="success" v-if="successList.length">
            <div class="tag_list">
              <span
                class="search_list"
                title="结果集过滤配置"
                @click="searchData"
              >
                <i class="el-icon-tickets"></i>
              </span>
              <el-tag
                effect="dark"
                :type="!successCurrentIndex && Allmessage ? '' : 'info'"
                @click="
                  Allmessage = true;
                  successCurrentIndex = null;
                "
                >消息合计</el-tag
              >
              <el-tag
                v-for="(item, index) in successList"
                :key="index"
                effect="dark"
                :type="
                  successCurrentIndex !== null && successCurrentIndex === index
                    ? ''
                    : 'info'
                "
                @click="currentClick(item, index)"
              >
                <img src="@/assets/main/1-con-ico08.png" alt />
                {{ "消息" + (index + 1) }}
              </el-tag>
            </div>
            <renderSearchResult
              v-if="
                successList[successCurrentIndex] &&
                successList[successCurrentIndex].sqlType === 'SELECT' &&
                successList[successCurrentIndex].success
              "
              @executeSQL="executeSQL"
              :queryResultData="successList[successCurrentIndex]"
              :readonly="true"
              @setQueryResultData="
                (newVal) => (successList[successCurrentIndex] = newVal)
              "
            />
            <div v-else class="sql_result sql_result--success">
              <span v-if="!Allmessage">
                {{ formatExecutionResult(successList[successCurrentIndex]) }}
              </span>
              <div v-else v-html="lineTexts" class="sql_result_content"></div>
            </div>
          </el-tab-pane>
          <!-- （' + errorList.length + '） -->
          <el-tab-pane :label="`失败合集（${errorList.length}）`" name="error" v-if="errorList.length">
            <div class="tag_list">
              <el-tag
                v-for="(item, index) in errorList"
                :key="index"
                effect="dark"
                :type="errorCurrentIndex === index ? '' : 'info'"
                @click="errorCurrentIndex = index"
              >
                <img src="@/assets/main/1-con-ico08.png" alt />
                {{ "消息" + (index + 1) }}
              </el-tag>
            </div>
            <span class="sql_result" v-if="errorMsg">{{ errorMsg }}</span>
            <span
              class="sql_result"
              v-html="
                errorList[errorCurrentIndex] &&
                errorList[errorCurrentIndex].message
                  ? errorList[errorCurrentIndex].message.replace(/\n/g, '<br/>')
                  : ''
              "
            ></span>
          </el-tab-pane>
        </el-tabs>
        <div class="page-up" @click="upAndDown">
          <span :class="flag ? 'el-icon-caret-bottom' : 'el-icon-caret-top'"></span>
        </div>
      </div>
    </div>
    <el-dialog
      title="查看日志"
      :visible.sync="dialogTableVisible"
      width="90%"
      :before-close="handleClose"
    >
      <span>
        <el-date-picker
          v-model="dateRange"
          style="width: 300px"
          value-format="yyyy-MM-dd"
          type="daterange"
          range-separator="-"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          size="small"
        ></el-date-picker>
        <el-input
          v-model.trim="logSearchKey"
          clearable
          size="small"
          style="width: 240px; margin-left: 10px"
          placeholder="搜索 SQL 内容"
          @keyup.enter.native="searchKey"
        ></el-input>
        <el-select
          v-model="logStatus"
          clearable
          size="small"
          style="width: 130px; margin-left: 10px"
          placeholder="执行状态"
        >
          <el-option label="成功" value="success"></el-option>
          <el-option label="失败" value="fail"></el-option>
        </el-select>
        <el-button
          type="primary"
          size="small"
          @click="searchKey()"
          style="margin-left: 10px"
          >搜 索</el-button
        >
        <el-table
          height="500"
          style="margin-top: 15px"
          :data="gridData"
          border
          v-loading="logLoading"
          empty-text="暂无执行审计记录"
          :cell-style="cellStyle"
          @cell-click="tableCellClick"
        >
          <el-table-column
            property="gmtCreate"
            label="创建时间"
            show-overflow-tooltip
          ></el-table-column>
          <el-table-column
            property="userId"
            label="用户ID"
            width="80"
          ></el-table-column>
          <el-table-column
            property="dataSourceName"
            label="数据源"
            show-overflow-tooltip
          ></el-table-column>
          <el-table-column
            property="type"
            label="数据库类型"
            width="100"
          ></el-table-column>
          <el-table-column
            property="databaseName"
            label="数据库"
            show-overflow-tooltip
          ></el-table-column>
          <el-table-column
            property="schemaName"
            label="模式"
            show-overflow-tooltip
          ></el-table-column>
          <el-table-column
            property="sqlType"
            label="SQL类型"
            width="90"
          ></el-table-column>
          <el-table-column
            property="ddl"
            label="SQL内容（已脱敏）"
            min-width="260"
            show-overflow-tooltip
          ></el-table-column>
          <el-table-column
            label="状态"
            width="80"
          >
            <template slot-scope="scope">
              <el-tag
                size="mini"
                :type="scope.row.status === 'success' ? 'success' : 'danger'"
              >{{ scope.row.status === "success" ? "成功" : "失败" }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column
            property="operationRows"
            label="影响行数"
            width="90"
          >
            <template slot-scope="scope">
              {{ scope.row.operationRows == null ? "-" : scope.row.operationRows }}
            </template>
          </el-table-column>
          <el-table-column label="耗时" width="90">
            <template slot-scope="scope">
              {{ scope.row.useTime == null ? "-" : `${scope.row.useTime} ms` }}
            </template>
          </el-table-column>
          <el-table-column
            property="errorMessage"
            label="失败信息"
            min-width="180"
            show-overflow-tooltip
          ></el-table-column>
        </el-table>
        <el-pagination
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
          :current-page="this.page.pageNum"
          :page-sizes="[10, 20, 50, 100]"
          :page-size="this.page.pageSize"
          layout="total, sizes, prev, pager, next, jumper"
          :total="this.page.total"
          background
        ></el-pagination>
      </span>
      <div slot="footer" style="margin-top: 30px" class="dialog-footer">
        <el-button size="small" @click="handleClose">取 消</el-button>
      </div>
    </el-dialog>
    <el-dialog
      title="结果集过滤配置"
      :visible.sync="dialog"
      width="50%"
      :before-close="closeDialog"
    >
      <div class="searchDialogBox">
        <div class="inputRow">
          <el-select
            size="mini"
            v-model="searchDialogForm.columnTitle"
            placeholder="请选择表头字段"
            @change="filterTitle"
            @clear="clearValue"
            clearable
          >
            <el-option
              v-for="item in headerName"
              :key="item.name"
              :label="item.name"
              :value="item.name"
            ></el-option>
          </el-select>
          <el-select
            size="mini"
            clearable
            style="width: 130px; margin-left: 10px"
            v-model="searchDialogForm.sql"
            placeholder="请选择规则"
          >
            <el-option
              v-for="item in filterConditions"
              :key="item"
              :label="item"
              :value="item"
            ></el-option>
          </el-select>
          <template
            v-if="
              searchDialogForm.sql != 'IS NULL' &&
              searchDialogForm.sql != 'IS NOT NULL'
            "
          >
            <el-date-picker
              style="width: 285px; margin-left: 10px"
              v-if="filterArray != '' && filterArray.dataType == 'DATETIME'"
              v-model="searchDialogForm.contentTest"
              type="datetime"
              placeholder="选择日期时间"
              clearable
              size="mini"
              value-format="yyyy-MM-dd hh:mm:ss"
              default-time="12:00:00"
            ></el-date-picker>
            <el-input
              v-else
              v-model="searchDialogForm.contentTest"
              clearable
              placeholder="请输入内容"
              style="width: 285px; margin-left: 10px"
              size="mini"
            ></el-input>
          </template>
        </div>
        <div class="btnBox">
          <el-button size="mini" plain @click="addSearchForm('And')"
            >Add to Filter(And)</el-button
          >
          <el-button size="mini" plain @click="addSearchForm('Or')"
            >Add to Filter(Or)</el-button
          >
        </div>
      </div>
      <div class="textareaBox">
        <el-input
          type="textarea"
          :autosize="{ minRows: 2, maxRows: 4 }"
          v-model="searchSql"
        ></el-input>
        <div class="btnBox">
          <el-button size="mini" plain @click="addSearchForm2('(')"
            >Add (</el-button
          >
          <el-button size="mini" plain @click="addSearchForm2(')')"
            >Add )</el-button
          >
        </div>
      </div>
      <div slot="footer" style="margin-top: 30px" class="dialog-footer">
        <el-button
          plain
          @click="
            searchSql = '';
            searchDialogForm.columnTitle = '';
            searchDialogForm.sql = '';
            searchDialogForm.contentTest = '';
          "
          >清 空</el-button
        >
        <el-button size="small" @click="closeDialog">取 消</el-button>
        <el-button size="small" @click="saveFilterData">确 定</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.sql_execute {
  height: 100%;
  display: flex;
  flex-flow: column;
  .sql_search_box {
    width: 100%;
    height: 0;
    flex: 1;
    background: #fff;
    display: flex;
    flex-direction: column;
    border-radius: 5px;
    flex-shrink: 0;
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
      .sql-console-status {
        margin-left: auto;
        color: #909399;
        font-size: 12px;
        white-space: nowrap;
        &.is-running { color: #409eff; }
      }
    }

    #editorcode {
      flex: 1;
      height: 0;
    }
  }

  .tableBox {
    display: flex;
    flex-direction: column;
    position: relative;
    padding: 0 10px;
    box-sizing: border-box;
    transition: all 0.3s ease 0s;
    .tableBox_top_border {
      position: absolute;
      width: 100%;
      height: 8px;
      cursor: n-resize;
      z-index: 99;
    }
    .sql_result_box {
      flex: 1;
      height: 0;
      width: 100%;
      background: #fff;
      // border-radius: 5px;
      margin-bottom: 10px;
      box-sizing: border-box;
      .page-up {
        background-color: #fff;
        position: absolute;
        right: 30px;
        top: 5px;
        width: 40px;
        height: 30px;
        border-radius: 20px;
        cursor: pointer;
        transition: 0.3s;
        box-shadow: 0 0 6px rgb(0 0 0 / 12%);
        z-index: 5;
        text-align: center;
        line-height: 30px;
        font-size: 25px;
        span {
          color: #409eff;
        }
      }
      .sql_result {
        overflow: auto;
        font-size: 14px;
        color: #ff4d4f;
        display: flex;
        justify-content: center;
        align-items: center;
        text-align: center;
        height: calc(100% - 48px);
        .sql_result_content{
          height: 100%;
          margin-top: 1%;
        }
      }
      .sql_result--success {
        color: #409eff;
      }
      & > .el-tabs {
        height: 100%;
        ::v-deep .el-tabs__content {
          height: calc(100% - 39px);
          .el-tab-pane {
            height: 100%;
            .tag_list {
              flex-shrink: 0;
              display: flex;
              min-width: 100%;
              overflow-x: auto;
              .el-tag {
                margin-right: 10px;
                cursor: pointer;
                display: flex;
                align-items: center;
                i {
                  flex-shrink: 0;
                  margin-right: 4px;
                }
                .textBox {
                  flex: 1;
                  display: flex;
                  align-items: center;
                }

                .text {
                  flex: 1;
                  width: fit-content;
                  overflow: hidden;
                  white-space: nowrap;
                  text-overflow: ellipsis;
                }
              }
              ::v-deep .el-tag--dark.el-tag--info {
                background: #fff;
                border-color: #fff;
                color: #68728c;
                .el-tag__close,
                .el-tag__close:hover {
                  background: #fff;
                  color: #68728c;
                }
              }
            }
            .table_box {
              padding: 0;
              height: calc(100% - 32px);
            }
          }
        }
      }
    }
  }
}
#icon {
  font-weight: bold;
  font-size: 16px;
  margin-left: 3px;
}
.search_list {
  display: flex;
  align-items: center;
  margin-right: 10px;
  font-size: 20px;
  cursor: pointer;
}
.el-dialog__footer {
  padding-top: 0px;
}

.searchDialogBox {
  display: flex;
  .inputRow {
    flex: 1;
    width: 0;
  }
  .btnBox {
    display: flex;
    flex-direction: column;

    .el-button {
      width: 140px;
      margin-left: 0;
      margin-bottom: 10px;
    }
  }
}
.recent-sql-title { font-weight: 600; margin-bottom: 8px; color: #303133; }
.recent-sql-list { max-height: 280px; overflow-y: auto; }
.recent-sql-item { display: flex; align-items: center; padding: 7px 0; border-bottom: 1px solid #ebeef5; }
.recent-sql-preview { flex: 1; min-width: 0; color: #606266; cursor: pointer; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.recent-sql-preview:hover { color: #409eff; }
.recent-sql-time { margin: 0 8px; color: #909399; font-size: 11px; white-space: nowrap; }
.recent-sql-delete { color: #c0c4cc; cursor: pointer; }
.recent-sql-delete:hover { color: #f56c6c; }
.textareaBox {
  display: flex;
  .el-textarea {
    flex: 1;
    width: 0;
    margin-right: 10px;
    ::v-deep textarea {
      height: 75px !important;
      resize: none;
    }
  }
  .btnBox {
    display: flex;
    flex-direction: column;
    .el-button {
      width: 80px;
      margin-left: 0;
      margin-bottom: 10px;
    }
  }
}
.draggble2 {
  width: 100%;
  height: 25vh;
}
</style>
