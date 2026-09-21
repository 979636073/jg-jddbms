<script>
import connectionServer from "@/api/main/connection";
import tableServer from "@/api/main/table";
import sqlServer from "@/api/main/sql";
import { v4 as uuidv4 } from "uuid";
export default {
  props: {
    tableData: {
      type: Array,
      default: [],
    },
    isLoading: {
      type: Boolean,
      default: false,
    },
    currentConfig: {
      type: Object,
    },
    getTableDetails: {
      type: Function,
    },
  },
  name: "DataInfo",
  data() {
    return {
      columnData:'',
      digLoading: false,
      radioColumn: {
        key: "transferVal",
        unique: "uniqueData",
        check: "textarea",
        // foregin: {
        //   first: "foreignValue",
        //   second: "referencedValue",
        // },
      },
      radioValue: {
        Primary: "key",
        Unique: "unique",
        CHECK: "check",
        FOREIGN_KEY: "foregin",
      },
      digTitle: "添加",
      // 当前修改行
      tableRowEditKey: "",
      childTableData: [],
      isShow: false,
      columns: [
        {
          title: "类型",
          dataIndex: "type",
        },
        {
          title: "状态",
          dataIndex: "status",
        },
        {
          title: "约束列",
          dataIndex: "column",
        },
        {
          title: "约束内容",
          dataIndex: "constraintsDesc",
        },
      ],
      dialogVisible: false,
      searchFrom: {
        name: "",
        schema: "",
        tableName: "",
        state: "",
      },
      fromData: {
        schema: "",
        tableName: "",
      },
      tableNameData: [],
      schemaData: [],
      radio: "key",
      uniqueData: [],
      textarea: "",
      activeName: "first",
      transferVal: [],
      transfer: [],
      foreignData: [],
      foreignValue: [],
      referencedData: [],
      referencedValue: [],
      editDialogVisible: false,
      searchParams: {
        schema: "",
        tableName: "",
        type: "",
        oldName: "",
        newName: "",
        state: "",
      },
      rowData: {},
    };
  },
  computed: {
    pageId() {
      return this.$route.params.id;
    },
    showOperation() {
      return (row) => {
        if (
          row.constraintsDesc &&
          row.constraintsDesc.includes("IS NOT NULL")
        ) {
          return false;
        } else {
          return true;
        }
      };
    },
  },
  methods: {
    // 刷新
    refresh() {
      this.getTableDetails(true);
    },
    // 新增提交
    createdConstraintFn() {
      if (this.digTitle == "修改") {
        this.updateFn(this.rowData);
        return;
      }
      if (!this.searchFrom.name) {
        this.$refs.conName.focus();
        return this.$message.error("约束名称必填");
      }
      // debugger
      if (["key", "unique"].includes(this.radio)) {
        this.columnData = this[this.radioColumn[this.radio]].join(",");
      } 
      // else if (this.radio == "foregin") {
      //   if (this.activeName == "first") {
      //     this.columnData = this.foreignValue.join(",");
      //   } else {
      //     this.referencedParams = this.referencedValue.join(",");
      //   }
      // }

      // this.referencedValue.forEach((item) => (this.referencedParams = item));
      const params = {
        constraintType:
          this.radio == "key"
            ? "Primary"
            : this.radio == "check"
            ? "CHECK"
            : this.radio == "unique"
            ? "Unique"
            : this.radio == "foregin"
            ? "VIRTUAL"
            : "",
        dataSourceId: this.currentConfig.uniqueData.dataSourceId,
        constraintName: this.searchFrom.name,
        columnName: this.foreignValue.join(",") ||this.columnData,
        schemaName: this.searchFrom.schema,
        tableName: this.searchFrom.tableName,
        check: this.radio == "check" ? this.textarea : undefined,
        forkColumn: this.radio == "foregin" ? this.referencedValue.join(",") : undefined,
        forkSchema: this.radio == "foregin" ? this.fromData.schema : undefined,
        forkTable:
          this.radio == "foregin" ? this.fromData.tableName : undefined,
        enabled: this.searchFrom.state == "true" ? true : false,
        isUpdate: false,
      };
      console.log(params, this.columnData,this[this.radioColumn[this.radio]], "params");
      let data = {
        newData: params,
        dataSourceId: this.currentConfig.uniqueData.dataSourceId,
      };
      tableServer.createConstraint(data).then((res) => {
        if (res.success) {
          this.getTableDetails(true);
          this.$message.success("添加成功");
          this.handleCloseFn();
        } else if (!res.success) {
          this.$message.error(res.errorCode);
        }
      });
    },
    getDataBaseTree() {
      const params = {
        dataSourceId: this.currentConfig.uniqueData.dataSourceId,
        dataSourceName: this.currentConfig.uniqueData.dataSourceName,
        refresh: true,
      };
      connectionServer.getSchemaList(params).then((res) => {
        this.schemaData = res.data;
      });
    },
    queryTransferData() {
      const params = {
        dataSourceId: this.currentConfig.uniqueData.dataSourceId,
        dataSourceName: this.currentConfig.uniqueData.dataSourceName,
        databaseType: this.currentConfig.uniqueData.databaseType,
        schemaName: this.searchFrom.schema,
        refresh: true,
        requestType: 1,
      };
      tableServer.getTableList(params).then((res) => {
        this.tableNameData = res.data.data;
      });
    },
    async getTableColumn() {
      const params = {
        dataSourceId: this.currentConfig.uniqueData.dataSourceId,
        schemaName: this.searchFrom.schema,
        tableName: this.searchFrom.tableName,
        refresh: true,
      };
      await tableServer.getTableDetails(params).then((res) => {
        this.transfer = res.data.columnList;
        this.foreignData = res.data.columnList;
        this.$forceUpdate();
        // console.log( this.transfer,this.transferVal,' this.transfer');
      });
    },
    // getTableColumn() {
    //   const params = {
    //     dataSourceId: this.currentConfig.uniqueData.dataSourceId,
    //     schemaName: this.searchFrom.schema,
    //     tableName: this.searchFrom.tableName,
    //     refresh: true,
    //   };
    //   tableServer.getTableDetails(params).then((res) => {
    //     this.transfer = res.data.columnList;
    //     this.foreignData = res.data.columnList;
    //   });
    // },
    async queryTableTranf() {
      const params = {
        dataSourceId: this.currentConfig.uniqueData.dataSourceId,
        schemaName: this.fromData.schema,
        tableName: this.fromData.tableName,
        refresh: true,
      };
      await tableServer.getTableDetails(params).then((res) => {
        this.$set(this, "referencedData", res.data.columnList);
      });
    },
    handleRowClick(row) {
      if (row.type == "FOREIGN_KEY" && row.foreign) {
        this.childTableData = [];
        this.childTableData.push(row.foreign);
        this.isShow = true;
      } else if (row.type == "FOREIGN_KEY" && row.foreignList) {
        this.childTableData = [];
        this.childTableData = row.foreignList;
        this.isShow = true;
      } else {
        this.isShow = false;
      }
    },
    changeCellStyle({ row, column }) {
      if (row.foreignTableName) {
        if (column.label == "约束内容") {
          return "color:blue; text-decoration:underline;";
        }
      }
    },
    deleteData(row) {
      const params = {
        keyName: row.keyName,
        name: row.name,
        dataSourceId: this.currentConfig.uniqueData.dataSourceId,
        schemaName: row.schemaName,
        tableName: row.tableName,
      };
      tableServer.deleteConstraint(params).then((res) => {
        if (res.success) {
          this.$message.success("删除成功");
          this.getTableDetails(true);
        } else {
          this.$message.error("删除失败!");
        }
      });
    },
    addData() {
      this.digTitle = "添加";
      this.searchFrom.name = "";
      this.searchFrom.state = "true";
      this.radio = "key";
      this.transferVal = [];
      this.dialogVisible = true;
    },
    handleCloseFn() {
      this.dialogVisible = false;
      this.searchFrom.schema = "";
      this.searchFrom.tableName = "";
      this.searchFrom.name = "";
      this.transfer = [];
      this.transferVal = [];
      this.radio = "key";
      this.foreignData = [];
      this.foreignValue = [];
      this.fromData.tableName = "";
      this.referencedValue = [];
      this.referencedData = [];
      this.rightDefaultChecked = [];
      this.rightDefaultChecked_Second = [];
    },
    updateFn(row) {
      console.log(row);
      let columnData = "";
      let referencedParams = null;
      if (["key", "unique"].includes(this.radio)) {
        columnData = this[this.radioColumn[this.radio]].join(",");
      } else if (this.radio == "foregin") {
        if (this.activeName == "first") {
          referencedParams = this.foreignValue.join(",");
        } else {
          referencedParams = this.referencedValue.join(",");
        }
      }

      let params = {
        constraintType: row.type,
        keyName: row.keyName,
        name: row.name,
        dataSourceId: this.currentConfig.uniqueData.dataSourceId,
        constraintName: this.searchFrom.name,
        columnName: columnData,
        schemaName: row.schemaName,
        tableName: row.tableName,
        enabled: this.searchFrom.state == "true" ? true : false,
        check: this.radio == "check" ? this.textarea : undefined,
        forkColumn: referencedParams,
        forkSchema: row.foreign
          ? row.foreign.foreignSchemaName
          : row.foreignList
          ? row.foreignList[0].foreignSchemaName
          : null,
        forkTable: row.foreign
          ? row.foreign.foreignTableName
          : row.foreignList
          ? row.foreignList[0].foreignTableName
          : null,
        isUpdate: true,
      };
      let oldParams = JSON.parse(JSON.stringify(params));
      oldParams.constraintName = row.keyName || row.name;
      oldParams.enabled = row.status == "VALID";
      oldParams.check = row.constraintsDesc;
      let data = {
        newData: params,
        oldData: oldParams,
        dataSourceId: this.currentConfig.uniqueData.dataSourceId,
      };
      console.log(data, "data");
      tableServer.createConstraint(data).then((res) => {
        if (res.success) {
          this.dialogVisible = false;
          this.getTableDetails(true);
        } else {
          this.$message.error(res.errorCode);
        }
      });
    },
    async editData(row) {
      console.log(row, 13);
      this.rowData = JSON.parse(JSON.stringify(row));
      let defaultCheck;
      if (this.rowData.foreignList?.length) {
        defaultCheck = this.rowData.foreignList[0];
      }
      if (row.status == "VALID") {
        this.searchFrom.state = "true";
      } else {
        this.searchFrom.state = "false";
      }
      this.digTitle = "修改";
      this.digLoading = true;
      this.activeName = 'first'
      this.searchFrom.name = row.keyName || row.name;
      this.searchFrom.schema = row.schemaName;
      this.searchFrom.tableName = row.tableName;
      await this.getTableColumn();
      if (defaultCheck) {
        this.fromData.schema = defaultCheck.foreignSchemaName;
        this.fromData.tableName = defaultCheck.foreignTableName;
        await this.queryTableTranf(); // 获取second data数据
      }
      this.radio = this.radioValue[row.type];
      let data = row.column?.split(",");

      this.$nextTick(async () => {
        switch (this.radio) {
          case "key":
            this.transferVal = this.transfer
              .filter((item) => data.includes(item.name))
              .map((obj) => obj.name);
            this.rightDefaultChecked = this.transferVal || [];
            break;
          case "check":
            this.textarea = row.constraintsDesc;
            break;
          case "unique":
            this.uniqueData = this.transfer
              .filter((item) => data.includes(item.name))
              .map((obj) => obj.name);
            this.rightDefaultChecked = this.uniqueData|| [];
            break;
          case "foregin":
            this.getDefaultForegin();
            break;
        }
        this.digLoading = false;
      });
      this.dialogVisible = true;
    },
    editHandleCloseFn() {
      this.editDialogVisible = false;
      this.searchParams.newName = "";
    },
    editFn() {
      this.updateFn(this.rowData);
    },
    selectTableData(val) {
      this.fromData.tableName = "";
      const params = {
        dataSourceId: this.currentConfig.uniqueData.dataSourceId,
        dataSourceName: this.currentConfig.uniqueData.dataSourceName,
        databaseType: this.currentConfig.uniqueData.databaseType,
        schemaName: val,
        refresh: true,
        requestType: 2,
      };
      tableServer.getTableList(params).then((res) => {
        if (res.success) {
          this.tableNameData = res.data.data;
        }
      });
    },
    foreignDetail(row) {
      let pageId = uuidv4();
      this.$router.push({
        path: "/workspace/look/" + pageId,
        query: { schemaName: row.schemaName, tableName: row.foreignTableName },
      });
      setTimeout(() => {
        this.$store.dispatch("jdTagsView/changeView", {
          title: row.foreignTableName,
          id: this.$route.params.id,
        });
        let send = {
          dataSourceId: this.currentConfig.uniqueData.dataSourceId,
          databaseType: this.currentConfig.uniqueData.databaseType,
          hasNextPage: true,
          pageNo: 1,
          pageSize: 100,
          schemaName: row.schemaName,
          sql: `select * from ${row.schemaName}.${row.foreignTableName}`,
          tableName: row.foreignTableName,
          total: 0,
          type: this.currentConfig.uniqueData.databaseType,
        };
        sqlServer.viewTable(send).then((res) => {
          if (res.success) {
            this.$store.dispatch("workspaceData/setDataCurrentData", {
              pageId: this.pageId,
              title: row.foreignTableName,
              type: "editTable",
              ...res.data[0],
              params: send,
              uniqueData: {
                dataSourceId: this.currentConfig.uniqueData.dataSourceId,
                dataSourceName: this.currentConfig.uniqueData.dataSourceName,
                sqlInfo: null,
                databaseType: this.currentConfig.uniqueData.databaseType,
                schemaName: row.schemaName,
                tableName: row.foreignTableName,
                isLoading: true,
                dataType: this.currentConfig.uniqueData.databaseType,
              },
            });
          } else {
            this.$message.error(res.errorMessage);
          }
        });
      }, 1000);
    },

    getDefaultForegin() {
      let defaultCheck = "";
      if (this.rowData && Object.keys(this.rowData).length) {
        defaultCheck = this.rowData?.foreignList[0];
        if (this.digTitle == "修改") {
        if (this.activeName == "first") {
          this.foreignValue = this.foreignData
            .filter((item) => item.name == defaultCheck.column)
            .map((obj) => obj.name);
          this.rightDefaultChecked = defaultCheck.column || [];
        } else if (this.activeName == "second") {
          // second
          if (this.referencedData && this.referencedData.length) {
            let foreignColumn = defaultCheck.foreignColumnName.split(",");
            this.referencedValue = this.referencedData
              .filter((item) => foreignColumn.includes(item.name))
              .map((obj) => obj.name);
            console.log(this.referencedValue, "this.referencedValue");
            this.rightDefaultChecked_Second = this.referencedValue;
          }
        }
      }
      }
    },
  },
  watch: {
    radio(){
      this.columnData = []
      this.foreignValue = []
      this.referencedValue = []
      this.transferVal = []
      this.uniqueData = []
      this.textarea = ''
      this.fromData.schema = ''
    },
    activeName: {
      handler() {
        this.getDefaultForegin();
      },
    },
    tableData: {
      handler() {
        this.childTableData = [];
        this.isShow = false;
      },
    },
    async dialogVisible(val) {
      if (!val) return;
      this.searchFrom.schema = this.currentConfig.uniqueData.schemaName;
      this.searchFrom.tableName = this.currentConfig.uniqueData.tableName;
      this.fromData.schema = this.currentConfig.uniqueData.schemaName;
      if (this.digTitle == "添加") {
        await this.getDataBaseTree();
        await this.queryTransferData();
        await this.getTableColumn();
      }
    },
  },
};
</script>

<template>
  <div class="draggable">
    <div class="table_btn_list">
      <el-button size="mini" type="text" @click="addData">
        <img src="@/assets/main/3-con-ico01.png" alt="" />
        添加
      </el-button>
      <el-button size="mini" type="text" @click="refresh">
        <img src="@/assets/main/5-ico3.png" alt="" />
        刷新
      </el-button>
    </div>
    <el-table
      size="mini"
      :data="tableData"
      row-key="key"
      height="calc(100% - 300px)"
      :cell-style="changeCellStyle"
      @row-click="handleRowClick"
      :header-cell-style="{ background: '#f1f3f9', color: '#68728c' }"
      highlight-current-row
      v-loading="isLoading"
      border
    >
      <el-table-column prop="keyName" label="名称">
        <template slot-scope="scope">
          <span>{{ scope.row.keyName || scope.row.name }}</span>
        </template>
      </el-table-column>
      <el-table-column
        sortable
        v-for="(item, index) in columns"
        :key="index"
        :prop="item.dataIndex"
        :label="item.title"
        :width="item.width"
        :fixed="item.fixed"
        show-overflow-tooltip
      >
      </el-table-column>
      <el-table-column width="250" label="操作">
        <!-- v-if="scope.row.constraintsDesc && scope.row.constraintsDesc.includes('IS NOT NULL')" -->
        <template slot-scope="scope" v-if="showOperation(scope.row)">
          <el-button
            @click.native="deleteData(scope.row)"
            size="mini"
            type="text"
            plain
          >
            <img src="@/assets/main/3-con-ico02.png" alt="" />
            删除
          </el-button>
          <el-button
            @click.native="editData(scope.row)"
            size="mini"
            type="text"
            plain
          >
            <img src="@/assets/main/3-con-ico03.png" alt="" />
            修改
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog
      :title="digTitle"
      :visible.sync="dialogVisible"
      width="870px"
      :before-close="handleCloseFn"
    >
      <div v-loading="digLoading">
        <el-form label-position="right" label-width="70px" :model="searchFrom">
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="约束名称">
                <el-input
                  v-model="searchFrom.name"
                  ref="conName"
                  size="mini"
                ></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="模式名">
                <el-select
                  disabled
                  v-model="searchFrom.schema"
                  @change="queryTransferData"
                  size="mini"
                  placeholder="请选择"
                >
                  <el-option
                    v-for="item in schemaData"
                    :key="item.name"
                    :label="item.name"
                    :value="item.name"
                  >
                  </el-option>
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="表名">
                <el-select
                  disabled
                  v-model="searchFrom.tableName"
                  size="mini"
                  placeholder="请选择"
                >
                  <el-option
                    v-for="item in tableNameData"
                    :key="item.name"
                    :label="item.name"
                    :value="item.name"
                  >
                  </el-option>
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="14">
              <el-form-item label="约束状态">
                <el-radio-group v-model="searchFrom.state">
                  <el-radio label="true">有效</el-radio>
                  <el-radio label="false">无效</el-radio>
                </el-radio-group>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
        <div class="exportTitle">
          <el-radio-group
            v-model="radio"
            size="small"
            class="exportBox"
            fill="#006fff"
            :disabled="digTitle == '修改'"
          >
            <el-radio-button label="key">Primary Key</el-radio-button>
            <el-radio-button label="check">Check</el-radio-button>
            <el-radio-button label="unique">Unique</el-radio-button>
            <el-radio-button label="foregin">Foregin Key</el-radio-button>
          </el-radio-group>
        </div>
        <div class="transfer" v-if="radio == 'key'">
          <el-transfer
            v-model="transferVal"
            :titles="['Table Column', 'Constrained']"
            :data="transfer"
            :right-default-checked="transferVal"
            :props="{ key: 'name', label: 'name' }"
          ></el-transfer>
        </div>
        <div class="transfer" v-if="radio == 'check'">
          <el-input
            type="textarea"
            :rows="4"
            placeholder="请输入内容"
            v-model="textarea"
          >
          </el-input>
        </div>
        <div class="transfer" v-if="radio == 'unique'">
          <el-transfer
            v-model="uniqueData"
            :titles="['Table Column', 'Constrained']"
            :data="transfer"
            :right-default-checked="uniqueData"
            :props="{ key: 'name', label: 'name' }"
          ></el-transfer>
        </div>
        <div v-if="radio == 'foregin'">
          <el-tabs v-model="activeName">
            <el-tab-pane label="This Table" name="first">
              <div class="transfer">
                <el-transfer
                  v-model="foreignValue"
                  :titles="['Available Table', 'Constrained']"
                  :data="foreignData"
                  :right-default-checked="foreignValue"
                  :props="{ key: 'name', label: 'name' }"
                ></el-transfer>
              </div>
            </el-tab-pane>
            <el-tab-pane label="Referenced Table" name="second">
              <div class="transfer">
                <div>
                  <el-form
                    label-position="left"
                    label-width="50px"
                    :model="fromData"
                    style="fromData"
                  >
                    <el-row :gutter="20">
                      <el-col :span="8">
                        <el-form-item label="模式名">
                          <el-select
                            :disabled="digTitle == '修改'"
                            v-model="fromData.schema"
                            size="mini"
                            filterable
                            placeholder="请选择"
                            style="width: 200px"
                            @change="selectTableData"
                          >
                            <el-option
                              v-for="item in schemaData"
                              :key="item.name"
                              :label="item.name"
                              :value="item.name"
                            >
                            </el-option>
                          </el-select>
                        </el-form-item>
                      </el-col>
                      <el-col :span="8">
                        <el-form-item label="表名" style="margin-left: 100px">
                          <el-select
                            :disabled="digTitle == '修改'"
                            v-model="fromData.tableName"
                            size="mini"
                            filterable
                            placeholder="请选择"
                            style="width: 200px"
                            @change="queryTableTranf"
                          >
                            <el-option
                              v-for="item in tableNameData"
                              :key="item.name"
                              :label="item.name"
                              :value="item.name"
                            >
                            </el-option>
                          </el-select>
                        </el-form-item>
                      </el-col>
                    </el-row>
                  </el-form>
                  <el-transfer
                    v-model="referencedValue"
                    :titles="['Available Table', 'Constrained']"
                    :data="referencedData"
                    :right-default-checked="referencedValue"
                    :props="{ key: 'name', label: 'name' }"
                  ></el-transfer>
                </div>
              </div>
            </el-tab-pane>
          </el-tabs>
        </div>
      </div>
      <span slot="footer" class="dialog-footer">
        <el-button @click="handleCloseFn" size="mini">取 消</el-button>
        <el-button type="primary" @click="createdConstraintFn" size="mini"
          >确 定</el-button
        >
      </span>
    </el-dialog>
    <el-dialog
      title="修改"
      :visible.sync="editDialogVisible"
      width="500px"
      :before-close="editHandleCloseFn"
    >
      <span>
        <el-form
          label-position="left"
          label-width="137px"
          :model="searchParams"
        >
          <el-form-item label="Schema">
            <el-input
              v-model="searchParams.schema"
              disabled
              size="mini"
            ></el-input>
          </el-form-item>
          <el-form-item label="Table Name">
            <el-input
              v-model="searchParams.tableName"
              disabled
              size="mini"
            ></el-input>
          </el-form-item>
          <el-form-item label="Constraint Type">
            <el-input
              v-model="searchParams.type"
              disabled
              size="mini"
            ></el-input>
          </el-form-item>
          <el-form-item label="Old Constraint Name">
            <el-input
              v-model="searchParams.oldName"
              disabled
              size="mini"
            ></el-input>
          </el-form-item>
          <el-form-item label="New Constraint Name">
            <el-input v-model="searchParams.newName" size="mini"></el-input>
          </el-form-item>
          <el-form-item label="Constraint State">
            <el-radio-group v-model="searchParams.state">
              <el-radio label="true">有效</el-radio>
              <el-radio label="false">无效</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-form>
      </span>
      <span slot="footer" class="dialog-footer">
        <el-button @click="editHandleCloseFn" size="mini">取 消</el-button>
        <el-button type="primary" @click="editFn" size="mini">确 定</el-button>
      </span>
    </el-dialog>
    <div style="margin-top: 10px" v-if="isShow">
      <el-table
        :data="childTableData"
        style="width: 50%"
        :header-cell-style="{ background: '#f1f3f9', color: '#68728c' }"
        border
      >
        <el-table-column align="center" prop="foreignColumnName" label="列名">
        </el-table-column>
        <el-table-column prop="foreignTableName" label="关联表名">
          <template slot-scope="scope">
            <span
              style="color: #409eff; cursor: pointer"
              @click="foreignDetail(scope.row)"
              >{{ scope.row.foreignTableName }}</span
            >
          </template>
        </el-table-column>
        <el-table-column prop="foreignSchemaName" label="关联模式">
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<style scoped lang="scss">
::v-deep .el-dialog .el-dialog__header {
  background: #dfe3f0 !important;
  .el-dialog__title {
    color: #242f57 !important;
  }
}
::v-deep .el-dialog .el-dialog__body {
  padding: 15px 20px;
}
::v-deep .el-form-item__label {
  font-size: 12px;
}
::v-deep .el-form-item {
  margin-bottom: 10px;
}
::v-deep {
  .el-table .el-button--text {
    margin: 0;
  }
  .el-table .el-button {
    & > span {
      display: flex;
      align-items: center;
      img {
        margin-right: 6px;
      }
    }
  }
}
.fromData {
  display: flex;
  justify-content: center;
}
.transfer {
  margin-top: 20px;
  display: flex;
  justify-content: center;
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
.table_btn_list {
  display: flex;
  align-items: center;
  padding: 10px 0;
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
.draggable {
  height: 100%;
}
.keyBox {
  width: 26px;
  height: 26px;
  display: flex;
  justify-content: center;
  align-items: center;
  cursor: pointer;
  position: relative;
  i {
    color: #d89614;
  }
  span {
    position: absolute;
    font-weight: bold;
    right: 4px;
    bottom: -5px;
    transform: scale(0.8);
  }
}
</style>
