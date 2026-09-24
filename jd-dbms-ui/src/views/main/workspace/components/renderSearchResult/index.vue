<script>
import sqlServer from "@/api/main/sql";
import SqlPreview from "../DataSource/SqlPreview.vue";
import MonacoEditor from "@/components/MonacoEditor/index.vue";
import ScreeningResult from "@/views/main/workspace/components/ScreeningResult/index.vue";
import { downloadFile } from "@/utils/file";
import { copyText } from "@/utils";
import { getToken } from "@/utils/auth";
import Filedialog from "./filedialog.vue";
import tableServer from "@/api/main/table";
import viewServer from "@/api/main/view";
import ViewGrantPanel from "../renderViewEditor/ViewGrantPanel.vue";
import importDataDialog from "./importDataDialog";

export default {
  name: "renderSearchResult_table",
  components: {
    SqlPreview,
    ScreeningResult,
    MonacoEditor,
    Filedialog,
    importDataDialog,
    ViewGrantPanel,
  },
  props: {
    queryResultData: {
      type: Object,
      default: () => {},
    },
    readonly: {
      type: Boolean,
      default: false,
    },
    typeView: {
      type: String,
      default: "",
    },
  },
  computed: {
    isMaterializedView() {
      return String(this.queryResultData?.uniqueData?.viewType || "").toUpperCase() === "MATERIALIZED VIEW";
    },
    headerList() {
      if (
        this.queryResultData.headerList &&
        this.queryResultData.headerList.length
      ) {
        return this.queryResultData.headerList.filter((item) => {
          return this.checkList.includes(item.name) && item.name != "行号" && item.name != "ROWID";
        });
      } else {
        return [];
      }
    },
    returnClass() {
      return (state) => {
        let className = "";
        switch (state) {
          case 0:
            break;
          case 1:
            className = "yellowCell activeCell";
            break;
          case 2:
            className = "redCell activeCell";
            break;
          case 3:
            className = "greenCell activeCell";
            break;
        }
        return className;
      };
    },
  },
  data() {
    let checkFile = (rule, value, callback) => {
      if (!this.fileName) {
        return callback(new Error("文件不能为空"));
      } else {
        callback();
      }
    };
    return {
      selections: [],
      sort: {
        order: "",
        prop: "",
      },
      selectedCols: [],
      lastClickCol: "",

      editStatus: true,
      editingOriginalRow: null,
      editingHeader: null,
      queryData: {},
      checked: false,
      isErrorExecute: false,
      importColumns: [],
      isLoading: false,
      isDialogLoading: false,
      dialogImportDataVisible: false,
      isCreate: false,
      newArr: [],
      isColor: false,
      pageSql: null,
      // sql操作
      sqlPreviewType: null,
      storageKey: "myState",
      sqlInfo: [],
      oldRow: null,
      customHeaders: {
        Authorization: "Bearer " + getToken(),
      },
      multipleSelection: [],
      checkList: [],
      showCheckHeader: false,
      queryParams: {
        params: null,
        pageNum: 1,
        pageSize: 100,
        isPage: false,
      },
      page: {
        pageNum: 1,
        pageSize: 20,
        total: 0,
      },
      updatePage: {
        pageNum: 1,
        pageSize: 20,
        total: 0,
      },
      resPage: {
        pageNum: 1,
        pageSize: 20,
        total: 0,
      },
      total: 0,
      clickRow: null, // 当前点击的行
      clickCell: null, // 当前点击的列
      selectedRow: -1,
      selectedColumn: null,
      dataTable: [],
      oldTableData: [],
      tableLoading: false,
      columnType: {
        date: ["DATE", "DATETIME", "TIMESTAMP", "YEAR"],
      },
      activeName: "add",
      activeTab: "SUCCESS",
      table_data: [],
      table_headers: [],
      editTableData: [],
      addTableData: [],
      editTableHeaders: [],
      editLeftTableData: [],
      editRightTableData: [],
      tabRowindex: null,
      tabColumnKey: null,
      pageLoading: false, // 页面loading标识
      filterSortForm: {
        keyName: "",
        condition: "",
        keyValue: "",
        keyValue2: "",

        leftSelect: -1,
        rightSelect: -1,
        nullsChecked: false,
        nullType: "last",
        sortArr: [],
      },
      localArr: [],
      timer: null,
      dialogImportVisible: false,
      importRounds: 1,
      fileName: "",
      databaseImportInfo: {
        logFile: "",
        errorNum: 0,
        message: [],
      },
      importForm: {
        haveTitle: true,
        startRow: 1,
        errorStop: false,
        errorRollback: false,
      },
      importRules: {
        haveTitle: [
          { required: true, message: "请选择是否存在表头", trigger: "change" },
        ],
        startRow: [
          {
            required: true,
            message: "请输入从第几行开始导入",
            trigger: "blur",
          },
        ],
        errorStop: [
          { required: true, message: "请选择是否错误停止", trigger: "change" },
        ],
        errorRollback: [
          { required: true, message: "请选择是否错误回滚", trigger: "change" },
        ],
        file: [{ validator: checkFile, trigger: "change" }],
      },
      sortObj: {},
      sortParams: {},
      fileType: "",
      pasteDialogVisible: false,
      pasteData: [],
      copyHeader: [],
      exoprtObj: {},
      addSuccessData: [],
      dataIndex: [],
      headers: [],
      sqlType: ["LIKE", "NOT LIKE", "=", ">=", "<=", "IS NULL", "IS NOT NULL"],
      filterArray: "",

      add_importRounds: 1,
      edit_importRounds: 1,
      isFileObj: {},
      fileList: [], // 上传文件列表,
      isFile: false,

      searchDialogVisible: false,
      searchDialogForm: {
        columnTitle: "",
        sql: "",
        contentTest: "",
      },

      searchSql: "",
      ctrlPressed: false, //是否按下ctrl
      selectedColumns: [], //复制选中列
      highlightedColumnIndex: [], //高亮列
      materializedViewRefreshing: false,
      materializedDdlLoading: false,
      materializedDdlVisible: false,
      materializedDdl: "",
      materializedGrantVisible: false,
    };
  },
  watch: {
    queryResultData: {
      handler(newVal, oldVal) {
        console.log(newVal, "变换值");
        if (newVal != oldVal) {
          this.sortObj = {}; // 清空排序入参
          this.sortParams = {}; // 存储筛选的对象 针对导出时候的传参 切换清空 防止切换表的时候影响
          this.isColor = false; // 切换表高级查询取消背景高亮
        }
        if (oldVal && newVal.tableName != oldVal.tableName) {
          this.searchDialogForm.columnTitle = "";
          this.searchDialogForm.sql = "";
          this.searchDialogForm.contentTest = "";
          this.searchSql = "";
          this.pageSql = null;
          this.filterSql = null;
          this.queryParams = { params: null, pageNum: 1, pageSize: 100 };
        }
        if (newVal.dataTable) {
          this.pageLoading = true;
          this.oldTableData = newVal.oldTableData;
          if (newVal.sqlInfo) {
            this.sqlInfo = newVal.sqlInfo;
          }
          this.$nextTick(() => {
            setTimeout(() => {
              this.$refs.ref_table.doLayout();
            }, 1000);
          });
        } else {
          this.isFile = false;
          let tableData = [];
          if (newVal?.dataList) {
            tableData = newVal.dataList?.map((item) => {
              let array = {};
              for (const dataKey in newVal.headerList) {
                array[newVal.headerList[dataKey].name] = item[dataKey];
              }
              return array;
            });
          }
          // setTimeout(() => {
          let old = null;
          if (tableData && tableData.length) {
            this.dataTable = tableData;
            old = JSON.parse(JSON.stringify(this.dataTable));
          } else {
            this.dataTable = [];
          }
          this.localArr = [];
          this.localArr.push(old || []);
          // console.log("刷新初始化数据", this.localArr);
          localStorage.setItem(this.storageKey, JSON.stringify(this.localArr));
          // }, 500);
          this.checkList = [];
          if (newVal?.headerList) {
            this.copyHeader = JSON.parse(JSON.stringify(newVal.headerList));
            if (this.copyHeader && this.copyHeader.length) {
              this.copyHeader.forEach((item) => {
                this.checkList.push(item.name);
              });
            }
          }
          console.log(this.checkList,'checkList');
          this.oldTableData = tableData && tableData.length
            ? JSON.parse(JSON.stringify(tableData))
            : [];
          this.sqlInfo = [];
        }
        this.total = Number(newVal?.fuzzyTotal || 0);
        this.$nextTick(() => {
          this.$refs.ref_table.clearSort();
          this.pageLoading = false;
          this.$refs.ref_table.doLayout();
        });
        // console.log(this.dataTable, this.headerList, "变换后的值");
      },
      immediate: true,
      deep: true,
    },
    activeName: {
      handler(val) {
        this.addSuccessData = [];
        if (val == "add") {
          if (this.add_importRounds == 3) {
            this.queryResultTableData();
            this.resPage.pageSize = 20;
            this.resPage.pageNum = 1;
            this.resPage.total = 0;
          } else {
            this.queryPageData(this.exoprtObj);
          }
        } else {
          if (this.edit_importRounds == 3) {
            this.queryResultTableData();
            this.resPage.pageSize = 20;
            this.resPage.pageNum = 1;
            this.resPage.total = 0;
          } else {
            this.queryPageData(this.exoprtObj);
          }
        }
      },
    },
    activeTab: {
      handler() {
        this.resPage.pageSize = 20;
        this.resPage.pageNum = 1;
        this.resPage.total = 0;
        this.queryResultTableData();
      },
    },
  },
  mounted() {
    //监听键盘按下以及抬起
    window.addEventListener("keydown", this.handleKeyDown);
    window.addEventListener("keyup", this.handleKeyUp);
  },
  beforeDestroy() {
    //销毁监听键盘按下以及抬起
    window.removeEventListener("keydown", this.handleKeyDown);
    window.removeEventListener("keyup", this.handleKeyUp);
  },
  methods: {
    async showMaterializedDdl() {
      this.materializedDdlLoading = true;
      try {
        const res = await viewServer.getViewDetail({
          dataSourceId: this.queryResultData.uniqueData.dataSourceId,
          databaseName: this.queryResultData.uniqueData.databaseName,
          schemaName: this.queryResultData.uniqueData.schemaName,
          tableName: this.queryResultData.uniqueData.tableName,
        });
        if (!res.success || !res.data?.ddl) {
          this.$message.error(res.errorMessage || "获取物化视图 DDL 失败");
          return;
        }
        this.materializedDdl = res.data.ddl;
        this.materializedDdlVisible = true;
      } catch (e) {
        this.$message.error("获取物化视图 DDL 失败");
      } finally {
        this.materializedDdlLoading = false;
      }
    },
    exportMaterializedDdl() {
      downloadFile(process.env.VUE_APP_BASE_API + "/api/rdb/table/exportViewDDL", {
        dataSourceId: this.queryResultData.uniqueData.dataSourceId,
        sql: this.materializedDdl,
      });
    },
    async refreshMaterializedView() {
      this.materializedViewRefreshing = true;
      try {
        const res = await viewServer.refreshMaterialized({
          dataSourceId: this.queryResultData.uniqueData.dataSourceId,
          databaseName: this.queryResultData.uniqueData.databaseName,
          schemaName: this.queryResultData.uniqueData.schemaName,
          tableName: this.queryResultData.uniqueData.tableName,
          viewType: this.queryResultData.uniqueData.viewType,
        });
        if (!res.success) {
          this.$message.error(res.errorMessage || "物化视图刷新失败");
          return;
        }
        this.$message.success("物化视图刷新成功");
        this.refresh();
      } catch (e) {
        this.$message.error("物化视图刷新失败，请稍后重试");
      } finally {
        this.materializedViewRefreshing = false;
      }
    },
    changeSort(order, column) {
      // console.log(order,column);
      this.sort = {
        order,
        prop: column.property,
      };
      let data = {
        column,
        order,
        prop: column.label,
      };
      this.pageLoading = true;
      this.sortChange(data, "");
    },

    //键盘按下
    handleKeyDown(event) {
      if (event.key === "Shift") {
        this.ctrlPressed = true;
      }
    },
    //键盘抬起
    handleKeyUp(event) {
      if (event.key === "Shift") {
        this.ctrlPressed = false;
        this.selectedColumns = [];
        this.highlightedColumnIndex = [];
      }
    },
    //表头点击（防止排序等默认行为干扰）
    handleHeaderClick(column, event) {
      if (this.ctrlPressed) {
        event.stopPropagation();
        let index = this.selectedColumns.findIndex(
          (row) => row === column.property
        );
        if (index === -1) {
          this.selectedColumns.push(column.property);
        } else {
          this.selectedColumns.splice(index, 1);
        }
        let cellIndex = this.headerList.findIndex(
          (i) => i.name == column.property
        );
        if (this.readonly) {
          if (cellIndex > -1) {
            this.highlightedColumnIndex.includes(cellIndex)
              ? (this.highlightedColumnIndex =
                  this.highlightedColumnIndex.filter((n) => n !== cellIndex))
              : this.highlightedColumnIndex.push(cellIndex);
          }
        } else {
          if (cellIndex > -1) {
            this.highlightedColumnIndex.includes(cellIndex + 1)
              ? (this.highlightedColumnIndex =
                  this.highlightedColumnIndex.filter(
                    (n) => n !== cellIndex + 1
                  ))
              : this.highlightedColumnIndex.push(cellIndex + 1);
          }
        }

        this.copySelectedColumns();
      } else {
        console.log("点击鼠标没按下", this.ctrlPressed);
      }
    },
    //处理自定义表头点击
    handleCustomHeaderClick(column, event) {
      if (this.ctrlPressed) {
        console.log("处理自定义表头点击");
        event.stopPropagation();
        // this.copySelectedColumns();
      }
    },
    copySelectedColumns() {
      if (this.selectedColumns && this.selectedColumns.length === 0) {
        this.$message.warning("请先选择要复制的列");
        return;
      }
      console.log("this.selectedColumns", this.selectedColumns);
      //获取数据
      let dataRows = this.dataTable.map((row) => {
        return this.selectedColumns.map((col) => {
          let value = row[col];
          if (value == null) value = "";
          if (typeof value === "string") {
            value = value.replace(/\t/g, " ").replace(/\n/g, " ");
          }
          return value;
        });
      });
      let textToCopy = "";
      textToCopy += dataRows.map((row) => row.join("\t")).join("\n");
      //复制到剪切板
      this.copyToClipboard(textToCopy);
      this.$message.success(
        `成功复制${this.selectedColumns.length}列数据到剪切板`
      );
    },
    copyToClipboard(text) {
      let textarea = document.createElement("textarea");
      textarea.value = text;
      textarea.style.position = "fixed";
      textarea.style.opacity = "0";
      document.body.appendChild(textarea);
      textarea.select();
      try {
        let successful = document.execCommand("copy");
        if (!successful) {
          throw new Error("复制失败");
        }
      } catch (error) {
        this.$message.error("复制失败");
      }
      document.body.removeChild(textarea);
    },
    onHeaderClick(event, clickedProp) {
      if (event.shiftKey) {
        if (!this.selectedCols.includes(clickedProp)) {
          this.selectedCols.push(clickedProp);
        }
      } else {
        this.selectedCols = [clickedProp];
      }
      this.lastClickCol = clickedProp;
      this.copySelectedCols();
    },

    copySelectedCols() {
      if (this.selectedCols.length == 0) return;
      let headers = this.headerList
        .filter((item) => this.selectedCols.includes(item.name))
        .map((c) => c.name);
      let rows = this.dataTable.map((row) =>
        this.selectedCols
          .map((col) => {
            let val = row[col];
            if (typeof val === "string" && val.includes("\n")) {
              val = `"${val}"`;
            }
            return val;
          })
          .join("\t")
      );
      console.log(headers, rows, ">>>>>>>>>>>>");
      let text = [headers.join("\t"), ...rows].join("\n");
      let textarea = document.createElement("textarea");
      textarea.style.position = "fixed";
      textarea.style.opacity = "0";
      textarea.value = text;
      document.body.appendChild(textarea);
      textarea.select();

      try {
        document.execCommand("copy");
        this.$message.success(`已复制${this.selectedCols.length}列`);
        setTimeout(() => {
          document.addEventListener("click", this.handleOutSideClick);
        }, 100);
      } catch (error) {
        this.$message.error("复制失败");
      }
    },
    handleOutSideClick(event) {
      let tableEL = this.$el.querySelector(".el-table");
      if (!tableEL.contains(event.target)) {
        this.selectedCols = [];
        this.lastClickCol = "";
        document.removeEventListener("click", this.handleOutSideClick);
      }
    },
    // file 转换 base64
    fileToBase64(file) {
      return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = (res) => {
          const base = res.target?.result;
          resolve(base);
        };
        reader.onerror = (error) => {
          reject(error);
        };
        reader.readAsDataURL(file.raw);
      });
    },
    fileChange(file, row, field) {
      this.fileToBase64(file).then((res) => {
        row[field] = res.split(",")[1];
        this.isFile = true;
        let sqlInfoTemp = [];
        let type = "";
        this.sqlInfo.forEach((item) => {
          if (item.rowId == row["行号"]) {
            type = item.type;
          } else {
            sqlInfoTemp.push(item);
          }
        });
        let sqlInfo = {};
        if (type == "CREATE") {
          sqlInfo = {
            type: "CREATE",
            rowId: row["行号"],
            dataList: this.queryResultData.headerList.map((item) => {
              return this.cellValue(row, item, true);
            }),
          };
        } else {
          sqlInfo = {
            type: "UPDATE",
            rowId: row["行号"],
            oldDataList: this.queryResultData.headerList.map((item) => {
              return this.cellValue(this.oldRow, item);
            }),
            dataList: this.queryResultData.headerList.map((item) => {
              return this.cellValue(row, item);
            }),
          };
        }
        sqlInfoTemp.push(sqlInfo);
        this.sqlInfo = sqlInfoTemp;
        console.log(this.sqlInfo, "第一次sqlInfo");
      });
    },
    refresh() {
      this.sort = {
        order: "",
        prop: "",
      };
      if (this.isColor) {
        this.advancedQuery();
      } else {
        // 顶部刷新按钮
        this.pageLoading = true;
        this.sortObj = {};
        this.sortParams = {};
        this.isColor = false;
        this.filterSql = "";
        this.pageSql = "";
        this.advancedQuery();
        this.clickRow = null;
        this.clickCell = null;
        this.editStatus = true;
      }
      this.queryParams.pageNum = 1;
    },
    // 粘贴事件
    pasteInfo(e) {
      if (this.readonly) return;
      e.preventDefault(); //阻止默认粘贴事件
      e.stopPropagation(); //阻止事件冒泡
      if (!this.selectedRow && !this.selectedColumn) {
        this.$message.warning("请选择开始粘贴位置！");
        return;
      }
      try {
        let clipboardData = e.clipboardData || window.clipboardData; // IE
        if (!clipboardData) {
          //chrome
          clipboardData = e.originalEvent.clipboardData;
        }
        let data = clipboardData.getData("Text"); //复制过来的内容
        if (data) {
          let rows = []; // 解析成为 数组 格式
          // console.log(clipboardData,data);
          let dataArr = data.split("\r\n");
          // console.log(dataArr);
          dataArr.forEach((item) => {
            if (item) {
              // 筛除全为空的数据
              let values = item.split("\t");
              let isEmpty = values.some((item) => {
                return item != "";
              });
              if (isEmpty) {
                rows.push(values);
              }
            }
          });
          console.log(rows[0], "rows[0]");
          if (rows.length) {
            if (rows.length == 1) {
              this.singleRowInsert(rows[0]);
              this.$message.success("粘贴成功");
            } else {
              // 多行插入
              this.pasteData = rows;
              this.pasteDialogVisible = true;
            }
          }
        }
      } catch (err) {
        this.$message.error(err);
      }
    },
    singleRowInsert(data) {
      // 单行插入
      let columnIndex = this.selectedColumn.index - 1;
      let keys = this.headerList
        .map((item) => {
          return item.name;
        })
        .filter((key, index) => {
          return index >= columnIndex;
        });
      let pasteObj = {};
      data.forEach((item, index) => {
        pasteObj[keys[index]] = item;
      });
      Object.assign(this.selectedRow, pasteObj);
      // if(!this.selectedRow.rowType || this.selectedRow.rowType != 'add'){
      this.inputBlur(this.selectedRow, this.selectedColumn);
      // }
    },
    multiRowInsert() {
      let columnIndex = this.selectedColumn.index - 1;
      let keys = this.headerList
        .map((item) => {
          return item.name;
        })
        .filter((key, index) => {
          return index >= columnIndex;
        });
      this.pasteData.forEach((item, index) => {
        if (index == 0) {
          this.singleRowInsert(item);
        } else {
          let pasteObj = {};
          item.forEach((val, index2) => {
            pasteObj[keys[index2]] = val;
          });
          this.addRow(pasteObj);
        }
      });
    },

    defaultDestroy() {
      // let MonacoEditorValue = this.$refs.screeningResult.getMonacoEditorValue();
      this.$emit("setQueryResultData", {
        ...this.queryResultData,
        queryParams: this.queryParams,
        dataTable: this.dataTable,
        oldTableData: this.oldTableData,
        sqlInfo: this.sqlInfo,
        // whereValue: MonacoEditorValue.whereValue,
        // orderByValue: MonacoEditorValue.orderByValue
      });
      // this.$refs.screeningResult.setMonacoEditorValue("", "");
    },
    getTableList(params) {
      if (params) this.queryParams.params = params;
      let send = {
        dataSourceId: this.queryResultData.uniqueData.dataSourceId,
        pageNo: this.queryParams.pageNum,
        pageSize: this.queryParams.pageSize,
        schemaName: this.queryResultData.uniqueData.schemaName,
        sql: `SELECT * FROM ${this.queryResultData.tableName}`,
        ...this.queryParams.params,
      };

      sqlServer.executeSql(send).then((res) => {
        if (res.data.length && res.data[0].success) {
          let tableData = res.data[0].dataList?.map((item) => {
            let array = {};
            for (const dataKey in res.data[0].headerList) {
              array[res.data[0].headerList[dataKey].name] = item[dataKey];
            }
            return array;
          });
          this.dataTable = tableData;
          this.$emit("setQueryResultData", {
            ...this.queryResultData,
            ...res.data[0],
            fuzzyTotal: this.total,
            queryParams: this.queryParams,
            dataTable: null,
            sqlInfo: null,
          });
        } else {
          this.$notify.error({
            title: "错误",
            message: res.data[0].message,
          });
        }
      });
    },
    //执行blob新增
    executeBlobData() {
      let send = {
        dataSourceId: this.queryResultData.uniqueData.dataSourceId,
        headerList: this.queryResultData.headerList,
        operations: this.sqlInfo,
        schemaName: this.queryResultData.uniqueData.schemaName,
        tableName: this.queryResultData.tableName,
        type: this.queryResultData.uniqueData.databaseType,
      };
      tableServer.addBlobData(send).then((res) => {
        if (res.success && res.data) {
          this.$message.success("保存成功");
          this.advancedQuery();
          this.isFile = false;
          this.sqlInfo = [];
          this.$refs.ref_table.clearSelection();
          this.clickRow = null;
          this.clickCell = null;
        } else {
          this.$notify.error({
            title: "保存失败!",
            message: res.errorMessage,
          });
        }
      });
    },

    async executeUpdateDataSql() {
      this.commitActiveEdit();
      this.syncPendingOperations();
      if (!this.sqlInfo.length) {
        this.$notify.error({
          title: "提醒",
          message: "表数据未做修改",
        });
        return;
      }
      const hasContentColumn = this.queryResultData.headerList.some(
        (item) => item.dataType === "CONTENT"
      );
      if (this.isFile || hasContentColumn) {
        this.executeBlobData();
      } else {
        console.log(this.sqlInfo, "this.sqlInfo");
        let sql;
        try {
          sql = await this.getExecuteUpdateSql(this.sqlInfo);
        } catch (error) {
          this.$notify.error({
            title: "生成保存语句失败",
            message: error.message || "请检查表结构和待保存数据",
          });
          return;
        }
        if (sql == "" || sql == null) {
          this.$notify.error({
            title: "提醒",
            message: "表数据未做修改",
          });
        } else {
          let send = {
            sql,
            dataSourceId: this.queryResultData.uniqueData.dataSourceId,
            databaseName: this.queryResultData.uniqueData?.databaseName,
            schemaName: this.queryResultData.uniqueData.schemaName,
            tableName: this.queryResultData.tableName,
          };
          let that = this;
          this.$modal
            .confirm("是否确认保存修改数据？")
            .then(function () {
              console.log(that.dataTable.length, that.selections?.length);
              if (that.selections?.length == that.dataTable?.length) {
                that.queryParams.pageNum--; // 如果整页删除 页码 -1
              }
              return sqlServer.executeUpdateDataSql(send);
            })
            .then((res) => {
              if (res.success) {
                this.$message.success("保存成功");
                this.editStatus = true;
                this.sqlInfo = [];
                this.$refs.ref_table.clearSelection();
                this.$refs.sqlPreview.dialogClose();
                if (this.pageSql) {
                  this.isColor = true;
                } else {
                  this.isColor = false;
                  this.pageSql = "";
                  this.advancedQuery();
                }
              } else {
                this.$notify.error({
                  title: "错误",
                  message: res.errorMessage,
                });
              }
            });
        }
      }
    },
    // 获取sql
    async getExecuteUpdateSql(operations) {
      let send = {
        dataSourceId: this.queryResultData.uniqueData.dataSourceId,
        databaseName: this.queryResultData.uniqueData?.databaseName,
        headerList: this.queryResultData.headerList,
        operations: operations,
        schemaName: this.queryResultData.uniqueData.schemaName,
        tableName: this.queryResultData.tableName,
        type: this.queryResultData.uniqueData.databaseType,
        isView: this.typeView == "views" ? true : undefined,
      };
      let res = await sqlServer.getExecuteUpdateSql(send);
      if (!res.success) {
        throw new Error(res.errorMessage || res.errorCode || "生成保存语句失败");
      }
      return res.data;
    },
    // 撤销按钮是否可用
    revokeDisableBarState() {
      // 如果有聚焦的行，但是没有操作过的数据，则不可用
      const operationType = ["CREATE", "UPDATE", "DELETE"];
      const curOperationRowNo = this.multipleSelection.map(
        (item) => item["行号"]
      );
      if (curOperationRowNo.length) {
        // 当前选中的行里面有没有操作过的数据
        const hasOperationData = this.sqlInfo.some((item) => {
          return (
            operationType.includes(item.type) &&
            curOperationRowNo.includes(item.rowId)
          );
        });
        if (hasOperationData) {
          return false;
        }
      }
      // // 如果有聚焦的单元格
      // if (editingCell && editingCell[2] === false) {
      //   const oldRowDataList = oldDataList.find((item) => item[0] === editingCell[1]);
      //   const oldData = oldRowDataList?.[editingCell[0]];
      //   // 如果当前单元格的数据和老数据一样，则可用
      //   if (oldData !== editingData) {
      //     return false;
      //   }
      // }
      // 如果都没，那撤销按钮不可用
      return true;
    },
    // 回退
    handelBack() {
      let storedState = localStorage.getItem(this.storageKey);
      let backList = JSON.parse(storedState);
      let list = backList.slice(0, -1);
      this.localArr = this.localArr.slice(0, -1);
      localStorage.setItem(this.storageKey, JSON.stringify(list));
      this.dataTable = list[list.length - 1];
      if (list.length == 1) {
        let addList = list.filter(
          (item) => item.rowType && item.rowType == "add"
        );
        if (!addList.length) {
          this.editStatus = true;
          this.sqlInfo = [];
          // return this.$message.warning("无回退内容");
        }
      }
    },
    inputFocus(textareaId) {
      this.$nextTick(() => {
        let textarea = document.querySelector(`#${textareaId}`);
        if (textarea) {
          setTimeout(() => {
            textarea.focus();
          });
        }
      });
    },
    // 失去焦点初始化
    inputBlur(row, header) {
      this.inputFocusFlag = false;
      let oldData1 = JSON.parse(JSON.stringify(this.dataTable));
      this.localArr.push(oldData1);

      localStorage.setItem(this.storageKey, JSON.stringify(this.localArr));
      let sqlInfoItem = this.sqlInfo.find((item) => {
        return item.rowId === row["行号"];
      });
      const editedHeader = header || this.editingHeader || this.selectedColumn;
      if (!editedHeader) return;
      const originalRow = this.editingOriginalRow || this.oldRow || {};
      let key = editedHeader.name || editedHeader.property;
      if (originalRow[key] !== row[key]) {
        if (sqlInfoItem) {
          if (sqlInfoItem.type === "DELETE") {
            sqlInfoItem.type = "UPDATE";
            if (sqlInfoItem.dataList) {
              sqlInfoItem.oldDataList = sqlInfoItem.dataList;
            } else {
              sqlInfoItem.oldDataList = this.queryResultData.headerList.map(
                (item) => {
                  return this.cellValue(originalRow, item);
                }
              );
            }
          }
          if (sqlInfoItem.type === "CREATE") {
            sqlInfoItem.dataList = this.newArr.map((item) => {
              return this.cellValue(row, item, true);
            });
          } else {
            sqlInfoItem.dataList = this.queryResultData.headerList.map(
              (item) => {
                return this.cellValue(row, item);
              }
            );
          }
        } else {
          this.sqlInfo.push({
            type: "UPDATE",
            rowId: row["行号"],
            oldDataList: this.queryResultData.headerList.map((item) => {
              return this.cellValue(originalRow, item);
            }),
            dataList: this.queryResultData.headerList.map((item) => {
              return this.cellValue(row, item, true);
            }),
          });
        }
      }

      this.clickRow = null;
      this.clickCell = null;
      this.editingOriginalRow = null;
      this.editingHeader = null;
    },
    cellValue(row, header, useDefault) {
      const value = row ? row[header.name] : null;
      if (value !== undefined && value !== null) return value;
      return useDefault && header.defaultValue
        ? "CHAT2DB_DEFAULT_VALUE"
        : null;
    },
    sameCellValue(left, right) {
      if (left === null || left === undefined) {
        return right === null || right === undefined;
      }
      if (right === null || right === undefined) return false;
      return String(left) === String(right);
    },
    syncPendingOperations() {
      const headers = this.queryResultData.headerList || [];
      const rowsById = new Map(
        this.dataTable.map((row) => [String(row["行号"]), row])
      );
      const originalById = new Map(
        (this.oldTableData || []).map((row) => [String(row["行号"]), row])
      );
      const operations = [];
      const fixedRowIds = new Set();

      this.sqlInfo.forEach((operation) => {
        const rowId = String(operation.rowId);
        const row = rowsById.get(rowId);
        if (operation.type === "CREATE" && row) {
          operations.push({
            type: "CREATE",
            rowId: operation.rowId,
            dataList: headers.map((header) => this.cellValue(row, header, true)),
          });
          fixedRowIds.add(rowId);
        } else if (operation.type === "DELETE") {
          const originalRow = originalById.get(rowId) || row;
          operations.push({
            type: "DELETE",
            rowId: operation.rowId,
            oldDataList: operation.oldDataList || headers.map(
              (header) => this.cellValue(originalRow, header)
            ),
          });
          fixedRowIds.add(rowId);
        }
      });

      this.dataTable.forEach((row) => {
        const rowId = String(row["行号"]);
        if (fixedRowIds.has(rowId)) return;
        const originalRow = originalById.get(rowId);
        if (!originalRow) return;
        const oldDataList = headers.map((header) => this.cellValue(originalRow, header));
        const dataList = headers.map((header) => this.cellValue(row, header));
        if (dataList.some((value, index) => !this.sameCellValue(value, oldDataList[index]))) {
          operations.push({
            type: "UPDATE",
            rowId: row["行号"],
            oldDataList,
            dataList,
          });
        }
      });
      this.sqlInfo = operations;
    },
    commitActiveEdit() {
      if (this.clickRow === null || !this.editingHeader) return;
      const row = this.dataTable[this.clickRow];
      if (row) this.inputBlur(row, this.editingHeader);
    },
    isDateColumn(dataType) {
      const type = String(dataType || "").toUpperCase();
      return type === "DATE" || type === "DATETIME" || type === "YEAR" || type.startsWith("TIMESTAMP");
    },
    datePickerType(dataType) {
      const type = String(dataType || "").toUpperCase();
      if (type === "YEAR") return "year";
      if (type === "DATE") return "date";
      return "datetime";
    },
    // 撤销
    handleRevoke() {
      const curOperationRowNo = this.multipleSelection.map(
        (item) => item["行号"]
      );
      if (this.revokeDisableBarState()) {
        return;
      }
      // 多行撤销处理
      if (curOperationRowNo?.length) {
        this.sqlInfo = this.sqlInfo.filter(
          (item) => !curOperationRowNo?.includes(item.rowId)
        );
        for (let i = 0; i < this.dataTable.length; i++) {
          let item = this.dataTable[i];
          if (curOperationRowNo.includes(item["行号"])) {
            let oldData = this.oldTableData.find(
              (oldData) => oldData["行号"] === item["行号"]
            );
            if (oldData) {
              item = oldData;
            } else {
              this.dataTable.splice(i, 1);
              i--;
            }
          }
          if (item["行号"] !== i + 1) {
            let sqlInfoItem = this.sqlInfo.find(
              (sql) => sql.rowId === item["行号"]
            );
            if (sqlInfoItem) sqlInfoItem.rowId = i + 1;
            item["行号"] = i + 1;
          }
        }
        this.$refs.ref_table.clearSelection();
      }
      // // 聚焦单元格撤销
      // if (editingCell && editingCell[2] === false) {
      //   const oldRowTableData = oldTableData.find((item) => item[colNoCode] === editingCell[1])!;
      //   const oldData = oldRowTableData[editingCell[0]];
      //   const _tableData = tableData.map((item) => {
      //     if (item[colNoCode] === editingCell[1]) {
      //       item[editingCell[0]] = oldData || '';
      //     }
      //     return item;
      //   });
      //
      //   // 如果撤销后这一行的数据和原始数据一样，则删除这条更新记录
      //   const newRowTableData = _tableData.find((item) => item[colNoCode] === editingCell[1])!;
      //   if (lodash.isEqual(newRowTableData, oldRowTableData)) {
      //     setUpdateData(updateData.filter((item) => item.rowId !== editingCell[1]));
      //   }
      //
      //   setTableData(_tableData);
      // }
    },
    // 导出
    handleExportSQLResult(command) {
      let commandData = {
        a: {
          exportType: "CSV",
          exportSize: "ALL",
        },
        b: {
          exportType: "INSERT",
          exportSize: "ALL",
        },
        c: {
          exportType: "CSV",
          exportSize: "CURRENT_PAGE",
        },
        d: {
          exportType: "INSERT",
          exportSize: "CURRENT_PAGE",
        },
        e: {
          exportType: "EXCEL",
          exportSize: "ALL",
        },
        f: {
          exportType: "EXCEL",
          exportSize: "CURRENT_PAGE",
        },
        g: {
          exportType: "INSERT",
          exportSize: "CHOOSE",
        },
        h: {
          exportType: "CSV",
          exportSize: "CHOOSE",
        },
        i: {
          exportType: "EXCEL",
          exportSize: "CHOOSE",
        },
      };

      // 获取主键字段
      let mainKey = [];
      this.queryResultData.headerList.forEach((item) => {
        if (item.primaryKey) {
          mainKey.push(item.name);
        }
      });

      let exportPrimaryKeyList = [];
      let isNullObj = {};

      if (this.multipleSelection) {
        this.multipleSelection.forEach((item) => {
          let primaryKey = {};
          mainKey.forEach((key) => {
            primaryKey[key] = item[key];
          });
          exportPrimaryKeyList.push(primaryKey);
          isNullObj = exportPrimaryKeyList[0];
        });
      }

      // 因为多个index参数 将原数据拷贝一份用于传参 tempDataList
      let tempDataList = JSON.parse(JSON.stringify(this.multipleSelection));
      tempDataList.forEach((item) => delete item.index);
      tempDataList.forEach((item) => {
        delete item.行号;
      });
      const params = {
        dataSourceId: this.queryResultData.uniqueData.dataSourceId,
        exportType: commandData[command].exportType,
        exportSize: commandData[command].exportSize,
        exportTableName: `"${this.queryResultData.params.schemaName}"."${this.queryResultData.params.tableName}"`,
        schemaName: this.queryResultData.uniqueData.schemaName,
        exportPrimaryKeyList:
          JSON.stringify(isNullObj) == "{}" ? [] : exportPrimaryKeyList,
        dataList:
          JSON.stringify(isNullObj) == "{}" &&
          (command == "i" || command == "g")
            ? tempDataList
            : undefined,
        pageNum: this.queryParams.pageNum,
        pageSize: this.queryParams.pageSize,
        databaseName: this.queryResultData.uniqueData.databaseName,
        databaseType: this.queryResultData.uniqueData.databaseType,
        tableName: `"${this.queryResultData.params.tableName}"`,
        sql: this.queryResultData.sql,
        originalSql: this.queryResultData.originalSql,
        filterSql: this.filterSql,
        isDesc:
          this.sortParams.order == "descending"
            ? false
            : this.sortParams.order == "ascending"
            ? true
            : undefined, // ascending 升序
        filterColumn: this.sortParams.prop,
      };
      downloadFile(
        process.env.VUE_APP_BASE_API + "/api/rdb/table/export",
        params
      );
      // this.filterSql = "";
    },
    // 预览sql
    async sqlPreview() {
      this.sqlPreviewType = "execute";
      this.commitActiveEdit();
      this.syncPendingOperations();
      try {
        let sql = await this.getExecuteUpdateSql(this.sqlInfo);
        this.$refs.sqlPreview.init(sql);
      } catch (error) {
        this.$notify.error({ title: "生成保存语句失败", message: error.message });
      }
    },
    // 添加行
    addRow(pasteObj) {
      this.isCreate = true;
      //  this.queryResultData.headerList = this.queryResultData.headerList.filter(item => item.name !== 'ROWID');
      let row = {
        rowType: "add",
        rowTimestamp: Date.now(),
      };
      // this.queryResultData.headerList = this.queryResultData.headerList.filter(item => item.name !== 'ROWID');
      this.queryResultData &&
        this.queryResultData.headerList.map((item) => {
          if (item.name === "行号") {
            row[item.name] = this.dataTable.length + 1;
          } else {
            row[item.name] = null;
          }
        });

      if (pasteObj) {
        Object.assign(row, pasteObj);
      }
      this.dataTable.push(row);
      this.newArr = this.queryResultData.headerList.filter(
        (item) => item.name !== "ROWID"
      );
      this.sqlInfo.push({
        type: "CREATE",
        rowId: row["行号"],
        dataList: this.queryResultData.headerList.map((item) => {
          return this.cellValue(row, item, true);
        }),
      });
      // console.log(this.sqlInfo, "this.sqlInfo");
      this.$nextTick(() => {
        this.$refs.ref_table.bodyWrapper.scrollTop =
          this.$refs.ref_table.bodyWrapper.scrollHeight;
      });
    },

    // 刪除行
    deleteRow(selectRow) {
      let multipleSelection = [];
      if (selectRow) {
        multipleSelection.push(selectRow);
      } else {
        multipleSelection = this.multipleSelection;
      }
      multipleSelection.forEach((row) => {
        let flag = this.sqlInfo.filter((item) => {
          return item.rowId === row["行号"];
        });
        if (flag.length) {
          if (flag[0].type === "CREATE") {
            for (let i = 0; i < this.sqlInfo.length; i++) {
              if (this.sqlInfo[i].rowId === row["行号"]) {
                this.sqlInfo.splice(i, 1);
                i--;
              }
            }
            for (let i = 0; i < this.dataTable.length; i++) {
              let item = this.dataTable[i];
              if (item["行号"] === row["行号"]) {
                this.dataTable.splice(i, 1);
                i--;
              }
              if (item["行号"] !== i + 1) {
                let sqlInfoItem = this.sqlInfo.find(
                  (sql) => sql.rowId === item["行号"]
                );
                if (sqlInfoItem) sqlInfoItem.rowId = i + 1;
                item["行号"] = i + 1;
              }
            }
          } else {
            let temp = flag[0].oldDataList;
            flag[0].type = "DELETE";
            flag[0].dataList = temp;
            flag[0].oldDataList = flag[0].dataList;
          }
        } else {
          this.sqlInfo.push({
            type: "DELETE",
            rowId: row["行号"],
            oldDataList: this.queryResultData.headerList.map((item) => {
              return this.cellValue(row, item);
            }),
          });
        }
      });
      // this.$refs.ref_table.clearSelection();
    },
    // 克隆行
    copyRow(row) {
      let copyRow = { ...row };
      let oldData1 = JSON.parse(JSON.stringify(this.dataTable));
      this.localArr.push(oldData1);
      localStorage.setItem(this.storageKey, JSON.stringify(this.localArr));
      copyRow.rowType = "add";
      copyRow.rowTimestamp = "add";
      copyRow["行号"] = this.dataTable.length + 1;
      delete copyRow["ROWID"];
      this.dataTable.push(copyRow);
      this.sqlInfo.push({
        type: "CREATE",
        rowId: copyRow["行号"],
        dataList: this.queryResultData.headerList.map((item) =>
          this.cellValue(copyRow, item, true)
        ),
      });
      this.$nextTick(() => {
        this.$refs.ref_table.bodyWrapper.scrollTop =
          this.$refs.ref_table.bodyWrapper.scrollHeight;
      });
    },
    handleSelectionChange(val) {
      this.selections = val;
      this.multipleSelection = val.sort((a, b) => a.index - b.index);
      this.multipleSelectionCopy = JSON.parse(
        JSON.stringify(this.multipleSelection)
      );
      //隐藏解决修改列表数据之后勾选多选框后保存无改动的问题
      // this.sqlInfo = this.multipleSelection;
    },
    // 双击单元格修改数据
    celldblclick(row, column, cell, event) {
      // console.log(this.queryResultData.headerList);
      // this.queryResultData.headerList.forEach(val=>{
      //   if (val.name == id && val.autoIncrement === 1 && val.primaryKey) {
      //   }
      // })
      this.editStatus = false;
      if (this.typeView == "views") return;
      if (this.readonly) return;
      if (this.queryResultData.tableName) {
        this.oldRow = JSON.parse(JSON.stringify(row));
        this.editingOriginalRow = JSON.parse(JSON.stringify(row));
        this.editingHeader = this.queryResultData.headerList.find(
          item => item.name === column.property
        );
        this.clickRow = row.index;
        this.clickCell = column.index;
        this.$nextTick(() => {
          const input = this.$refs.ref_table.$el.querySelector(
              `.el-table__body tbody tr:nth-child(${
                this.clickRow + 1
              }) td:nth-child(${this.clickCell + 1}) .focusInput input`
            );
          if (input) input.focus();
          // this.$nextTick(() =>{
          //    let inputref = `focusInput${row.index}__${column.property}`
          // console.log(this.$refs[inputref]);
          // })
        });
      }
    },
    // 把每一行的索引放进row
    tableRowClassName({ row, rowIndex }) {
      row.index = rowIndex;
      for (let i = 0; i < this.sqlInfo.length; i++) {
        let item = this.sqlInfo[i];
        if (row["行号"] === item.rowId) {
          return item.type === "DELETE" ? "delete-row" : "update-row";
        }
      }
      return "";
    },

    // 把每一列的索引放进column
    tableCellClassName({ column, columnIndex }) {
      column.index = columnIndex;
    },
    async updateColumn() {
      let columnValue = await this.$refs.sqlPreview.getValue();
      let row = this.dataTable[this.clickRow];
      row[this.queryResultData.headerList[this.clickCell].name] = columnValue;
      this.inputBlur(row);
      this.$refs.sqlPreview.dialogClose();
    },
    // 单元格右击
    handleContextMenu(row, column, event, e) {
      // console.log(row, column, event, e);
      if (this.readonly) return;
      e.preventDefault();
      this.$contextmenu.destroy();
      let nodeItems;
      this.queryResultData.headerList.map((item) => {
        if (item.name == column.property) {
          this.isFileObj = item;
        }
      });
      if (column.property === "行号") {
        let dataList = this.queryResultData.headerList.map((item) => {
          return row[item.name];
        });
        nodeItems = [
          {
            label: "复制行为",
            children: [
              {
                label: "Insert 语句",
                onClick: async () => {
                  let _updateData = [
                    {
                      type: "CREATE",
                      dataList: dataList,
                      rowId: (this.dataTable.length + 1).toString(),
                    },
                  ];
                  let sql = await this.getExecuteUpdateSql(_updateData);
                  this.copy(sql);
                },
              },
              {
                label: "Update 语句",
                onClick: async () => {
                  let _updateData = [
                    {
                      type: "UPDATE_COPY",
                      dataList: dataList,
                      rowId: (this.dataTable.length + 1).toString(),
                    },
                  ];
                  let sql = await this.getExecuteUpdateSql(_updateData);
                  this.copy(sql);
                },
              },
              {
                label: "制表符分隔值",
                onClick: () => {
                  let headerNames = this.queryResultData.headerList.map(
                    (item) => {
                      return item.name === "行号" || item.name === "ROWID"
                        ? ""
                        : item.name;
                    }
                  );
                  let newHeaderNames = headerNames.slice(1, -1);
                  let columnValue = newHeaderNames.map((item) =>
                    row[item] ? row[item] : "null"
                  );

                  let copyInfo = columnValue.join("         ");
                  this.copy(copyInfo);
                },
              },
              {
                label: "制表符分隔值(字段名)",
                onClick: () => {
                  let headerNames = this.queryResultData.headerList.map(
                    (item) => {
                      return item.name === "行号" || item.name === "ROWID"
                        ? ""
                        : item.name;
                    }
                  );
                  let newHeaderNames = headerNames.slice(1, -1);
                  newHeaderNames.join("         ");
                  this.copy(newHeaderNames);
                },
              },
              {
                label: "制表符分隔值(字段名和数据)",
                onClick: () => {
                  let headerNames = this.queryResultData.headerList.map(
                    (item) => {
                      return item.name === "行号" || item.name === "ROWID"
                        ? ""
                        : item.name;
                    }
                  );
                  let newHeaderNames = headerNames.slice(1, -1);
                  let columnValue = newHeaderNames.map((item) =>
                    row[item] ? row[item] : "null"
                  );

                  let copyInfo =
                    headerNames.join("         ") +
                    "/n" +
                    columnValue.join("         ");
                  this.copy(copyInfo);
                },
              },
            ],
          },
          {
            label: "克隆行",
            onClick: () => {
              this.copyRow(JSON.parse(JSON.stringify(row)));
            },
          },
          {
            label: "删除行",
            onClick: () => {
              this.deleteRow(JSON.parse(JSON.stringify(row)));
            },
          },
        ];
      } else if (
        this.isFileObj.dataType === "BYTE" &&
        row[this.isFileObj.name].length
      ) {
        // 针对blob 单元格右键
        nodeItems = [
          {
            label: "预览",
            onClick: () => {
              const formData = new FormData();
              let { tableName, schemaName, dataSourceId } =
                this.queryResultData.uniqueData;
              let params = {
                tableName,
                schemaName,
                dataSourceId,
                rowId: row.ROWID,
                blobColumn: column.property,
              };
              // formData.append("blobStr", row[this.isFileObj.name]);
              tableServer.getBlobData(params).then((res) => {
                if (res.data) {
                  let blobData = res.data;
                  formData.append("blobStr", blobData);
                  tableServer.getTableDataBlob(formData).then((res) => {
                    if (res.success) {
                      this.$refs.fileRef.fromData.type = res.data;
                      this.$refs.fileRef.blobStr = blobData;
                      this.$refs.fileRef.dialogVisible = true;
                      if (res.data.includes(".docx")) {
                        this.$refs.fileRef.isShow = false;
                      } else {
                        this.$refs.fileRef.isShow = true;
                      }
                    } else {
                      this.$message.error(res.errorCode);
                    }
                  });
                } else {
                  return;
                }
              });
            },
          },
        ];
      } else {
        let dataList = this.queryResultData.headerList.map((item) => {
          return row[item.name];
        });
        nodeItems = [
          // {
          //   label: "查看/修改数据",
          //   onClick: () => {
          //     this.oldRow = JSON.parse(JSON.stringify(row));
          //     this.clickRow = row.index;
          //     this.clickCell = column.index;
          //     this.sqlPreviewType = "update";
          //     this.$refs.sqlPreview.init(row[column.property]);
          //   },
          // },
          {
            label: "复制",
            onClick: () => {
              this.copy(row[column.property]);
            },
          },
          {
            label: "复制行为",
            children: [
              {
                label: "Insert 语句",
                onClick: async () => {
                  let _updateData = [
                    {
                      type: "CREATE",
                      dataList: dataList,
                      rowId: (this.dataTable.length + 1).toString(),
                    },
                  ];
                  let sql = await this.getExecuteUpdateSql(_updateData);
                  this.copy(sql);
                },
              },
              {
                label: "Update 语句",
                onClick: async () => {
                  let _updateData = [
                    {
                      type: "UPDATE_COPY",
                      dataList: dataList,
                      rowId: (this.dataTable.length + 1).toString(),
                    },
                  ];
                  let sql = await this.getExecuteUpdateSql(_updateData);
                  this.copy(sql);
                },
              },
              {
                label: "制表符分隔值",
                onClick: () => {
                  let headerNames = this.queryResultData.headerList.map(
                    (item) => {
                      return item.name === "行号" || item.name === "ROWID"
                        ? ""
                        : item.name;
                    }
                  );
                  let newHeaderNames = headerNames.slice(1, -1);
                  let columnValue = newHeaderNames.map((item) =>
                    row[item] ? row[item] : "null"
                  );

                  let copyInfo = columnValue.join("         ");
                  this.copy(copyInfo);
                },
              },
              {
                label: "制表符分隔值(字段名)",
                onClick: () => {
                  let headerNames = this.queryResultData.headerList.map(
                    (item) => {
                      return item.name === "行号" || item.name === "ROWID"
                        ? ""
                        : item.name;
                    }
                  );
                  let newHeaderNames = headerNames.slice(1, -1);
                  newHeaderNames.join("         ");
                  this.copy(newHeaderNames);
                },
              },
              {
                label: "制表符分隔值(字段名和数据)",
                onClick: () => {
                  let headerNames = this.queryResultData.headerList.map(
                    (item) => {
                      return item.name === "行号" || item.name === "ROWID"
                        ? ""
                        : item.name;
                    }
                  );
                  let newHeaderNames = headerNames.slice(1, -1);
                  let columnValue = newHeaderNames.map((item) =>
                    row[item] ? row[item] : "null"
                  );

                  let copyInfo =
                    headerNames.join("         ") +
                    "/n" +
                    columnValue.join("         ");
                  this.copy(copyInfo);
                },
              },
            ],
          },
          {
            label: "克隆行",
            onClick: () => {
              let newRow = JSON.parse(JSON.stringify(row));
              this.queryResultData.headerList.forEach((item) => {
                if (item.autoIncrement === 1) {
                  newRow[item.name] = "";
                }
              });
              this.copyRow(newRow);
              // this.copyRow(JSON.parse(JSON.stringify(row)));
            },
          },
          {
            label: "设置为NULL",
            onClick: () => {
              let flag = this.sqlInfo.filter((item) => {
                return item.rowId === row["行号"];
              });
              if (!flag.length) this.oldRow = JSON.parse(JSON.stringify(row));
              const header = this.queryResultData.headerList.find(
                (item) => item.name === column.property
              );
              row[column.label] = null;
              this.inputBlur(row, header);
            },
          },
          {
            label: "删除行",
            onClick: () => {
              this.deleteRow(JSON.parse(JSON.stringify(row)));
            },
          },
        ];
      }
      this.$contextmenu({
        items: nodeItems,
        event: e,
        customClass: "resource-context-menu",
        zIndex: 999,
        minWidth: 100,
      });
      return false;
    },
    copy(msg) {
      copyText(msg);
    },
    handleSizeChange(val) {
      this.queryParams.pageNum = 1;
      this.queryParams.pageSize = val;
      if (JSON.stringify(this.sortObj) == "{}") {
        this.advancedQuery(false);
      } else {
        this.sortChange(this.sortObj, false);
      }
    },
    handleSize(val) {
      this.page.pageSize = val;
      this.queryPageData(this.exoprtObj);
    },
    handleUpdateSize(val) {
      this.updatePage.pageSize = val;
      this.queryPageData(this.exoprtObj);
    },
    handleCurrentChange(val) {
      this.queryParams.pageNum = val;
      this.queryParams.isPage = true;
      if (JSON.stringify(this.sortObj) == "{}") {
        this.advancedQuery(false);
      } else {
        this.sortChange(this.sortObj, false);
      }
    },
    handleUpdateCurrent(val) {
      this.updatePage.pageNum = val;
      this.queryPageData(this.exoprtObj);
    },
    handleCurrent(val) {
      this.page.pageNum = val;
      this.queryPageData(this.exoprtObj);
    },
    handleCellClick(row, column, cell, event) {
      if (this.readonly) return;
      if (column.columnKey == 1 || column.index == 0) return;
      // console.log(JSON.stringify(column));
      // console.log(JSON.stringify(cell));

      this.selectedRow = row;
      this.oldRow = JSON.parse(JSON.stringify(row));
      this.selectedColumn = column;
    },
    tableCellStyle({ row, column, columnIndex }) {
      if (this.ctrlPressed) {
        if (this.highlightedColumnIndex.includes(columnIndex)) {
          return { background: "#e5f6ff" };
        }
        return {};
      } else {
        // 如果当前行中的单元格正在聚焦或编辑
        if (
          row === this.selectedRow &&
          column.property === this.selectedColumn.property &&
          column.index !== 0 &&
          column.columnKey != 1
        ) {
          return {
            backgroundColor: "#1890ff",
            color: "#fff",
          };
        } else {
          return this.selectedCols.includes(column.property)
            ? "highlight-column"
            : "";
        }
      }
    },
    // 导入
    handleAvatarSuccess(response, file) {
      this.fileName = response.fileName;
    },
    fileSave() {
      this.isDialogLoading = true;
      const params = {
        dataSourceId: this.queryResultData.uniqueData.dataSourceId,
        schemaName: this.queryResultData.uniqueData.schemaName,
        name: this.queryResultData.uniqueData?.tableName,
        // indexList: this.dataIndex,
        isErrorExecute: this.isErrorExecute,
        columns: this.importColumns,
      };
      if (this.activeName == "add") {
        params.type = 1;
        // params.updateDataList = undefined;
        // params.insertDataList = JSON.parse(JSON.stringify(this.addTableData));
        // params.insertDataList.forEach(item => {
        //   delete item.index;
        //   let keys = Object.keys(item);
        //   if (keys.join(",").indexOf("_viewType")) {
        //     keys.forEach(key => {
        //       if (key.includes("_viewType")) {
        //         delete item[key];
        //       }
        //     });
        //   }
        // });
      } else {
        params.type = 2;
        // params.insertDataList = undefined;
        // params.updateDataList = JSON.parse(
        //   JSON.stringify(this.editLeftTableData)
        // );
        // params.updateDataList.forEach(item => {
        //   delete item.index;
        //   let keys = Object.keys(item);
        //   if (keys.join(",").indexOf("_viewType")) {
        //     keys.forEach(key => {
        //       if (key.includes("_viewType")) {
        //         delete item[key];
        //       }
        //     });
        //   }
        // });
      }

      // const params = {
      //   dataSourceId: this.queryResultData.uniqueData.dataSourceId,
      //   schemaName: this.queryResultData.uniqueData.schemaName,
      //   name: this.queryResultData.uniqueData?.tableName,
      //   insertDataList:
      //     this.activeName == "add" ? this.addTableData : undefined,
      //   updateDataList:
      //     this.activeName == "edit" ? this.editLeftTableData : undefined,
      //   indexList: this.dataIndex,
      // };
      sqlServer.dataSaveExecute(params).then((res) => {
        if (res.success) {
          if (this.activeName == "add") {
            this.add_importRounds = 3;
          } else {
            this.edit_importRounds = 3;
          }
          this.queryResultTableData();
          this.sortChange(this.sortObj);
        } else {
          this.isDialogLoading = false;
          this.$message.error(res.errorCode);
        }
      });
    },
    executeFileData() {
      this.addSuccessData.forEach((item) => {
        delete item.index && delete item.错误信息;
      });
      const params = {
        dataSourceId: this.queryResultData.uniqueData.dataSourceId,
        schemaName: this.queryResultData.uniqueData.schemaName,
        name: this.queryResultData.uniqueData?.tableName,
        // insertDataList:
        //   this.activeName == "add" ? this.addSuccessData : undefined,
        // updateDataList:
        //   this.activeName == "edit" ? this.addSuccessData : undefined,
        indexList: this.dataIndex,
      };

      if (this.activeName == "add") {
        params.insertDataList = this.addSuccessData;
        params.updateDataList = undefined;
      } else {
        params.insertDataList = undefined;
        params.updateDataList = this.addSuccessData;
      }

      sqlServer.dataSaveExecute(params).then((res) => {
        if (res.success) {
          this.activeTab = "SUCCESS";
        } else {
          this.$message.error(res.errorCode);
        }
      });
    },
    startImport2() {
      if (!this.fileName) {
        return this.$message({
          type: "error",
          message: "文件不能为空",
        });
      }
      this.dialogImportVisible = false;
      this.dialogImportDataVisible = true;
      this.queryData = {
        dataSourceId: this.queryResultData.uniqueData.dataSourceId,
        schemaName: this.queryResultData.uniqueData.schemaName,
        name: this.queryResultData.uniqueData.tableName,
        databaseName: this.queryResultData.uniqueData.databaseName,
        checked: this.checked,
        fileName: this.fileName,
      };
    },
    async startImport(fromHeardList, toHeardList) {
      this.dialogImportDataVisible = false;
      this.dialogImportVisible = true;
      this.isDialogLoading = true;
      let newToHeardList = toHeardList.filter((item) => {
        return item.value != "" && item.value != null;
      });
      newToHeardList.forEach((i) => (i.name = i.value));
      let newFromHeardList = [];
      newToHeardList.forEach((item) => {
        newFromHeardList.push(fromHeardList[item.number - 1]);
      });
      const formData = new FormData();
      formData.append(
        "dataSourceId",
        this.queryResultData.uniqueData.dataSourceId
      );
      formData.append("schemaName", this.queryResultData.uniqueData.schemaName);
      formData.append("name", this.queryResultData.uniqueData?.tableName);
      // formData.append("file", this.fileName);
      formData.append("fromHeardList", newFromHeardList);
      formData.append("toHeardList", newToHeardList);
      let params = {
        dataSourceId: this.queryResultData.uniqueData.dataSourceId,
        schemaName: this.queryResultData.uniqueData.schemaName,
        name: this.queryResultData.uniqueData?.tableName,
        fromHeardList: newFromHeardList,
        toHeardList: newToHeardList,
      };
      await sqlServer.executePreview(params).then((res) => {
        this.exoprtObj = res;

        if (res.code == 200) {
          this.importRounds = 2;
          this.activeName = "add";
          this.add_importRounds = 2;
          this.edit_importRounds = 2;
          this.queryPageData(res);
        } else {
          this.isDialogLoading = false;
          this.importRounds = 1;
          this.$message.error(res.errorCode);
        }
      });
      // this.importRounds = 2;
      // this.activeName = "add";
      // this.add_importRounds = 2;
      // this.edit_importRounds = 2;
      // this.queryPageData();
    },
    queryPageData(res) {
      this.isDialogLoading = true;
      const formData = new FormData();
      formData.append(
        "dataSourceId",
        this.queryResultData.uniqueData.dataSourceId
      );
      formData.append("schemaName", this.queryResultData.uniqueData.schemaName);
      formData.append("name", this.queryResultData.uniqueData?.tableName);
      formData.append(
        "dataType",
        this.activeName == "add"
          ? "ADD"
          : this.activeName == "edit"
          ? "UPDATE"
          : "ADD"
      );
      formData.append(
        "size",
        this.activeName == "add" ? this.page.pageSize : this.updatePage.pageSize
      );
      formData.append(
        "page",
        this.activeName == "add" ? this.page.pageNum : this.updatePage.pageNum
      );
      sqlServer.queryPageTable(formData).then((response) => {
        this.isDialogLoading = false;
        if (response.data) {
          let { dataList, dataIndex, updateDataList, total } = response.data;
          this.dataIndex = dataIndex;
          let data = null;
          if (this.activeName == "add") {
            data = dataList;
            this.page.total = total;
          } else {
            data = updateDataList;
            this.updatePage.total = total;
          }

          if (data && data.length) {
            this.table_headers = this.editTableHeaders = Object.keys(data[0]);
            let leftTableData = [];
            let rightTableData = [];
            data.forEach((item, index) => {
              let oldValue = {},
                newValue = {};
              this.table_headers.forEach((key, index2) => {
                oldValue[key] = item[key].oldValue;
                newValue[key] = item[key].newValue;

                let viewType = item[key].viewType;
                if (viewType != 0) {
                  newValue[key + "_viewType"] = viewType;
                  oldValue[key + "_viewType"] = viewType;
                }
              });

              leftTableData.push(newValue);
              rightTableData.push(oldValue);
            });
            if (this.activeName == "add") {
              this.addTableData = leftTableData;
            } else {
              this.editLeftTableData = leftTableData;
              this.editRightTableData = rightTableData;
            }
          }
          // if (this.activeName == "add") {
          //   let keys = Object.keys(response.data.dataList[0]);
          //   this.table_headers = keys;
          //   if (res.data.insertDataList.length) {
          //     this.page.total = response.data.total;
          //     this.addTableData = response.data.dataList;
          //   }
          // } else {
          //   this.updatePage.total = response.data.total;
          //   this.editTableData = response.data.updateDataList;
          //   let keys = "";
          //   let leftTableData = [];
          //   let rightTableData = [];
          //   this.editTableData.forEach((item, index) => {
          //     if (index == 0) {
          //       keys = Object.keys(item);
          //     }
          //     let oldValue = {},
          //       newValue = {};
          //     keys.forEach((key, index2) => {
          //       oldValue[key] = item[key].oldValue;
          //       newValue[key] = item[key].newValue;

          //       let viewType = item[key].viewType;
          //       if (viewType != 0) {
          //         newValue[key + "_viewType"] = viewType;
          //         oldValue[key + "_viewType"] = viewType;
          //       }
          //     });
          //     leftTableData.push(newValue);
          //     rightTableData.push(oldValue);
          //   });
          //   this.editLeftTableData = leftTableData;
          //   this.editRightTableData = rightTableData;
          //   if (this.editLeftTableData.length) {
          //     this.editTableHeaders = keys;
          //   }
          // }
        } else {
          this.table_headers = [];
          this.addTableData = [];
          this.editTableData = [];
          this.page.total = 0;
          this.updatePage.total = 0;
        }
      });
    },
    queryResultTableData() {
      this.isDialogLoading = true;
      const formData = new FormData();
      formData.append(
        "dataSourceId",
        this.queryResultData.uniqueData.dataSourceId
      );
      formData.append("schemaName", this.queryResultData.uniqueData.schemaName);
      formData.append("name", this.queryResultData.uniqueData?.tableName);
      formData.append(
        "dataType",
        this.activeTab == "SUCCESS" ? "SUCCESS" : "ERROR"
      );
      formData.append(
        "executeType",
        this.activeName == "add" ? "ADD" : "UPDATE"
      );
      formData.append("size", this.resPage.pageSize);
      formData.append("page", this.resPage.pageNum);
      sqlServer.queryPageTable(formData).then((res) => {
        this.isDialogLoading = false;
        this.addSuccessData = [];
        if (res.data) {
          if (res.data.dataList.length) {
            let keys = Object.keys(res.data.dataList[0]);
            this.headers = keys;
          }
          this.resPage.total = res.data.total;
          res.data.dataList.forEach((i) => {
            for (let key in i) {
              if (i[key] && typeof i[key] === "object") {
                i[key] = i[key].newValue;
              }
            }
          });
          this.addSuccessData = res.data.dataList;
        }
      });
    },
    beforeAvatarUpload(file) {
      const isLt50M = file.size / 1024 / 1024 < 50;
      if (!isLt50M) {
        this.$message.error("上传文件大小不能超过 50MB!");
      }
      return isLt50M;
    },
    download_Logs() {
      if (this.databaseImportInfo.status === "FINISH") {
        downloadFile(
          process.env.VUE_APP_BASE_API + "/api/rdb/table/import/download",
          {
            dataSourceId: this.queryResultData.uniqueData.dataSourceId,
            schemaName: this.queryResultData.uniqueData.schemaName,
            databaseName: this.queryResultData.uniqueData.databaseName,
            importUrl: this.databaseImportInfo.logFile,
            tableName: this.queryResultData.uniqueData?.tableName,
            haveTitle: true,
            startRow: 1,
            errorStop: false,
            errorRollback: false,
          }
        );
      } else {
        this.$message.warning("数据表导入未结束！");
      }
    },
    handleimportDataClose(val) {
      this.dialogImportDataVisible = val;
    },
    handleClose() {
      this.databaseImportInfo = {
        logFile: "",
        errorNum: 0,
        message: [],
      };
      this.importForm = {
        haveTitle: true,
        startRow: 1,
        errorStop: false,
        errorRollback: false,
      };
      this.fileName = "";
      if (!this.timer) {
        this.importRounds = 1;
        this.add_importRounds = 1;
        this.edit_importRounds = 1;
      }
      this.$refs.commonUpload?.clearFiles();
      this.dialogImportVisible = false;
      this.dialogImportDataVisible = false;
      this.addTableData = [];
      this.editLeftTableData = [];
      this.editRightTableData = [];
      this.addSuccessData = [];
      this.queryParams = {
        params: null,
        pageNum: 1,
        pageSize: 100,
        isPage: false,
      };
      this.page = {
        pageNum: 1,
        pageSize: 20,
        total: 0,
      };
      this.updatePage = {
        pageNum: 1,
        pageSize: 20,
        total: 0,
      };
      this.resPage = {
        pageNum: 1,
        pageSize: 20,
        total: 0,
      };

      const params = {
        schemaName: this.queryResultData.uniqueData.schemaName,
        name: this.queryResultData.uniqueData?.tableName,
        dataSourceId: this.queryResultData.uniqueData.dataSourceId,
      };
      sqlServer.clearPageData(params).then((res) => {
        console.log(res);
      });
    },
    addAndToFilter() {
      let sql = this.$refs.filterBox.getValue();
      sqlServer
        .filterSqlFormat({
          dataSourceId: this.queryResultData.uniqueData.dataSourceId,
          columnName: this.filterSortForm.keyName,
          filterType: this.filterSortForm.condition,
          startValue: this.filterSortForm.keyValue,
          endValue: this.filterSortForm.keyValue2,
        })
        .then((res) => {
          let data = res.data;
          if (sql) {
            sql = `${sql} \n And ${data}`;
          } else {
            sql = `${data}`;
          }

          this.$refs.filterBox.setValue(sql);
          this.filterSortForm.keyName = "";
          this.filterSortForm.condition = "";
          this.filterSortForm.keyValue = "";
          this.filterSortForm.keyValue2 = "";
        });
    },
    addOrToFilter() {
      let sql = this.$refs.filterBox.getValue();
      sqlServer
        .filterSqlFormat({
          dataSourceId: this.queryResultData.uniqueData.dataSourceId,
          columnName: this.filterSortForm.keyName,
          filterType: this.filterSortForm.condition,
          startValue: this.filterSortForm.keyValue,
          endValue: this.filterSortForm.keyValue2,
        })
        .then((res) => {
          let data = res.data;
          if (sql) {
            sql = `${sql} \n Or ${data}`;
          } else {
            sql = `${data}`;
          }

          this.$refs.filterBox.setValue(sql);
          this.filterSortForm.keyName = "";
          this.filterSortForm.condition = "";
          this.filterSortForm.keyValue = "";
          this.filterSortForm.keyValue2 = "";
        });
    },
    addBracketToFilter(str) {
      let sql = this.$refs.filterBox.getValue();
      sql += `${str} \n`;
      this.$refs.filterBox.setValue(sql);
    },
    // 清空筛选
    clearFilter() {
      this.$refs.filterBox.setValue();
    },
    // 添加到右边盒子
    addRightBox(type) {
      // console.log(type);
      let selectData = this.sortHeaderData[this.filterSortForm.leftSelect];
      sqlServer
        .sortSqlFormat({
          dataSourceId: this.queryResultData.uniqueData.dataSourceId,
          columnName: selectData.name,
          sorType: type,
          nullType: this.filterSortForm.nullsChecked
            ? this.filterSortForm.nullType
            : "",
        })
        .then((res) => {
          selectData.resDataStr = res.data;
          selectData.rowIndex = this.filterSortForm.sortArr.length;
          this.filterSortForm.sortArr.push(selectData);
        });
    },
    // 添加到左边初始盒子
    addLeftBox() {
      let selectData =
        this.filterSortForm.sortArr[this.filterSortForm.rightSelect];
      let rowIndex = selectData.rowIndex;

      this.filterSortForm.sortArr = this.filterSortForm.sortArr.filter(
        (item) => {
          if (item.rowIndex > rowIndex) {
            item.rowIndex -= 1;
          }
          return item.name != selectData.name;
        }
      );
    },
    // 上下调整顺序
    handleOrder(flag) {
      let selectData =
        this.filterSortForm.sortArr[this.filterSortForm.rightSelect];
      if (flag == "top" && selectData.rowIndex > 0) {
        selectData.rowIndex -= 1;
        this.filterSortForm.sortArr[
          this.filterSortForm.rightSelect - 1
        ].rowIndex += 1;
        this.filterSortForm.rightSelect -= 1;
      } else if (
        flag == "bottom" &&
        selectData.rowIndex < this.filterSortForm.sortArr.length - 1
      ) {
        selectData.rowIndex += 1;
        this.filterSortForm.sortArr[
          this.filterSortForm.rightSelect + 1
        ].rowIndex -= 1;
        this.filterSortForm.rightSelect += 1;
      }
      this.filterSortForm.sortArr.sort((a, b) => {
        return a.rowIndex - b.rowIndex;
      });
    },
    // sumbitFilterSortForm() {
    //   const notChangedSql = `select * from ${this.queryResultData.tableName}`;
    //   const whereValue = this.$refs.filterBox.getValue().trim() || "";
    //   const orderByValue =
    //     this.filterSortForm.sortArr
    //       .map(item => {
    //         return item.resDataStr;
    //       })
    //       .join(",")
    //       .trim() || "";

    //   let sql = whereValue
    //     ? notChangedSql + " WHERE " + whereValue
    //     : notChangedSql;
    //   sql = orderByValue ? sql + " ORDER BY " + orderByValue : sql;
    //   this.getTableList({ sql });
    // },
    sortChange(data, isColumn) {
      console.log(data, isColumn);
      if (!this.queryParams.isPage) {
        this.queryParams.pageNum = 1; //整库排序 点击排序默认回到第一页
      }
      this.sortObj = data;
      this.sortParams = data;
      // this.isColor = true;
      // this.filterSql = '';
      // this.pageSql = '';
      this.advancedQuery(isColumn);
    },
    headerCellStyle({ row, column, rowIndex, columnIndex }) {
      if (column.className != "el-table-column--selection") {
        let { id: className, property } = column;
        this.$nextTick(() => {
          let thDom = this.$refs.ref_table.$el.querySelector(`th.${className}`);
          thDom.setAttribute("data-property", property);
          if (!column.label) {
            this.setRowSpan(column, row);
          }
        });
      }
    },
    setRowSpan(column, row) {
      this.$nextTick(() => {
        let { id: className, property } = column;
        let thDom = this.$refs.ref_table.$el.querySelector(`th.${className}`);
        thDom.style.display = "none";
        try {
          thDom.parentNode.previousSibling
            .querySelectorAll("th")
            .forEach((th) => {
              if (th.getAttribute("data-property") == property) {
                th.setAttribute("rowSpan", 2);
                throw "";
              }
              // console.log(th);
            });
        } catch (err) {}
      });
    },
    getCell(index, key) {
      this.tabRowindex = index;
      this.tabColumnKey = key;
    },
    inputBlurFn() {
      this.tabRowindex = -1;
      this.tabColumnKey = "";
    },
    isDisableds(val) {
      let str = null;
      this.queryResultData.headerList.forEach((va) => {
        Object.keys(val).forEach((v) => {
          if (v === va.name && va.autoIncrement === 1 && va.primaryKey) {
            str = v;
          }
        });
      });
      return str;
    },
    isShowHeader() {
      this.showCheckHeader = !this.showCheckHeader;
    },
    advancedQuery(isColumn) {
      console.log(this.searchSql, "this.searchSql");
      this.isDialogLoading = true;
      this.isCreate = false;
      let ref = false;
      let height = this.pageSql == "" || this.pageSql == undefined;
      if (this.isColor && height) {
        ref = true;
        this.isColor = false;
      }
      if (!this.isColor) {
        this.pageSql = "";
        this.searchSql = "";
        this.filterSql = "";
        this.searchDialogForm.columnTitle = "";
        this.searchDialogForm.sql = "";
        this.searchDialogForm.contentTest = "";
      }
      // 取消高级查询，页面重置第一页
      if (ref) {
        this.queryParams.pageNum = 1;
      }
      const params = {
        ...this.queryResultData.params,
        pageNo: this.queryParams.pageNum,
        pageSize: this.queryParams.pageSize,
        isAsc:
          JSON.stringify(this.sortObj) == "{}"
            ? undefined
            : this.sortObj.order == "descending"
            ? true
            : this.sortObj.order == "ascending"
            ? false
            : undefined,
        orderByColumn:
          JSON.stringify(this.sortObj) == "{}" ? undefined : this.sortObj.prop,
      };

      if (params.consoleId) {
        params.sql = this.queryResultData.params.sql;
      } else if (this.pageSql) {
        params.sql = this.queryResultData.params.sql + `WHERE ${this.pageSql}`;
      } else {
        params.sql = this.queryResultData.params.sql;
      }
      // 是否需要请求头部
      if (isColumn === false) {
        params.isColumn = false;
      }

      sqlServer.executeSql(params).then((res) => {
        this.isDialogLoading = false;
        if (res.data[0].success) {
          this.queryResultData.fuzzyTotal = Number(res.data[0].fuzzyTotal);
          this.queryResultData.pageNum = Number(res.data[0].pageNo);
          this.queryResultData.pageSize = Number(res.data[0].pageSize);
          this.queryResultData.duration = Number(res.data[0].duration);
          this.pageLoading = false;
          this.sqlInfo = [];
          // 暴力实现
          if (isColumn !== false) {
            this.queryResultData.headerList = res.data[0].headerList;
          }
          this.queryResultData.dataList = res.data[0]?.dataList;
          this.$forceUpdate();
          // this.sortObj = {};
        } else {
          this.$message.error(res.data[0].message);
        }
      });
    },
    clickSize(val) {
      this.resPage.pageSize = val;
      this.queryResultTableData();
    },
    ClickCurrent(val) {
      this.resPage.pageNum = val;
      this.queryResultTableData();
    },
    filterTitle() {
      this.filterArray = this.headerList.filter((item) => {
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
        sql = `"${this.searchDialogForm.columnTitle}" ${this.searchDialogForm.sql} '%${this.searchDialogForm.contentTest}%' `;
      } else {
        if (
          "" == this.searchDialogForm.contentTest ||
          undefined == this.searchDialogForm.contentTest
        ) {
          sql = `"${this.searchDialogForm.columnTitle}" ${this.searchDialogForm.sql}`;
        } else {
          sql = `"${this.searchDialogForm.columnTitle}" ${this.searchDialogForm.sql} '${this.searchDialogForm.contentTest}' `;
        }
      }

      if (this.searchSql != "") {
        this.searchSql += `\n${str} ${sql}`;
      } else {
        this.searchSql = sql;
      }
      this.filterSql = JSON.parse(JSON.stringify(this.searchSql)); // 作为导出时传递参数
      this.pageSql = JSON.parse(JSON.stringify(this.searchSql)); // 针对高级查询分页 作为标示传递
    },
    addSearchForm2(str) {
      if (str == "(") {
        this.searchSql += `\n${str} `;
        this.pageSql = JSON.parse(JSON.stringify(this.searchSql));
        this.filterSql = JSON.parse(JSON.stringify(this.searchSql));
      } else {
        this.searchSql += ` ${str} `;
        this.pageSql = JSON.parse(JSON.stringify(this.searchSql));
        this.filterSql = JSON.parse(JSON.stringify(this.searchSql));
      }
    },
    handleExportResult() {
      const params = {
        dataSourceId: this.queryResultData.uniqueData.dataSourceId,
        executeType: this.activeName === "add" ? "ADD" : "UPDATE",
        schemaName: this.queryResultData.uniqueData.schemaName,
        name: this.queryResultData.uniqueData?.tableName,
      };
      downloadFile(
        process.env.VUE_APP_BASE_API + "/common/exportErrorData",
        params
      );
    },
  },
};
</script>

<template>
  <div
    class="table_box"
    v-loading="pageLoading"
    :style="{ marginTop: readonly ? '5px' : 0 }"
  >
    <div class="table_btn_list" v-if="!readonly">
      <el-tag v-if="isMaterializedView" size="mini" type="info" class="materialized-readonly-tag">
        物化视图 · 只读
      </el-tag>
      <el-button
        v-if="isMaterializedView"
        size="mini"
        type="primary"
        plain
        :loading="materializedViewRefreshing"
        @click="refreshMaterializedView"
      >刷新物化视图</el-button>
      <el-button
        v-if="isMaterializedView"
        size="mini"
        plain
        :loading="materializedDdlLoading"
        @click="showMaterializedDdl"
      >查看 DDL</el-button>
      <el-button v-if="isMaterializedView" size="mini" plain @click="materializedGrantVisible = true">授权</el-button>
      <el-popover
        placement="bottom"
        width="280"
        popper-class="custom-popover"
        trigger="click"
      >
        <div class="content-wrapper">
          <el-checkbox-group v-model="checkList">
            <template v-for="item in copyHeader">
              <el-checkbox
                v-show="item.name != '行号' && item.name != 'ROWID'"
                :key="item.name"
                :label="item.name"
                >{{ item.name }}</el-checkbox
              >
            </template>
          </el-checkbox-group>
        </div>
        <el-button size="mini" type="text" slot="reference">
          <img src="@/assets/main/3-con-ico01.png" alt /> 过滤字段
        </el-button>
      </el-popover>
      <el-button
        @click="addRow"
        size="mini"
        type="text"
        style="margin-left: 0"
        v-show="this.queryResultData.uniqueData.dataType !== 'views'"
      >
        <img src="@/assets/main/3-con-ico01.png" alt />
        添加
      </el-button>
      <el-button
        @click="deleteRow()"
        :disabled="!multipleSelection.length"
        size="mini"
        type="text"
        v-if="typeView !== 'views'"
      >
        <img src="@/assets/main/3-con-ico02.png" alt />
        删除
      </el-button>
      <el-button
        @click="handelBack"
        size="mini"
        type="text"
        :disabled="editStatus"
        v-show="this.queryResultData.uniqueData.dataType !== 'views'"
      >
        <img src="@/assets/main/15-1.png" alt />
        回退
      </el-button>
      <el-button
        @click="executeUpdateDataSql"
        v-if="typeView !== 'views'"
        size="mini"
        type="primary"
        plain
      >
        <img src="@/assets/main/1-con-ico22.png" alt />
        保存
      </el-button>
      <el-button @click="refresh" size="mini" type="primary" plain>
        <img src="@/assets/main/5-ico3.png" alt />
        刷新
      </el-button>
      <el-dropdown
        @command="handleExportSQLResult"
        trigger="click"
        style="margin-left: 10px"
      >
        <el-button size="mini" type="primary" plain>
          <img src="@/assets/main/3-con-ico05.png" alt />
          导出
        </el-button>
        <el-dropdown-menu slot="dropdown">
          <el-dropdown-item command="e">导出结果集 excel</el-dropdown-item>
          <el-dropdown-item command="b">导出结果集 insert sql</el-dropdown-item>
          <el-dropdown-item command="i" v-if="this.multipleSelection.length > 0"
            >导出选中结果集 excel</el-dropdown-item
          >
          <el-dropdown-item command="g" v-if="this.multipleSelection.length > 0"
            >导出选中结果集 insert sql</el-dropdown-item
          >
          <el-dropdown-item command="f"
            >导出当前页结果集 excel</el-dropdown-item
          >
          <el-dropdown-item command="d"
            >导出当前页结果集 insert sql</el-dropdown-item
          >
        </el-dropdown-menu>
      </el-dropdown>
      <el-button
        size="mini"
        type="primary"
        plain
        style="margin-left: 10px"
        @click="
          dialogImportVisible = true;
          isLoading = true;
        "
        v-show="this.queryResultData.uniqueData.dataType !== 'views'"
      >
        <img src="@/assets/main/3-con-ico04.png" alt />
        导入
      </el-button>
      <el-button
        size="mini"
        type="primary"
        plain
        style="margin-left: 10px"
        @click="searchDialogVisible = true"
        :style="{
          backgroundColor: isColor ? '#E6A23C' : '',
          color: isColor ? '#606266' : '',
        }"
        >高级查询</el-button
      >
    </div>
    <!-- <ScreeningResult ref="screeningResult" :queryResultData="queryResultData" @getTableList="getTableList" /> -->
    <div id="tableContainer" style="height: 85%; width: 100%">
      <el-table
        :data="dataTable"
        ref="ref_table"
        size="mini"
        class="table-custom-class jdTable_small"
        style="width: 100%; overflow: auto; height: 100%"
        border
        @cell-click="handleCellClick"
        @cell-contextmenu="handleContextMenu"
        @selection-change="handleSelectionChange"
        @paste.native="pasteInfo($event)"
        @sort-change="sortChange"
        @cell-dblclick="celldblclick"
        :cell-style="tableCellStyle"
        :row-class-name="tableRowClassName"
        :cell-class-name="tableCellClassName"
        @header-click="handleHeaderClick"
      >
        <el-table-column
          v-if="!readonly"
          type="selection"
          width="40"
        ></el-table-column>
        <!--  :sortable="!ctrlPressed" -->
        <el-table-column
          v-for="(header, index) in headerList"
          :key="header.name + header.comment"
          :label="header.name"
          :prop="header.name"
          show-overflow-tooltip
          :column-key="header.autoIncrement && header.primaryKey ? '1' : '0'"
          :min-width="headerList.length >= 10 ? '150' : 'auto'"
        >
          <template slot="header" slot-scope="scope">
            <el-tooltip
              class="item"
              effect="dark"
              :content="scope.column.label"
              placement="top"
            >
              <span v-show="false" style="cursor: pointer; user-select: none">{{
                scope.column.label
              }}</span>
            </el-tooltip>

            <div class="sort_header">
              <span class="sort_icons" v-show="!ctrlPressed">
                <i
                  class="el-icon-caret-top"
                  style="margin-bottom: -7px"
                  :class="{
                    active:
                      sort.prop == header.name && sort.order == 'ascending',
                  }"
                  @click="changeSort('ascending', scope.column)"
                ></i>
                <i
                  class="el-icon-caret-bottom"
                  :class="{
                    active:
                      sort.prop == header.name && sort.order == 'descending',
                  }"
                  @click="changeSort('descending', scope.column)"
                ></i>
              </span>
              <el-tooltip
                class="item"
                effect="dark"
                :content="scope.column.label"
                placement="top"
              >
                <span style="cursor: pointer; user-select: none">{{
                  scope.column.label
                }}</span>
              </el-tooltip>
            </div>
          </template>
          <template slot-scope="scope">
            <div
              v-if="
                scope.$index === clickRow && scope.column.index === clickCell
              "
            >
              <el-date-picker
                v-if="isDateColumn(header.dataType)"
                v-model="scope.row[header.name]"
                :type="datePickerType(header.dataType)"
                size="mini"
                @change="inputBlur(scope.row, header)"
                value-format="yyyy-MM-dd HH:mm:ss"
                placeholder="选择日期"
                class="focusInput"
              ></el-date-picker>
              <el-upload
                v-else-if="header.dataType == 'BYTE'"
                class="upload"
                ref="upload"
                action="none"
                :file-list="fileList"
                :on-change="(file) => fileChange(file, scope.row, header.name)"
                :limit="1"
                :auto-upload="false"
              >
                <el-button slot="trigger" size="mini" type="primary"
                  >选取文件</el-button
                >
              </el-upload>
              <el-popover v-else placement="top" width="500" trigger="focus">
                <el-input
                  style="width: 470px"
                  type="textarea"
                  :rows="10"
                  :id="`textarea_${scope.$index}_${header.name}`"
                  :disabled="header.name === isDisableds(scope.row)"
                  v-model="scope.row[header.name]"
                  maxlength="300"
                  @blur="inputBlur(scope.row, header)"
                />
                <el-input
                  slot="reference"
                  class="focusInput"
                  :ref="'focusInput' + scope.row.index + '__' + header.name"
                  :disabled="header.name === isDisableds(scope.row)"
                  v-model="scope.row[header.name]"
                  maxlength="300"
                  @focus="inputFocus(`textarea_${scope.$index}_${header.name}`)"
                  size="mini"
                />
              </el-popover>
            </div>
            <template v-else>
              <span v-if="scope.row[header.name]">
                <span v-if="header.dataType == 'BYTE'">文件</span>
                <span v-else>{{ scope.row[header.name] }}</span>
              </span>
              <span v-else style="color: rgba(35, 36, 41, 0.45)"
                >&lt;null&gt;</span
              >
            </template>
          </template>
          <!-- 切换表有些表显示不出来的问题为以下代码 -->
          <template v-if="header.comment">
            <el-table-column
              :label="header.comment"
              :prop="header.name"
              :key="header.name"
              :column-key="
                header.autoIncrement && header.primaryKey ? '1' : '0'
              "
              :min-width="headerList.length >= 10 ? '150' : 'auto'"
              show-overflow-tooltip
            >
              <template slot-scope="scope">
                <div
                  v-if="scope.$index === clickRow && index + 1 === clickCell"
                >
                  <el-date-picker
                    v-if="isDateColumn(header.dataType)"
                    v-model="scope.row[header.name]"
                    :type="datePickerType(header.dataType)"
                    size="mini"
                    @change="inputBlur(scope.row, header)"
                    value-format="yyyy-MM-dd HH:mm:ss"
                    placeholder="选择日期"
                    class="focusInput"
                  ></el-date-picker>
                  <el-upload
                    v-else-if="header.dataType == 'BYTE'"
                    class="upload"
                    ref="upload"
                    action="none"
                    :file-list="fileList"
                    :on-change="
                      (file) => fileChange(file, scope.row, header.name)
                    "
                    :limit="1"
                    :auto-upload="false"
                  >
                    <el-button slot="trigger" size="mini" type="primary"
                      >选取文件</el-button
                    >
                  </el-upload>
                  <el-popover
                    v-else
                    placement="top"
                    width="500"
                    trigger="focus"
                  >
                    <el-input
                      style="width: 470px"
                      type="textarea"
                      :rows="10"
                      :id="`textarea_${scope.$index}_${header.name}`"
                      :disabled="header.name === isDisableds(scope.row)"
                      v-model="scope.row[header.name]"
                      maxlength="300"
                      @blur="inputBlur(scope.row, header)"
                    />
                    <el-input
                      slot="reference"
                      class="focusInput"
                      :ref="'focusInput' + scope.row.index + '__' + header.name"
                      :disabled="header.name === isDisableds(scope.row)"
                      v-model="scope.row[header.name]"
                      maxlength="300"
                      @focus="
                        inputFocus(`textarea_${scope.$index}_${header.name}`)
                      "
                      size="mini"
                    />
                  </el-popover>
                </div>
                <template v-else>
                  <span v-if="scope.row[header.name]">
                    <span v-if="header.dataType == 'BYTE'">文件</span>
                    <span v-else>{{ scope.row[header.name] }}</span>
                  </span>
                  <span v-else style="color: rgba(35, 36, 41, 0.45)"
                    >&lt;null&gt;</span
                  >
                </template>
              </template>
            </el-table-column>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <div class="table_bottom_box">
      <div class="table_bottom_info">
        <span>【结果】 {{ queryResultData.description }}</span>
        <span>【耗时】 {{ queryResultData.duration }}ms</span>
        <span>
          【查询行数】
          {{ queryResultData.totalExact === false ? "至少 " : "" }}{{ queryResultData.fuzzyTotal }}行
        </span>
        <span v-if="queryResultData.hasNextPage">【提示】结果较多，请翻页继续查看</span>
      </div>
      <el-pagination
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
        :current-page="queryParams.pageNum"
        :page-sizes="[10, 50, 100, 200, 500, 1000]"
        :page-size="queryParams.pageSize"
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
      ></el-pagination>
    </div>

    <SqlPreview ref="sqlPreview" title="SQL预览">
      <el-button
        v-if="sqlPreviewType === 'execute'"
        style="position: absolute; right: 60px; top: 12px"
        @click="executeUpdateDataSql"
        size="mini"
        type="primary"
        plain
      >
        <img src="@/assets/main/1-con-ico22.png" alt />
        执行
      </el-button>
      <el-button
        v-else-if="sqlPreviewType === 'update'"
        style="position: absolute; right: 60px; top: 12px"
        @click="updateColumn"
        size="mini"
        type="primary"
        plain
      >
        <img src="@/assets/main/3-con-ico03.png" alt />
        修改
      </el-button>
    </SqlPreview>
    <importDataDialog
      v-if="dialogImportDataVisible"
      :dialogImportVisible="dialogImportDataVisible"
      :queryResultData="queryData"
      @prestartImport="startImport"
      @handleClose="handleClose"
    />

    <el-dialog
      title="批量导入确认"
      :visible.sync="dialogImportVisible"
      :close-on-click-modal="false"
      :before-close="handleClose"
      :width="importRounds === 1 ? '50%' : '80%'"
      custom-class="pldrqrBox"
    >
      <div v-loading="isDialogLoading" element-loading-text="努力加载中">
        <el-form
          ref="importForm"
          :rules="importRules"
          v-if="importRounds === 1"
          :model="importForm"
          label-width="160px"
        >
          <el-form-item label="文件" prop="file">
            <el-upload
              class="upload-demo"
              ref="commonUpload"
              drag
              accept=".xls, .xlsx"
              action="/dev-api/common/upload"
              :headers="customHeaders"
              :on-success="handleAvatarSuccess"
              :before-upload="beforeAvatarUpload"
              :limit="1"
            >
              <i class="el-icon-upload"></i>
              <div class="el-upload__text">
                将文件拖到此处，或
                <em>点击上传</em>
              </div>
              <div class="el-upload__tip" slot="tip">
                只能上传.xls、 .xlsx文件，且不超过50MB
                <!-- <el-checkbox v-model="checked">错误时停止</el-checkbox> -->
              </div>
            </el-upload>
          </el-form-item>
        </el-form>
        <div v-else style="height: 600px">
          <el-tabs v-model="activeName">
            <el-tab-pane label="新增" name="add">
              <template v-if="add_importRounds == 2">
                <el-table
                  :data="addTableData"
                  class="importDialogTable"
                  style="width: 100%"
                  height="500"
                  :header-cell-style="{
                    background: '#f1f3f9',
                    color: '#68728c',
                  }"
                >
                  <el-table-column
                    v-for="(item, index) in table_headers"
                    :key="index"
                    show-overflow-tooltip
                    min-width="154"
                    :label="item"
                    :prop="item"
                  >
                    <template slot-scope="scope">
                      <el-input
                        :class="returnClass(scope.row[item + '_viewType'])"
                        size="mini"
                        v-model="scope.row[item]"
                        v-if="
                          scope.$index == tabRowindex && item == tabColumnKey
                        "
                        @blur="inputBlurFn"
                      ></el-input>
                      <!--  @click="getCell(scope.$index, item)" -->
                      <div
                        v-else
                        :class="returnClass(scope.row[item + '_viewType'])"
                      >
                        {{ scope.row[item] }}
                      </div>
                    </template>
                  </el-table-column>
                </el-table>
                <el-pagination
                  @size-change="handleSize"
                  @current-change="handleCurrent"
                  :current-page="page.pageNum"
                  :page-sizes="[20, 50, 100]"
                  :page-size="page.pageSize"
                  layout="total, sizes, prev, pager, next, jumper"
                  :total="page.total"
                ></el-pagination>
              </template>
              <template v-else>
                <el-tabs v-model="activeTab" type="card">
                  <el-tab-pane label="成功" name="SUCCESS">
                    <el-table
                      class="importDialogTable"
                      :data="addSuccessData"
                      style="width: 100%"
                      height="425"
                      :header-cell-style="{
                        background: '#f1f3f9',
                        color: '#68728c',
                      }"
                      border
                    >
                      <el-table-column
                        v-for="(item, index) in headers"
                        :key="index"
                        show-overflow-tooltip
                        :label="item"
                        :prop="item"
                      ></el-table-column>
                    </el-table>
                    <el-pagination
                      @size-change="clickSize"
                      @current-change="ClickCurrent"
                      :current-page="resPage.pageNum"
                      :page-sizes="[20, 50, 100]"
                      :page-size="resPage.pageSize"
                      layout="total, sizes, prev, pager, next, jumper"
                      :total="resPage.total"
                    ></el-pagination>
                  </el-tab-pane>
                  <el-tab-pane label="失败" name="ERROR">
                    <el-table
                      class="importDialogTable"
                      :data="addSuccessData"
                      style="width: 100%"
                      height="425"
                      :header-cell-style="{
                        background: '#f1f3f9',
                        color: '#68728c',
                      }"
                      ref="errorTable"
                      border
                    >
                      <el-table-column
                        v-for="(item, index) in headers"
                        min-width="150"
                        :key="index"
                        show-overflow-tooltip
                        :label="item"
                        :prop="item"
                      >
                        <template slot-scope="scope">
                          <el-input
                            size="mini"
                            v-model="scope.row[item]"
                            v-if="
                              scope.$index == tabRowindex &&
                              item == tabColumnKey
                            "
                            @blur="inputBlurFn"
                          ></el-input>
                          <!-- @click="getCell(scope.$index, item)" -->
                          <div v-else>{{ scope.row[item] }}</div>
                        </template>
                      </el-table-column>
                    </el-table>
                    <el-pagination
                      @size-change="clickSize"
                      @current-change="ClickCurrent"
                      :current-page="resPage.pageNum"
                      :page-sizes="[20, 50, 100]"
                      :page-size="resPage.pageSize"
                      layout="total, sizes, prev, pager, next, jumper"
                      :total="resPage.total"
                    ></el-pagination>
                  </el-tab-pane>
                </el-tabs>
              </template>
            </el-tab-pane>
            <el-tab-pane label="修改" name="edit">
              <template v-if="edit_importRounds == 2">
                <div class="editBox">
                  <el-table
                    ref="editLeftTable"
                    class="importDialogTable"
                    :data="editLeftTableData"
                    style="width: 45%"
                    height="500"
                    :header-cell-style="{
                      background: '#f1f3f9',
                      color: '#68728c',
                    }"
                    border
                  >
                    <el-table-column
                      v-for="(item, index) in editTableHeaders"
                      :key="index"
                      :label="item"
                      :prop="item"
                      min-width="140"
                      show-overflow-tooltip
                    >
                      <template slot-scope="scope">
                        <el-input
                          :class="returnClass(scope.row[item + '_viewType'])"
                          size="mini"
                          v-model="scope.row[item]"
                          v-if="
                            scope.$index == tabRowindex && item == tabColumnKey
                          "
                          @blur="inputBlurFn"
                        ></el-input>
                        <!-- @click="getCell(scope.$index, item)" -->
                        <div
                          v-else
                          :class="returnClass(scope.row[item + '_viewType'])"
                        >
                          {{ scope.row[item] }}
                        </div>
                      </template>
                    </el-table-column>
                  </el-table>
                  <i
                    class="el-icon-right"
                    style="font-size: 30px; padding: 15px"
                  ></i>
                  <el-table
                    ref="editRightTable"
                    class="importDialogTable"
                    :data="editRightTableData"
                    style="width: 45%"
                    height="500"
                    :header-cell-style="{
                      background: '#f1f3f9',
                      color: '#68728c',
                    }"
                    border
                  >
                    <el-table-column
                      v-for="(item, index) in editTableHeaders"
                      :key="index"
                      :label="item"
                      :prop="item"
                      min-width="140"
                      show-overflow-tooltip
                    >
                      <template slot-scope="scope">
                        <div
                          :class="returnClass(scope.row[item + '_viewType'])"
                        >
                          {{ scope.row[item] }}
                        </div>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>
                <el-pagination
                  @size-change="handleUpdateSize"
                  @current-change="handleUpdateCurrent"
                  :current-page="updatePage.pageNum"
                  :page-sizes="[20, 50, 100]"
                  :page-size="updatePage.pageSize"
                  layout="total, sizes, prev, pager, next, jumper"
                  :total="updatePage.total"
                ></el-pagination>
              </template>
              <template v-else>
                <el-tabs v-model="activeTab" type="card">
                  <el-tab-pane label="成功" name="SUCCESS">
                    <el-table
                      :data="addSuccessData"
                      class="importDialogTable"
                      style="width: 100%"
                      height="425"
                      :header-cell-style="{
                        background: '#f1f3f9',
                        color: '#68728c',
                      }"
                    >
                      <el-table-column
                        v-for="(item, index) in headers"
                        min-width="150"
                        :key="index"
                        show-overflow-tooltip
                        :label="item"
                        :prop="item"
                      ></el-table-column>
                    </el-table>
                    <el-pagination
                      @size-change="clickSize"
                      @current-change="ClickCurrent"
                      :current-page="resPage.pageNum"
                      :page-sizes="[20, 50, 100]"
                      :page-size="resPage.pageSize"
                      layout="total, sizes, prev, pager, next, jumper"
                      :total="resPage.total"
                    ></el-pagination>
                  </el-tab-pane>
                  <el-tab-pane label="失败" name="ERROR">
                    <el-table
                      :data="addSuccessData"
                      class="importDialogTable"
                      style="width: 100%"
                      height="425"
                      :header-cell-style="{
                        background: '#f1f3f9',
                        color: '#68728c',
                      }"
                    >
                      <el-table-column
                        v-for="(item, index) in headers"
                        min-width="150"
                        :key="index"
                        show-overflow-tooltip
                        :label="item"
                        :prop="item"
                      >
                        <template slot-scope="scope">
                          <el-input
                            size="mini"
                            v-model="scope.row[item]"
                            v-if="
                              scope.$index == tabRowindex &&
                              item == tabColumnKey
                            "
                            @blur="inputBlurFn"
                          ></el-input>
                          <!-- @click="getCell(scope.$index, item)" -->
                          <div v-else>{{ scope.row[item] }}</div>
                        </template>
                      </el-table-column>
                    </el-table>
                    <el-pagination
                      @size-change="clickSize"
                      @current-change="ClickCurrent"
                      :current-page="resPage.pageNum"
                      :page-sizes="[20, 50, 100]"
                      :page-size="resPage.pageSize"
                      layout="total, sizes, prev, pager, next, jumper"
                      :total="resPage.total"
                    ></el-pagination>
                  </el-tab-pane>
                </el-tabs>
              </template>
            </el-tab-pane>
          </el-tabs>
          <el-checkbox
            v-model="isErrorExecute"
            style="position: absolute; top: 70px; right: 20px"
            >报错终止执行</el-checkbox
          >
        </div>
      </div>
      <div slot="footer" class="dialog-footer">
        <div
          class="colorInfo"
          v-if="
            importRounds == 2 &&
            ((add_importRounds === 2 && activeName == 'add') ||
              (edit_importRounds === 2 && activeName == 'edit')) &&
            this.activeName == 'edit'
          "
        >
          <div class="yellowCell activeCell"></div>
          改动
        </div>
        <el-button
          type="primary"
          size="small"
          v-if="importRounds === 1"
          @click="startImport2"
          >下一步</el-button
        >
        <el-button
          type="success"
          size="small"
          v-if="
            (add_importRounds === 2 && activeName == 'add') ||
            (edit_importRounds === 2 && activeName == 'edit')
          "
          @click="fileSave"
          >执行</el-button
        >
        <!-- <el-button
          type="success"
          size="small"
          v-if="
            ((add_importRounds === 3 && activeName == 'add') ||
              (edit_importRounds === 3 && activeName == 'edit')) &&
            activeTab == 'ERROR'
          "
          @click="executeFileData"
        >执行</el-button>-->
        <el-button
          v-if="
            (add_importRounds === 3 &&
              activeName == 'add' &&
              activeTab == 'ERROR') ||
            (edit_importRounds === 3 &&
              activeName == 'edit' &&
              activeTab == 'ERROR')
          "
          size="mini"
          :disabled="!addSuccessData || addSuccessData.length <= 0"
          @click="handleExportResult"
          >下 载</el-button
        >
        <el-button @click="handleClose" size="small">关 闭</el-button>
      </div>
    </el-dialog>

    <el-dialog
      title="提示"
      :visible.sync="pasteDialogVisible"
      width="400px"
      :close-on-press-escape="false"
      :close-on-click-modal="false"
      :show-close="false"
    >
      <span>将{{ pasteData.length }}行数据插入行数据中,是否新增?</span>
      <span slot="footer" class="dialog-footer">
        <el-button plain @click="multiRowInsert(), (pasteDialogVisible = false)"
          >是</el-button
        >
        <!-- <el-button plain @click="singleRowInsert(pasteData[0]), (pasteDialogVisible = false)">否</el-button> -->
        <el-button plain @click="pasteDialogVisible = false">取 消</el-button>
      </span>
    </el-dialog>
    <Filedialog ref="fileRef"></Filedialog>

    <el-dialog
      title="高级查询"
      :visible.sync="searchDialogVisible"
      width="800px"
      :close-on-press-escape="false"
      :close-on-click-modal="false"
      :show-close="false"
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
              v-for="item in headerList"
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
              v-for="item in sqlType"
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
          v-model="pageSql"
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
      <span slot="footer">
        <el-button
          plain
          @click="
            isColor = false;
            searchSql = '';
            pageSql = '';
            filterSql = '';
            searchDialogForm.columnTitle = '';
            searchDialogForm.sql = '';
            searchDialogForm.contentTest = '';
          "
          >清 空</el-button
        >
        <el-button plain @click="searchDialogVisible = false">关 闭</el-button>
        <el-button
          plain
          @click="
            searchDialogVisible = false;
            isColor = true;
            queryParams.pageNum = 1;
            advancedQuery();
          "
          >确 定</el-button
        >
      </span>
    </el-dialog>
    <el-dialog title="物化视图 DDL" :visible.sync="materializedDdlVisible" width="70%">
      <pre class="materialized-ddl">{{ materializedDdl }}</pre>
      <span slot="footer">
        <el-button @click="materializedDdlVisible = false">关闭</el-button>
        <el-button type="primary" @click="exportMaterializedDdl">导出 DDL</el-button>
      </span>
    </el-dialog>
    <el-dialog title="物化视图授权" :visible.sync="materializedGrantVisible" width="800px" append-to-body>
      <ViewGrantPanel v-if="materializedGrantVisible" :query-result-data="queryResultData" />
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
::v-deep .el-table--mini .el-table__cell {
  padding: 2px 0px !important;
}
::v-deep .el-table--medium .el-table__cell {
  padding: 3px !important;
}
.table_box {
  // height: calc(100% - 58px);
  height: 100%;
  background: #fff;
  padding: 0 20px;
  border-radius: 5px;
  width: 100%;
  .table_btn_list {
    position: relative;
    display: flex;
    align-items: center;
    padding: 10px 0;

    .materialized-readonly-tag {
      margin-right: 10px;
    }

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
    justify-content: space-between;

    .table_bottom_info {
      font-size: 12px;
      color: rgba(35, 36, 41, 0.88);

      & > span {
        margin-right: 16px;
      }
    }
  }
}
.materialized-ddl {
  max-height: 60vh;
  overflow: auto;
  white-space: pre-wrap;
}
.left_right_title {
  display: flex;
  justify-content: space-between;
}

.errorMessageList {
  list-style: none;
  line-height: 22px;
  font-size: 14px;
  padding-left: 0;
}

::v-deep {
  .table-custom-class .el-table__header-wrapper tr th {
    &.is-sortable {
      .cell {
        position: relative;
        padding-left: 25px;
      }
      .caret-wrapper {
        position: absolute;
        left: 0;
      }
    }
    .highlight-column {
      background-color: rgb(24, 144, 255);
      color: rgb(255, 255, 255);
    }
    .cell {
      white-space: nowrap;
    }
  }

  .el-checkbox {
    display: block;
  }
  .table-custom-class {
    .el-table__body-wrapper {
      overflow: unset !important;
    }
    .el-table__header-wrapper {
      overflow: unset !important;
    }
    .sort_header {
      display: flex;
      align-items: center;
      .sort_icons {
        display: flex;
        flex-direction: column;
        font-size: 15px;
        margin-right: 10px;
        i {
          // margin-bottom: -5px;
          cursor: pointer;
          color: #c0c4cc;
          &:active,
          &.active {
            color: #409eff;
          }
        }
      }
    }
  }
}
.editBox {
  display: flex;
  align-items: center;
}

.importDialogTable {
  ::v-deep .el-table__body-wrapper {
    .el-table__cell {
      box-sizing: border-box;
      .cell {
        padding: 0;
        width: 100% !important;
        & > div {
          padding-left: 10px;
          padding-right: 10px;
        }
      }
      .el-input {
        padding: 0 !important;
        .el-input__inner {
          height: 25px;
          line-height: 25px;
          padding: 0 10px;
        }
      }
    }
  }
}

::v-deep .filterSortDialog {
  .el-dialog__body {
    padding-top: 0;
  }
  .filterBox {
    .searchBox {
      display: flex;
      .inputBox {
        flex: 4;
        display: flex;
        .el-select {
          flex: 1.5;
          &:nth-child(2) {
            flex: 0.8;
            margin: 0 15px;
          }
        }
        .el-input {
          flex: 1.5;
        }
      }
      .btnBox {
        margin-left: 15px;
        flex: 1;
        display: flex;
        flex-direction: column;
        .el-button {
          width: 100%;
          &:last-child {
            margin-left: 0;
            margin-top: 10px;
          }
        }
      }
    }
    .valueBox {
      margin-top: 10px;
      display: flex;
      & > div:first-child {
        flex: 1;
        width: 0;
        border: 1px solid #b4b4b4;
        border-radius: 5px;
        overflow: hidden;
      }
      & > div:last-child {
        margin-left: 15px;
        display: flex;
        flex-direction: column;
        .el-button {
          &:last-child {
            margin-left: 0;
            margin-top: 10px;
          }
        }
      }
    }
    .btnsBox {
      margin-top: 10px;
    }
  }
  .sortBox {
    display: flex;
    height: 50vh;
    & > div {
      flex: 1;
      border: 1px solid #b4b4b4;
      border-radius: 5px;
      margin-right: 10px;
      .row {
        box-sizing: border-box;
        padding: 10px;
        cursor: pointer;
        &.active,
        &:hover {
          background: #1d4bbe;
          color: #fff;
        }
      }
      &.leftBox {
        flex: 0.8;
      }
      &.handleBox {
        flex: 0.5;
        border: none;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        & > .el-button {
          margin-left: 0;
          margin-bottom: 10px;
          &:last-child {
            margin-bottom: 0;
          }
        }
        &:last-child {
          flex: 0;
          justify-content: flex-start;
          margin-right: 0;
        }

        &:nth-child(2) {
          & > .el-button:nth-child(3),
          & > .el-button:nth-child(2) {
            width: 110px;
          }
        }

        .checkboxDiv {
          display: flex;
          flex-direction: column;
          align-items: center;
          border-radius: 5px;
          justify-content: center;
          border: 1px solid #b4b4b4;
          padding: 15px;
          margin-top: 30px;
          position: relative;
          .el-checkbox {
            position: absolute;
            top: -15%;
            .el-checkbox__label {
              background: #fff;
            }
          }

          .el-radio {
            margin-right: 30px;
          }
        }
      }
    }
  }
}
.activeCell {
  width: 100%;
  height: 25px;
  line-height: 25px;
  color: #fff;
  &.redCell {
    background: #ce0000;
    ::v-deep .el-input__inner {
      border-color: #ce0000;
    }
  }
  &.greenCell {
    background: green;
    ::v-deep .el-input__inner {
      border-color: green;
    }
  }
  &.yellowCell {
    background: rgb(238, 184, 6);
    ::v-deep .el-input__inner {
      border-color: rgb(238, 184, 6);
    }
  }

  &.el-input {
    background: transparent;
  }
}

.el-dialog.pldrqrBox {
  .dialog-footer {
    display: flex;
    justify-content: flex-end;
    .colorInfo {
      flex: 1;
      display: flex;
      align-items: center;
      font-size: 14px;
      div {
        width: 30px;
        height: 15px;
        margin-left: 25px;
        margin-right: 5px;
        &:first-child {
          margin-left: 0px;
        }
      }
    }
  }
}

.upload {
  display: flex;
  align-items: center;
}
::v-deep .el-upload-list {
  margin-left: 10px;
}
::v-deep .el-upload-list__item:first-child {
  margin: 0px;
}
::v-deep .el-upload-list__item {
  margin: 0px;
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
.custom-popover .content-wrapper {
  max-height: 300px !important; /* 设置最大高度 */
  overflow-y: auto; /* 超出时显示垂直滚动条 */
}
::v-deep .el-tabs {
  .el-tabs__content {
    height: 550px !important;
  }
}
// #tableContainer {
//   ::v-deep .el-table {
//     .el-table__header {
//       width: auto !important;
//     }
//   }
// }
</style>
