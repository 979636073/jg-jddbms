<template>
  <div>
    <el-dialog
      title="新增表"
      :visible.sync="tableDialogVisibl"
      :before-close="handleClose"
      width="70%"
    >
      <span>
        <el-form label-position="left" label-width="70px" :model="searchParams">
          <el-form-item label="表名" class="is-required">
            <el-input
              placeholder="请输入内容"
              size="mini"
              v-model="searchParams.name"
              show-word-limit
              maxlength="50"
            >
            </el-input>
          </el-form-item>
          <el-form-item label="注释">
            <el-input
              placeholder="请输入内容"
              size="mini"
              v-model="searchParams.note"
              show-word-limit
              maxlength="50"
            >
            </el-input>
          </el-form-item>
        </el-form>
        <el-button
          type="primary"
          size="small"
          @click="addData"
          style="margin-bottom: 10px"
          >新增</el-button
        >
        <el-table
          :data="tableData"
          style="width: 300%"
          height="550"
          ref="ref_table"
          border
          :header-cell-style="{ background: '#f1f3f9', color: '#68728c' }"
        >
          <el-table-column
            prop="name"
            label="列名"
            width="180"
            show-overflow-tooltip
          >
            <template slot-scope="scope">
              <el-input
                v-model="scope.row.name"
                size="mini"
                placeholder="请输入内容"
                maxlength="50"
              ></el-input>
            </template>
          </el-table-column>
          <el-table-column
            prop="type"
            label="类型"
            width="180"
            show-overflow-tooltip
          >
            <template slot-scope="scope">
              <el-select
                v-model="scope.row.columnType"
                size="mini"
                placeholder="请选择"
                filterable
                @change="(value) => selectType(value, scope.row)"
              >
                <el-option
                  v-for="item in columnType"
                  :key="item.typeName"
                  :label="item.typeName"
                  :value="item.typeName"
                >
                </el-option>
              </el-select>
            </template>
          </el-table-column>
          <el-table-column prop="columnSize" label="精度" show-overflow-tooltip>
            <template slot-scope="scope">
              <el-input
                v-model="scope.row.columnSize"
                @input="(val) => handleInput(val, scope.row)"
                size="mini"
                placeholder="请输入内容"
                maxlength="50"
                :disabled="!scope.row.isDisable"
              ></el-input>
            </template>
          </el-table-column>
          <el-table-column
            prop="nullable"
            label="非空"
            show-overflow-tooltip
            width="70"
          >
            <template slot-scope="scope">
              <el-checkbox
                v-model="scope.row.nullable"
                :disabled="scope.row.primaryKey || scope.row.autoIncrement"
              ></el-checkbox>
            </template>
          </el-table-column>
          <el-table-column
            prop="primaryKey"
            label="主键"
            show-overflow-tooltip
            width="70"
          >
            <template slot-scope="scope">
              <el-checkbox
                :disabled="scope.row.unique"
                @change="
                  (val) => {
                    fieldsChange(val, scope.row);
                  }
                "
                v-model="scope.row.primaryKey"
              >
              </el-checkbox>
            </template>
          </el-table-column>
          <el-table-column
            prop="unique"
            label="唯一键"
            show-overflow-tooltip
            width="90"
          >
            <template slot-scope="scope">
              <el-checkbox
                v-model="scope.row.unique"
                :disabled="scope.row.primaryKey"
              >
              </el-checkbox>
            </template>
          </el-table-column>
          <el-table-column
            prop="defaultValue"
            label="默认值"
            show-overflow-tooltip
          >
            <template slot-scope="scope">
              <el-input
                :disabled="
                  scope.row.unique ||
                  scope.row.primaryKey ||
                  scope.row.autoIncrement
                "
                v-model="scope.row.defaultValue"
                size="mini"
                placeholder="请输入默认值"
                maxlength="120"
              ></el-input>
            </template>
          </el-table-column>
          <el-table-column
            prop="autoIncrement"
            label="是否自增"
            show-overflow-tooltip
            v-if="dataBaseInfo.type != 'ORACLE'"
            width="90"
          >
            <template slot-scope="scope">
              <el-checkbox
                @change="handleCheck(scope.row, scope.$index)"
                v-if="
                  ['BIGINT', 'INT', 'INTEGER', 'SMALLINT', 'TINYINT'].includes(
                    scope.row.columnType
                  )
                "
                v-model="scope.row.autoIncrement"
              ></el-checkbox>
            </template>
          </el-table-column>

          <el-table-column prop="comment" label="注释" show-overflow-tooltip>
            <template slot-scope="scope">
              <el-input
                v-model="scope.row.comment"
                size="mini"
                placeholder="请输入内容"
                maxlength="120"
              ></el-input>
            </template>
          </el-table-column>
          <el-table-column width="120" label="操作">
            <template slot-scope="scope">
              <el-button
                @click="deleteData(scope.row, scope.$index)"
                size="mini"
                type="text"
                plain
              >
                <img src="@/assets/main/3-con-ico02.png" alt="" />
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </span>
      <span slot="footer" class="dialog-footer">
        <el-button @click="tableCloseFn(false)" size="mini">取 消</el-button>
        <el-button type="primary" size="mini" @click="saveData"
          >确 定</el-button
        >
      </span>
    </el-dialog>
  </div>
</template>

<script>
import tableServer from "@/api/main/table";
let initData = {
  name: "",
  columnType: "",
  columnSize: "",
  comment: "",
  nullable: false,
  primaryKey: false,
  autoIncrement: false,
  isDisable: false,
};
export default {
  props: {
    tableDialogVisibl: {
      type: Boolean,
      default: false,
    },
    dataBaseInfo: {
      type: Object,
      default: {},
    },
    schema: {
      type: String,
      default: "",
    },
    getTableDataList: {
      type: Function,
    },
  },

  data() {
    return {
      selectIndex: '',
      isDisable: false,
      searchParams: {
        name: "",
        note: "",
      },
      tableData: [],
      columnType: [],
      rowindex: [],
    };
  },
  watch: {
    tableDialogVisibl(newVal) {
      if (newVal == true) {
        this.getDatabaseFieldTypeList();
      } else {
        this.tableData = [];
        this.searchParams.name = "";
        this.searchParams.note = "";
      }
    },
  },
  mounted() {
    this.getDatabaseFieldTypeList();
  },
  methods: {
    handleCheck(row, index) {
      console.log(this.selectIndex,index);
      if (this.selectIndex === index) {
        this.selectIndex = null;
      } else {
        this.selectIndex = index;
      }
        console.log(this.selectIndex, " this.selectIndex");
      if (!this.selectIndex && this.selectIndex !== 0) {
        row.autoIncrement = false;
        row.nullable = row.primaryKey;
      }else{
      this.tableData.forEach((item, id) => {
        if(id == 0 && id == index){
          item.autoIncrement = true;
          item.nullable = true;
        }else{
          item.autoIncrement = id == index;
          item.nullable = id == index ? id == index : item.primaryKey;
        }
      });
      }
    },
    fieldsChange(val, row) {
      if (row.primaryKey || row.autoIncrement) {
        row.nullable = true;
      } else {
        row.nullable = false;
      }
    },
    handleInput(val, row) {
      const inteaerOnly = /^\d*$/;
      if (!inteaerOnly.test(val)) {
        row.columnSize = val.replace(/[^\d]/g, "");
      }
    },
    // 设置自增或主键非空数据默认勾选

    // 选中类型自动携带精度
    selectType(val, row) {
      row.autoIncrement = false;
      row.nullable = false;
      this.columnType.map((item) => {
        item.typeName;
        if (item.typeName == val) {
          row.columnSize = item.defaultValue;
          row.isDisable = item.supportLength;
        }
      });
    },
    tableCloseFn(val) {
      this.$emit("tableCloseFn", val);
      this.tableData = [];
    },
    handleClose() {
      this.$emit("tableCloseFn", false);
      this.tableData = [];
    },
    addData() {
      let data = JSON.parse(JSON.stringify(initData));
      this.tableData.push(data);
      this.$nextTick(() => {
        this.$refs.ref_table.bodyWrapper.scrollTop =
          this.$refs.ref_table.bodyWrapper.scrollHeight;
      });
    },
    deleteData(row, index) {
      this.tableData.splice(index, 1);
    },
    getDatabaseFieldTypeList() {
      let send = {
        dataSourceId: this.dataBaseInfo.id,
        databaseName: this.dataBaseInfo.alias,
      };
      tableServer.getDatabaseFieldTypeList(send).then((res) => {
        this.columnType = res.data?.columnTypes;
      });
    },
    // tableRowClassName({rowIndex}) {
    //   return this.rowindex.includes(rowIndex) ? 'rowClass' : ''
    // },
    saveData() {
      this.rowindex = [];
      if (!this.searchParams.name) {
        return this.$message.error("表名称不能为空!");
      }
      this.tableData.forEach((item, index) => {
        if (item.name && item.columnType) {
        } else {
          this.rowindex.push(index);
        }
      });
      if (this.rowindex.length > 0) {
        return this.$message.error("列名或类型不能为空");
      }
      let obj = {
        schemaName: this.schema,
        tableName: this.searchParams.name,
      };
      this.tableData.forEach((item) => {
        item = Object.assign(item, obj);
      });
      let tableList = this.tableData.map((item) => {
        return {
          ...item,
          nullable: item.nullable ? 1 : 0,
        };
      });
      const parmas = {
        dataSourceId: this.dataBaseInfo.id,
        schemaName: this.schema,
        name: this.searchParams.name,
        comment: this.searchParams.note,
        columnList: tableList,
      };
      console.log(parmas, "parmas");
      tableServer.addTable(parmas).then((res) => {
        if (res.success) {
          this.$emit("tableCloseFn", false);
          this.getTableDataList(this.schema, true);
          this.$message.success("新建表成功");
        } else {
          this.$message.error(res.errorMessage);
        }
      });
    },
  },
};
</script>

<style lang='scss' scoped>
::v-deep .el-table--medium .el-table__cell {
  padding: 5px 0;
}
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
.rowClass {
  background: red;
}
</style>
