<script>
import Sortable from "sortablejs";
import { v4 as uuidv4 } from "uuid";
import IncludeColModal from "@/views/main/workspace/components/renderTableEditor/includeColModal.vue";

let InitialData = {
  key: null,
  columnList: [],
  name: "",
  type: null,
  comment: null,
  editStatus: "ADD",
  tableName:'',
};
export default {
  name: "IndexList",
  components: { IncludeColModal },
  props: {
    databaseSupportField: {
      type: Object,
    },
    columnList: {
      type: Array,
      default: [],
    },
    tableData: {
      type: Array,
      default: [],
    },
    currentConfig: {
      type: Object,
    },
    getTableDetails: {
      type: Function,
    },
    isLoading: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      // 当前修改行
      tableRowEditKey: "",
      columns: [
        {
          title: "索引名称",
          dataIndex: "name",
        },                
        {
          title: "键名称",
          dataIndex: "keyName",
        },
        {
          title: "索引类型",
          dataIndex: "type",
        },
        {
          title: "包含列",
          dataIndex: "columnList",
        },
        {
          title: "表",
          dataIndex: "tableName",
        },
      ],
      editingData: null,
    };
  },
  mounted() {
    this.rowDrop();
  },
  methods: {
    addData() {
      let data = JSON.parse(JSON.stringify(InitialData));
      data.tableName = this.currentConfig.uniqueData.tableName;
      data.key = uuidv4();
      this.tableData.push(data);
      this.tableRowEditKey = data.key;
      this.edit(data);
    },
    edit(record) {
      if (record.key !== this.editingData?.key) {
        this.editingData = record || null;
      }
    },
    deleteData(row, index) {
      if (row.editStatus === "ADD") {
        this.tableData.splice(index, 1);
      } else {
        this.tableData?.map((item) => {
          if (item.key === row?.key) {
            this.editingData = null;
            item.editStatus = "DELETE";
          }
        });
      }
    },
    tableRowClassName({ row, rowIndex }) {
      return row.editStatus == 'DELETE' ? "delete-row" : row.editStatus == 'ADD' || row.editStatus == 'MODIFY'? "update-row":"";
    },
    openIncludeColModal(row) {
      this.$nextTick(() => {
        this.$refs.includeColModal.init(row.columnList);
      });
    },
    saveIncludeCol(columnList) {
      let editRow = this.tableData.find(
        (item) => item.key === this.tableRowEditKey
      );
      editRow.columnList = columnList;
      this.handleFieldsChange("columnList", editRow);
    },
    cellclick(row, column) {
      if (this.editingData?.key !== row.key) {
        this.tableRowEditKey = row.key;
        this.edit(row);
      }
    },
    handleInput(val,name,row) {
      row[name] = val.replace(/[@!=]/g,'')
    },
    handleFieldsChange(name, row) {
      let value = row[name];
      if (name === "nullable") value = value ? 1 : 0;
      this.tableData.forEach((item) => {
        if (item.key === this.editingData?.key) {
          item[name] = value;
          if (item.editStatus !== "ADD") {
            item.editStatus = "MODIFY";
          }
        }
      });
    },
    rowDrop() {
      const tbody = document.querySelector(
        ".indexList .el-table__body-wrapper tbody"
      );
      const _this = this;
      Sortable.create(tbody, {
        animation: 150,
        draggable: ".indexList .el-table__row",
        onEnd({ newIndex, oldIndex }) {
          const currRow = _this.tableData.splice(oldIndex, 1)[0];
          const aa = _this.tableData.splice(newIndex, 0, currRow);
        },
      });
    },
    refresh() {
      this.getTableDetails(true);
    },
  },
};
</script>

<template>
  <div class="draggable indexList">
    <div class="table_btn_list">
      <el-button
        size="mini"
        type="text"
        style="margin-left: 0"
        @click="addData"
      >
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
      height="550"
      @cell-click="cellclick"
      v-loading="isLoading"
      :header-cell-style="{ background: '#f1f3f9', color: '#68728c' }"
      :row-class-name="tableRowClassName"
      border
    >
      <el-table-column type="index" label="序号"></el-table-column>
      <template v-for="(item, index) in columns">
        <el-table-column
          v-if="item.dataIndex === 'comment'? currentConfig && currentConfig.uniqueData.databaseType !== 'GBASE' : true"
          :key="index"
          :prop="item.dataIndex"
          :label="item.title"
          :width="item.width"
          :fixed="item.fixed"
        >
          <template slot-scope="{ row }">
            <template v-if="tableRowEditKey === row.key">
              <el-input
                v-if="item.dataIndex === 'tableName' || item.dataIndex === 'name'"
                v-model="row[item.dataIndex]"
                size="mini"
                :disabled="row.editStatus != 'ADD' || (row.editStatus == 'ADD' && item.dataIndex == 'tableName')"
                @change="handleFieldsChange(item.dataIndex, row)"
                @input="(val)=>handleInput(val,item.dataIndex,row)"
                show-word-limit
                maxlength="50"
              />
              <el-select
                v-else-if="item.dataIndex === 'type'"
                v-model="row[item.dataIndex]"
                placeholder="请选择"
                filterable
                :disabled="row.editStatus != 'ADD'"
                size="mini"
                @change="handleFieldsChange(item.dataIndex, row)"
              >
                <el-option
                  v-for="item in databaseSupportField.indexTypes"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                >
                </el-option>
              </el-select>
              <span v-else-if="item.dataIndex === 'columnList'">
                <el-button
                  @click="openIncludeColModal(row)"
                  size="mini"
                  type="text"
                  plain
                >
                  <img src="@/assets/main/3-con-ico03.png" alt="" />
                  编辑
                </el-button>
                <span>{{
                  row[item.dataIndex].map((item) => item.columnName).join(",")
                }}</span>
              </span>

              <el-input
                v-else-if="item.dataIndex === 'comment'"
                v-model="row[item.dataIndex]"
                :disabled="row.editStatus != 'ADD'"
                size="mini"
                maxlength="20"
                @change="handleFieldsChange(item.dataIndex, row)"
              />
            </template>
            <span v-else>
              {{item.dataIndex === "columnList" ? row[item.dataIndex].map((item) => item.columnName).join(",") : row[item.dataIndex]}}
            </span>
          </template>
        </el-table-column>
      </template>
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
    <!-- <el-button @click="addData" size="mini" icon="el-icon-plus" style="width: 100%; margin-top: 10px;">添加索引</el-button> -->
    <include-col-modal
      ref="includeColModal"
      @saveIncludeCol="saveIncludeCol"
      :columnList="columnList"
    />
  </div>
</template>

<style scoped lang="scss">
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
.delete-row {
  background: #fdd4cd;
}
.update-row {
  background: #f6ffed;
}
</style>
