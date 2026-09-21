<template>
  <div>
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
          <div class="el-upload__tip" slot="tip">只能上传.xls、 .xlsx文件，且不超过50MB</div>
        </el-upload>
      </el-form-item>
    </el-form>
    <div v-else>
      <el-tabs v-model="activeName">
        <el-tab-pane label="新增" name="add">
          <template v-if="add_importRounds == 2">
            <el-table
              :data="addTableData"
              class="importDialogTable"
              style="width: 100%"
              height="400"
              :header-cell-style="{ background: '#f1f3f9', color: '#68728c' }"
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
                        scope.$index == tabRowindex &&
                        item == tabColumnKey
                      "
                    @blur="inputBlurFn"
                  ></el-input>
                  <div
                    v-else
                    @click="getCell(scope.$index,item)"
                    :class="returnClass(scope.row[item + '_viewType'])"
                  >{{ scope.row[item] }}</div>
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
                  height="400"
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
                  height="400"
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
                      <div v-else @click="getCell(scope.$index,item)">{{ scope.row[item] }}</div>
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
                height="450"
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
                      v-if="scope.$index == tabRowindex &&item == tabColumnKey"
                      @blur="inputBlurFn"
                    ></el-input>
                    <div
                      v-else
                      @click="getCell(scope.$index,item)"
                      :class="returnClass(scope.row[item + '_viewType'])"
                    >{{ scope.row[item] }}</div>
                  </template>
                </el-table-column>
              </el-table>
              <i class="el-icon-right" style="font-size: 30px; padding: 15px"></i>
              <el-table
                ref="editRightTable"
                class="importDialogTable"
                :data="editRightTableData"
                style="width: 45%"
                height="450"
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
                    <div :class="returnClass(scope.row[item + '_viewType'])">{{ scope.row[item] }}</div>
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
                  height="400"
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
                  height="400"
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
                        v-if="scope.$index == tabRowindex && item == tabColumnKey"
                        @blur="inputBlurFn"
                      ></el-input>
                      <div v-else @click="getCell(scope.$index,item)">{{ scope.row[item] }}</div>
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
    </div>
    <div slot="footer" class="dialog-footer">
      <div
        class="colorInfo"
        v-if="importRounds == 2 && ((add_importRounds === 2 && activeName == 'add') ||
            (edit_importRounds === 2 && activeName == 'edit'))"
      >
        <div class="yellowCell activeCell"></div>改动
        <div class="redCell activeCell"></div>非空
        <div class="greenCell activeCell"></div>唯一
      </div>
      <el-button type="primary" size="small" v-if="importRounds === 1" @click="startImport">下一步</el-button>
      <el-button
        type="success"
        size="small"
        v-if="
            (add_importRounds === 2 && activeName == 'add') ||
            (edit_importRounds === 2 && activeName == 'edit')
          "
        @click="fileSave"
      >执行</el-button>
      <el-button
        type="success"
        size="small"
        v-if="
            ((add_importRounds === 3 && activeName == 'add') ||
              (edit_importRounds === 3 && activeName == 'edit')) &&
            activeTab == 'ERROR'
          "
        @click="executeFileData"
      >执行</el-button>
      <el-button @click="handleClose" size="small">关 闭</el-button>
    </div>
  </div>
</template>

<script>
export default {
  data() {
    return {
      importRules: {
        file: [{ required: true, message: "请选择文件", trigger: "change" }]
      }
    };
  },
  methods: {
    // 导入
    handleAvatarSuccess(response, file) {
      this.fileName = response.fileName;
    }
  }
};
</script>

<style lang='scss' scoped>
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
</style>
