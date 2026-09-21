<script>
import MonacoEditor from "@/components/MonacoEditor/index.vue";
import connectionServer from "@/api/main/connection";
import sqlServer from "@/api/main/sql";
import tableServer from "@/api/main/table";
import viewServer from "@/api/main/view";
export default {
  name: "TriggerList",
  components: { MonacoEditor },
  props: {
    tableData: {
      type: Array,
      default: () => [],
    },
    currentConfig: {
      type: Object,
      default: () => {},
    },
  },
  data() {
    return {
      schemaData: [],
      tableNameData: [],
      columnNameData: [],
      formLabelAlign: {
        triggerName: "",
        fires: "",
        event:'',
        tableOrView:'',
        check: false,
      },
      isADD: false,
      isEdit: false,
      editObj: {},
      dialogVisible:false,
      viewData:[],
      tableOrView:[],
    };
  },
  mounted() {
  
  },
  methods: {
    addTriggerData() {
      this.isADD = true;
      this.isEdit = false;
      this.dialogVisible = true;
      this.getTableDataList();
      this.getViewDataList()
      this.$nextTick(()=>{
        this.$refs.OrderMonacoEditor.setValue(" ");
      })
    },
    submitTeigger() {
      if (this.isADD) {
        let sql = this.$refs.OrderMonacoEditor.getValue();
        const parmas = {
          dataSourceId: this.currentConfig?.uniqueData.dataSourceId,
          isUpdate: false,
          sql: sql,
        };
        sqlServer.addTriggerData(parmas).then((res) => {
          if (!res.success) {
            return this.$message.error(res.errorCode);
          }
          this.refreshFn();
          this.$message.success('新增触发器成功!')
          this.$refs.OrderMonacoEditor.setValue("");
        });
      } else if (this.isEdit) {
        let sql = this.$refs.OrderMonacoEditor.getValue();
        const parmas = {
          dataSourceId: this.currentConfig?.uniqueData.dataSourceId,
          isUpdate: true,
          sql: sql,
          triggerName: this.editObj.triggerName,
          schemaName: this.currentConfig?.uniqueData.schemaName,
        };
        sqlServer.addTriggerData(parmas).then((res) => {
          if(res.success) {
            this.refreshFn();
            this.$message.success('修改触发器成功!');
          } else if(!res.success) {
            this.$message.error(res.errorCode)
          }
        });
      }
    },
    handelDelete(row) {
      const parmas = {
        dataSourceId: this.currentConfig?.uniqueData.dataSourceId,
        triggerName: row.triggerName,
        schemaName: this.currentConfig?.uniqueData.schemaName,
      };
      sqlServer.deleteTriggerData(parmas).then((res) => {
        if (res.success) {
          this.refreshFn();
        } else if (res.success == false) {
          return this.$message.error(res.errorCode);
        }
      });
    },
    handelEdit(row) {
      this.editObj = row;
      this.$refs.OrderMonacoEditor.setValue(row.querySql);
      this.isADD = false;
      this.isEdit = true;
    },
    handleClose() {
      this.formLabelAlign.triggerName = "";
      this.formLabelAlign.fires = "";
      this.formLabelAlign.event = "";
      this.formLabelAlign.tableOrView = '';
      this.formLabelAlign.check = false;
      this.dialogVisible = false;
    },
    getDataBaseTree() {
      const params = {
        dataSourceId: this.currentConfig?.uniqueData.dataSourceId,
        dataBaseName: this.currentConfig?.uniqueData.databaseName,
        refresh: true,
      };
      connectionServer.getSchemaList(params).then((res) => {
        this.schemaData = res.data;
      });
    },
    getTableDataList() {
      console.log(this.currentConfig,'this.currentConfig')
      const params = {
        dataSourceId: this.currentConfig?.uniqueData.dataSourceId,
        dataSourceName: this.currentConfig?.uniqueData.dataSourceName,
        databaseType: this.currentConfig?.uniqueData.databaseType,
        schemaName: this.currentConfig?.uniqueData.schemaName,
        refresh: true,
        requestType: 2,
      };
      tableServer.getTableList(params).then((res) => {
        if (res.data.data.length > 0) {
          this.tableNameData = res.data.data;
        }
      });
    },
    getViewDataList() {
      const params = {
        dataSourceId: this.currentConfig?.uniqueData.dataSourceId,
        dataSourceName: this.currentConfig?.uniqueData.dataSourceName,
        databaseType: this.currentConfig?.uniqueData.databaseType,
        schemaName: this.currentConfig?.uniqueData.schemaName,
        refresh: true,
        requestType: 2,
      };
      debugger
      viewServer.getViewList(params).then((res) => {
        if(res.data.data.length > 0) {
          this.viewData = res.data.data;
          this.tableOrView = [... this.tableNameData,...this.viewData]
        }
      })
    },
    queryColumnList() {
      const params = {
        dataSourceId: this.currentConfig?.uniqueData.dataSourceId,
        databaseName: this.currentConfig?.uniqueData.databaseName,
        schemaName: this.formLabelAlign.schema,
        tableName: this.formLabelAlign.tableName,
      };
      tableServer.getAllFieldByTable(params).then((res) => {
        this.columnNameData = res.data;
      });
    },
    refreshFn() {
      this.$emit("refreshFn");
      this.isEdit = false;
      this.isADD = false;
      this.$refs.OrderMonacoEditor.setValue("");
    },
    createTrigger() {
      let sqlValue =  `create or replace trigger ${this.formLabelAlign.triggerName} 
        ${this.formLabelAlign.fires} ${this.formLabelAlign.event}
        on ${this.formLabelAlign.tableOrView} 
        ${this.formLabelAlign.check ? '' : 'for each row'} 
      declare 
      begin
       ; 
      end ${this.formLabelAlign.triggerName}`;
      this.$refs.OrderMonacoEditor.setValue(sqlValue);
      this.handleClose()
    }
  },
  watch: {
    tableData: {
      handler(newVal) {
        if (newVal.length <= 0) {
          this.$refs.OrderMonacoEditor.setValue("");
        }
      },
    },
    currentConfig(newVal) {
      if(newVal) {
        this.isADD = false;
        this.isEdit = false;
      }
    },
  },
};
</script>

<template>
  <div class="draggable">
    <div class="table_btn_list">
      <el-button
        size="mini"
        type="text"
        style="margin-left: 0"
        @click="addTriggerData"
      >
        <img src="@/assets/main/3-con-ico01.png" alt="" />
        添加
      </el-button>
      <el-button size="mini" type="text" @click="refreshFn">
        <img src="@/assets/main/5-ico3.png" alt="" />
        刷新
      </el-button>
    </div>
    <el-table
      :data="tableData"
      style="width: 100%"
      size="mini"
      :header-cell-style="{ background: '#f1f3f9', color: '#68728c' }"
      height="380"
      border
    >
      <el-table-column
        prop="triggerName"
        label="触发器名"
        sortable
      ></el-table-column>
      <el-table-column prop="type" label="类型" sortable></el-table-column>
      <el-table-column prop="status" label="状态" sortable></el-table-column>
      <el-table-column prop="event" label="触发事件" sortable></el-table-column>
      <el-table-column
        prop="schemaName"
        label="所属模式"
        sortable
      ></el-table-column>
      <el-table-column label="操作" width="240">
        <template slot-scope="scope">
          <el-button
            @click="handelDelete(scope.row)"
            size="mini"
            type="text"
            plain
          >
            <img src="@/assets/main/3-con-ico02.png" alt="" />
            删除
          </el-button>
          <el-button
            @click="handelEdit(scope.row)"
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
    <div class="buttonGroup">
      <div class="title" v-if="isADD">新增触发器</div>
      <div class="title" v-if="isEdit">修改触发器</div>
      <el-button
        type="primary"
        v-if="isADD || isEdit"
        size="small"
        @click="submitTeigger"
        >提交</el-button
      >
    </div>
    <div style="margin-top: 10px" v-show="isADD || isEdit">
      <MonacoEditor
        ref="OrderMonacoEditor"
        dom="orderBy"
        style="width: 100%; height: 300px"
      />
    </div>
    <el-dialog
      title="新增触发器"
      :visible.sync="dialogVisible"
      width="30%"
      :before-close="handleClose">
      <span>
        <el-form
          label-position="right"
          label-width="80px"
          :model="formLabelAlign"
        >
          <el-form-item label="Trigger Name">
            <el-input v-model="formLabelAlign.triggerName" style="width:215px" placeholder="请输入内容"></el-input>
          </el-form-item>
          <el-form-item label="Fires">
            <el-select v-model="formLabelAlign.fires" placeholder="请选择活动区域">
              <el-option label="befor" value="befor"></el-option>
              <el-option label="after" value="after"></el-option>
              <el-option label="instead of" value="instead of"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="Event">
            <el-select v-model="formLabelAlign.event" placeholder="请选择活动区域">
              <el-option label="insert" value="insert"></el-option>
              <el-option label="update" value="update"></el-option>
              <el-option label="delete" value="delete"></el-option>
              <el-option label="insert or update" value="insert or update"></el-option>
              <el-option label="insert or update or delete" value="insert or update or delete"></el-option>

            </el-select>
          </el-form-item>
          <el-form-item label="Table or View">
            <el-select v-model="formLabelAlign.tableOrView" placeholder="请选择活动区域">
              <el-option v-for="item in tableOrView" :key="item.name" :label="item.name" :value="item.name"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="Statement level">
            <el-checkbox v-model="formLabelAlign.check"></el-checkbox>
          </el-form-item>
        </el-form>
      </span>
      <span slot="footer" class="dialog-footer">
        <el-button @click="handleClose">取 消</el-button>
        <el-button type="primary" @click="createTrigger()">确 定</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.draggable {
  height: 100%;
}
.buttonGroup {
  width: 100%;
  align-items: center;
  display: flex;
  justify-content: space-between;
  .title {
    width: 80px;
    margin-top: 10px;
    font-size: 14px;
    height: 30px;
    line-height: 30px;
    text-align: center;
  }
}

::v-deep {
  .el-form-item__label {
    width: 150px !important;
  }
  .el-table--mini .el-table__cell {
    padding: 2px;
  }
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
</style>
