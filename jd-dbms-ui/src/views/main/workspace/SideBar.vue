<script>
import connectionServer from "@/api/main/connection";
import sqlServer from "@/api/main/sql";
import viewServer from "@/api/main/view";
import tableServer from "@/api/main/table";
import userServer from "@/api/main/user";
import tableSpaceServer from "@/api/main/tableSpace";
import SqlPreview from "@/views/main/workspace/components/DataSource/SqlPreview.vue";
import CreateOption from "./components/CreateOption/index.vue";
import addTableSpaceVue from "./components/siedBarDialog/addTableSpace.vue";
import addUser from "./components/siedBarDialog/addUser.vue";
import addTable from "./components/siedBarDialog/addtable.vue";
import addView from "./components/siedBarDialog/addView.vue";
import { database } from "@/utils/database";
import { v4 as uuidv4 } from "uuid";
import { copyText } from "@/utils";
import ViewImport from "./components/viewImportSql";
import draggable from "vuedraggable";

const nodeTypes = {
  tables: "Table",
  table: "Table",
  views: "View",
  view: "View",
  functions: "Function",
  procedures: "Procedure",
  triggers: "Trigger",
};
const nodeDeleteFunction = {
  table: "deleteTable",
  view: "viewDelete",
  function: "delete_function",
  processdure: "delete_procedure",
  trigger: "delete_triggers",
};
const BATCH_DELETE_TASK_THRESHOLD = 20;
export default {
  props: {
    dataBaseInfo: {
      type: Object,
      default: () => {},
    },
    dataInfo: {
      type: Object,
      default: {},
    },
    isQuery: {
      type: Boolean,
      default: false,
    },
  },
  name: "leftTree",
  dicts: ["model_type"],
  components: {
    CreateOption,
    SqlPreview,
    addTableSpaceVue,
    addUser,
    addTable,
    addView,
    ViewImport,
    draggable,
  },
  data() {
    return {
      openIndex:null,
      menuList: [
        {
          value: "1",
          name: "数据模板",
        },
        {
          value: "2",
          name: "复制表",
          children: [],
        },
        {
          value: "3",
          name: "复制表及数据",
          children: [],
        },
        {
          value: "4",
          name: "新建窗口",
        },
      ],
      selectedOptions2: [],
      cascaderOptions: [],
      copyschemaName: "",
      timer: null,
      tableSearchTimer: null,
      tableSearchRequestId: 0,
      treeClickCount: 0,
      isOpened: true,
      DDLTitle: "",
      //计算数否双击
      lastClickTime: 0,
      // 是否刷新
      dataSourceId: "",
      // 拿到详情
      detailDataSource: [],
      // 选中的表格名称
      nameTbale: "",
      refresh: false,
      database,
      databaseName: "",
      filterName: "",
      type: "tables",
      schema: "",
      activeClass: -1,
      options: [],
      databaseTree: [],
      databaseMaps: new Map(),
      dataTreeList: [],
      dataSourceListData: [],
      defaultProps: {
        children: "children",
        label: "label",
        isLeaf: "leaf",
      },
      editDialogVisible: false,
      tableDialogVisibl: false,
      viewDialogVisible: false,
      userDialogVisible: false,
      ctrlKey: false,
      seleCtrlKet: [],
      allTableNames: null,
      tableListPageNo: 1,
      tableListTotal: 0,
      hasMoreTables: false,
      heightIndexs: [],
      contrast: undefined,
      styleLoading: false,
      loadingText: "正在加载对象列表，请稍候…",
      spaceInfo: {},
      userInfo: {},
      contextMenuVisible: false,
      contextMenu_top: 0,
      contextMenu_left: 0,
      context_current_item: {},
      detailsObjs: {},
      detailsObj: {},
      buttonVisible: false,
    };
  },
  computed: {
    visitedViews() {
      return this.$store.getters.visitedViews2;
    },
    pageId() {
      return this.$route.params.id;
    },
    openConfigList() {
      return this.$store.state.workspace.workspaceTabList;
    },
    dataSourceList() {
      return this.$store.state.workspace.dataSourceList;
    },
    currentDataSource() {
      return this.$store.state.workspace.currentConnectionDetails;
    },
    currentTable() {
      return this.$store.state.workspace.currentTable;
    },
    filterDataTreeList() {
      if (this.filterName) {
        return this.dataTreeList.filter((item) => {
          return this.type == "tableSpace"
            ? item.tableDetails.tableSpace
                .toLowerCase()
                .indexOf(this.filterName.toLowerCase()) != -1
            : item.name.toLowerCase().indexOf(this.filterName.toLowerCase()) !=
                -1;
        });
      }
      return this.dataTreeList;
    },
    filteredTableNames() {
      const names = this.allTableNames || this.dataTreeList.map((item) => item.name);
      const keyword = this.filterName.toLowerCase();
      return this.type == "tables" ? names.filter((name) => !keyword || name.toLowerCase().includes(keyword)) : [];
    },
    isCurrentFilterAllSelected() {
      return this.filteredTableNames.length > 0 && this.filteredTableNames.every((name) => this.seleCtrlKet.includes(name));
    },
    isCurrentFilterPartiallySelected() {
      return !this.isCurrentFilterAllSelected && this.filteredTableNames.some((name) => this.seleCtrlKet.includes(name));
    },
  },
  watch: {
    databaseName(val) {
      this.$refs.tree.filter(val);
    },
    dataSourceList(newVal) {
      let _ID = this.dataSourceId;
      this.dataSourceId = "";
      setTimeout(() => (this.dataSourceId = _ID ? _ID : this.dataSourceId));
    },
    contextMenuVisible(value) {
      if (value) {
        document.body.addEventListener("click", this.closeMenu);
      } else {
        document.body.removeEventListener("click", this.closeMenu);
      }
    },
  },
  mounted() {
    window.addEventListener("keydown", this.handelKeyDown);
    // console.log(this.dataBaseInfo)
    document.title = `${this.dataBaseInfo.type}${this.dataBaseInfo.alias}/${this.dataBaseInfo.userName}`;
    this.getDataBaseTreeData();
    if (this.$route.query.schemaName) {
      // 此判断是从约束进来打开新窗口
      this.schema = this.$route.query.schemaName;
      this.getTableDataList(this.schema);
    } else {
      // debugger
      if (this.$route.query.databaseName && this.$route.query.tableName) {
        this.schema = this.$route.query.databaseName;
        // this.filterName = this.$route.query.tableName
        this.$emit("isType", this.type, this.schema, this.activeClass);
        this.getTableDataList(this.schema);
      } else {
        this.schema = this.dataInfo.userName;
        this.$emit("isType", this.type, this.schema, this.activeClass);
        this.getTableDataList(this.schema);
      }
    }
    this.$EventBus.$on("refreshRightTable", (page) => {
      this.refreshRightTable(page);
    });
    if (this.$route.query.tableName) {
      this.filterName = this.$route.query.tableName;
    }
  },
  destroyed() {
    window.removeEventListener("beforeunload", this.beforeunload);
  },
  //解绑事件
  beforeDestroy() {
    this.$EventBus.$off("refreshRightTable");
    clearTimeout(this.tableSearchTimer);
  },
  methods: {
    handleClick(val,index) {
      if(!val.children){
        switch (val.value) {
          case '1': // 数据模板
            this.routerSql()
            break;
          case '4': // 新建窗口
            this.newTableLable()
            break;
        }
        this.openIndex = null
        this.contextMenuVisible = false
      }else if(val.children){
        // console.log(val);
        this.copyschemaName = ""
        this.openIndex =  this.openIndex == index ? null : index
      }
    },
    isMaterializedView(item) {
      return String(item?.tableDetails?.type || item?.viewType || item?.type || "").toUpperCase() === "MATERIALIZED VIEW";
    },
    changeCopy(val) {
      //  console.log(val,this.copyschemaName,this.openIndex,this.openIndex == 1);
      let status = this.openIndex !== 1
      if (val) {
        this.openIndex = null
        this.contextMenuVisible = false
        this.copyTable(status);
      }
    },
    beforeunload(e) {
      e = e || window.event;
      if (e) {
        e.preventDefault();
        e.returnValue = false;
      }
      console.log("提示");
    },
    getDataBaseTreeData() {
      const params = {
        dataSourceId: this.dataInfo.dateSourceId,
        dataSourceName: this.dataInfo.dateSourceName,
        refresh: false,
      };
      // debugger
      connectionServer.getSchemaList(params).then((res) => {
        this.dataSourceListData = res.data;
        this.menuList.forEach((item) => {
          if (item.children) {
            item.children = this.dataSourceListData;
          }
        });
        this.$store.dispatch("workspaceData/setDataSourceData", res.data);
      });
    },
    // refreshScheam() {
    //   this.$emit("refreshScheam");
    // },
    changeDataSource(val) {
      let _data = this.dataSourceList.find((item) => item.id === val);
      this.$store.commit("SET_CURRENTCONNECTIONDETAILS", _data);
      this.getDataBaseTree();
    },
    // 获取数据库树形结构
    getDataBaseTree(dataSource) {
      // debugger
      if (dataSource) {
        this.dataSourceId = dataSource.id;
        let _data = this.dataSourceList.find(
          (item) => item.id === dataSource.id
        );
        this.$store.commit("SET_CURRENTCONNECTIONDETAILS", _data);
      } else {
        dataSource = this.$store.state.workspace.currentConnectionDetails;
      }
      this.databaseTree = [];
      let send = {
        dataSourceId: dataSource.id,
        dataSourceName: dataSource.alias,
        refresh: false,
      };
      let fun;
      if (dataSource?.supportDatabase) {
        fun = connectionServer.getDatabaseList;
      } else {
        fun = connectionServer.getSchemaList;
      }
      fun(send).then((res) => {
        this.$store.commit("SET_DATABASELIST", res.data);
        this.databaseTree = res.data?.map((item) => {
          let extraParams = {
            dataSourceId: this.currentDataSource.id,
            dataSourceName: this.currentDataSource.alias,
            databaseType: this.currentDataSource.type,
            databaseName: this.currentDataSource.supportDatabase
              ? item.name
              : "",
            schemaName: this.currentDataSource.supportSchema ? item.name : "",
          };
          let { dataSourceId, databaseName, schemaName } = extraParams;
          const preCode = [dataSourceId, databaseName, schemaName].join("-");
          return {
            uuid: uuidv4(),
            key: item.name,
            label: item.name,
            extraParams: extraParams,
            children: [
              {
                uuid: uuidv4(),
                key: `${preCode}-tables`,
                label: "表",
                treeNodeType: "tables",
                extraParams: extraParams,
              },
              {
                uuid: uuidv4(),
                key: `${preCode}views`,
                label: "视图",
                treeNodeType: "views",
                extraParams: extraParams,
              },
              {
                uuid: uuidv4(),
                key: `${preCode}functions`,
                label: "函数",
                treeNodeType: "functions",
                extraParams: extraParams,
              },
              {
                uuid: uuidv4(),
                key: `${preCode}procedures`,
                label: "存储过程",
                treeNodeType: "procedures",
                extraParams: extraParams,
              },
              {
                uuid: uuidv4(),
                key: `${preCode}triggers`,
                label: "触发器",
                treeNodeType: "triggers",
                extraParams: extraParams,
              },
            ],
          };
        });
      });
      setTimeout(() => {
        this.refresh = false;
      }, 30);
    },
    // 获取数据表
    getTableList(nodeData, resolve) {
      let send = {
        dataSourceId: nodeData.extraParams.dataSourceId,
        dataSourceName: nodeData.extraParams.dataSourceName,
        databaseType: nodeData.extraParams.databaseType,
        databaseName: nodeData.extraParams.databaseName,
        schemaName: nodeData.extraParams.schemaName,
        refresh: this.refresh ? this.refresh : false,
        pageNo: 1,
        pageSize: 200,
      };
      tableServer.getTableList(send).then((res) => {
        resolve(
          res.data.data.map((item) => ({
            uuid: uuidv4(),
            label: item.name,
            treeNodeType: "table",
            key: item.name,
            pinned: item.pinned,
            comment: item.comment,
            extraParams: {
              ...nodeData.extraParams,
              tableName: item.name,
            },
            leaf: true,
          }))
        );
      });
      setTimeout(() => {
        this.refresh = false;
      }, 30);
    },
    // 新建查询
    createConsole(node) {
      this.$store.dispatch("createConsole", {
        dataSourceId: node.data.extraParams.dataSourceId,
        dataSourceName: node.data.extraParams.dataSourceName,
        databaseType: node.data.extraParams.databaseType,
        databaseName: node.data.extraParams?.databaseName,
        schemaName: node.data.extraParams?.schemaName,
      });
    },
    // 数据库树初始化
    loadNode(node, resolve) {
      this.databaseMaps.set(node.id, { node, resolve });
      let nodeData = node.data;
      if (node.level === 0) {
        resolve(nodeData);
      }

      if (node.level === 1) {
        resolve(nodeData.children);
      }

      if (node.level >= 2) {
        let send = {
          dataSourceId: nodeData.extraParams.dataSourceId,
          dataSourceName: nodeData.extraParams.dataSourceName,
          databaseType: nodeData.extraParams.databaseType,
          schemaName: nodeData.extraParams?.schemaName,
          databaseName: nodeData.extraParams?.databaseName,
          refresh: false,
          pageNo: 1,
          pageSize: 1000,
        };
        if (nodeData.label === "表") {
          this.getTableList(nodeData, resolve);
        } else if (nodeData.label === "视图") {
          debugger;
          viewServer.getViewList(send).then((res) => {
            resolve(
              res.data.data.map((item) => ({
                uuid: uuidv4(),
                label: item.name,
                treeNodeType: "view",
                key: item.name,
                pinned: item.pinned,
                comment: item.comment,
                extraParams: {
                  ...nodeData.extraParams,
                  tableName: item.name,
                  viewType: item.tableDetails?.type,
                },
                viewType: item.tableDetails?.type,
                leaf: true,
              }))
            );
          });
        } else if (nodeData.label === "函数") {
          sqlServer.getFunctionList(send).then((res) => {
            resolve(
              res.data.data.map((item) => ({
                uuid: uuidv4(),
                label: item.functionName,
                treeNodeType: "function",
                key: item.functionName,
                pinned: item.pinned,
                comment: item.comment,
                extraParams: {
                  ...nodeData.extraParams,
                  functionName: item.functionName,
                },
                leaf: true,
              }))
            );
          });
        } else if (nodeData.label === "存储过程") {
          sqlServer.getProcedureList(send).then((res) => {
            resolve(
              res.data.data.map((item) => ({
                uuid: uuidv4(),
                label: item.procedureName,
                treeNodeType: "processdure",
                key: item.procedureName,
                pinned: item.pinned,
                comment: item.comment,
                extraParams: {
                  ...nodeData.extraParams,
                  procedureName: item.procedureName,
                },
                leaf: true,
              }))
            );
          });
        } else if (nodeData.label === "触发器") {
          sqlServer.getTriggerList(send).then((res) => {
            resolve(
              res.data.data.map((item) => ({
                uuid: uuidv4(),
                label: item.triggerName,
                treeNodeType: "trigger",
                key: item.triggerName,
                pinned: item.pinned,
                comment: item.comment,
                extraParams: {
                  ...nodeData.extraParams,
                  triggerName: item.triggerName,
                },
                leaf: true,
              }))
            );
          });
        }
      }
    },
    refreshRightTable(data) {
      console.log(data);
      let { uniqueData } = data;
      let send = {
        dataSourceId: uniqueData.dataSourceId,
        databaseType: uniqueData.databaseType,
        databaseName: uniqueData?.databaseName,
        hasNextPage: true,
        pageNo: 1,
        pageSize: 200,
        schemaName: uniqueData?.schemaName,
        sql: `select * from ${
          uniqueData?.schemaName || uniqueData?.databaseName
        }.${uniqueData.tableName}`,
        tableName: uniqueData.tableName,
        total: 0,
        type: uniqueData.databaseType,
      };
      sqlServer.viewTable(send).then((res) => {
        if (!res.success) {
          this.$message.error(res.errorMessage);
          return;
        }
        this.openConfigList.forEach((element) => {
          if (element.id === data.id) {
            Object.assign(element, {
              ...res.data[0],
            });
          }
        });
      });
    },
    // 点击打开数据表
    handleNodeClick(data, node, isRightClick = false) {
      //记录点击次数
      this.treeClickCount++;
      //单次点击次数超过2次不作处理,直接返回,也可以拓展成多击事件
      if (this.treeClickCount >= 2) {
        return;
      }
      //计时器,计算300毫秒为单位,可自行修改
      this.timer = window.setTimeout(() => {
        if (this.treeClickCount > 1 || isRightClick) {
          //把次数归零
          this.treeClickCount = 0;
          //双击事件
          if (data.treeNodeType === "table" || data.treeNodeType === "view") {
            let { extraParams } = data;
            let send = {
              isDataView: data.treeNodeType === "view" ? false : true,
              dataSourceId: extraParams.dataSourceId,
              databaseType: extraParams.databaseType,
              databaseName: extraParams?.databaseName,
              hasNextPage: true,
              pageNo: 1,
              pageSize: 200,
              schemaName: extraParams?.schemaName,
              sql: `select * from ${
                extraParams?.schemaName || extraParams?.databaseName
              }.${extraParams.tableName}`,
              tableName: extraParams.tableName,
              total: 0,
              type: this.currentDataSource.type,
            };
            sqlServer.viewTable(send).then((res) => {
              let flag = this.openConfigList.filter(
                (item) => item.id === data.uuid
              );

              !flag.length &&
                this.openConfigList.push({
                  ...res.data[0],
                  id: data.uuid,
                  type: "editTableData",
                  title: data.label,
                  uniqueData: {
                    dataSourceId: extraParams.dataSourceId,
                    databaseType: extraParams.databaseType,
                    databaseName: extraParams.databaseName,
                    schemaName: extraParams.schemaName,
                    tableName: extraParams.tableName,
                  },
                });

              this.$store.commit("SET_ACTIVECONSOLEID", data.uuid);
            });
          } else if (data.treeNodeType === "functions") {
            this.functionView(node);
          } else if (data.treeNodeType === "procedures") {
            this.triggerNameView(node);
          } else if (data.treeNodeType === "triggers") {
            this.procedureView(node);
          }
        } else {
          this.treeClickCount = 0;
        }
        if (data.treeNodeType === "table") {
          this.$store.commit("SET_CURRENTTABLE", data);
        } else {
          this.$store.commit("SET_CURRENTTABLE", null);
        }
      }, 300);
    },
    openCreateOption(node, nodeData, type) {
      this.$nextTick(() => {
        this.$refs.createOption.init(nodeData);
      });
    },

    // 数据表、视图、触发器、函数 刷新
    tableRefresh(node) {
      if (!node) return;
      if (node == "tableRefresh") {
        return this.getTableDataList(this.schema, true);
      }
      this.refresh = true;
      if (!node.expanded) {
        this.$refs.tree.$set(node, "expanded", true);
      }
      node.loaded = false;
      node.loadData();
    },
    // 数据表 置顶
    handelPinTable(nodeData, node) {
      let api = nodeData.pinned ? "deleteTablePin" : "addTablePin";
      let send = {
        dataSourceId: nodeData.extraParams.dataSourceId,
        databaseName: nodeData.extraParams.databaseName,
        schemaName: nodeData.extraParams.schemaName,
        tableName: nodeData.extraParams.tableName,
      };
      sqlServer[api](send).then(() => {
        node.parent.loaded = false;
        node.parent.loadData();
      });
    },
    // 数据表 查看DDL
    viewDDL(nodeData) {
      let send = {
        dataSourceId: nodeData.extraParams.dataSourceId,
        databaseName: nodeData.extraParams.databaseName,
        schemaName: nodeData.extraParams.schemaName,
        tableName: nodeData.extraParams.tableName,
      };
      this.DDLTitle = "DDL-" + nodeData.label;
      sqlServer.exportCreateTableSql(send).then((res) => {
        this.$refs.sqlPreview.init(res.data);
      });
    },
    // 数据表 删除表
    deleteTable(nodeData, node, params) {
      this.$confirm('确定要删除"' + nodeData.label + '"吗？', "提示", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      })
        .then(async () => {
          const send = {
            dataSourceId: nodeData.extraParams?.dataSourceId,
            databaseName: nodeData.extraParams?.databaseName,
            schemaName: nodeData.extraParams?.schemaName,
            tableName: nodeData.extraParams?.tableName,
            ...params,
          };
          sqlServer[nodeDeleteFunction[nodeData.treeNodeType]](send).then(
            (res) => {
              if (res.success) {
                node.parent.loaded = false;
                node.parent.loadData();
                this.$message.success("删除成功");
              } else {
                this.$message.error("删除失败");
              }
            }
          );
        })
        .catch(() => {
          this.$message({
            type: "info",
            message: "已取消删除",
          });
        });
    },
    // 查看函数
    openFunction(treeNodeData) {
      this.$store.dispatch("createConsole", {
        name: treeNodeData.label,
        operationType: "function",
        dataSourceId: treeNodeData.extraParams.dataSourceId,
        dataSourceName: treeNodeData.extraParams.dataSourceName,
        databaseType: treeNodeData.extraParams.databaseType,
        databaseName: treeNodeData.extraParams?.databaseName,
        schemaName: treeNodeData.extraParams?.schemaName,
        loadSQL: () => {
          return new Promise((resolve) => {
            sqlServer
              .getFunctionDetail({
                dataSourceId: treeNodeData.extraParams.dataSourceId,
                databaseType: treeNodeData.extraParams.databaseType,
                databaseName: treeNodeData.extraParams.databaseName,
                schemaName: treeNodeData.extraParams?.schemaName,
                functionName: treeNodeData.extraParams.functionName,
              })
              .then((res) => {
                // 更新ddl
                resolve(res.data.functionBody);
              });
          });
        },
      });
    },
    // 查看存储过程
    openProcedure(treeNodeData) {
      this.$store.dispatch("createConsole", {
        name: treeNodeData.label,
        operationType: "procedure",
        dataSourceId: treeNodeData.extraParams.dataSourceId,
        dataSourceName: treeNodeData.extraParams.dataSourceName,
        databaseType: treeNodeData.extraParams.databaseType,
        databaseName: treeNodeData.extraParams?.databaseName,
        schemaName: treeNodeData.extraParams?.schemaName,
        loadSQL: () => {
          return new Promise((resolve) => {
            sqlServer
              .getProcedureDetail({
                dataSourceId: treeNodeData.extraParams.dataSourceId,
                databaseType: treeNodeData.extraParams.databaseType,
                databaseName: treeNodeData.extraParams.databaseName,
                schemaName: treeNodeData.extraParams?.schemaName,
                procedureName: treeNodeData.extraParams?.procedureName,
              })
              .then((res) => {
                // 更新ddl
                resolve(res.data.procedureBody);
              });
          });
        },
      });
    },
    // 查看触发器
    openTrigger(treeNodeData) {
      this.$store.dispatch("createConsole", {
        name: treeNodeData.label,
        operationType: "trigger",
        dataSourceId: treeNodeData.extraParams.dataSourceId,
        dataSourceName: treeNodeData.extraParams.dataSourceName,
        databaseType: treeNodeData.extraParams.databaseType,
        databaseName: treeNodeData.extraParams?.databaseName,
        schemaName: treeNodeData.extraParams?.schemaName,
        loadSQL: () => {
          return new Promise((resolve) => {
            sqlServer
              .getTriggerDetail({
                dataSourceId: treeNodeData.extraParams.dataSourceId,
                databaseType: treeNodeData.extraParams.databaseType,
                databaseName: treeNodeData.extraParams.databaseName,
                schemaName: treeNodeData.extraParams?.schemaName,
                triggerName: treeNodeData.extraParams?.triggerName,
              })
              .then((res) => {
                // 更新ddl
                resolve(res.data.triggerBody);
              });
          });
        },
      });
    },
    // 复制名称
    CopyName(nodeData) {
      copyText(nodeData.label);
    },
    // 数据库树筛选
    filterNode(value, data) {
      if (!value) return true;
      return data.label.indexOf(value) !== -1;
    },
    async getTableDataList(val, isRefreshCache) {
      clearTimeout(this.tableSearchTimer);
      const requestId = ++this.tableSearchRequestId;
      this.filterName = "";
      this.$emit("filterChange", "");
      // this.title = this.dataBaseInfo.url
      this.schema = val || this.dataBaseInfo.userName;
      this.styleLoading = true;
      this.loadingText = this.type == "tables" ? "正在加载表列表，请稍候…" : "正在加载对象列表，请稍候…";
      if (!this.isQuery) {
        this.$store.dispatch("workspaceData/setDataCurrentData", {
          pageId: this.pageId,
          uniqueData: false,
        });
      }
      this.heightIndexs = [];
      this.seleCtrlKet = [];
      this.allTableNames = null;
      this.tableListPageNo = 1;
      this.tableListTotal = 0;
      this.hasMoreTables = false;
      this.activeClass = -1;
      this.detailDataSource = [];
      if (!this.dataInfo.dataSource) {
        this.styleLoading = false;
        return;
      }

      const params = {
        dataSourceId: this.dataInfo.dataSource.id,
        dataSourceName: this.dataInfo.dataSource.alias,
        databaseType: this.dataInfo.dataSource.type,
        schemaName: this.schema,
        // 模式切换只加载表名列表；表详情在点击具体表时再按需加载
        refresh: false,
        // 默认表列表需要展示注释、行数、创建时间和最后结构变化时间。
        requestType: this.type == "tables" || this.type == "views" ? 2 : 1,
        pageNo: 1,
        // 首屏只加载一页名称，避免达梦返回数万条对象后阻塞浏览器渲染。
        pageSize: 200,
        isRefreshCache: false,
      };
      let res = null;
      try {
        if (this.type == "tables") {
          if (isRefreshCache) {
            params.isRefreshCache = isRefreshCache;
          }
          res = await tableServer.getTableList(params);
        } else if (this.type == "views") {
          res = await viewServer.getViewList(params);
        } else if (this.type == "users") {
          params.databaseName = this.dataInfo.dataSource.alias;
          res = await userServer.getUserList(params);
        } else if (this.type == "tableSpace") {
          res = await tableSpaceServer.getTableSpaceList(params);
        }
        if (requestId !== this.tableSearchRequestId) return;
        if (!res || !res.success) {
          this.$message.error(res?.errorCode || res?.errorMessage || "加载失败");
          return;
        }
        let data = res.data.data || [];
        // 先让 loading 遮罩完成一次绘制，再处理大列表，避免页面出现“卡死”假象。
        await this.$nextTick();
        // 侧边栏只需要名称和少量状态字段，避免把接口返回的完整元数据
        //（约数万条记录）全部纳入 Vue 响应式对象，造成长时间卡顿。
        const detailDataSource = [];
        this.dataTreeList = data.map((item, index) => {
          const details = item.tableDetails || {};
          const tableDetails = {
            tableSpace: details.tableSpace,
            valid: details.valid,
            path: details.path,
            type: details.type,
          };
          if (
            this.$route.query.tableName &&
            item.name == this.$route.query.tableName
          ) {
            this.handelClickItem(item, index, {});
          }
          detailDataSource.push({
            name: item.name,
            schema: details.schema,
            tableSpace: details.tableSpace,
            comment: item.comment || details.comment,
            numRows: details.numRows,
            created: details.created,
            lastDDL: details.lastDDL,
            type: details.type,
          });
          return {
            name: item.name,
            comment: item.comment || details.comment,
            pinned: item.pinned,
            tableDetails,
          };
        });
        this.detailDataSource = detailDataSource;
        this.tableListTotal = Number(res.data.total) || data.length;
        this.hasMoreTables = this.type == "tables" && data.length < this.tableListTotal;
        if (this.type == "views") {
          this.filterDataTreeList.forEach((val) => {
            data.forEach((v) => {
              if (val.name === v.name && v.tableDetails.valid === "VALID") {
                val.checkStyle = 1;
              } else if (val.name === v.name) {
                val.checkStyle = 2;
              }
            });
          });
        }
        this.$emit("queryDetailTable", this.detailDataSource, res.data.total || 0);
      } catch (e) {
        if (requestId === this.tableSearchRequestId) {
          this.$message.error("加载对象失败，请稍后重试");
        }
      } finally {
        if (requestId === this.tableSearchRequestId) this.styleLoading = false;
      }
    },
    // 视图编译
    viewExecute(type) {
      const includesMaterializedView = this.seleCtrlKet.some((name) => {
        const item = this.dataTreeList.find((view) => view.name === name);
        return this.isMaterializedView(item);
      });
      if (type !== "all" && includesMaterializedView) {
        this.$message.warning("物化视图不支持普通视图编译，请仅选择普通视图");
        return;
      }
      this.styleLoading = true;
      const params = {
        dataSourceId: this.dataInfo.dataSource.id,
        schemaName: this.schema,
        tableName: type == "all" ? "" : JSON.stringify(this.seleCtrlKet),
      };
      viewServer.viewAllExecute(params).then((res) => {
        if (this.seleCtrlKet.length > 0 && type !== "all") {
          this.filterDataTreeList.forEach((val) => {
            val.checkStyle = 1;
            this.seleCtrlKet.forEach((v, i) => {
              if (v === val.name) {
                if (res.data[i].success) {
                  val.checkStyle = 1;
                } else {
                  val.checkStyle = 2;
                }
              }
            });
          });
        } else {
          this.filterDataTreeList.forEach((val) => {
            res.data.forEach((v) => {
              if (val.name === v.tableName && v.success) {
                val.checkStyle = 1;
              } else if (val.name === v.tableName) {
                val.checkStyle = 2;
              }
            });
          });
        }
        if (!res.success) {
          this.$message.error(res.errorCode || res.errorMessage);
        } else {
          this.$message.success(res.errorCode || res.errorMessage);
        }
        this.styleLoading = false;
      });
    },
    addSideBar() {
      if (this.type == "tableSpace") {
        this.editDialogVisible = true;
      } else if (this.type == "tables") {
        this.tableDialogVisibl = true;
      } else if (this.type == "views") {
        this.viewDialogVisible = true;
      } else if (this.type == "users") {
        this.userDialogVisible = true;
      }
    },
    deleteData() {
      if (!this.seleCtrlKet.length) {
        return this.$message.warning(
          `请先选择${
            this.type == "tables"
              ? "表"
              : this.type == "views"
              ? "视图"
              : this.type == "tableSpace"
              ? "表空间"
              : this.type == "users"
              ? "用户"
              : ""
          }`
        );
      }
      if (
        this.type == "views" &&
        this.seleCtrlKet.some((name) => {
          const item = this.dataTreeList.find((view) => view.name === name);
          return this.isMaterializedView(item);
        })
      ) {
        return this.$message.warning("物化视图暂不支持在此处批量删除");
      }
      const count = this.seleCtrlKet.length;
      const message = this.type == "tables"
        ? `确定要删除已选的 ${count} 张表吗？此操作不可恢复。`
        : "确定要删除吗?";
      this.$confirm(message, "提示", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      })
        .then(async () => {
          const params = {
            dataSourceId: this.dataInfo.dataSource.id,
            schemaName: this.schema,
          };
          if (this.type == "tables") {
            params.tableNames = this.seleCtrlKet;
            if (this.seleCtrlKet.length > BATCH_DELETE_TASK_THRESHOLD) {
              this.styleLoading = true;
              this.loadingText = "正在提交后台删除任务，请稍候…";
              await this.$nextTick();
              try {
                const res = await tableServer.deleteTableDataTask(params);
                if (res.success) {
                  this.seleCtrlKet = [];
                  this.$message.success("批量删除已转入任务中心，可继续操作");
                  this.$EventBus.$emit("openTaskCenter");
                } else {
                  this.$message.error(res.errorMessage || "提交删除任务失败");
                }
              } catch (e) {
                this.$message.error("提交删除任务失败，请稍后重试");
              } finally {
                this.styleLoading = false;
              }
              return;
            }
            this.styleLoading = true;
            this.loadingText = `正在删除 ${this.seleCtrlKet.length} 张表，请稍候…`;
            await this.$nextTick();
            try {
              const res = await tableServer.deleteTableData(params);
              if (res.success) {
                await this.getTableDataList(this.schema, true);
                this.$message.success("删除成功");
              } else {
                this.$message.error(res.errorMessage);
              }
            } catch (e) {
              this.$message.error("删除表失败，请稍后重试");
            } finally {
              this.styleLoading = false;
            }
          } else if (this.type == "views") {
            params.viewNames = this.seleCtrlKet;
            viewServer.deleteViewFn(params).then((res) => {
              if (res.success) {
                this.getTableDataList(this.schema);
              } else {
                this.$message.error(res.errorMessage);
              }
            });
          } else if (this.type == "tableSpace") {
            params.tableSpaceNames = this.seleCtrlKet;
            tableSpaceServer.deleteTableSpace(params).then((res) => {
              if (res.success) {
                this.getTableDataList(this.schema);
                this.$message.success("删除成功");
              } else {
                this.$message.error(res.errorMessage);
              }
            });
          } else if (this.type == "users") {
            params.userNames = this.seleCtrlKet;
            userServer.deleteUser(params).then((res) => {
              if (res.success) {
                this.getTableDataList(this.schema);
                this.$message.success("删除成功");
                this.getDataBaseTreeData();
              } else {
                this.$message.error(res.errorMessage);
              }
            });
          }
        })
        .catch(() => {});
    },
    toggleTableSelection(item, index) {
      const tableName = item.name;
      if (this.seleCtrlKet.includes(tableName)) {
        this.seleCtrlKet = this.seleCtrlKet.filter((name) => name !== tableName);
      } else {
        this.seleCtrlKet.push(tableName);
      }
    },
    async toggleFilteredTableSelection() {
      if (this.isCurrentFilterAllSelected) {
        this.seleCtrlKet = this.seleCtrlKet.filter((name) => !this.filteredTableNames.includes(name));
      } else {
        if (!this.allTableNames) {
          this.styleLoading = true;
          this.loadingText = "正在获取全部表名，请稍候…";
          try {
            const res = await tableServer.getAllTableList({
              dataSourceId: this.dataInfo.dataSource.id,
              dataSourceName: this.dataInfo.dataSource.alias,
              databaseType: this.dataInfo.dataSource.type,
              schemaName: this.schema,
              refresh: false,
            });
            if (!res.success) {
              this.$message.error(res.errorMessage || "获取表名失败");
              return;
            }
            this.allTableNames = (res.data || []).map((item) => item.name);
          } catch (e) {
            this.$message.error("获取表名失败，请稍后重试");
            return;
          } finally {
            this.styleLoading = false;
          }
        }
        this.seleCtrlKet = Array.from(new Set([...this.seleCtrlKet, ...this.filteredTableNames]));
      }
    },
    async loadMoreTables() {
      if (!this.hasMoreTables || this.styleLoading) return;
      this.styleLoading = true;
      this.loadingText = "正在加载更多表，请稍候…";
      try {
        const res = await tableServer.getTableList({
          dataSourceId: this.dataInfo.dataSource.id,
          dataSourceName: this.dataInfo.dataSource.alias,
          databaseType: this.dataInfo.dataSource.type,
          schemaName: this.schema,
          refresh: false,
          requestType: 2,
          pageNo: this.tableListPageNo + 1,
          pageSize: 200,
          searchKey: this.filterName || undefined,
          isRefreshCache: false,
        });
        if (!res.success) {
          this.$message.error(res.errorMessage || "加载更多表失败");
          return;
        }
        const data = res.data.data || [];
        this.dataTreeList = this.dataTreeList.concat(data.map((item) => ({
          name: item.name,
          comment: item.comment || (item.tableDetails || {}).comment,
          pinned: item.pinned,
          tableDetails: {
            tableSpace: (item.tableDetails || {}).tableSpace,
            valid: (item.tableDetails || {}).valid,
            path: (item.tableDetails || {}).path,
            type: (item.tableDetails || {}).type,
          },
        })));
        this.detailDataSource = this.detailDataSource.concat(data.map((item) => {
          const details = item.tableDetails || {};
          return {
            name: item.name,
            schema: details.schema,
            tableSpace: details.tableSpace,
            comment: item.comment || details.comment,
            numRows: details.numRows,
            created: details.created,
            lastDDL: details.lastDDL,
            type: details.type,
          };
        }));
        this.tableListPageNo += 1;
        this.tableListTotal = Number(res.data.total) || this.tableListTotal;
        this.hasMoreTables = this.dataTreeList.length < this.tableListTotal && data.length > 0;
        this.$emit("queryDetailTable", this.detailDataSource, this.tableListTotal);
      } catch (e) {
        this.$message.error("加载更多表失败，请稍后重试");
      } finally {
        this.styleLoading = false;
      }
    },
    editHandleCloseFn(val) {
      this.editDialogVisible = val;
    },
    tableCloseFn(val) {
      this.tableDialogVisibl = val;
    },
    viewCloseFn(val) {
      this.viewDialogVisible = val;
    },
    userHandleCloseFn(val) {
      this.userDialogVisible = val;
    },
    checkTypeFn() {
      this.filterName = "";
      this.getTableDataList(this.schema);
      this.$emit("isType", this.type);
    },
    handelKeyDown(event) {
      this.ctrlKey = event.key == "Control";
    },
    // 切表事件
    async handelClickItem(items, index, event) {
      this.contextMenuVisible = false;
      this.$refs.dataSourcePop?.doClose();
      console.log(this.dataInfo, "this.dataInfo");
      if (this.isQuery) return;
      this.$emit("load", true);
      if (this.type != "tables" && this.ctrlKey && event.ctrlKey) {
        if (this.heightIndexs.includes(index)) {
          this.heightIndexs = this.heightIndexs.filter((i) => i !== index);
        } else {
          this.heightIndexs.push(index);
        }
        if (this.seleCtrlKet.includes(items.name)) {
          this.seleCtrlKet = this.seleCtrlKet.filter((i) => i !== items.name);
        } else {
          this.seleCtrlKet.push(items.name || items.tableDetails.tableSpace);
        }
      } else {
        this.heightIndexs = [index];
        if (this.type != "tables") {
          this.seleCtrlKet = [items.name || items.tableDetails.tableSpace];
        }
        this.nameTbale = items.name;
        this.activeClass = index;
        let type = "";
        const materializedView = this.type == "views" && this.isMaterializedView(items);
        this.$store.dispatch("jdTagsView/changeView", {
          title: items.name || items.tableDetails.tableSpace,
          id: this.$route.params.id,
        });
        this.$store.state.jdTagsView.titleMap.push({
          title: items.name || items.tableDetails.tableSpace,
          id: this.$route.params.id,
        });
        if (this.type == "tables") {
          type = "editTable";
        } else if (this.type == "views") {
          type = materializedView ? "editTableData" : "editView";
        } else if (this.type == "users") {
          type = "editUser";
        } else if (this.type == "tableSpace") {
          type = "editTableSpace";
        }
        if (this.type == "tables" || this.type == "views") {
          let send = {
            dataSourceId: this.dataInfo.dataSource.id,
            databaseType: this.dataInfo.dataSource.type,
            databaseName: this.dataInfo.dataSource.databaseName,
            hasNextPage: true,
            pageNo: 1,
            pageSize: 100,
            skipCount: false,
            schemaName: this.schema,
            sql: `select * from "${this.schema}"."${items.name}"`,
            tableName: items.name,
            total: 0,
            type: this.dataInfo.dataSource.type,
            isDataView: this.type == "views" ? false : true,
          };
          await sqlServer.executeSql(send).then((res) => {
            const result = res?.data?.[0];
            if (!res?.success || !result) {
              this.$message.error(res?.errorMessage || "打开对象失败");
              return;
            }
            if (result.success) {
              this.$store.dispatch("workspaceData/setDataCurrentData", {
                pageId: this.pageId,
                title: items.name,
                type: type,
                ...result,
                canEdit: materializedView ? false : result.canEdit,
                params: send,
                uniqueData: {
                  dataSourceId: this.dataInfo.dataSource.id,
                  dataSourceName: this.dataInfo.dataSource.alias,
                  databaseName: "",
                  sqlInfo: null,
                  databaseType: this.dataInfo.dataSource.type,
                  schemaName: this.schema,
                  tableName: items.name,
                  viewType: items.tableDetails?.type,
                  isLoading: true,
                  dataType: this.type,
                },
              });
            } else {
              this.$message.error(result.message || "打开对象失败");
              this.$store.dispatch("workspaceData/setDataCurrentData", {
                pageId: this.pageId,
                params: {},
                title: items.name,
                type: type,
                uniqueData: {
                  dataSourceId: this.dataInfo.dataSource.id,
                  schemaName: this.schema,
                  tableName: items.name,
                  viewType: items.tableDetails?.type,
                  sqlInfo: null,
                },
              });
            }
            result.success
              ? (items.checkStyle = 1)
              : (items.checkStyle = 2);
            this.$emit("load", false);
          }).catch(() => {
            this.$message.error("打开对象失败，请稍后重试");
          }).finally(() => {
            this.$emit("load", false);
          });
        } else if (this.type == "users") {
          let send = {
            dataSourceId: this.dataInfo.dataSource.id,
            databaseType: this.dataInfo.dataSource.type,
            databaseName: this.dataInfo.databaseName,
            hasNextPage: true,
            pageNo: 1,
            pageSize: 200,
            schemaName: this.schema,
            tableName: items.name,
            userName: items.name,
            total: 0,
            type: this.dataInfo.dataSource.type,
          };
          userServer.getUserDetails(send).then((res) => {
            this.$emit("load", false);
            this.$store.dispatch("workspaceData/setDataCurrentData", {
              pageId: this.pageId,
              title: items.name,
              type: type,
              tableDetails: res.data.tableDetails,
              uniqueData: {
                dataSourceId: this.dataInfo.dataSource.id,
                databaseName: this.dataInfo.dataSource.alias,
                sqlInfo: null,
                databaseType: this.dataInfo.dataSource.type,
                userName: items.name,
              },
            });
          }).catch(() => {
            this.$message.error("打开用户失败，请稍后重试");
          }).finally(() => {
            this.$emit("load", false);
          });
        } else if (this.type == "tableSpace") {
          let send = {
            dataSourceId: this.dataInfo.dataSource.id,
            path: items.tableDetails.path,
          };
          tableSpaceServer.getTableSpaceDetails(send).then((res) => {
            this.$emit("load", false);
            this.$store.dispatch("workspaceData/setDataCurrentData", {
              pageId: this.pageId,
              title: items.name,
              type: type,
              // tableDetails: res.data.tableDetails,
              uniqueData: {
                dataSourceId: this.dataInfo.dataSource.id,
                databaseName: this.dataInfo.dataSource.alias,
                path: items.tableDetails.path,
              },
            });
          }).catch(() => {
            this.$message.error("打开表空间失败，请稍后重试");
          }).finally(() => {
            this.$emit("load", false);
          });
        } else {
          this.$store.dispatch("workspaceData/setDataCurrentData", {
            pageId: this.pageId,
            title: items.name,
            type: type,
            uniqueData: {
              dataSourceId: this.dataInfo.dataSource.id,
              dataSourceName: this.dataInfo.dataSource.alias,
              databaseName: "",
              databaseType: this.dataInfo.dataSource.type,
              schemaName: this.schema,
              tableName: items.name,
            },
          });
          this.$emit("load", false);
        }
      }
    },
    addTableFn() {
      if (this.type == "tables") {
        console.log("新增表");
      } else if (this.type == "tableSpace") {
        console.log("新增表空间");
      }
    },
    deleteFn() {
      this.$confirm('确定要删除"' + this.nameTbale + '"吗？', "提示", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      }).then(() => {
        const params = {
          dataSourceId: this.dataInfo.dataSource.id,
          databaseName: "",
          schemaName: this.schema,
          tableName: this.nameTbale,
        };
        tableServer.deleteTable(params).then((res) => {
          if (res.success) {
            this.$message.success("删除成功");
            this.getTableDataList(this.schema);
            this.$store.dispatch("addWorkspaceTab", false);
          } else {
            this.$message.error("删除失败");
          }
        });
      });
    },
    isopenFn() {
      this.$emit("isopenFn");
    },
    refshTableData(val) {
      let send = {
        dataSourceId: this.currentConfig?.uniqueData.dataSourceId,
        databaseName: this.currentConfig?.uniqueData.databaseName,
        path: val,
      };
      tableSpaceServer.getTableSpaceDetails(send).then((res) => {
        this.tableData = [];
        this.tableData.push(res.data.tableDetails);
      });
    },
    creatRightMenu(tag, e) {
      if (this.type == "tables") {
        e.preventDefault();
        const menuMinHeight = 50;
        const offsetTop = this.$el.getBoundingClientRect().top;
        const offsetHeight = this.$el.offsetHeight;
        const maxTop = offsetHeight - menuMinHeight;
        const top = e.clientY - offsetTop + 120;
        if (top > maxTop) {
          this.contextMenu_top = maxTop;
        } else {
          this.contextMenu_top = top;
        }
        this.contextMenu_left = e.clientX;
        this.contextMenuVisible = true;
        this.openIndex = null
        this.copyschemaName = "";
        this.context_current_item = {
          dataSourceId: this.dataInfo.dataSource.id,
          schemaName: this.schema,
          ...tag,
        };
      }
    },
    closeMenu() {
      this.contextMenuVisible = false;
    },
    copyTable(ref) {
      const params = {
        tableName: this.context_current_item.name,
        copySchemaName: this.copyschemaName,
        schemaName: this.context_current_item.schemaName,
        dataSourceId: this.context_current_item.dataSourceId,
        isData: ref,
      };
      tableServer.copyTable(params).then((res) => {
        if (res.success) {
          if (res.data[0].message) {
            this.$message.error(res.data[0].message);
          } else {
            this.$message.success("复制成功");
            this.getTableDataList(this.schema, true);
          }
        } else {
          this.$message.error("复制失败");
        }
      });
    },
    newTableLable() {
      let databaseName = this.context_current_item.schemaName;
      let tableName = this.context_current_item.name;
      this.$EventBus.$emit("openLookPage", { databaseName, tableName });
    },
    routerSql() {
      const params = {
        tableName: this.context_current_item.name,
        schemaName: this.context_current_item.schemaName,
        dataSourceId: this.context_current_item.dataSourceId,
      };
      tableServer.querySearchSql(params).then((res) => {
        let pageId = uuidv4();
        this.$store
          .dispatch("workspaceData/createConsole", {
            pageId,
            dataSourceId: this.dataInfo.dataSource.id,
            dataSourceName: this.dataInfo.dataSource.alias,
            databaseType: this.dataInfo.dataSource.type,
            sql: res.data,
          })
          .then((res) => {
            this.$router.push({
              path: "/workspace/query/" + pageId,
            });
          });
      });
    },
    // 视图导入功能
    viewImport() {
      this.$refs.viewRef.dialogVisible = true;
    },
    // 搜索时过滤掉 @！=
    handleInput(val) {
      this.filterName = val.replace(/[@!=]/g, "");
      this.heightIndexs = [];
      this.activeClass = -1;
      this.$emit("filterChange", this.filterName);
      if (this.type !== "tables") return;
      clearTimeout(this.tableSearchTimer);
      this.tableSearchTimer = setTimeout(() => this.searchTableList(), 300);
    },
    async searchTableList() {
      if (!this.dataInfo.dataSource) return;
      const requestId = ++this.tableSearchRequestId;
      this.styleLoading = true;
      this.loadingText = "正在搜索全部表，请稍候…";
      try {
        const res = await tableServer.getTableList({
          dataSourceId: this.dataInfo.dataSource.id,
          dataSourceName: this.dataInfo.dataSource.alias,
          databaseType: this.dataInfo.dataSource.type,
          schemaName: this.schema,
          refresh: false,
          requestType: 2,
          pageNo: 1,
          pageSize: 200,
          searchKey: this.filterName || undefined,
          isRefreshCache: false,
        });
        if (requestId !== this.tableSearchRequestId) return;
        if (!res.success) {
          this.$message.error(res.errorMessage || "搜索表失败");
          return;
        }
        const data = res.data.data || [];
        this.dataTreeList = data.map(item => {
          const details = item.tableDetails || {};
          return {
            name: item.name,
            comment: item.comment || details.comment,
            pinned: item.pinned,
            tableDetails: {
              tableSpace: details.tableSpace,
              valid: details.valid,
              path: details.path,
              type: details.type,
            },
          };
        });
        this.detailDataSource = data.map(item => {
          const details = item.tableDetails || {};
          return {
            name: item.name,
            schema: details.schema,
            tableSpace: details.tableSpace,
            comment: item.comment || details.comment,
            numRows: details.numRows,
            created: details.created,
            lastDDL: details.lastDDL,
            type: details.type,
          };
        });
        this.tableListPageNo = 1;
        this.tableListTotal = Number(res.data.total) || data.length;
        this.hasMoreTables = data.length < this.tableListTotal;
        this.$emit("queryDetailTable", this.detailDataSource, this.tableListTotal);
      } catch (e) {
        if (requestId === this.tableSearchRequestId) {
          this.$message.error("搜索表失败，请稍后重试");
        }
      } finally {
        if (requestId === this.tableSearchRequestId) this.styleLoading = false;
      }
    },
    visibleChange(val) {
      if (val) {
        this.getDataBaseTreeData();
      }
    },
    cloneComponent(val) {
      this.detailsObjs = JSON.parse(JSON.stringify(val));
      this.detailsObjs.name =
        `"` + this.schema + '"."' + this.detailsObjs.name + `"`;
    },
    onEnd(val) {
      this.detailsObj = JSON.parse(JSON.stringify(this.detailsObjs));
      this.$emit("setDetailsObj", this.detailsObj);
    },
    toggleTooltipVisible() {
      this.buttonVisible = !this.buttonVisible;
    },
  },
};
</script>

<template>
  <div class="database_tree">
    <div class="database_form">
      <div class="database_title">
        <el-tooltip
          class="item"
          effect="dark"
          manual
          ref="buttonTooltip"
          v-model="buttonVisible"
          :content="dataBaseInfo.url"
          placement="top-start"
        >
          <span @click="toggleTooltipVisible()">{{
            dataBaseInfo.url || ""
          }}</span>
        </el-tooltip>
      </div>
      <div class="database_form_item">
        <el-select
          size="mini"
          filterable
          v-model="schema"
          style="width: 240px"
          ref="selectAAA"
          @change="getTableDataList"
          @visible-change="visibleChange"
          placeholder="请选择"
        >
          <el-option
            v-for="(item, index) in dataSourceListData"
            :key="index"
            :label="item.name"
            :value="item.name"
          ></el-option>
        </el-select>
      </div>
      <div class="database_form_item">
        <el-select
          v-model="type"
          style="width: 240px"
          @change="checkTypeFn"
          size="mini"
        >
          <el-option
            v-for="dict in dict.type.model_type"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
            :disabled="isQuery && dict.label !== '表' && dict.label !== '视图'"
          />
        </el-select>
      </div>
      <el-input
        size="mini"
        v-model="filterName"
        placeholder="搜索名称"
        show-word-limit
        maxlength="50"
        @input="handleInput"
      ></el-input>
    </div>
    <div style="flex: 1; min-height: 0; position: relative">
      <div class="database_titles">
        <el-checkbox
          v-if="type == 'tables' && !isQuery"
          class="select-all"
          :value="isCurrentFilterAllSelected"
          :indeterminate="isCurrentFilterPartiallySelected"
          :disabled="filteredTableNames.length === 0"
          @click.native.stop
          @change="toggleFilteredTableSelection"
        >{{ isCurrentFilterAllSelected ? '取消全选' : '全选' }}</el-checkbox>
        <div
          class="downLoad"
          @click="viewImport"
          v-show="this.type == 'views' && !isQuery"
          title="视图导入"
        >
          <i class="el-icon-upload2"></i>
        </div>
        <img
          src="@/assets/main/15-1.png"
          alt
          title="全部视图编译"
          @click="viewExecute('all')"
          v-if="this.type == 'views' && !isQuery"
        />
        <img
          src="@/assets/main/18-1.png"
          alt
          title="批量视图编译"
          @click="viewExecute('batch')"
          v-if="this.type == 'views' && !isQuery"
        />
        <img
          @click="addSideBar"
          :title="
            this.type == 'tables'
              ? '新增表'
              : this.type == 'views'
              ? '新增视图'
              : this.type == 'users'
              ? '新增用户'
              : this.type == 'tableSpace'
              ? '新增表空间'
              : ''
          "
          src="@/assets/main/1-1加-hover.png"
          alt
          v-if="!isQuery"
        />
        <img
          @click="deleteData"
          :title="
            this.type == 'tables'
              ? `批量删除表（已选${seleCtrlKet.length}张）`
              : this.type == 'views'
              ? '删除视图'
              : this.type == 'users'
              ? '删除用户'
              : this.type == 'tableSpace'
              ? '删除表空间'
              : ''
          "
          src="@/assets/main/1-1减-hover.png"
          alt
          v-if="!isQuery"
        />
        <span v-if="type == 'tables' && !isQuery" class="selected-count">
          已选：{{ seleCtrlKet.length }}
        </span>
        <img
          @click="
            () => {
              refresh = true;
              this.getTableDataList(this.schema, true);
            }
          "
          title="刷新"
          src="@/assets/main/1-1刷新.png"
          alt
        />
      </div>
      <div class="isopen" @click="isopenFn">
        <img src="@/assets/main/1-1-sub侧边.png" alt />
      </div>
      <el-scrollbar class="object-list-scrollbar" style="height: calc(100% - 40px)" v-loading="styleLoading" :element-loading-text="loadingText">
        <draggable
          class="components-draggable"
          :list="filterDataTreeList"
          :group="{ name: 'componentsGroup', pull: 'clone', put: false }"
          :clone="cloneComponent"
          draggable=".dargitemWrap"
          :sort="false"
          @end="onEnd"
          v-if="isQuery"
        >
          <div
            v-for="(item, index) in filterDataTreeList"
            :key="item.name || item.tableDetails.tableSpace || index"
            @click.stop="handelClickItem(item, index, $event)"
            @contextmenu="creatRightMenu(item, $event)"
          class="dargitemWrap virtual-list-item"
            :class="{ active: heightIndexs.includes(index) }"
          >
            <span
              class="table_img"
              v-show="type == 'views' && item.checkStyle === 1"
            >
              <i class="el-icon-check" style="color: green"></i>
            </span>
            <span
              class="table_img"
              v-show="type == 'views' && item.checkStyle === 2"
            >
              <i class="el-icon-close" style="color: red"></i>
            </span>
            <span class="table_img" v-if="type == 'tables'">
              <img
                v-if="index == activeClass"
                src="@/assets/main/tableIconhover.png"
                alt
              />
              <img v-else src="@/assets/main/tableIcon.png" alt />
            </span>
            <span class="table_img" v-if="type == 'views'">
              <img
                v-if="heightIndexs.includes(index)"
                src="@/assets/main/subico02-hover.png"
                alt
              />
              <img v-else src="@/assets/main/subico02.png" alt />
            </span>
            <span
              v-if="type == 'views'"
              class="view-type-badge"
              :class="{ materialized: isMaterializedView(item) }"
            >{{ isMaterializedView(item) ? '物化' : '普通' }}</span>
            <span class="table_img" v-if="type == 'users'">
              <img
                v-if="index == activeClass"
                src="@/assets/main/1-sub-ico04-hover.png"
                alt
              />
              <img v-else src="@/assets/main/1-sub-ico04.png" alt />
            </span>
            <span class="table_img" v-if="type == 'tableSpace'">
              <img
                v-if="index == activeClass"
                src="@/assets/main/1-sub-ico03-hover.png"
                alt
              />
              <img v-else src="@/assets/main/1-sub-ico03.png" alt />
            </span>
            <span class="table_name">
              {{ item.name || item.tableDetails.tableSpace }}
            </span>
            <span
              v-if="item.comment && (type == 'tables' || type == 'views')"
              class="table_comment"
              :title="item.comment"
            >
              {{ item.comment }}
            </span>
          </div>
        </draggable>
        <div
          v-for="(item, index) in filterDataTreeList"
          :key="item.name || item.tableDetails.tableSpace || index"
          @click.stop="type != 'tables' && handelClickItem(item, index, $event)"
          @contextmenu="creatRightMenu(item, $event)"
          class="itemWrap virtual-list-item"
          :class="{ active: heightIndexs.includes(index), selected: type == 'tables' && seleCtrlKet.includes(item.name) }"
          v-else
        >
          <el-checkbox
            v-if="type == 'tables'"
            class="table-selection"
            :value="seleCtrlKet.includes(item.name)"
            :aria-label="`选择表 ${item.name}`"
            @click.native.stop
            @change="toggleTableSelection(item)"
          />
          <span
            class="table_img"
            v-show="type == 'views' && item.checkStyle === 1"
          >
            <i class="el-icon-check" style="color: green"></i>
          </span>
          <span
            class="table_img"
            v-show="type == 'views' && item.checkStyle === 2"
          >
            <i class="el-icon-close" style="color: red"></i>
          </span>
          <span class="table_img" v-if="type == 'tables'">
            <img
              v-if="index == activeClass"
              src="@/assets/main/tableIconhover.png"
              alt
            />
            <img v-else src="@/assets/main/tableIcon.png" alt />
          </span>
          <span class="table_img" v-if="type == 'views'">
            <img
              v-if="heightIndexs.includes(index)"
              src="@/assets/main/subico02-hover.png"
              alt
            />
            <img v-else src="@/assets/main/subico02.png" alt />
          </span>
          <span
            v-if="type == 'views'"
            class="view-type-badge"
            :class="{ materialized: isMaterializedView(item) }"
          >{{ isMaterializedView(item) ? '物化' : '普通' }}</span>
          <span class="table_img" v-if="type == 'users'">
            <img
              v-if="index == activeClass"
              src="@/assets/main/1-sub-ico04-hover.png"
              alt
            />
            <img v-else src="@/assets/main/1-sub-ico04.png" alt />
          </span>
          <span class="table_img" v-if="type == 'tableSpace'">
            <img
              v-if="index == activeClass"
              src="@/assets/main/1-sub-ico03-hover.png"
              alt
            />
            <img v-else src="@/assets/main/1-sub-ico03.png" alt />
          </span>
          <span class="table_name" :class="{ 'table-name-clickable': type == 'tables' }" @click.stop="type == 'tables' && handelClickItem(item, index, $event)">
            {{ item.name || item.tableDetails.tableSpace }}
          </span>
          <span
            v-if="item.comment && (type == 'tables' || type == 'views')"
            class="table_comment"
            :title="item.comment"
          >
            {{ item.comment }}
          </span>
        </div>
        <div v-if="type == 'tables' && hasMoreTables" class="load-more-tables" @click="loadMoreTables">
          加载更多表（已加载 {{ dataTreeList.length }} / {{ tableListTotal }}）
        </div>
      </el-scrollbar>
    </div>

    <ul
      class="contextMenuBox"
      v-show="contextMenuVisible"
      :style="{ left: contextMenu_left + 'px', top: contextMenu_top + 'px' }"
    >
      <li
        v-for="(item,index) in menuList"
        :key="item.name"
        class="menu_item"
        :class="{ 'has-children': item.children }"
        @click.stop="handleClick(item,index)"
      >
        {{ item.name }}
        <ul v-if="item.children && openIndex == index" class="submenu">
           <el-select
            size="mini"
            filterable
            v-model="copyschemaName"
            placeholder="请选择"
            @change="changeCopy"
          >
            <el-option
              v-for="(item, index) in item.children"
              :key="index"
              :label="item.name"
              :value="item.name"
            ></el-option>
          </el-select>
          <!-- <li
            v-for="child in item.children"
            :key="child.name"
            class="menu_item"
            @click.stop="handleClick(child)"
          >
            {{ child.name }}
          </li> -->
        </ul>
      </li>
      <!-- <div>
        <el-button @click="routerSql">数据模板</el-button>
      </div>
      <div>
        <el-popover
          placement="bottom"
          title="模式名"
          width="200"
          trigger="click"
          ref="dataSourcePop"
        >
          <el-select
            size="mini"
            filterable
            v-model="copyschemaName"
            @visible-change="(val)=>handleCascaderChange(val,false)"
            placeholder="请选择"
          >
            <el-option
              v-for="(item, index) in dataSourceListData"
              :key="index"
              :label="item.name"
              :value="item.name"
            ></el-option>
          </el-select>
          <el-button slot="reference">复制表</el-button>
        </el-popover>
      </div>
      <div>
        <el-popover
          placement="bottom"
          title="模式名"
          width="200"
          trigger="click"
          ref="dataSourcePop"
        >
          <el-select
            size="mini"
            filterable
            v-model="copyschemaName"
            @visible-change="(val)=>handleCascaderChange(val,true)"
            placeholder="请选择"
          >
            <el-option
              v-for="(item, index) in dataSourceListData"
              :key="index"
              :label="item.name"
              :value="item.name"
            ></el-option>
          </el-select>
          <el-button slot="reference">复制表及数据</el-button>
        </el-popover>
        <el-button @click="copyTable(true)">复制表及数据</el-button> -->
      <!-- </div>
      <div>
        <el-button @click="newTableLable">新建窗口</el-button>
      </div> -->
    </ul>
    <CreateOption ref="createOption" />
    <SqlPreview ref="sqlPreview" :title="DDLTitle" />
    <addTableSpaceVue
      :editDialogVisible="editDialogVisible"
      @editHandleCloseFn="editHandleCloseFn"
      @refshTableData="refshTableData"
      :dataBaseInfo="dataBaseInfo"
      :schema="schema"
      :getTableDataList="getTableDataList"
      :spaceInfo="spaceInfo"
      :saveType="1"
    ></addTableSpaceVue>
    <addTable
      :tableDialogVisibl="tableDialogVisibl"
      @tableCloseFn="tableCloseFn"
      :dataBaseInfo="dataBaseInfo"
      :schema="schema"
      :getTableDataList="getTableDataList"
    ></addTable>
    <addView
      :viewDialogVisible="viewDialogVisible"
      @viewCloseFn="viewCloseFn"
      :dataBaseInfo="dataBaseInfo"
      :schema="schema"
      :getTableDataList="getTableDataList"
    ></addView>
    <addUser
      :userDialogVisible="userDialogVisible"
      @userHandleCloseFn="userHandleCloseFn"
      :dataBaseInfo="dataBaseInfo"
      :schema="schema"
      :getTableDataList="getTableDataList"
      :getDataBaseTreeData="getDataBaseTreeData"
      :userInfo="userInfo"
    ></addUser>
    <ViewImport
      ref="viewRef"
      :dataInfo="dataInfo"
      :schema="schema"
      :getTableDataList="getTableDataList"
    ></ViewImport>
  </div>
</template>

<style scoped lang="scss">
.isopen {
  position: absolute;
  z-index: 99;
  right: 0px;
  top: 30%;
  cursor: pointer;
}
.contextMenuBox,
.submenu {
  margin: 0;
  background: #fff;
  z-index: 99;
  min-width: 120px;
  max-height: 400px;
  position: absolute;
  list-style-type: none;
  padding: 5px 0;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 400;
  color: #333;
  border: 1px solid #eee;
  box-shadow: 2px 2px 3px 0 rgba(0, 0, 0, 0.3);
  .menu_item {
    padding: 5px 15px;
    cursor: pointer;
    white-space: nowrap;
    position: relative;
    text-align: center;
    &:hover {
      background: #eee;
    }
  }
  .has-children {
    .submenu {
      top: 0;
      left: 100%;
       overflow: auto;
      // display: none;
    }
    // &:hover {
    //   .submenu {
    //     display: block;
    //     overflow: auto;
    //   }
    // }
  }
  // .el-button {
  //   width: 100%;
  //   margin: 0;
  //   padding: 7px 16px;
  //   cursor: pointer;
  //   border: none;
  //   &:hover {
  //     background: #eee;
  //   }
  // }
}
.itemWrap {
  width: max-content;
  min-width: 100%;
  height: 25px;
  display: flex;
  align-items: center;
  font-size: 13px;
  cursor: pointer;
  content-visibility: auto;
  contain-intrinsic-size: 25px;
  .table_name {
    padding-left: 5px;
    font-size: 12px;
    flex-shrink: 0;
    white-space: nowrap;
  }
  .table_comment {
    margin-left: 6px;
    color: #9aa4b8;
    font-size: 11px;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
    min-width: 0;
  }
  .table_img {
    padding-left: 10px;
  }
}
.dargitemWrap {
  width: max-content;
  min-width: 100%;
  height: 25px;
  display: flex;
  align-items: center;
  font-size: 13px;
  cursor: move;
  content-visibility: auto;
  contain-intrinsic-size: 25px;
  .table_name {
    padding-left: 5px;
    font-size: 12px;
    flex-shrink: 0;
    white-space: nowrap;
  }
  .table_comment {
    margin-left: 6px;
    color: #9aa4b8;
    font-size: 11px;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
    min-width: 0;
  }
  .table_img {
    padding-left: 10px;
  }
  &:hover {
    border: 1px dashed #787be8;
    color: #787be8;
  }
}
.active {
  color: #006fff;
  background: #f0f5ff;
  border-left: 3px solid #006fff;
  box-sizing: border-box;
  .table_img {
    padding-left: 7px;
  }
}
.database_tree {
  height: 100%;
  background: #fff;
  display: flex;
  flex-flow: column;
  flex-shrink: 0;
  margin-right: 5px;

  .header {
    position: relative;
    display: flex;
    align-items: center;
    padding: 14px 0 0 14px;

    img {
      width: 48px;
      height: 21px;
    }

    h3 {
      font-size: 18px;
      font-weight: bold;
      color: #3469fb;
      margin-left: 5px;
    }

    span {
      position: absolute;
      right: 27px;
      bottom: -14px;
      font-size: 12px;
      color: #3f70fb;
    }
  }

  .database_title {
    margin-top: 5px;
    width: 100%;
    display: flex;
    align-items: center;
    font-size: 12px;
    color: #4a5565;
    display: flex;
    justify-content: space-between;
    padding-right: 20px;
    border: 1px solid #dcdfe6;
    height: 29px;
    border-radius: 4px;
    background: #f6f8fc;
    > span {
      margin-left: 15px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
  }
  .database_titles {
    display: flex;
    width: 100%;
    height: 30px;
    align-items: center;
    margin-top: 8px;
    justify-content: right;
    padding: 0px 20px 10px 20px;
    box-sizing: border-box;
    gap: 8px;
    cursor: pointer;
    > img {
      flex-shrink: 0;
    }
    .selected-count {
      color: #6b7280;
      font-size: 12px;
      white-space: nowrap;
      flex: 0 0 auto;
    }
    .select-all {
      margin-right: auto;
      font-size: 12px;
      flex-shrink: 0;
    }
    // justify-content: space-around;?
    // font-size: 16px;
    // color: #4a5565;
    // font-weight: bold;
    // display: flex;
    // flex: 1;
    // padding-right: 20px;
    // & > i {
    //   cursor: pointer;
    // }
  }

  .database_form {
    padding: 0px 20px 0;

    .database_form_item {
      display: flex;
      align-items: center;
      margin-top: 2px;
      & > span {
        width: 48px;
        font-size: 12px;
        flex-shrink: 0;
      }
    }
  }
}
.downLoad {
  width: 18px;
  height: 18px;
  border: 2px solid #006fff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #006fff;
  margin-right: 10px;
  margin-top: 2px;
  > i {
    font-weight: bold;
  }
}
.table-selection {
  margin-left: 8px;
  flex-shrink: 0;
}
.table-name-clickable {
  cursor: pointer;
}
.view-type-badge {
  flex: 0 0 auto;
  margin-left: 5px;
  padding: 0 4px;
  border: 1px solid #b8c6dc;
  border-radius: 3px;
  color: #68728c;
  font-size: 10px;
  line-height: 16px;
  background: #f6f8fc;
}
.view-type-badge.materialized {
  border-color: #9dbcf5;
  color: #006fff;
  background: #f0f5ff;
}
::v-deep .object-list-scrollbar .el-scrollbar__view {
  min-width: max-content;
}
.selected {
  background: #f0f5ff;
}
.load-more-tables {
  padding: 10px 0;
  color: #006fff;
  font-size: 12px;
  text-align: center;
  cursor: pointer;
}
</style>
