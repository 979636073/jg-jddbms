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
      resourceFilter: "",
      resourceTreeLoading: false,
      pendingConnectedDataSource: null,
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
      context_current_node: null,
      copyTableDialogVisible: false,
      copyTableWithData: false,
      copySchemaOptionsLoading: false,
      treeTableSelections: {},
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
    this.resourceTreeReady = this.initializeResourceTree();
    this.$EventBus.$on("refreshRightTable", (page) => {
      this.refreshRightTable(page);
    });
    this.$EventBus.$on("refreshDataSourceTree", this.initializeResourceTree);
    this.$EventBus.$on("dataSourceConnected", this.handleDataSourceConnected);
  },
  destroyed() {
    window.removeEventListener("beforeunload", this.beforeunload);
    window.removeEventListener("keydown", this.handelKeyDown);
  },
  //解绑事件
  beforeDestroy() {
    this.$EventBus.$off("refreshRightTable");
    this.$EventBus.$off("refreshDataSourceTree", this.initializeResourceTree);
    this.$EventBus.$off("dataSourceConnected", this.handleDataSourceConnected);
    clearTimeout(this.tableSearchTimer);
  },
  methods: {
    async loadRouteObject(route) {
      const { query, params } = route;
      if (!query.tableName || this.$store.state.workspaceData.dataCurrentData.get(params.id)?.uniqueData) return;
      await this.resourceTreeReady;
      const dataSource = this.databaseTree.find(item => String(item.dataSource.id) === String(query.dataSourceId))?.dataSource;
      if (!dataSource || !this.isDataSourceConnected(dataSource.id)) return;
      const types = { table: "tables", view: "views", user: "users", tableSpace: "tableSpace" };
      await this.handelClickItem({ name: query.tableName, tableDetails: { type: query.viewType, path: query.path } }, 0, {}, {
        pageId: params.id, dataSource, databaseName: query.databaseName || "", schema: query.schemaName || query.databaseName || "", type: types[query.objectType] || "tables",
      });
    },
    async initializeResourceTree() {
      this.resourceTreeLoading = true;
      try {
        const res = await connectionServer.getList({
          pageNo: 1,
          pageSize: 10000,
          refresh: true,
          searchKey: "",
        });
        if (!res?.success) throw new Error(res?.errorMessage || "数据源列表加载失败");
        const list = Array.isArray(res.data?.data) ? res.data.data : [];
        this.$store.commit("SET_DATASOURCELIST", list);
        this.databaseTree = list.map((dataSource) => this.createDataSourceNode(dataSource));
        if (!list.length) {
          this.$store.commit("workspaceData/SET_DATA_BASE_INFO", {});
          this.$store.commit("workspaceData/SET_DATA_INFO", {});
          this.$store.commit("SET_CURRENTCONNECTIONDETAILS", null);
          sessionStorage.removeItem("createData");
        }

      } catch (e) {
        this.databaseTree = [];
        this.$message.error("数据源列表加载失败，请稍后重试");
      } finally {
        this.resourceTreeLoading = false;
        if (this.pendingConnectedDataSource) {
          const dataSource = this.pendingConnectedDataSource;
          this.pendingConnectedDataSource = null;
          this.handleDataSourceConnected(dataSource);
        }
      }
    },
    createDataSourceNode(dataSource) {
      return {
        key: `datasource-${dataSource.id}`,
        label: this.dataSourceDisplayName(dataSource),
        treeNodeType: "datasource",
        leaf: !this.isDataSourceConnected(dataSource.id),
        dataSource,
        extraParams: this.createExtraParams(dataSource),
      };
    },
    dataSourceDisplayName(dataSource) {
      const alias = String(dataSource?.alias || "").trim();
      if (alias && !alias.startsWith("@")) return alias;
      return dataSource?.user || dataSource?.userName || alias || `数据源 ${dataSource?.id}`;
    },
    isDataSourceConnected(id) {
      return Boolean(this.$store.state.workspace.connectedDataSourceIds[id]);
    },
    async handleDataSourceConnected(dataSource) {
      if (this.resourceTreeLoading) {
        this.pendingConnectedDataSource = dataSource;
        return;
      }
      await this.$nextTick();
      const root = this.$refs.resourceTree?.getNode(`datasource-${dataSource.id}`);
      if (!root) return;
      root.data.dataSource = dataSource;
      root.data.leaf = false;
      root.isLeafByUser = false;
      root.loaded = false;
      root.updateLeafState();
      if (this._inactive) return;
      this.activateDataSourceContext(dataSource);
      await this.expandResourceNode(root);
      const hierarchy = root.childNodes.find((node) => ["schemaCollection", "databaseCollection"].includes(node.data.treeNodeType));
      if (hierarchy) await this.expandResourceNode(hierarchy);
    },
    createExtraParams(dataSource, context = {}) {
      return {
        dataSourceId: dataSource.id,
        dataSourceName: dataSource.alias,
        databaseType: dataSource.type,
        databaseName: context.databaseName || "",
        schemaName: context.schemaName || "",
      };
    },
    createHierarchyNode(dataSource) {
      const isDatabase = Boolean(dataSource.supportDatabase);
      return {
        key: `datasource-${dataSource.id}-${isDatabase ? "databases" : "schemas"}`,
        label: isDatabase ? "数据库" : "模式",
        treeNodeType: isDatabase ? "databaseCollection" : "schemaCollection",
        leaf: false,
        dataSource,
        extraParams: this.createExtraParams(dataSource),
      };
    },
    createObjectGroups(dataSource, context = {}) {
      const extraParams = this.createExtraParams(dataSource, context);
      const prefix = [
        dataSource.id,
        extraParams.databaseName,
        extraParams.schemaName,
      ].join("-");
      return [
        ["tables", "数据表"],
        ["views", "视图"],
        ["functions", "函数"],
        ["procedures", "存储过程"],
        ["triggers", "触发器"],
      ].map(([groupType, label]) => ({
        key: `${prefix}-${groupType}`,
        label,
        groupType,
        treeNodeType: "objectGroup",
        leaf: false,
        dataSource,
        extraParams,
      }));
    },
    createAdministrationGroups(dataSource) {
      return [
        ["users", "用户"],
        ["tableSpace", "表空间"],
      ].map(([groupType, label]) => ({
        key: `datasource-${dataSource.id}-${groupType}`,
        label,
        groupType,
        treeNodeType: "objectGroup",
        leaf: false,
        dataSource,
        extraParams: this.createExtraParams(dataSource, {
          schemaName: dataSource.user || dataSource.userName || "",
        }),
      }));
    },
    async loadResourceNode(node, resolve) {
      const data = node.data || {};
      if (node.level === 0) {
        resolve(this.databaseTree);
        return;
      }
      if (data.treeNodeType === "datasource") {
        if (!this.isDataSourceConnected(data.dataSource.id)) {
          resolve([]);
          this.openDataSourceManager(data.dataSource, "connect");
          return;
        }
        resolve([
          this.createHierarchyNode(data.dataSource),
          ...this.createAdministrationGroups(data.dataSource),
        ]);
        return;
      }
      if (data.treeNodeType === "schemaCollection" || data.treeNodeType === "databaseCollection") {
        await this.loadResourceContainers(data, resolve);
        return;
      }
      if (data.treeNodeType === "database") {
        if (data.dataSource.supportSchema) {
          await this.loadResourceSchemas(data, resolve);
        } else {
          resolve(this.createObjectGroups(data.dataSource, data.extraParams));
        }
        return;
      }
      if (data.treeNodeType === "schema") {
        resolve(this.createObjectGroups(data.dataSource, data.extraParams));
        return;
      }
      if (data.treeNodeType === "objectGroup") {
        await this.loadResourceObjects(data, resolve);
        return;
      }
      resolve([]);
    },
    async loadResourceContainers(data, resolve) {
      const isDatabase = data.treeNodeType === "databaseCollection";
      const api = isDatabase ? connectionServer.getDatabaseList : connectionServer.getSchemaList;
      try {
        const res = await api({
          dataSourceId: data.dataSource.id,
          dataSourceName: data.dataSource.alias,
          refresh: false,
        });
        const items = Array.isArray(res.data) ? res.data : [];
        resolve(items.map((item) => {
          const name = item.name;
          const context = isDatabase ? { databaseName: name } : { schemaName: name };
          return {
            key: `${data.dataSource.id}-${isDatabase ? "database" : "schema"}-${name}`,
            label: name,
            treeNodeType: isDatabase ? "database" : "schema",
            leaf: false,
            dataSource: data.dataSource,
            extraParams: this.createExtraParams(data.dataSource, context),
          };
        }));
      } catch (e) {
        resolve([]);
        this.$message.error(`${data.label}加载失败`);
      }
    },
    async loadResourceSchemas(data, resolve) {
      try {
        const res = await connectionServer.getSchemaList({
          dataSourceId: data.dataSource.id,
          dataSourceName: data.dataSource.alias,
          databaseName: data.extraParams.databaseName,
          refresh: false,
        });
        const items = Array.isArray(res.data) ? res.data : [];
        resolve(items.map((item) => ({
          key: `${data.dataSource.id}-${data.extraParams.databaseName}-schema-${item.name}`,
          label: item.name,
          treeNodeType: "schema",
          leaf: false,
          dataSource: data.dataSource,
          extraParams: this.createExtraParams(data.dataSource, {
            databaseName: data.extraParams.databaseName,
            schemaName: item.name,
          }),
        })));
      } catch (e) {
        resolve([]);
        this.$message.error("模式加载失败");
      }
    },
    async loadResourceObjects(data, resolve) {
      const params = {
        ...data.extraParams,
        refresh: false,
        requestType: data.groupType === "tables" || data.groupType === "views" ? 2 : 1,
        pageNo: 1,
        pageSize: data.groupType === "tables" ? 200 : 1000,
      };
      try {
        let res;
        if (data.groupType === "tables") res = await tableServer.getTableList(params);
        else if (data.groupType === "views") res = await viewServer.getViewList(params);
        else if (data.groupType === "functions") res = await sqlServer.getFunctionList(params);
        else if (data.groupType === "procedures") res = await sqlServer.getProcedureList(params);
        else if (data.groupType === "triggers") res = await sqlServer.getTriggerList(params);
        else if (data.groupType === "users") res = await userServer.getUserList(params);
        else if (data.groupType === "tableSpace") res = await tableSpaceServer.getTableSpaceList(params);
        const items = Array.isArray(res?.data?.data) ? res.data.data : [];
        resolve(items.map((item, index) => this.createObjectNode(data, item, index)));
      } catch (e) {
        resolve([]);
        this.$message.error(`${data.label}加载失败`);
      }
    },
    expandResourceNode(node) {
      if (!node || node.isLeaf) return Promise.resolve(node);
      if (node.expanded && node.loaded) return Promise.resolve(node);
      return new Promise((resolve) => {
        node.expand(() => this.$nextTick(() => resolve(node)), true);
      });
    },
    createObjectNode(group, item, index) {
      const typeMap = {
        tables: "table",
        views: "view",
        functions: "function",
        procedures: "processdure",
        triggers: "trigger",
        users: "user",
        tableSpace: "tableSpace",
      };
      const name = item.name
        || item.functionName
        || item.procedureName
        || item.triggerName
        || item.userName
        || item.tableDetails?.tableSpace
        || `对象 ${index + 1}`;
      const extraParams = {
        ...group.extraParams,
        tableName: name,
        functionName: item.functionName,
        procedureName: item.procedureName,
        triggerName: item.triggerName,
        userName: item.userName || item.name,
        viewType: item.tableDetails?.type,
      };
      return {
        key: `${group.key}-${name}`,
        label: name,
        treeNodeType: typeMap[group.groupType],
        groupType: group.groupType,
        groupKey: group.key,
        leaf: true,
        pinned: item.pinned,
        comment: item.comment,
        tableDetails: item.tableDetails || {},
        viewType: item.tableDetails?.type,
        dataSource: group.dataSource,
        extraParams,
      };
    },
    activateDataSourceContext(dataSource, context = {}, preserveSelection = false) {
      if (!dataSource) return;
      const schemaName = context.schemaName || dataSource.user || dataSource.userName || "";
      const previousDataSourceId = this.dataInfo?.dataSource?.id;
      const contextChanged = String(previousDataSourceId || "") !== String(dataSource.id)
        || (schemaName && this.schema && schemaName !== this.schema);
      if (contextChanged && !preserveSelection) {
        this.activeClass = -1;
        this.heightIndexs = [];
        this.nameTbale = "";
        this.seleCtrlKet = [];
      }
      const dataInfo = {
        dateSourceId: dataSource.id,
        dateSourceName: dataSource.alias,
        userName: schemaName,
        databaseName: context.databaseName || "",
        dataSource,
      };
      this.schema = schemaName;
      this.dataSourceId = dataSource.id;
      this.$store.commit("workspaceData/SET_DATA_BASE_INFO", dataSource);
      this.$store.commit("workspaceData/SET_DATA_INFO", dataInfo);
      this.$store.commit("SET_CURRENTCONNECTIONDETAILS", dataSource);
      sessionStorage.setItem("createData", JSON.stringify(dataInfo));
      document.title = "数据库管理软件";
    },
    async handleResourceNodeClick(data, node) {
      const dataSource = data.dataSource;
      if (data.treeNodeType === "datasource" && !this.isDataSourceConnected(dataSource.id)) {
        this.openDataSourceManager(dataSource, "connect");
        return;
      }
      if (node && !node.isLeaf) {
        if (node.expanded) node.collapse();
        else node.expand();
      }
      if (dataSource) {
        this.activateDataSourceContext(dataSource, data.extraParams || {});
      }
      if (["datasource", "schemaCollection", "databaseCollection"].includes(data.treeNodeType)) {
        return;
      }
      if (data.treeNodeType === "schema" || data.treeNodeType === "database") {
        this.schema = data.extraParams.schemaName || data.extraParams.databaseName || "";
        return;
      }
      if (data.treeNodeType === "objectGroup") {
        this.type = data.groupType;
        this.schema = data.extraParams.schemaName || data.extraParams.databaseName || "";
        this.$emit("isType", this.type, this.schema, this.activeClass);
        if (["tables", "views", "users", "tableSpace"].includes(this.type)) {
          await this.$nextTick();
          this.getTableDataList(this.schema);
        }
        return;
      }
      this.type = data.groupType;
      this.schema = data.extraParams.schemaName || data.extraParams.databaseName || "";
      await this.$nextTick();
      if (data.treeNodeType === "table") {
        this.openTableInWindow({
          name: data.label,
          comment: data.comment,
          tableDetails: data.tableDetails || {},
        }, 0, {});
      } else if (["view", "user", "tableSpace"].includes(data.treeNodeType)) {
        this.openTableInWindow({
          name: data.label,
          comment: data.comment,
          tableDetails: data.tableDetails || {},
          viewType: data.viewType,
        }, 0, {}, data.treeNodeType);
      } else if (data.treeNodeType === "function") {
        this.openFunction(data);
      } else if (data.treeNodeType === "processdure") {
        this.openProcedure(data);
      } else if (data.treeNodeType === "trigger") {
        this.openTrigger(data);
      }
    },
    handleResourceFilter(value) {
      this.$refs.resourceTree?.filter(value);
    },
    resourceFilterNode(value, data) {
      if (!value) return true;
      return String(data.label || "").toLowerCase().includes(value.toLowerCase());
    },
    refreshResourceNode(node) {
      if (!node) return;
      if (node.data.treeNodeType === "datasource" && !this.isDataSourceConnected(node.data.dataSource.id)) {
        this.openDataSourceManager(node.data.dataSource, "connect");
        return;
      }
      node.loaded = false;
      if (node.expanded) node.loadData();
      else node.expand();
    },
    selectedTreeTableNames(groupKey) {
      return this.treeTableSelections[groupKey] || [];
    },
    isTreeTableSelected(data) {
      return this.selectedTreeTableNames(data.groupKey).includes(data.label);
    },
    toggleTreeTableSelection(data, selected) {
      const names = this.selectedTreeTableNames(data.groupKey);
      const next = selected
        ? Array.from(new Set([...names, data.label]))
        : names.filter((name) => name !== data.label);
      this.$set(this.treeTableSelections, data.groupKey, next);
    },
    loadedTreeTableNames(node) {
      return (node?.childNodes || [])
        .filter((item) => item.data?.treeNodeType === "table")
        .map((item) => item.data.label);
    },
    isAllLoadedTreeTablesSelected(data, node) {
      const loaded = this.loadedTreeTableNames(node);
      const selected = this.selectedTreeTableNames(data.key);
      return loaded.length > 0 && loaded.every((name) => selected.includes(name));
    },
    toggleAllLoadedTreeTables(data, node) {
      const loaded = this.loadedTreeTableNames(node);
      if (!loaded.length) {
        this.$message.warning("请先展开并加载数据表");
        return;
      }
      const selected = this.selectedTreeTableNames(data.key);
      const next = this.isAllLoadedTreeTablesSelected(data, node)
        ? selected.filter((name) => !loaded.includes(name))
        : Array.from(new Set([...selected, ...loaded]));
      this.$set(this.treeTableSelections, data.key, next);
    },
    async deleteSelectedTreeTables(data, node) {
      const tableNames = this.selectedTreeTableNames(data.key);
      if (!tableNames.length) {
        this.$message.warning("请先勾选要删除的表");
        return;
      }
      try {
        await this.$confirm(
          `确定要删除已选的 ${tableNames.length} 张表吗？此操作不可恢复。`,
          "批量删除表",
          {
            confirmButtonText: "删除",
            cancelButtonText: "取消",
            type: "warning",
          }
        );
        const params = {
          dataSourceId: data.extraParams.dataSourceId,
          schemaName: data.extraParams.schemaName || data.extraParams.databaseName,
          tableNames,
        };
        if (tableNames.length > BATCH_DELETE_TASK_THRESHOLD) {
          const res = await tableServer.deleteTableDataTask(params);
          if (!res.success) throw new Error(res.errorMessage || "提交删除任务失败");
          this.$delete(this.treeTableSelections, data.key);
          this.$message.success("批量删除已转入任务中心，可继续操作");
          this.$EventBus.$emit("openTaskCenter");
          return;
        }
        const res = await tableServer.deleteTableData(params);
        if (!res.success) throw new Error(res.errorMessage || "删除失败");
        this.$delete(this.treeTableSelections, data.key);
        this.refreshResourceNode(node);
        this.$message.success("删除成功");
      } catch (error) {
        if (error !== "cancel" && error !== "close") {
          this.$message.error(error?.message || "删除表失败，请稍后重试");
        }
      }
    },
    openDataSourceManager(dataSource, mode) {
      this.$emit("manageDataSources", { id: dataSource?.id, mode: mode || (dataSource ? "edit" : "create") });
    },
    closeDataSourceFromTree(dataSource) {
      this.$confirm(`确定要关闭数据源“${this.dataSourceDisplayName(dataSource)}”的连接吗？`, "提示", {
        confirmButtonText: "确定", cancelButtonText: "取消", type: "warning",
      }).then(async () => {
        const res = await connectionServer.closeDataSource({ id: dataSource.id });
        if (!res.success) {
          this.$message.error(res.errorMessage || "关闭连接失败");
          return;
        }
        this.$store.commit("SET_DATASOURCE_CONNECTED", { id: dataSource.id, connected: false });
        if (String(this.dataInfo?.dataSource?.id) === String(dataSource.id)) {
          this.$store.commit("workspaceData/SET_DATA_BASE_INFO", {});
          this.$store.commit("workspaceData/SET_DATA_INFO", {});
          this.$store.commit("SET_CURRENTCONNECTIONDETAILS", null);
          sessionStorage.removeItem("createData");
        }
        this.$message.success("连接已关闭");
        this.$EventBus.$emit("refreshDataSourceTree");
      }).catch(() => {});
    },
    async createConsoleForDataSource(data) {
      if (!this.isDataSourceConnected(data.dataSource.id)) {
        this.openDataSourceManager(data.dataSource, "connect");
        return;
      }
      this.activateDataSourceContext(data.dataSource, data.extraParams || {});
      await this.$store.dispatch("workspaceData/createConsole", {
        pageId: this.pageId,
        ...data.extraParams,
      });
    },
    deleteDataSourceFromTree(dataSource) {
      this.$confirm(`确定要删除数据源“${this.dataSourceDisplayName(dataSource)}”吗？`, "提示", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      }).then(async () => {
        const res = await connectionServer.deleteDataSource(dataSource.id);
        if (res.success) {
          this.$store.commit("SET_DATASOURCE_CONNECTED", { id: dataSource.id, connected: false });
          this.$message.success("删除成功");
          this.initializeResourceTree();
        } else {
          this.$message.error(res.errorMessage || "删除失败");
        }
      }).catch(() => {});
    },
    async addObjectFromTree(data) {
      this.activateDataSourceContext(data.dataSource, data.extraParams || {});
      this.type = data.groupType;
      this.schema = data.extraParams.schemaName || data.extraParams.databaseName || "";
      await this.$nextTick();
      this.addSideBar();
    },
    async deleteObjectFromTree(data, node) {
      this.activateDataSourceContext(data.dataSource, data.extraParams || {});
      this.type = data.groupType;
      this.schema = data.extraParams.schemaName || data.extraParams.databaseName || "";
      if (["table", "view", "function", "processdure", "trigger"].includes(data.treeNodeType)) {
        this.deleteTable(data, node);
        return;
      }
      this.seleCtrlKet = [data.label];
      await this.$nextTick();
      this.deleteData();
    },
    resourceNodeIcon(data) {
      const icons = {
        datasource: "el-icon-connection",
        databaseCollection: "el-icon-coin",
        schemaCollection: "el-icon-folder",
        database: "el-icon-coin",
        schema: "el-icon-folder-opened",
        objectGroup: "el-icon-folder",
        table: "el-icon-tickets",
        view: "el-icon-view",
        function: "el-icon-set-up",
        processdure: "el-icon-cpu",
        trigger: "el-icon-bell",
        user: "el-icon-user",
        tableSpace: "el-icon-box",
      };
      return icons[data.treeNodeType] || "el-icon-document";
    },
    handleResourceContextMenu(event, data, node) {
      if (data.treeNodeType !== "table") return;
      this.activateDataSourceContext(data.dataSource, data.extraParams || {});
      this.type = "tables";
      this.schema = data.extraParams.schemaName || data.extraParams.databaseName || "";
      this.creatRightMenu({
        name: data.label,
        label: data.label,
        treeNodeType: data.treeNodeType,
        groupType: "tables",
        extraParams: data.extraParams,
        dataSource: data.dataSource,
        tableDetails: data.tableDetails || {},
      }, event, node);
    },
    executeResourceContextAction(action) {
      this.contextMenuVisible = false;
      const data = this.context_current_item;
      if (action === "open") this.handleResourceNodeClick(data);
      else if (action === "newWindow") this.newTableLable();
      else if (action === "ddl") this.viewDDL(data);
      else if (action === "template") this.routerSql();
      else if (action === "copyName") this.CopyName(data);
      else if (action === "copyTable") this.openCopyTableDialog(false);
      else if (action === "copyTableWithData") this.openCopyTableDialog(true);
      else if (action === "delete") this.deleteTable(data, this.context_current_node);
    },
    async openCopyTableDialog(withData) {
      this.copyTableWithData = withData;
      this.copyschemaName = "";
      this.copyTableDialogVisible = true;
      this.copySchemaOptionsLoading = true;
      try {
        await this.getDataBaseTreeData();
      } finally {
        this.copySchemaOptionsLoading = false;
      }
    },
    confirmCopyTable() {
      if (!this.copyschemaName) {
        this.$message.warning("请选择目标模式");
        return;
      }
      this.copyTable(this.copyTableWithData);
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
      return connectionServer.getSchemaList(params).then((res) => {
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
        sql: uniqueData?.databaseType === "DM" && uniqueData?.dataType === "tables"
          ? `select t.*, t.rowid from "${uniqueData.schemaName}"."${uniqueData.tableName}" t`
          : `select * from ${uniqueData?.schemaName || uniqueData?.databaseName}.${uniqueData.tableName}`,
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
              sql: data.treeNodeType === "table" && extraParams.databaseType === "DM"
                ? `select t.*, t.rowid from "${extraParams.schemaName}"."${extraParams.tableName}" t`
                : `select * from ${extraParams?.schemaName || extraParams?.databaseName}.${extraParams.tableName}`,
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
            ...nodeData.extraParams,
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
      this.nameTbale = "";
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
        const routeSchema = this.$route.query.schemaName || this.$route.query.databaseName;
        if (
          this.type == "tables" &&
          this.$route.query.tableName &&
          (!routeSchema || routeSchema === this.schema)
        ) {
          const routeTableName = this.$route.query.tableName;
          let routeTableIndex = this.dataTreeList.findIndex(item => item.name === routeTableName);
          if (routeTableIndex === -1) {
            const targetRes = await tableServer.getTableList({
              ...params,
              pageNo: 1,
              pageSize: 200,
              searchKey: routeTableName,
              isRefreshCache: false,
            });
            if (requestId !== this.tableSearchRequestId) return;
            const targetItem = targetRes?.success
              ? (targetRes.data.data || []).find(item => item.name === routeTableName)
              : null;
            if (targetItem) {
              const details = targetItem.tableDetails || {};
              this.dataTreeList.unshift({
                name: targetItem.name,
                comment: targetItem.comment || details.comment,
                pinned: targetItem.pinned,
                tableDetails: {
                  tableSpace: details.tableSpace,
                  valid: details.valid,
                  path: details.path,
                  type: details.type,
                },
              });
              detailDataSource.unshift({
                name: targetItem.name,
                schema: details.schema,
                tableSpace: details.tableSpace,
                comment: targetItem.comment || details.comment,
                numRows: details.numRows,
                created: details.created,
                lastDDL: details.lastDDL,
                type: details.type,
              });
              routeTableIndex = 0;
            }
          }
          this.activeClass = routeTableIndex;
          this.heightIndexs = routeTableIndex === -1 ? [] : [routeTableIndex];
          this.nameTbale = routeTableName;
          this.scrollToActiveTable();
        }
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
        const results = res.data || [];
        this.filterDataTreeList.forEach((view) => {
          const result = results.find((item) => item.tableName === view.name);
          if (result) view.checkStyle = result.success ? 1 : 2;
        });
        const failures = results.filter((item) => !item.success);
        if (failures.length) {
          this.$message.warning(res.errorMessage || failures.map((item) => item.message).join("; "));
        } else if (!res.success) {
          this.$message.error(res.errorMessage || "视图编译失败");
        } else {
          this.$message.success(res.errorMessage || "视图编译成功");
        }
      }).catch(() => this.$message.error("视图编译失败"))
        .finally(() => { this.styleLoading = false; });
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
      const count = this.seleCtrlKet.length;
      const message = this.type == "tables"
        ? `确定要删除已选的 ${count} 张表吗？此操作不可恢复。`
        : this.type == "views"
        ? `确定要删除已选的 ${count} 个视图吗？普通视图和物化视图将按实际类型删除，此操作不可恢复。`
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
                this.seleCtrlKet = [];
                this.getTableDataList(this.schema, true);
                this.$message.success("删除成功");
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
              const items = res.data?.items || [];
              if (res.success) {
                this.getTableDataList(this.schema);
                this.$message.success("删除成功");
                this.getDataBaseTreeData();
              } else {
                if (items.some(item => item.status === 'SUCCESS')) {
                  this.getTableDataList(this.schema);
                  this.getDataBaseTreeData();
                }
                const failed = items.filter(item => item.status === 'FAILED');
                this.$message.error(failed.map(item => `${item.name}: ${item.message}`).join('; ') || res.errorMessage || '删除失败');
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
        const existingTableNames = new Set(this.dataTreeList.map(item => item.name));
        const newData = data.filter(item => !existingTableNames.has(item.name));
        this.dataTreeList = this.dataTreeList.concat(newData.map((item) => ({
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
        this.detailDataSource = this.detailDataSource.concat(newData.map((item) => {
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
      if (event.key === "Escape") this.contextMenuVisible = false;
    },
    openTableInWindow(items, index, event, objectType = "table") {
      if (!items?.name) return;
      const config = this.$store.state.workspaceData.dataCurrentData.get(this.pageId);
      const currentName = config ? config.uniqueData?.tableName : this.$route.query.tableName;
      const currentSchema = config ? config.uniqueData?.schemaName : this.$route.query.schemaName;
      const hasSelectedTable = Boolean(currentName) && (!currentSchema || currentSchema === this.schema);
      const query = {
        dataSourceId: this.dataInfo.dataSource.id,
        dataSourceName: this.dataInfo.dataSource.alias,
        schemaName: this.schema,
        tableName: items.name,
        objectType,
        viewType: items.tableDetails?.type,
        path: items.tableDetails?.path,
      };
      if (!hasSelectedTable && !this.$route.path.includes("/workspace/query/")) {
        this.$router.replace({
          path: this.$route.path,
          query,
        }).catch(() => {});
        this.handelClickItem(items, index, event);
        return;
      }
      this.$EventBus.$emit("openLookPage", query);
    },
    scrollToActiveTable() {
      if (this.activeClass < 0) return;
      this.$nextTick(() => {
        const scrollbar = this.$refs.objectListScrollbar;
        const wrap = scrollbar?.wrap || scrollbar?.$refs?.wrap;
        const activeItem = wrap?.querySelector(`[data-object-index="${this.activeClass}"]`);
        if (!wrap || !activeItem) return;
        const centerOffset = Math.max((wrap.clientHeight - activeItem.offsetHeight) / 2, 0);
        wrap.scrollTop = Math.max(activeItem.offsetTop - centerOffset, 0);
      });
    },
    // 切表事件
    async handelClickItem(items, index, event, target = null) {
      const dataSource = target?.dataSource || this.dataInfo.dataSource;
      const schema = target?.schema ?? this.schema;
      const objectGroup = target?.type || this.type;
      const pageId = target?.pageId || this.pageId;
      const databaseName = target?.databaseName ?? this.dataInfo.databaseName ?? dataSource.databaseName ?? "";
      this.contextMenuVisible = false;
      this.$refs.dataSourcePop?.doClose();
      console.log(this.dataInfo, "this.dataInfo");
      if (this.isQuery && !target) return;
      this.$emit("load", true, pageId);
      if (!target && objectGroup != "tables" && this.ctrlKey && event.ctrlKey) {
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
        if (!target) {
          this.heightIndexs = [index];
          if (objectGroup != "tables") {
            this.seleCtrlKet = [items.name || items.tableDetails.tableSpace];
          }
          this.nameTbale = items.name;
          this.activeClass = index;
        }
        let type = "";
        const materializedView = objectGroup == "views" && this.isMaterializedView(items);
        const objectName = items.name || items.tableDetails.tableSpace;
        const viewTitle = objectGroup == "tables" && schema
          ? `${schema}.${objectName}`
          : objectName;
        this.$store.dispatch("jdTagsView/changeView", {
          title: viewTitle,
          id: pageId,
        });
        this.$store.state.jdTagsView.titleMap.push({
          title: viewTitle,
          id: pageId,
        });
        if (objectGroup == "tables") {
          type = "editTable";
        } else if (objectGroup == "views") {
          type = materializedView ? "editTableData" : "editView";
        } else if (objectGroup == "users") {
          type = "editUser";
        } else if (objectGroup == "tableSpace") {
          type = "editTableSpace";
        }
        if (objectGroup == "tables" || objectGroup == "views") {
          let send = {
            dataSourceId: dataSource.id,
            databaseType: dataSource.type,
            databaseName: databaseName,
            hasNextPage: true,
            pageNo: 1,
            pageSize: 100,
            skipCount: false,
            schemaName: schema,
            sql: dataSource.type === "ORACLE" && objectGroup == "tables"
              ? `select t.*, t.rowid as "ROWID" from "${schema}"."${items.name}" t`
              : dataSource.type === "DM" && objectGroup == "tables"
                ? `select t.*, t.rowid from "${schema}"."${items.name}" t`
                : `select * from "${schema}"."${items.name}"`,
            tableName: items.name,
            total: 0,
            type: dataSource.type,
            isDataView: objectGroup == "views" ? false : true,
          };
          await sqlServer.executeSql(send).then((res) => {
            const result = res?.data?.[0];
            if (!res?.success || !result) {
              this.$message.error(res?.errorMessage || "打开对象失败");
              return;
            }
            if (result.success) {
              this.$store.dispatch("workspaceData/setDataCurrentData", {
                pageId: pageId,
                title: items.name,
                type: type,
                ...result,
                canEdit: materializedView ? false : result.canEdit,
                params: send,
                uniqueData: {
                  dataSourceId: dataSource.id,
                  dataSourceName: dataSource.alias,
                  databaseName: databaseName,
                  sqlInfo: null,
                  databaseType: dataSource.type,
                  schemaName: schema,
                  tableName: items.name,
                  viewType: items.tableDetails?.type,
                  isLoading: true,
                  dataType: objectGroup,
                },
              });
            } else {
              this.$message.error(result.message || "打开对象失败");
              this.$store.dispatch("workspaceData/setDataCurrentData", {
                pageId: pageId,
                params: {},
                title: items.name,
                type: type,
                uniqueData: {
                  dataSourceId: dataSource.id,
                  schemaName: schema,
                  tableName: items.name,
                  viewType: items.tableDetails?.type,
                  sqlInfo: null,
                },
              });
            }
            result.success
              ? (items.checkStyle = 1)
              : (items.checkStyle = 2);
            this.$emit("load", false, pageId);
          }).catch(() => {
            this.$message.error("打开对象失败，请稍后重试");
          }).finally(() => {
            this.$emit("load", false, pageId);
          });
        } else if (objectGroup == "users") {
          let send = {
            dataSourceId: dataSource.id,
            databaseType: dataSource.type,
            databaseName: databaseName,
            hasNextPage: true,
            pageNo: 1,
            pageSize: 200,
            schemaName: schema,
            tableName: items.name,
            userName: items.name,
            total: 0,
            type: dataSource.type,
          };
          userServer.getUserDetails(send).then((res) => {
            this.$emit("load", false, pageId);
            this.$store.dispatch("workspaceData/setDataCurrentData", {
              pageId: pageId,
              title: items.name,
              type: type,
              tableDetails: res.data.tableDetails,
              uniqueData: {
                dataSourceId: dataSource.id,
                databaseName: dataSource.alias,
                sqlInfo: null,
                databaseType: dataSource.type,
                userName: items.name,
              },
            });
          }).catch(() => {
            this.$message.error("打开用户失败，请稍后重试");
          }).finally(() => {
            this.$emit("load", false, pageId);
          });
        } else if (objectGroup == "tableSpace") {
          let send = {
            dataSourceId: dataSource.id,
            path: items.tableDetails.path,
          };
          tableSpaceServer.getTableSpaceDetails(send).then((res) => {
            this.$emit("load", false, pageId);
            this.$store.dispatch("workspaceData/setDataCurrentData", {
              pageId: pageId,
              title: items.name,
              type: type,
              // tableDetails: res.data.tableDetails,
              uniqueData: {
                dataSourceId: dataSource.id,
                databaseName: dataSource.alias,
                path: items.tableDetails.path,
              },
            });
          }).catch(() => {
            this.$message.error("打开表空间失败，请稍后重试");
          }).finally(() => {
            this.$emit("load", false, pageId);
          });
        } else {
          this.$store.dispatch("workspaceData/setDataCurrentData", {
            pageId: pageId,
            title: items.name,
            type: type,
            uniqueData: {
              dataSourceId: dataSource.id,
              dataSourceName: dataSource.alias,
              databaseName: databaseName,
              databaseType: dataSource.type,
              schemaName: schema,
              tableName: items.name,
            },
          });
          this.$emit("load", false, pageId);
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
    creatRightMenu(tag, e, node = null) {
      if (this.type == "tables") {
        e.preventDefault();
        const menuWidth = 220;
        const menuHeight = 306;
        this.contextMenu_top = Math.max(8, Math.min(e.clientY, window.innerHeight - menuHeight - 8));
        this.contextMenu_left = Math.max(8, Math.min(e.clientX, window.innerWidth - menuWidth - 8));
        this.contextMenuVisible = true;
        this.context_current_node = node;
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
            this.copyTableDialogVisible = false;
            this.$message.success("复制成功");
            if (this.context_current_node?.parent) {
              this.refreshResourceNode(this.context_current_node.parent);
            }
          }
        } else {
          this.$message.error("复制失败");
        }
      });
    },
    newTableLable() {
      let databaseName = this.context_current_item.schemaName;
      let tableName = this.context_current_item.name;
      this.$EventBus.$emit("openLookPage", {
        dataSourceId: this.context_current_item.dataSourceId || this.dataInfo.dataSource.id,
        dataSourceName: this.dataInfo.dataSource.alias,
        databaseName,
        tableName,
        objectType: "table",
        forceNew: true,
      });
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
    <div class="resource-explorer">
      <div class="resource-header">
        <span class="resource-title">数据库连接</span>
        <span class="resource-count">{{ databaseTree.length }}</span>
        <button class="resource-icon-button" title="刷新连接" @click="initializeResourceTree">
          <i class="el-icon-refresh"></i>
        </button>
        <button class="resource-icon-button" title="新增连接" @click="openDataSourceManager()">
          <i class="el-icon-plus"></i>
        </button>
      </div>
      <el-input
        v-model="resourceFilter"
        class="resource-search"
        size="mini"
        clearable
        prefix-icon="el-icon-search"
        placeholder="搜索过滤库/表/用户..."
        @input="handleResourceFilter"
      />
      <el-scrollbar ref="resourceTreeScrollbar" class="resource-tree-scrollbar" v-loading="resourceTreeLoading">
        <el-tree
          ref="resourceTree"
          class="resource-tree"
          :data="databaseTree"
          :props="defaultProps"
          :load="loadResourceNode"
          :filter-node-method="resourceFilterNode"
          node-key="key"
          lazy
          highlight-current
          :expand-on-click-node="false"
          @node-click="handleResourceNodeClick"
          @node-contextmenu="handleResourceContextMenu"
        >
          <span slot-scope="{ node, data }" class="resource-node">
            <span class="resource-node-main">
              <el-checkbox
                v-if="data.treeNodeType === 'table'"
                class="resource-table-checkbox"
                :value="isTreeTableSelected(data)"
                :aria-label="`选择表 ${data.label}`"
                @click.native.stop
                @change="toggleTreeTableSelection(data, $event)"
              />
              <span
                v-if="data.treeNodeType === 'datasource'"
                class="connection-status"
                :class="{ online: data.dataSource && isDataSourceConnected(data.dataSource.id) }"
              ></span>
              <i v-else :class="resourceNodeIcon(data)" class="resource-node-icon"></i>
              <span class="resource-node-label" :title="data.label">{{ data.label }}</span>
              <span v-if="data.treeNodeType === 'datasource'" class="database-type">
                {{ data.dataSource && data.dataSource.type }}
              </span>
              <span
                v-if="data.treeNodeType === 'objectGroup' && data.groupType === 'tables' && selectedTreeTableNames(data.key).length"
                class="resource-selection-count"
              >已选 {{ selectedTreeTableNames(data.key).length }}</span>
            </span>
            <span class="resource-node-actions">
              <template v-if="data.treeNodeType === 'datasource'">
                <button v-if="isDataSourceConnected(data.dataSource.id)" title="关闭连接" @click.stop="closeDataSourceFromTree(data.dataSource)">
                  <i class="el-icon-switch-button"></i>
                </button>
                <button title="新建查询" @click.stop="createConsoleForDataSource(data)">
                  <i class="el-icon-edit-outline"></i>
                </button>
                <button title="刷新" @click.stop="refreshResourceNode(node)">
                  <i class="el-icon-refresh"></i>
                </button>
                <button title="编辑连接" @click.stop="openDataSourceManager(data.dataSource)">
                  <i class="el-icon-setting"></i>
                </button>
                <button title="删除连接" @click.stop="deleteDataSourceFromTree(data.dataSource)">
                  <i class="el-icon-delete"></i>
                </button>
              </template>
              <template v-else-if="data.treeNodeType === 'objectGroup'">
                <button
                  v-if="data.groupType === 'tables'"
                  :title="isAllLoadedTreeTablesSelected(data, node) ? '取消全选已加载表' : '全选已加载表'"
                  @click.stop="toggleAllLoadedTreeTables(data, node)"
                >
                  <i :class="isAllLoadedTreeTablesSelected(data, node) ? 'el-icon-circle-check' : 'el-icon-circle-plus-outline'"></i>
                </button>
                <button
                  v-if="data.groupType === 'tables'"
                  class="danger-action"
                  title="批量删除已选表"
                  @click.stop="deleteSelectedTreeTables(data, node)"
                >
                  <i class="el-icon-delete"></i>
                </button>
                <button
                  v-if="['tables', 'views', 'users', 'tableSpace'].includes(data.groupType)"
                  title="新增"
                  @click.stop="addObjectFromTree(data)"
                >
                  <i class="el-icon-plus"></i>
                </button>
                <button title="刷新" @click.stop="refreshResourceNode(node)">
                  <i class="el-icon-refresh"></i>
                </button>
              </template>
              <button
                v-else-if="['table', 'view', 'function', 'processdure', 'trigger', 'user', 'tableSpace'].includes(data.treeNodeType)"
                title="删除"
                @click.stop="deleteObjectFromTree(data, node)"
              >
                <i class="el-icon-delete"></i>
              </button>
            </span>
          </span>
        </el-tree>
        <div v-if="!resourceTreeLoading && !databaseTree.length" class="resource-empty">
          <i class="el-icon-connection"></i>
          <span>暂无数据源</span>
          <el-button type="text" @click="openDataSourceManager()">新增连接</el-button>
        </div>
      </el-scrollbar>
    </div>

    <div v-if="false" class="database_form">
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
    <div v-if="false" style="flex: 1; min-height: 0; position: relative">
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
      <el-scrollbar ref="objectListScrollbar" class="object-list-scrollbar" style="height: calc(100% - 40px)" v-loading="styleLoading" :element-loading-text="loadingText">
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
          :data-object-index="index"
          @click.stop="type == 'tables' ? openTableInWindow(item, index, $event) : handelClickItem(item, index, $event)"
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
        <div v-if="type == 'tables' && hasMoreTables" class="load-more-tables" @click="loadMoreTables">
          加载更多表（已加载 {{ dataTreeList.length }} / {{ tableListTotal }}）
        </div>
      </el-scrollbar>
    </div>

    <div
      class="resource-context-menu"
      v-show="contextMenuVisible"
      :style="{ left: contextMenu_left + 'px', top: contextMenu_top + 'px' }"
      role="menu"
      @click.stop
    >
      <div class="context-menu-title" :title="context_current_item.name">
        <i class="el-icon-tickets"></i>
        <span>{{ context_current_item.name }}</span>
      </div>
      <button role="menuitem" @click="executeResourceContextAction('open')">
        <i class="el-icon-folder-opened"></i><span>打开</span>
      </button>
      <button role="menuitem" @click="executeResourceContextAction('newWindow')">
        <i class="el-icon-copy-document"></i><span>在新页签打开</span>
      </button>
      <div class="context-menu-divider"></div>
      <button role="menuitem" @click="executeResourceContextAction('ddl')">
        <i class="el-icon-document"></i><span>查看 DDL</span>
      </button>
      <button role="menuitem" @click="executeResourceContextAction('template')">
        <i class="el-icon-edit-outline"></i><span>数据模板</span>
      </button>
      <button role="menuitem" @click="executeResourceContextAction('copyTable')">
        <i class="el-icon-document-copy"></i><span>复制表结构</span>
      </button>
      <button role="menuitem" @click="executeResourceContextAction('copyTableWithData')">
        <i class="el-icon-files"></i><span>复制表及数据</span>
      </button>
      <button role="menuitem" @click="executeResourceContextAction('copyName')">
        <i class="el-icon-document-copy"></i><span>复制表名</span>
      </button>
      <div class="context-menu-divider"></div>
      <button class="danger" role="menuitem" @click="executeResourceContextAction('delete')">
        <i class="el-icon-delete"></i><span>删除表</span>
      </button>
    </div>
    <el-dialog
      :title="copyTableWithData ? '复制表及数据' : '复制表结构'"
      :visible.sync="copyTableDialogVisible"
      width="380px"
      append-to-body
      :close-on-click-modal="false"
    >
      <div class="copy-table-dialog-copy">
        <div class="copy-source-name">源表：{{ context_current_item.name }}</div>
        <el-form label-position="top" size="small">
          <el-form-item label="目标模式">
            <el-select
              v-model="copyschemaName"
              v-loading="copySchemaOptionsLoading"
              filterable
              style="width: 100%"
              placeholder="请选择目标模式"
            >
              <el-option
                v-for="item in dataSourceListData"
                :key="item.name"
                :label="item.name"
                :value="item.name"
              ></el-option>
            </el-select>
          </el-form-item>
        </el-form>
      </div>
      <span slot="footer" class="dialog-footer">
        <el-button @click="copyTableDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmCopyTable">开始复制</el-button>
      </span>
    </el-dialog>
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
.resource-context-menu {
  position: fixed;
  z-index: 3000;
  width: 212px;
  padding: 6px;
  border: 1px solid #dfe4ec;
  border-radius: 6px;
  background: #fff;
  box-shadow: 0 10px 28px rgba(38, 51, 77, 0.18);
  color: #34425a;
  font-size: 12px;
}
.context-menu-title {
  display: flex;
  align-items: center;
  height: 30px;
  padding: 0 8px;
  color: #7a879b;
  font-size: 11px;
  font-weight: 600;
}
.context-menu-title i {
  margin-right: 7px;
  color: #5f75a0;
}
.context-menu-title span {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.resource-context-menu button {
  display: flex;
  align-items: center;
  width: 100%;
  height: 30px;
  padding: 0 9px;
  border: 0;
  border-radius: 3px;
  background: transparent;
  color: #34425a;
  font-size: 12px;
  text-align: left;
  cursor: pointer;
}
.resource-context-menu button i {
  width: 18px;
  margin-right: 7px;
  color: #6e7e96;
  text-align: center;
}
.resource-context-menu button:hover {
  background: #edf4ff;
  color: #006fff;
}
.resource-context-menu button:hover i {
  color: #006fff;
}
.resource-context-menu button.danger,
.resource-context-menu button.danger i {
  color: #d64a4a;
}
.resource-context-menu button.danger:hover {
  background: #fff1f1;
}
.context-menu-divider {
  height: 1px;
  margin: 5px 3px;
  background: #edf0f5;
}
.copy-source-name {
  margin-bottom: 14px;
  padding: 8px 10px;
  border: 1px solid #e4e9f1;
  border-radius: 4px;
  background: #f7f9fc;
  color: #4b5970;
  font-size: 12px;
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
.resource-explorer {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  border: 1px solid #e5e9f2;
  background: #fff;
}
.resource-header {
  display: flex;
  align-items: center;
  height: 38px;
  padding: 0 8px 0 12px;
  border-bottom: 1px solid #edf0f5;
}
.resource-title {
  color: #25324b;
  font-size: 13px;
  font-weight: 600;
}
.resource-count {
  min-width: 18px;
  height: 18px;
  margin-left: 6px;
  padding: 0 5px;
  border-radius: 9px;
  background: #f0f3f8;
  color: #7a869a;
  font-size: 11px;
  line-height: 18px;
  text-align: center;
}
.resource-icon-button,
.resource-node-actions button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  padding: 0;
  border: 0;
  border-radius: 3px;
  background: transparent;
  color: #7b8799;
  cursor: pointer;
}
.resource-icon-button:hover,
.resource-node-actions button:hover {
  background: #edf4ff;
  color: #006fff;
}
.resource-header .resource-icon-button:first-of-type {
  margin-left: auto;
}
.resource-search {
  padding: 8px;
}
.resource-tree-scrollbar {
  flex: 1;
  min-height: 0;
}
.resource-node {
  display: flex;
  align-items: center;
  width: calc(100% - 6px);
  min-width: 0;
  height: 26px;
}
.resource-node-main {
  display: flex;
  align-items: center;
  min-width: 0;
  flex: 1;
}
.connection-status {
  width: 8px;
  height: 8px;
  flex: 0 0 8px;
  margin-right: 7px;
  border-radius: 50%;
  background: #b7bec9;
}
.connection-status.online {
  background: #38b26c;
  box-shadow: 0 0 0 2px #e3f6ea;
}
.resource-node-icon {
  width: 16px;
  margin-right: 5px;
  color: #6d7b91;
  text-align: center;
}
.resource-node-label {
  overflow: hidden;
  color: #435169;
  font-size: var(--workspace-body-font-size, 12px);
  white-space: nowrap;
  text-overflow: ellipsis;
}
.resource-table-checkbox {
  flex: 0 0 auto;
  margin-right: 6px;
}
.resource-selection-count {
  flex: 0 0 auto;
  margin-left: 7px;
  padding: 0 5px;
  border-radius: 8px;
  background: #eaf2ff;
  color: #3476d4;
  font-size: 10px;
  line-height: 16px;
}
.database-type {
  flex: 0 0 auto;
  margin-left: 6px;
  padding: 0 4px;
  border: 1px solid #d9e2f0;
  border-radius: 3px;
  color: #8591a6;
  font-size: 9px;
  line-height: 15px;
}
.resource-node-actions {
  display: none;
  flex: 0 0 auto;
  align-items: center;
  padding-left: 4px;
  background: inherit;
}
.resource-node:hover .resource-node-actions {
  display: flex;
}
.resource-node-actions button {
  width: 20px;
  height: 20px;
  font-size: 12px;
}
.resource-node-actions button.danger-action:hover {
  background: #fff1f1;
  color: #d64a4a;
}
.resource-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 150px;
  flex-direction: column;
  color: #9aa4b5;
  font-size: 12px;
}
.resource-empty > i {
  margin-bottom: 6px;
  font-size: 24px;
}
::v-deep .resource-search .el-input__inner {
  height: 28px;
  border-color: #dfe4ec;
  border-radius: 3px;
  background: #f8f9fb;
  font-size: 12px;
}
::v-deep .resource-tree-scrollbar .el-scrollbar__wrap {
  overflow-x: auto;
}
::v-deep .resource-tree-scrollbar .el-scrollbar__view {
  min-width: 100%;
}
::v-deep .resource-tree .el-tree-node__content {
  height: 28px;
  min-width: max-content;
  padding-right: 4px;
}
::v-deep .resource-tree .el-tree-node__content:hover {
  background: #f2f6fb;
}
::v-deep .resource-tree .el-tree-node.is-current > .el-tree-node__content {
  background: #eaf2ff;
}
::v-deep .resource-tree .el-tree-node__expand-icon {
  color: #8793a6;
  font-size: 12px;
}
::v-deep .resource-tree .el-tree-node__expand-icon.is-leaf {
  width: 0;
  margin: 0;
  padding: 0;
  visibility: hidden;
}
::v-deep .resource-table-checkbox .el-checkbox__label {
  display: none;
}
::v-deep .resource-table-checkbox .el-checkbox__inner {
  width: 13px;
  height: 13px;
}
::v-deep .resource-table-checkbox .el-checkbox__inner::after {
  left: 4px;
  top: 1px;
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
