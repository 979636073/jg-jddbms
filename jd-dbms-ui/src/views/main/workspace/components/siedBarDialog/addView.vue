<template>
  <el-dialog class="view-create-dialog"
    :title="viewKind === 'materialized' ? '新增物化视图' : '新增普通视图'"
    :model-value="viewDialogVisible" :before-close="handleClose" width="760px">
    <div class="view-create-tabs">
      <button :class="{ active: editorMode === 'guided' }" type="button" @click="editorMode = 'guided'">可视化创建</button>
      <button :class="{ active: editorMode === 'sql' }" type="button" @click="editorMode = 'sql'">直接输入 SQL</button>
    </div>
    <div class="view-create-body">
      <template v-if="editorMode === 'guided'">
        <div class="view-create-section-title">基本信息</div>
        <el-form class="view-create-grid" label-position="top" size="small">
          <el-form-item label="所属模式"><el-input :model-value="schema" disabled /></el-form-item>
          <el-form-item label="视图名称（必填）"><el-input v-model.trim="form.name" placeholder="请输入视图名称" /></el-form-item>
        </el-form>
        <template v-if="viewKind === 'normal'">
          <div class="view-create-options">
            <el-checkbox v-model="form.orReplace">存在时替换</el-checkbox>
            <el-checkbox v-model="form.force">强制创建</el-checkbox>
          </div>
          <div class="view-create-section-title">列与访问约束</div>
          <el-form class="view-create-grid" label-position="top" size="small">
            <el-form-item label="视图列名（可选，逗号分隔）"><el-input v-model="form.columns" placeholder="留空时沿用 SELECT 列名" /></el-form-item>
            <el-form-item label="访问约束">
              <el-select v-model="form.restriction" style="width: 100%">
                <el-option label="无" value="none" />
                <el-option label="只读" value="readOnly" />
                <el-option label="检查更新条件" value="check" />
              </el-select>
            </el-form-item>
          </el-form>
        </template>
        <template v-else>
          <div class="view-create-section-title">物化与刷新</div>
          <el-form class="view-create-grid" label-position="top" size="small">
            <el-form-item label="构建时机">
              <el-select v-model="form.buildMode" style="width: 100%">
                <el-option label="立即构建" value="IMMEDIATE" />
                <el-option label="延迟构建" value="DEFERRED" />
              </el-select>
            </el-form-item>
            <el-form-item label="刷新方式">
              <el-select v-model="form.refreshMode" style="width: 100%">
                <el-option label="完全刷新" value="COMPLETE" />
                <el-option label="自动选择" value="FORCE" />
                <el-option label="快速刷新（需物化视图日志）" value="FAST" />
              </el-select>
            </el-form-item>
            <el-form-item label="刷新触发">
              <el-select v-model="form.refreshOn" style="width: 100%">
                <el-option label="按需刷新" value="DEMAND" />
                <el-option label="提交时刷新" value="COMMIT" />
              </el-select>
            </el-form-item>
          </el-form>
          <p class="view-create-note">快速刷新依赖源表日志；延迟构建后需手动完成首次刷新。</p>
        </template>
        <div class="view-create-section-title">查询定义（必填）</div>
        <el-input v-model="form.query" type="textarea" :rows="7" placeholder="SELECT ... FROM ..." />
        <div class="view-create-section-title">生成的 SQL</div>
        <pre class="view-create-preview">{{ previewSql }}</pre>
      </template>
      <template v-else>
        <p class="view-create-note">可输入完整建视图语句；复杂选项也可在此自行指定。</p>
        <el-input v-model="searchParams.textarea" type="textarea" :rows="16"
          :placeholder="viewKind === 'materialized' ? 'CREATE MATERIALIZED VIEW ... AS SELECT ...' : 'CREATE VIEW ... AS SELECT ...'" />
      </template>
    </div>
    <template #footer><span  class="dialog-footer">
      <el-button size="small" @click="viewCloseFn(false)">取消</el-button>
      <el-button type="primary" size="small" :loading="submitting" @click="addViewFn">创建视图</el-button>
    </span></template>
  </el-dialog>
</template>

<script>
import viewServer from "@/api/main/view";
import { buildViewSql } from "./viewCreateSql";

const emptyForm = () => ({ name: "", query: "", columns: "", orReplace: false, force: false,
  restriction: "none", buildMode: "IMMEDIATE", refreshMode: "COMPLETE", refreshOn: "DEMAND" });

export default {
  props: {
    viewDialogVisible: { type: Boolean, default: false },
    dataBaseInfo: { type: Object, default: () => ({}) },
    schema: { type: String, default: "" },
    viewKind: { type: String, default: "normal" },
    getTableDataList: { type: Function },
    uploadFormInfo: { type: Object },
  },
  data() {
    return { editorMode: "guided", form: emptyForm(), searchParams: { textarea: "" }, submitting: false };
  },
  computed: {
    previewSql() {
      try { return buildViewSql(this.form, this.schema, this.viewKind); }
      catch (error) { return "填写视图名称和 SELECT 查询后显示 SQL 预览"; }
    },
  },
  watch: {
    uploadFormInfo: {
      handler(val) {
        if (val?.textarea) {
          this.searchParams.textarea = val.textarea;
          this.editorMode = "sql";
        }
      },
      immediate: true,
      deep: true,
    },
  },
  methods: {
    viewCloseFn(val) {
      this.form = emptyForm();
      this.searchParams.textarea = "";
      this.editorMode = "guided";
      this.$emit("viewCloseFn", val);
    },
    handleClose() { this.viewCloseFn(false); },
    async addViewFn() {
      let sql;
      try {
        sql = this.editorMode === "guided" ? buildViewSql(this.form, this.schema, this.viewKind) : this.searchParams.textarea.trim();
      } catch (error) {
        this.$message.error(error.message);
        return;
      }
      const validType = this.viewKind === "materialized"
        ? /^CREATE\s+MATERIALIZED\s+VIEW\b/i.test(sql)
        : /^CREATE\s+(?:OR\s+REPLACE\s+)?(?:FORCE\s+)?VIEW\b/i.test(sql);
      if (!validType) {
        this.$message.error(this.viewKind === "materialized" ? "请输入 CREATE MATERIALIZED VIEW 语句" : "请输入 CREATE VIEW 语句");
        return;
      }
      this.submitting = true;
      try {
        const res = await viewServer.addViewFn({ dataSourceId: this.dataBaseInfo.id, schemaName: this.schema, viewSql: sql });
        if (!res.success || !res.data?.[0]?.success) {
          this.$message.error(res.data?.[0]?.message || res.errorMessage || "新建失败");
          return;
        }
        this.$message.success(this.uploadFormInfo?.textarea ? "导入成功" : "新建成功");
        this.$emit("created");
        this.viewCloseFn(false);
      } catch (error) {
        this.$message.error(error?.message || "新建失败");
      } finally {
        this.submitting = false;
      }
    },
  },
};
</script>

<style lang="scss" scoped>
::v-deep .view-create-dialog .el-dialog__header { border-bottom: 1px solid #e5e9f2; }
::v-deep .view-create-dialog .el-dialog__body { padding: 0 24px; }
::v-deep .view-create-dialog .el-dialog__footer { border-top: 1px solid #e5e9f2; }
.view-create-tabs { display: flex; gap: 24px; border-bottom: 1px solid #e5e9f2; }
.view-create-tabs button { border: 0; background: none; padding: 14px 0 12px; color: #69788e; cursor: pointer; font-size: 13px; }
.view-create-tabs button.active { color: #006fff; border-bottom: 2px solid #006fff; font-weight: 600; }
.view-create-body { max-height: min(64vh, 650px); overflow-y: auto; padding: 16px 2px 20px; }
.view-create-section-title { color: #26354a; font-size: 13px; font-weight: 600; margin: 4px 0 12px; }
.view-create-section-title:not(:first-child) { margin-top: 20px; }
.view-create-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 16px; }
.view-create-grid ::v-deep .el-form-item { margin-bottom: 10px; }
.view-create-grid ::v-deep .el-form-item__label { padding-bottom: 4px; font-size: 12px; }
.view-create-options { display: flex; gap: 20px; margin: 2px 0 18px; }
.view-create-options ::v-deep .el-checkbox { margin-right: 0; }
.view-create-note { margin: 0 0 12px; color: #69788e; font-size: 12px; }
.view-create-preview { min-height: 65px; margin: 0; padding: 12px; background: #f6f9fc; border: 1px solid #e5e9f2; border-radius: 3px; white-space: pre-wrap; overflow-wrap: anywhere; font-size: 12px; color: #435169; }
</style>
