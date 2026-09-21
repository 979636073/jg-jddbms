<script>

import {v4 as uuidv4} from "uuid";

let InitialData = {
  key: null,
  ascOrDesc: null, // 升序还是降序
  cardinality: null, // 基数
  collation: null, // 排序规则
  columnName: null, // 列名
  comment: null, // 注释
  filterCondition: null, // 过滤条件
  indexName: null, // 索引名
  indexQualifier: null, // 索引限定符
  nonUnique: null, // 是否唯一
  ordinalPosition: null, // 位置
  schemaName: null, // 模式名
  type: null, // 类型
  pages: null, // 页数

  databaseName: null, // 数据库名
  tableName: null, // 表名
}
export default {
  name: 'includeColModal',
  props: {
    columnList: {
      type: Array,
      default: []
    },
  },
  data() {
    return {
      // 当前修改行
      tableRowEditKey: '',
      editingKey: '',
      title: '包含列',
      dialogVisible: false,
      tableData: [],
      columns: [
        {
          title: '列名',
          dataIndex: 'columnName',
        }, {
          title: '排序',
          dataIndex: 'ascOrDesc',
        }
      ]
    }
  },
  methods: {
    addData() {
      let data = JSON.parse(JSON.stringify(InitialData))
      data.key = uuidv4()
      this.tableData.push(data)
      this.tableRowEditKey = data.key
      this.edit(data);
    },
    edit(record) {
      this.editingKey = record.key || null
    },
    cellclick(row, column) {
      if (this.editingData.key !== row.key) {
        this.tableRowEditKey = row.key
      }
    },
    deleteData(row, index) {
      this.tableData.splice(index, 1)
    },
    init(columnList) {
      this.tableData = JSON.parse(JSON.stringify(columnList))
      this.tableData.forEach(item => {
        if (!item.key) item.key = uuidv4()
      })
      this.dialogVisible = true
    },
    handleClose(done) {
      done();
    },
    dialogClose() {
      this.dialogVisible = false;
    },
    saveIncludeCol() {
      this.$emit('saveIncludeCol', this.tableData)
      this.dialogClose()
    }
  }
}
</script>

<template>
  <el-dialog
    :title="title"
    :visible.sync="dialogVisible"
    width="500"
    :before-close="handleClose"
  >
    <el-button @click="addData" size="mini" icon="el-icon-plus" style="margin-bottom: 10px;">新 增</el-button>
    <el-table
      size="mini"
      :data="tableData"
      row-key="name"
      @cell-click="cellclick"
    >
      <el-table-column
        type="index"
        width="50">
      </el-table-column>
      <el-table-column
        v-for="(item, index) in columns"
        :key="index"
        :prop="item.dataIndex"
        :label="item.title"
        :width="item.width"
        :fixed="item.fixed"
      >
        <template slot-scope="{ row }">
          <template v-if="tableRowEditKey === row.key">
            <el-select
              v-if="item.dataIndex === 'columnName'"
              v-model="row[item.dataIndex]"
              placeholder="请选择"
              filterable
              size="mini"
              @change="edit(row)"
            >
              <el-option
                v-for="item in columnList.map(item => {
                  return {
                    label: item.name,
                    value: item.name,
                  }
                })"
                :key="item.value"
                :label="item.label"
                :value="item.value">
              </el-option>
            </el-select>
            <el-select
              v-else-if="item.dataIndex === 'ascOrDesc'"
              v-model="row[item.dataIndex]"
              placeholder="请选择"
              filterable
              size="mini"
              @change="edit(row)"
            >
              <el-option
                v-for="item in [
                  { label: 'ASC', value: 'ASC' },
                  { label: 'DESC', value: 'DESC' },
                ]"
                :key="item.value"
                :label="item.label"
                :value="item.value">
              </el-option>
            </el-select>
          </template>
          <span v-else>
            {{ row[item.dataIndex] }}
          </span>
        </template>
      </el-table-column>
      <el-table-column width="120" label="操作">
        <template slot-scope="scope">
          <el-button @click="deleteData(scope.row, scope.$index)" size="mini" type="text" plain>
            <img src="@/assets/main/3-con-ico02.png" alt="">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <span slot="footer" class="dialog-footer">
      <el-button type="primary" plain @click="dialogClose">取 消</el-button>
      <el-button type="primary" @click="saveIncludeCol">确 定</el-button>
    </span>
  </el-dialog>
</template>

<style scoped lang="scss">

</style>
