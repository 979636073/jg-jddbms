<template>
  <div>
    <el-dialog
      :title="title"
      :model-value="editDialogVisible"
      :before-close="handleClose"
      width="640px"
    >
        <el-form
          label-position="left"
          label-width="125px"
          :model="form"
          ref="form"
          :rules="rules"
        >
          <div class="form-section">基本信息</div>
          <el-form-item label="表空间名称" prop="tableSpace">
            <el-input
              placeholder="请输入表空间名称"
              size="small"
              v-model="form.tableSpace"
              maxlength="128"
            >
            </el-input>
          </el-form-item>
          <el-form-item label="数据文件路径" prop="path">
            <el-input
              placeholder="请输入数据库服务器上的数据文件完整路径"
              size="small"
              class="path-input"
              v-model="form.path"
            >
            </el-input>
            <el-button
              size="small"
              @click="addSpace"
              >参考已有路径</el-button
            >
            <div class="form-hint">创建时可参考已有数据文件的目录，系统会生成新的文件名。</div>
          </el-form-item>

          <el-form-item label="初始容量" prop="totalSize">
            <el-input
              v-model="form.totalSize"
              size="small"
              class="input-with-select"
              type="number"
              min="1"
              placeholder="请输入大于零的容量"
            >
              <template #append><el-select
                v-model="form.sizeUnit"

                placeholder="单位"
                style="width: 112px"
              >
                <el-option
                  v-for="item in sizeUnits"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                >
                </el-option>
              </el-select></template>
            </el-input>
          </el-form-item>
          <div class="form-section">扩展设置</div>
          <el-form-item label="自动扩展">
            <el-checkbox v-model="autoCheck">空间不足时自动扩展</el-checkbox>
          </el-form-item>
          <el-form-item v-if="autoCheck" label="每次扩展容量">
            <el-input
              :disabled="!autoCheck"
              v-model="form.autoSize"
              size="small"
              class="input-with-select"
              type="number"
              min="1"
              placeholder="请输入大于零的容量"
            >
              <template #append><el-select
                :disabled="!autoCheck"
                v-model="form.autoSizeUnit"

                placeholder="单位"
                style="width: 112px"
              >
                <el-option
                  v-for="item in sizeUnits"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                >
                </el-option>
              </el-select></template>
            </el-input>
          </el-form-item>
          <el-form-item v-if="autoCheck" label="最大容量">
            <el-checkbox v-model="checked" :disabled="!autoCheck"
              >不设上限</el-checkbox
            >
          </el-form-item>
          <el-form-item v-if="autoCheck && !checked" label="容量上限">
            <el-input
              v-model="form.expandUpperLimit"
              :disabled="!autoCheck || checked"
              size="small"
              class="input-with-select"
              type="number"
              min="1"
              placeholder="请输入大于初始容量的上限"
            >
              <template #append><el-select
                v-model="form.maxSizeUnit"
                :disabled="!autoCheck || checked"

                placeholder="单位"
                style="width: 112px"
              >
                <el-option
                  v-for="item in sizeUnits"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                >
                </el-option>
              </el-select></template>
            </el-input>
          </el-form-item>
        </el-form>
      <template #footer><span  class="dialog-footer">
        <el-button @click="editHandleCloseFn(false)" size="small"
          >取消</el-button
        >
        <el-button type="primary" :loading="saving" @click="saveTableSpace" size="small"
          >{{ isEditing ? '保存修改' : '创建表空间' }}</el-button
        >
      </span></template>
    </el-dialog>
    <!-- 文件选择 -->
    <el-dialog
      title="参考已有数据文件路径"
      v-model="dialogSpace"
      width="800px"
      style="width: 100%"
      height="500"
    >
      <span>
        <p class="form-hint">{{ isEditing ? '请选择需要使用的数据文件。' : '选择已有文件后，只会参考其所在目录，不会复用已有文件。' }}</p>
        <el-table
          :data="tableData"
          highlight-current-row
          @current-change="handleCurrentChange"
          style="width: 100%"
          height="410"
          border
          v-loading="fileLoading"
        >
          <el-table-column
            prop="name"
            label="所属表空间"
            width="180"
            show-overflow-tooltip
          >
          </el-table-column>
          <el-table-column prop="path" label="数据文件路径" show-overflow-tooltip>
          </el-table-column>
        </el-table>
        <!-- <div style="margin-top: 10px; float: right"> -->
        <pagination
          v-show="total > 0"
          :total="total"
          v-model:page="queryParams.pageNo"
          v-model:limit="queryParams.pageSize"
          @pagination="getList"
        />
        <!-- </div> -->
      </span>
      <template #footer><span  class="dialog-footer">
        <el-button size="small" @click="dialogSpace = false">取消</el-button>
        <el-button type="primary" size="small" :disabled="!selectTable || !selectTable.path" @click="submit">使用所选路径</el-button>
      </span></template>
    </el-dialog>
  </div>
</template>

<script>
import tableSpaceServer from "@/api/main/tableSpace";

const initialForm = () => ({
  path: '', tableSpace: '', totalSize: '128', sizeUnit: 'MB',
  autoSize: '', autoSizeUnit: 'MB', expandUpperLimit: '', maxSizeUnit: 'MB',
  isUpdate: false,
});

export default {
  props: {
    editDialogVisible: {
      type: Boolean,
      default: false,
    },
    schema: {
      type: String,
      default: "",
    },
    saveType: {
      type: Number,
      default: 1,
    },
    getTableDataList: {
      type: Function,
    },
    dataBaseInfo: {
      type: Object,
      default: {},
    },
    spaceInfo: {
      type: Object,
      default: {},
    },
  },
  data() {
    return {
      sizeUnits: [
        { value: 'KB', label: '千字节' },
        { value: 'MB', label: '兆字节' },
        { value: 'GB', label: '吉字节' },
      ],
      rules: {
        tableSpace: [{ required: true, message: "请输入表空间名称", trigger: "blur" }],
        path: [{ required: true, message: "请输入数据文件路径", trigger: "blur" }],
        totalSize: [{ required: true, message: "请输入初始容量", trigger: "blur" }],
      },
      form: initialForm(),
      oldTableSpace: {},
      tableData: [],
      total: 0,
      checked: true,
      autoCheck: false,
      dialogSpace: false,
      selectTable: null,
      saving: false,
      fileLoading: false,
      queryParams: {
        pageNo: 1,
        pageSize: 10,
      },
      dataSourceId: "",
    };
  },
  computed: {
    isEditing() { return Number(this.saveType) === 2; },
    title() { return this.isEditing ? '修改表空间' : '新增表空间'; },
  },
  watch: {
    editDialogVisible(newVal) {
      if (newVal) {
        this.dataSourceId = this.dataBaseInfo.dataSourceId || this.dataBaseInfo.id;
        this.selectTable = null;
        this.checked = true;
        this.autoCheck = false;
        this.form = initialForm();
        if (this.isEditing) {
          this.oldTableSpace = JSON.parse(JSON.stringify(this.spaceInfo));
          this.form = { ...initialForm(), ...this.oldTableSpace, isUpdate: true };
          this.autoCheck = Boolean(this.oldTableSpace.autoCheck)
            || this.oldTableSpace.autoScaling === 'YES';
          this.checked = ['67108863', '0'].includes(String(this.oldTableSpace.expandUpperLimit || ''));
        } else {
          this.oldTableSpace = {};
        }
        this.$nextTick(() => this.$refs.form?.clearValidate());
      }
    },
  },
  methods: {
    editHandleCloseFn(val) {
      this.form = initialForm();
      this.autoCheck = false;
      this.checked = true;
      this.selectTable = null;
      this.$emit("editHandleCloseFn", val);
    },
    handleClose() {
      this.editHandleCloseFn(false);
    },
    addSpace() {
      this.selectTable = null;
      this.dialogSpace = true;
      this.getList();
    },
    async getList() {
      this.fileLoading = true;
      try {
        const res = await tableSpaceServer.getTableSpaceFileName({
          ...this.queryParams, dataSourceId: this.dataSourceId,
        });
        if (res.success) {
          this.tableData = res.data.data || [];
          this.total = res.data.total || 0;
        } else {
          this.$message.error('读取数据文件列表失败');
        }
      } catch (error) {
        this.$message.error('读取数据文件列表失败');
      } finally { this.fileLoading = false; }
    },
    handleCurrentChange(val) {
      this.selectTable = val;
    },
    submit() {
      if (!this.selectTable?.path) return;
      if (this.isEditing) {
        this.form.path = this.selectTable.path;
      } else {
        const name = this.form.tableSpace.trim();
        if (!name) {
          this.$message.warning('请先输入表空间名称');
          return;
        }
        const existingPath = this.selectTable.path;
        const separatorIndex = Math.max(existingPath.lastIndexOf('/'), existingPath.lastIndexOf('\\'));
        if (separatorIndex < 0) {
          this.$message.error('所选文件没有可用的目录，请手动输入路径');
          return;
        }
        const directory = existingPath.slice(0, separatorIndex + 1);
        const extension = /\.[^./\\]+$/.exec(existingPath)?.[0] || '.dbf';
        this.form.path = `${directory}${name.replace(/[\\/]/g, '_')}_DATA${extension}`;
      }
      this.dialogSpace = false;
      this.$nextTick(() => this.$refs.form?.validateField('path'));
    },
    sizeInMb(value, unit) {
      return Number(value) * ({ KB: 1 / 1024, MB: 1, GB: 1024 }[unit] || 1);
    },
    async saveTableSpace() {
      if (this.saving) return;
      const valid = await this.$refs.form.validate().catch(() => false);
      if (!valid) return;
      if (!Number.isFinite(Number(this.form.totalSize)) || Number(this.form.totalSize) <= 0) {
        this.$message.error('初始容量必须大于零');
        return;
      }
      if (this.autoCheck) {
        if (!Number.isFinite(Number(this.form.autoSize)) || Number(this.form.autoSize) <= 0) {
          this.$message.error('每次扩展容量必须大于零');
          return;
        }
        if (!this.checked) {
          if (!Number.isFinite(Number(this.form.expandUpperLimit)) || Number(this.form.expandUpperLimit) <= 0) {
            this.$message.error('请输入有效的容量上限');
            return;
          }
          if (this.sizeInMb(this.form.expandUpperLimit, this.form.maxSizeUnit)
              <= this.sizeInMb(this.form.totalSize, this.form.sizeUnit)) {
            this.$message.error('容量上限必须大于初始容量');
            return;
          }
        }
      }
      const payload = {
        ...this.form,
        dataSourceId: this.dataSourceId,
        autoSize: this.autoCheck ? this.form.autoSize : '',
        expandUpperLimit: this.autoCheck && !this.checked ? this.form.expandUpperLimit : '',
      };
      this.saving = true;
      try {
        const res = this.isEditing
          ? await tableSpaceServer.updateTableSpace({
            dataSourceId: this.dataSourceId, newTableSpace: payload, oldTableSpace: this.oldTableSpace,
          })
          : await tableSpaceServer.createTableSpace(payload);
        if (!res.success) {
          this.$message.error(this.isEditing ? '修改失败，请检查表空间设置及数据库权限' : '创建失败，请检查名称、数据文件路径、容量及数据库权限');
          return;
        }
        this.$modal.msgSuccess(this.isEditing ? '修改成功' : '创建成功');
        if (this.isEditing) this.$emit('refshTableData', payload.path);
        else this.getTableDataList?.(this.schema);
        this.editHandleCloseFn(false);
      } catch (error) {
        this.$message.error(this.isEditing ? '修改失败，请检查表空间设置及数据库权限' : '创建失败，请检查名称、数据文件路径、容量及数据库权限');
      } finally { this.saving = false; }
    },
  },
};
</script>

<style lang='scss' scoped>
.form-section {
  margin: 4px 0 14px;
  padding-left: 9px;
  border-left: 3px solid #409eff;
  color: #242f57;
  font-size: 13px;
  font-weight: 600;
}
.path-input { width: calc(100% - 114px); margin-right: 8px; }
.form-hint { margin: 4px 0 0; color: #68728c; font-size: 12px; line-height: 18px; }
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
  margin-bottom: 18px;
}
</style>
