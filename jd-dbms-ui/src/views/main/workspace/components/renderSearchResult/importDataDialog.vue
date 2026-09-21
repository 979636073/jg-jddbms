<template>
  <el-dialog
    title="批量导入确认"
    :visible.sync="dialogImportVisible"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :show-close="false"
    :before-close="handleClose"
    width="80%"
  >
    <div v-loading="isLoading" element-loading-text="努力加载中">
      <el-form
        ref="importForm"
        :rules="importRules"
        v-if="importRounds === 0"
        :model="importForm"
        :inline="true"
        label-width="160px"
      >
        <el-form-item prop="file">
          <el-input v-show="false" v-model="fileName" placeholder="请输入内容"></el-input>
          <el-upload
            class="upload-demo"
            ref="commonUpload"
            drag
            accept=".xls, .xlsx, .sql, .csv"
            :action="uploadImgUrl + '/common/upload'"
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
            <div class="el-upload__tip" slot="tip">只能上传.xls、 .xlsx文件，且不超过50MB</div>
          </el-upload>
        </el-form-item>
      </el-form>
      <div v-else-if="importRounds === 1" style="height:600px">
        <el-table :data="tableData" style="width: 100%" height="550" border>
          <el-table-column v-for="(item, index) in header_tables" :key="index" align="center">
            <template #header>
              <el-select
                v-model="item.value"
                clearable
                @change="(value) => filterHeader(value, index)"
                placeholder="请选择"
              >
                <el-option
                  v-for="item in options"
                  :key="item.name"
                  :label="item.name"
                  :value="item.name"
                ></el-option>
              </el-select>
              <!-- <el-checkbox
              class="isComparFieldCheckbox"
              v-model="item.isCompareField"
              :true-label="1"
              :false-label="0"
              ></el-checkbox>-->
            </template>
            <el-table-column
              show-overflow-tooltip
              :label="item.itemkey"
              :prop="item.itemkey"
              align="center"
              min-width="150"
            ></el-table-column>
          </el-table-column>
        </el-table>
        <el-pagination
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
          :current-page="queryParams.pageNum"
          :page-sizes="[20, 50, 100]"
          :page-size="queryParams.pageSize"
          layout="total, sizes, prev, pager, next, jumper"
          :total="queryParams.total"
        ></el-pagination>
      </div>
      <div v-else-if="importRounds === 2">
        <el-tabs v-model="activeName">
          <el-tab-pane label="新增" name="add">
            <el-table
              :data="addTableData"
              style="width: 100%"
              height="400"
              border
              :header-cell-style="{ background: '#f1f3f9', color: '#68728c' }"
              @cell-click="getCell"
              :cell-class-name="getRowCloumn"
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
                    size="mini"
                    v-model="scope.row[item]"
                    v-if="
                    scope.row.index == tabRowindex &&
                    scope.column.index == tabColumnIndex
                  "
                    @blur="inputBlurFn"
                  ></el-input>
                  <div v-else>{{ scope.row[item] }}</div>
                </template>
              </el-table-column>
            </el-table>
            <!-- <el-pagination
              style="float:right"
              @size-change="handleSize"
              @current-change="handleCurrent"
              :current-page="page.pageNum"
              :page-size="page.pageSize"
              layout="total, sizes, prev, pager, next, jumper"
              :total="page.total"
            >
            </el-pagination>-->
          </el-tab-pane>
          <el-tab-pane label="修改" name="edit">
            <el-table
              :data="editTableData"
              style="width: 100%"
              height="400"
              :header-cell-style="{ background: '#f1f3f9', color: '#68728c' }"
              border
              @cell-click="getCell"
              :cell-class-name="getRowCloumn"
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
                    size="mini"
                    v-model="scope.row[item]"
                    v-if="
                    scope.row.index == tabRowindex &&
                    scope.column.index == tabColumnIndex
                  "
                    @blur="inputBlurFn"
                  ></el-input>
                  <div v-else>{{ scope.row[item] }}</div>
                </template>
              </el-table-column>
            </el-table>
            <!-- <el-pagination
              style="float:right"
              @size-change="handleUpdateSize"
              @current-change="handleUpdateCurrent"
              :current-page="updatePage.pageNum"
              :page-size="updatePage.pageSize"
              layout="total, sizes, prev, pager, next, jumper"
              :total="updatePage.total"
            >
            </el-pagination>-->
          </el-tab-pane>
        </el-tabs>
      </div>
      <div v-else-if="importRounds === 3">
        <el-tabs v-model="activeName">
          <el-tab-pane label="新增" name="add">
            <el-tabs v-model="activeTab" type="card">
              <el-tab-pane label="成功" name="SUCCESS">
                <el-table
                  :data="addSuccessData"
                  style="width: 100%"
                  height="500"
                  :header-cell-style="{ background: '#f1f3f9', color: '#68728c' }"
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
                  :data="addSuccessData"
                  style="width: 100%"
                  height="400"
                  :header-cell-style="{ background: '#f1f3f9', color: '#68728c' }"
                  ref="errorTable"
                  @cell-click="getCell"
                  :cell-class-name="getRowCloumn"
                  border
                >
                  <el-table-column
                    v-for="(item, index) in headers"
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
                        scope.row.index == tabRowindex &&
                        scope.column.index == tabColumnIndex
                      "
                        @blur="inputBlurFn"
                      ></el-input>
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
          </el-tab-pane>
          <el-tab-pane label="修改" name="edit">
            <el-tabs v-model="activeTab" type="card">
              <el-tab-pane label="成功" name="SUCCESS">
                <el-table
                  :data="addSuccessData"
                  style="width: 100%"
                  height="500"
                  :header-cell-style="{ background: '#f1f3f9', color: '#68728c' }"
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
                  :data="addSuccessData"
                  style="width: 100%"
                  height="500"
                  :header-cell-style="{ background: '#f1f3f9', color: '#68728c' }"
                  @cell-click="getCell"
                  :cell-class-name="getRowCloumn"
                >
                  <el-table-column
                    v-for="(item, index) in headers"
                    :key="index"
                    show-overflow-tooltip
                    :label="item"
                    :prop="item"
                  >
                    <template slot-scope="scope">
                      <el-input
                        size="mini"
                        v-model="scope.row[item]"
                        v-if="scope.row.index == tabRowindex && scope.column.index == tabColumnIndex"
                        @blur="inputBlurFn"
                      ></el-input>
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
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>
    <!-- <div class="noData" v-show="!tableData.length">正在加载中...</div> -->

    <div slot="footer" class="dialog-footer">
      <el-button type="primary" size="small" v-if="importRounds === 3" @click="backRounds">上一步</el-button>
      <el-button
        type="primary"
        size="small"
        v-if="importRounds === 0 || importRounds === 1"
        @click="startImport(0)"
      >下一步</el-button>
      <el-button type="success" size="small" v-if="importRounds === 2" @click="fileSave">执行</el-button>
      <el-button
        type="success"
        size="small"
        v-if="importRounds === 3 && activeTab == 'ERROR'"
        @click="executeFileData"
      >执行</el-button>
      <el-button @click="handleClose" size="small">关 闭</el-button>
    </div>
  </el-dialog>
</template>

<script>
import { getToken } from "@/utils/auth";
import sqlServer from "@/api/main/sql";
import modelServer from "@/api/main/model";
import tableServer from "@/api/main/table";
import { v4 as uuidv4 } from "uuid";
export default {
  props: {
    dialogImportVisible: {
      type: Boolean,
      default: false
    },
    queryResultData: {
      type: Object,
      default: () => {}
    }
    // prestartImport: {
    //   type: Function,
    //   default: null
    // }
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
      importRounds: 1,
      isLoading: false,
      importForm: {
        haveTitle: true,
        startRow: 1,
        errorStop: false,
        errorRollback: false
      },
      importRules: {
        haveTitle: [
          { required: true, message: "请选择是否存在表头", trigger: "change" }
        ],
        startRow: [
          {
            required: true,
            message: "请输入从第几行开始导入",
            trigger: "blur"
          }
        ],
        errorStop: [
          { required: true, message: "请选择是否错误停止", trigger: "change" }
        ],
        errorRollback: [
          { required: true, message: "请选择是否错误回滚", trigger: "change" }
        ],
        file: [{ validator: checkFile, trigger: "change" }]
      },
      fileName: "",
      tableName: "",
      activeName: "add",
      activeTab: "SUCCESS",
      arrayHeader: [],
      addTableData: [],
      editTableData: [],
      table_headers: [],
      addSuccessData: [],
      dataIndex: [],
      headers: [],
      tableData: [],
      header_tables: [],
      header_list: [],
      fromTitle: [],
      toTitle: [],
      modelId: "",
      options: [],
      exoprtObj: {},
      tabRowindex: null,
      tabColumnIndex: null,
      customHeaders: {
        Authorization: "Bearer " + getToken()
      },
      uploadImgUrl: process.env.VUE_APP_BASE_API,
      page: {
        pageNum: 1,
        pageSize: 99999,
        total: 0
      },
      updatePage: {
        pageNum: 1,
        pageSize: 99999,
        total: 0
      },
      resPage: {
        pageNum: 1,
        pageSize: 20,
        total: 0
      },
      queryParams: {
        pageNum: 1,
        pageSize: 20,
        total: 0
      }
    };
  },
  mounted() {
    this.startImport(1);
  },
  methods: {
    handleSizeChange(val) {
      this.queryParams.pageNum = 1;
      this.queryParams.pageSize = val;
      this.getTableAllData();
    },
    handleCurrentChange(val) {
      this.queryParams.pageNum = val;
      this.getTableAllData();
    },
    handleSize(val) {
      this.page.pageSize = val;
      this.queryPageData(this.exoprtObj);
    },
    handleCurrent(val) {
      this.page.pageNum = val;
      this.queryPageData(this.exoprtObj);
    },
    handleUpdateSize(val) {
      this.updatePage.pageSize = val;
      this.queryPageData(this.exoprtObj);
    },
    handleUpdateCurrent(val) {
      this.updatePage.pageNum = val;
      this.queryPageData(this.exoprtObj);
    },
    clickSize(val) {
      this.resPage.pageSize = val;
      this.queryResultTableData();
    },
    ClickCurrent(val) {
      this.resPage.pageNum = val;
      this.queryResultTableData();
    },
    getCell(row, column, cell, event) {
      this.tabRowindex = row.index;
      this.tabColumnIndex = column.index;
    },
    getRowCloumn({ row, column, rowIndex, columnIndex }) {
      row.index = rowIndex;
      column.index = columnIndex;
    },
    inputBlurFn() {
      this.tabRowindex = null;
      this.tabColumnIndex = "";
    },
    handleAvatarSuccess(response, file) {
      this.fileName = response.fileName;
    },
    beforeAvatarUpload(file) {
      const isLt50M = file.size / 1024 / 1024 < 50;

      if (!isLt50M) {
        this.$message.error("上传文件大小不能超过 50MB!");
      }
      return isLt50M;
    },
    // 上一步
    backRounds() {
      this.importRounds = 2;
      this.queryPageData(this.exoprtObj);
    },
    fileSave() {
      this.addTableData?.forEach(item => {
        delete item.index;
      });
      this.editTableData?.forEach(item => {
        delete item.index;
      });
      const params = {
        dataSourceId: this.queryResultData.dataSourceId,
        schemaName: this.queryResultData.schemaName,
        name: this.queryResultData.name,
        insertDataList:
          this.activeName == "add" ? this.addTableData : undefined,
        updateDataList:
          this.activeName == "edit" ? this.editTableData : undefined,
        indexList: this.dataIndex
      };
      sqlServer.dataSaveExecute(params).then(res => {
        if (res.success) {
          this.importRounds = 3;
          this.queryResultTableData();
        } else {
          this.$message.error(res.errorCode);
        }
      });
    },
    executeFileData() {
      this.addSuccessData.forEach(item => {
        delete item.index && delete item.errorMessage;
      });
      const params = {
        dataSourceId: this.queryResultData.dataSourceId,
        schemaName: this.queryResultData.schemaName,
        name: this.queryResultData.name,
        insertDataList:
          this.activeName == "add" ? this.addSuccessData : undefined,
        updateDataList:
          this.activeName == "edit" ? this.addSuccessData : undefined,
        indexList: this.dataIndex
      };
      sqlServer.dataSaveExecute(params).then(res => {
        if (res.success) {
          this.activeTab = "SUCCESS";
        } else {
          this.$message.error(res.errorCode);
        }
      });
    },
    getTableAllData() {
      this.isLoading = true;
      const formData = new FormData();
      formData.append("dataSourceId", this.queryResultData.dataSourceId);
      formData.append("schemaName", this.queryResultData.schemaName);
      formData.append("name", this.queryResultData.name);
      // formData.append("file", this.queryResultData.fileName);
      formData.append("ref", true);
      formData.append("size", this.queryParams.pageSize);
      formData.append("page", this.queryParams.pageNum);
      sqlServer.previewTableAllPage(formData).then(res => {
        this.tableData = res.data.dataList;
        this.isLoading = false;
      });
    },
    async startImport(id) {
      this.isLoading = true;
      if (id == 1) {
        const params = {
          // 获取下拉框数据集
          dataSourceId: this.queryResultData.dataSourceId,
          databaseName: this.queryResultData.databaseName,
          schemaName: this.queryResultData.schemaName,
          tableName: this.queryResultData.name
        };
        let res = await tableServer.getAllFieldByTable(params);
        // this.options = res.data.map(item => {
        //   return {
        //     // number: item.number,
        //     name: item.name,
        //     columnType: item.columnType
        //   };
        // });
        this.options = res.data;
        const formData = new FormData();
        formData.append("dataSourceId", this.queryResultData.dataSourceId);
        formData.append("schemaName", this.queryResultData.schemaName);
        formData.append("name", this.queryResultData.name);
        formData.append("file", this.queryResultData.fileName);
        formData.append("ref", true);
        formData.append("size", this.queryParams.pageSize);
        formData.append("page", this.queryParams.pageNum);
        sqlServer.previewTableAllData(formData).then(res => {
          this.exoprtObj = res;
          if (res.code == 200) {
            this.queryParams.total = res.data.total;
            // this.importRounds = 1;
            this.header_list = res.data.heardList;
            this.header_tables = res.data.heardList.map((item, index) => {
              let obj = { ...item };
              obj.value = "";
              obj.columnType = "";
              obj.isCompareField = 0;
              obj.itemkey = item.name;
              obj.number = index + 1;
              (obj.oldValue = ""), (obj.newValue = "");
              return obj;
            });
            this.tableData = res.data.dataList;
            this.isLoading = false;
            this.querySelectHeaderData();
          }
        });

        // } else
        if (this.importRounds == 10) {
          this.fromTitle = [];
          this.toTitle = [];
          this.header_tables.forEach(item => {
            this.fromTitle.push({ name: item.key, number: item.number });
            this.toTitle.push({
              name: item.value,
              number: item.number,
              isCompareField: item.isCompareField == 1 ? true : false,
              columnType: item.columnType
            });
          });
          const params = {
            fromModelTableVOS: this.fromTitle, // ex 表头 [{name；xx ,number 1 }]
            toModelTableVOS: this.toTitle, // 第一行 [{{name；xx ,number 1 }}]
            modelName: uuidv4(), // uuID
            toDataBaseId: this.queryResultData.dataSourceId,
            toSchema: this.queryResultData.schemaName, //模式名
            toTable: this.queryResultData.name, //表名
            modelType: 3,
            enabled: false,
            isExecute: true,
            conflictRules: 1
          };
          modelServer.nextField(params).then(res => {
            if (res.success) {
              this.modelId = res.data.id;
              this.executeData();
            } else {
              this.$message.error(res.errorCode);
            }
          });
        }
      } else {
        let flag = false;
        // debugger
        for (let index = 0; index < this.header_tables.length; index++) {
          const element = this.header_tables[index];
          if (!element.value) {
            flag = true;
            continue;
          }
        }
        //  if(!flag){
        this.$parent.activeName = "add";
        this.$parent.add_importRounds = 2;
        this.$parent.edit_importRounds = 2;
        this.$parent.importRounds = 2;
        this.$parent.importColumns = this.header_tables.map(item => item.value);
        this.$emit("prestartImport", this.header_list, this.header_tables);
        // this.prestartImport(this.header_list, this.header_tabless);
        //  }
        //  else{
        //    this.$message.error('请选择表头');
        //  }
      }
    },

    // 执行
    executeData() {
      const params = {
        modelId: this.modelId ? this.modelId : undefined, // 上次结果的Id
        file: this.fileName // 文件名
      };
      modelServer.executeFile(params).then(res => {
        if (res.data) {
          this.$router.push({
            path: "/model/common",
            query: {
              taskFlag: 1,
              modelId: this.modelId
            }
          });
        }
      });
    },

    querySelectHeaderData() {
      // setTimeout(()=> {

      this.header_tables.map((item, i) => {
        let obj = { name: item.itemkey, number: i + 1 };
        this.arrayHeader.push(obj);
      });
      const params = {
        dataSourceId: this.queryResultData.dataSourceId,
        schemaName: this.queryResultData.schemaName,
        toTableName: this.queryResultData.name,
        columnWHVOS: this.arrayHeader
      };
      modelServer.getAimTableData(params).then(res => {
        if (res.success) {
          res.data.forEach((item, index) => {
            if (this.header_tables[item.number - 1]) {
              this.header_tables[item.number - 1].value = item.name;
              this.header_tables[item.number - 1].columnType = item.columnType;
            }

            // return {
            //   number: item.number,
            //   name: item.name,
            //   columnType: item.columnType
            // };
          });

          // this.header_tables.sort(this.compare('number'))
          // console.log(this.header_tables,'this.header_tables');
        }
      });
      // },500)
    },
    // 根据字段排序
    compare(val) {
      return function(a, b) {
        let value1 = a[val];
        let value2 = b[val];
        return value1 - value2;
      };
    },
    filterHeader(value, index) {
      this.header_tables.forEach((item, i) => {
        if (item.value == value && i != index) {
          item.value = "";
        }
      });
    },
    queryPageData(res) {
      const formData = new FormData();
      formData.append("dataSourceId", this.queryResultData.dataSourceId);
      formData.append("schemaName", this.queryResultData.schemaName);
      formData.append("name", this.queryResultData.name);
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
      sqlServer.queryPageTable(formData).then(response => {
        if (response.data) {
          this.dataIndex = response.data.dataIndex;
          if (response.data.dataList.length) {
            let keys = Object.keys(response.data.dataList[0]);
            this.table_headers = keys;
          }
          if (res.data.insertDataList.length) {
            this.page.total = response.data.total;
            this.addTableData = response.data.dataList;
          } else if (res.data.updateDataList.length) {
            this.updatePage.total = response.data.total;
            this.editTableData = response.data.dataList;
          }
        } else {
          this.addTableData = [];
          this.editTableData = [];
          this.page.total = 0;
          this.updatePage.total = 0;
        }
      });
    },
    queryResultTableData() {
      const formData = new FormData();
      formData.append("dataSourceId", this.queryResultData.dataSourceId);
      formData.append("schemaName", this.queryResultData.schemaName);
      formData.append("name", this.queryResultData.name);
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
      sqlServer.queryPageTable(formData).then(res => {
        this.addSuccessData = [];
        if (res.data) {
          if (res.data.dataList.length) {
            let keys = Object.keys(res.data.dataList[0]);
            this.headers = keys;
          }
          this.resPage.total = res.data.total;
          this.addSuccessData = res.data.dataList;
        }
      });
    },
    handleClose() {
      // modelServer.clearMappingData().then(() => {});
      this.databaseImportInfo = {
        logFile: "",
        errorNum: 0,
        message: []
      };
      this.fileName = "";
      this.queryParams = {
        pageNum: 1,
        pageSize: 20,
        total: 0
      };
      this.$refs.commonUpload?.clearFiles();
      this.$emit("handleClose");
    }
  },
  watch: {
    activeName: {
      handler() {
        this.addSuccessData = [];
        this.queryPageData(this.exoprtObj);
        if (this.importRounds == 3) {
          this.queryResultTableData();
          this.resPage.pageSize = 20;
          this.resPage.pageNum = 1;
          this.resPage.total = 0;
        }
      }
    },
    activeTab: {
      handler() {
        this.queryResultTableData();
        this.resPage.pageSize = 20;
        this.resPage.pageNum = 1;
        this.resPage.total = 0;
      }
    }
  }
};
</script>

<style lang='scss' scoped>
::v-deep {
  .el-dialog__body {
    padding: 0px 20px;
  }
  .el-table--medium .el-table__cell {
    padding: 3px;
  }
  .el-checkbox__input {
    border-radius: 50% !important;
  }
}
.noData {
  font-size: 20px;
  width: 100%;
  height: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
}
.table_box {
  display: flex;
  justify-content: center;
  align-items: center;
}
::v-deep .isComparFieldCheckbox {
  margin-left: 15px;
  .el-checkbox__inner {
    border-radius: 50%;
    &::after {
      height: 5px;
      width: 5px;
      left: 50%;
      top: 50%;
      transform: translate(-50%, -50%);
      background: #fff;
      border-radius: 50%;
    }
  }
}
</style>
