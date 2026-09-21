<script>
import Sortable from "sortablejs";
import { v4 as uuidv4 } from "uuid";

let InitialData = {
  key: null,
  oldName: null,
  name: "",
  tableName: null,
  columnType: null,
  dataType: null,
  defaultValue: null,
  autoIncrement: null,
  comment: null,
  primaryKey: null,
  primaryKeyOrder: null,
  schemaName: null,
  databaseName: null,
  typeName: null,
  columnSize: null,
  bufferLength: null,
  decimalDigits: null,
  numPrecRadix: null,
  nullableInt: null,
  sqlDataType: null,
  sqlDatetimeSub: null,
  charOctetLength: null,
  ordinalPosition: null,
  nullable: null,
  generatedColumn: null,
  charSetName: null,
  collationName: null,
  value: null,
  editStatus: "ADD"
};
export default {
  name: "ColumnList",
  props: {
    databaseSupportField: {
      type: Object
    },
    tableData: {
      type: Array,
      default: []
    }
  },
  data() {
    return {
      // 当前修改行
      tableRowEditKey: "",
      columns: [
        {
          title: "列名",
          dataIndex: "name",
          width: "160px"
        },
        {
          title: "类型",
          dataIndex: "columnType",
          width: "200px"
        },
        {
          title: "长度",
          dataIndex: "columnSize",
          width: "120px"
        },
        {
          title: "可空",
          dataIndex: "nullable",
          width: "100px"
        },
        {
          title: "键",
          dataIndex: "primaryKey",
          width: "50px"
        },
        {
          title: "注释",
          dataIndex: "comment"
        }
      ],
      editingData: null,
      editingConfig: null
    };
  },
  computed: {
    currentDataSource() {
      return this.$store.state.workspace.currentConnectionDetails;
    }
  },
  mounted() {
    this.rowDrop();
  },
  methods: {
    addData() {
      let data = JSON.parse(JSON.stringify(InitialData));
      data.key = uuidv4();
      this.tableData.push(data);
      this.tableRowEditKey = data.key;
      this.edit(data);
    },
    edit(record) {
      if (record.key) {
        this.editingData = record;
        // 根据当前字段类型，设置编辑配置
        this.databaseSupportField.columnTypes.forEach(i => {
          if (i.typeName === record.columnType) {
            this.editingConfig = {
              ...i,
              editKey: record.key
            };
          }
        });
      }
    },
    deleteData(row, index) {
      if (row.editStatus === "ADD") {
        this.tableData.splice(index, 1);
      } else {
        this.tableData.forEach(item => {
          if (item.key === row?.key) {
            this.editingData = null;
            this.editingConfig = null;
            item.editStatus = "DELETE";
          }
        });
      }
    },
    cellclick(row, column) {
      if (
        this.currentDataSource.type === "SQLITE" &&
        row.editStatus !== "Add"
      ) {
        return;
      }
      if (this.editingData?.key !== row.key) {
        this.tableRowEditKey = row.key;
        this.edit(row);
      }
    },
    handelPrimaryKey(_data) {
      let newData = this.tableData.map(item => {
        let primaryKeyOrder = item.primaryKeyOrder;

        // 取消主键if
        if (_data.primaryKey) {
          // 如果取消的时当前的字段，主键顺序为null
          if (_data.key === item.key) {
            primaryKeyOrder = null;
          } else {
            // 如果当前字段是主键，取消主键的时候，比当前字段顺序大的字段顺序-1
            if (
              _data.primaryKeyOrder &&
              item.primaryKeyOrder &&
              item.primaryKeyOrder >= _data.primaryKeyOrder
            ) {
              primaryKeyOrder = item.primaryKeyOrder - 1;
            }
          }
        } else {
          // 增加主键if
          // 增加主键的时候，主键顺序为当前表的最大主键顺序+1
          if (_data.key === item.key) {
            primaryKeyOrder =
              Math.max(
                ...this.tableData.map(i => {
                  return i.primaryKeyOrder || 0;
                })
              ) + 1;
          }
          // 对于当前字段之前的字段，主键顺序不变
        }

        if (item.key === _data?.key) {
          // 判断当前数据是新增的数据还是编辑后的数据
          let editStatus = item.editStatus;
          if (editStatus !== "ADD") {
            editStatus = "MODIFY";
          }

          const editingDataItem = {
            ...item,
            primaryKey: !item.primaryKey,
            primaryKeyOrder,
            nullable: !item.primaryKey ? 0 : item.nullable,
            editStatus
          };
          return editingDataItem;
        }
        return {
          ...item,
          primaryKeyOrder
        };
      });

      this.$emit("setColumnList", newData);
    },
    handelNullable(_data) {
      this.tableData.forEach(item => {
        if (item.key === _data?.key) {
          // 判断当前数据是新增的数据还是编辑后的数据
          let editStatus = item.editStatus;
          if (editStatus !== "ADD") {
            editStatus = "MODIFY";
          }
          item.editStatus = editStatus;
        }
      });
    },
    handleFieldsChange(name, row) {
      let value = row[name];
      if (name === "nullable") value = value ? 1 : 0;
      this.tableData.forEach(item => {
        if (item.key === this.editingData?.key) {
          item[name] = value;
          // 判断当前数据是新增的数据还是编辑后的数据
          if (item.editStatus !== "ADD") {
            item.editStatus = "MODIFY";
          }

          if (name === "columnType") {
            // 根据当前字段类型，设置编辑配置
            this.databaseSupportField.columnTypes.forEach(i => {
              if (i.typeName === value) {
                this.editingConfig = {
                  ...this.editingConfig,
                  ...i
                };
              }
            });
            // 特殊处理VARCHAR的默认长度 为255
            if (value === "VARCHAR" && item.columnSize === null) {
              item.columnSize = 255;
            }
          }
        }
      });
    },
    rowDrop() {
      const tbody = document.querySelector(
        ".scriptInfo .el-table__body-wrapper tbody"
      );
      const _this = this;
      Sortable.create(tbody, {
        animation: 150,
        draggable: ".scriptInfo .el-table__row",
        onEnd({ newIndex, oldIndex }) {
          const currRow = _this.tableData.splice(oldIndex, 1)[0];
          const aa = _this.tableData.splice(newIndex, 0, currRow);
        }
      });
    }
  }
};
</script>

<template>
  <div class="draggable scriptInfo">
    <el-table
      size="mini"
      :data="tableData.filter(tabelItem => tabelItem.editStatus !== 'DELETE')"
      row-key="key"
      @cell-click="cellclick"
      height="calc(100% - 300px)"
    >
      <el-table-column
        v-for="(item, index) in columns"
        :key="index"
        :prop="item.dataIndex"
        :label="item.title"
        :width="item.width"
        :fixed="item.fixed"
      >
        <template slot-scope="{ row }">
          <template v-if="item.dataIndex === 'nullable' || item.dataIndex === 'primaryKey'">
            <el-checkbox
              v-if="item.dataIndex === 'nullable'"
              v-model="row[item.dataIndex]"
              @change="() => {
                if (currentDataSource.type === 'SQLITE' && row.editStatus !== 'ADD') {
                  return null;
                }
                handelNullable(row);
                handleFieldsChange(item.dataIndex, row)
              }"
              :true-label="1"
              :false-label="0"
              :disabled="
                (editingConfig && editingConfig.supportNullable === false) ||
                row.primaryKey ||
                (currentDataSource.type === 'SQLITE' && row.editStatus !== 'ADD')
              "
            ></el-checkbox>
            <div v-else-if="item.dataIndex === 'primaryKey'">
              <div
                :class="{
                  keyBox: true,
                  disabledKeyBox: currentDataSource.type === 'SQLITE' && row.editStatus !== 'ADD'
                }"
                @click="() => {
                  if (currentDataSource.type === 'SQLITE' && row.editStatus !== 'ADD') {
                    return null
                  }
                  handelPrimaryKey(row)
                }"
              >
                <template v-if="row.primaryKey">
                  <i class="icon iconfont">&#xe775;</i>
                  <span>{{ row.primaryKeyOrder }}</span>
                </template>
              </div>
            </div>
          </template>
          <template v-else-if="tableRowEditKey === row.key">
            <el-input
              v-if="item.dataIndex === 'name'"
              v-model="row[item.dataIndex]"
              size="mini"
              @change="handleFieldsChange(item.dataIndex, row)"
            />
            <el-select
              v-else-if="item.dataIndex === 'columnType'"
              v-model="row[item.dataIndex]"
              placeholder="请选择"
              filterable
              size="mini"
              @change="() => {
                edit(row)
                handleFieldsChange(item.dataIndex, row)
              }"
            >
              <el-option
                v-for="item in databaseSupportField.columnTypes"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              ></el-option>
            </el-select>
            <el-input
              v-else-if="item.dataIndex === 'columnSize'"
              v-model="row[item.dataIndex]"
              type="number"
              size="mini"
              :disabled="!editingConfig || !editingConfig.supportLength"
              @change="handleFieldsChange(item.dataIndex, row)"
            ></el-input>
            <el-input
              v-else-if="item.dataIndex === 'comment'"
              v-model="row[item.dataIndex]"
              size="mini"
              :disabled="editingConfig && !editingConfig.supportComments"
              @change="handleFieldsChange(item.dataIndex, row)"
            />
          </template>
          <span v-else>{{ row[item.dataIndex] }}</span>
        </template>
      </el-table-column>
      <el-table-column width="120" label="操作">
        <template slot-scope="scope">
          <el-button @click="deleteData(scope.row, scope.$index)" size="mini" type="text" plain>
            <img src="@/assets/main/3-con-ico02.png" alt />
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-button
      @click="addData"
      size="mini"
      icon="el-icon-plus"
      style="width: 100%; margin-top: 10px;"
    >添加列</el-button>

    <el-form
      style="
        width: 400px; height: 200px;
        position: absolute;
        bottom: 0;
        left: 50%;
        margin-left: -200px;
        display: flex;
        flex-flow: column;
      "
      size="mini"
      ref="form"
      :model="editingData"
      label-width="80px"
    >
      <el-form-item v-if="editingConfig && editingConfig.supportAutoIncrement" label="是否自增">
        <el-checkbox
          @change="handleFieldsChange('autoIncrement', editingData)"
          v-model="editingData.autoIncrement"
        ></el-checkbox>
      </el-form-item>
      <el-form-item v-if="currentDataSource.type === 'SQLSERVER'" label="稀疏">
        <el-checkbox
          @change="handleFieldsChange('sparse', editingData)"
          v-model="editingData.sparse"
        ></el-checkbox>
      </el-form-item>
      <el-form-item v-if="editingConfig && editingConfig.supportDefaultValue" label="默认值">
        <el-select
          @change="handleFieldsChange('defaultValue', editingData)"
          v-model="editingData.defaultValue"
          allow-create
          filterable
          clearable
          placeholder="请选择"
        >
          <el-option
            v-for="item in databaseSupportField.defaultValues"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          ></el-option>
        </el-select>
      </el-form-item>
      <el-form-item v-if="editingConfig && editingConfig.supportCharset" label="字符集">
        <el-select
          @change="handleFieldsChange('charSetName', editingData)"
          v-model="editingData.charSetName"
          filterable
          clearable
          placeholder="请选择"
        >
          <el-option
            v-for="item in databaseSupportField.charsets"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          ></el-option>
        </el-select>
      </el-form-item>
      <el-form-item v-if="editingConfig && editingConfig.supportCollation" label="排序规则">
        <el-select
          @change="handleFieldsChange('collationName', editingData)"
          v-model="editingData.collationName"
          filterable
          clearable
          placeholder="请选择"
        >
          <el-option
            v-for="item in databaseSupportField.collations"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          ></el-option>
        </el-select>
      </el-form-item>
      <el-form-item v-if="editingConfig && editingConfig.supportScale" label="小数点">
        <el-input
          @change="handleFieldsChange('decimalDigits', editingData)"
          type="number"
          v-model="editingData.decimalDigits"
        />
      </el-form-item>
      <el-form-item v-if="editingConfig && editingConfig.supportUnit" label="单位">
        <el-select
          @change="handleFieldsChange('unit', editingData)"
          v-model="editingData.unit"
          filterable
          clearable
          placeholder="请选择"
        >
          <el-option
            v-for="item in [{ label: 'CHAR', value: 'CHAR'}, { label: 'BYTE', value: 'BYTE' }]"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          ></el-option>
        </el-select>
      </el-form-item>
      <el-form-item v-if="editingConfig && editingConfig.supportValue" label="值">
        <el-input
          @change="handleFieldsChange('value', editingData)"
          type="number"
          v-model="editingData.value"
        />
      </el-form-item>
    </el-form>
  </div>
</template>

<style scoped lang="scss">
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
