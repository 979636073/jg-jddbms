<template>
  <el-dialog title="新增模式" :visible="visible" :before-close="close" width="460px">
    <el-form ref="form" :model="form" :rules="rules" label-width="90px" @submit.native.prevent>
      <el-form-item label="模式名称" prop="name">
        <el-input v-model="form.name" size="small" maxlength="128" placeholder="请输入模式名称" @keyup.enter.native="save" />
      </el-form-item>
      <el-form-item label="所属用户">
        <span>{{ dataSourceUser || '当前连接用户' }}</span>
        <div class="form-hint">新模式归当前数据库连接用户所有。</div>
      </el-form-item>
    </el-form>
    <span slot="footer">
      <el-button size="small" @click="close">取消</el-button>
      <el-button size="small" type="primary" :loading="saving" @click="save">创建模式</el-button>
    </span>
  </el-dialog>
</template>

<script>
import connectionServer from '@/api/main/connection';
import sqlServer from '@/api/main/sql';

export default {
  props: {
    visible: { type: Boolean, default: false },
    dataSource: { type: Object, default: () => ({}) },
  },
  data() {
    return {
      form: { name: '' },
      saving: false,
      rules: {
        name: [
          { required: true, message: '请输入模式名称', trigger: 'blur' },
          { pattern: /^[A-Za-z_][A-Za-z0-9_]*$/, message: '模式名称只能使用英文字母、数字和下划线，且不能以数字开头', trigger: 'blur' },
        ],
      },
    };
  },
  computed: {
    dataSourceUser() { return this.dataSource.user || this.dataSource.userName || ''; },
  },
  methods: {
    close() {
      if (this.saving) return;
      this.form.name = '';
      this.$refs.form?.clearValidate();
      this.$emit('close');
    },
    async save() {
      if (this.saving) return;
      const valid = await this.$refs.form.validate().catch(() => false);
      if (!valid) return;
      const name = this.form.name.trim();
      this.saving = true;
      try {
        const params = { dataSourceId: this.dataSource.id, refresh: true };
        const before = await connectionServer.getSchemaList(params);
        if (!before.success || !Array.isArray(before.data)) throw new Error('读取模式列表失败');
        if (before.data.some(item => item.name === name)) {
          this.$message.warning('模式名称已存在');
          return;
        }
        const result = await sqlServer.executeDDL({
          dataSourceId: this.dataSource.id,
          databaseType: this.dataSource.type,
          sql: `CREATE SCHEMA "${name}"`,
        });
        if (!result.success || !result.data?.success) {
          this.$message.error('创建模式失败，请检查名称和数据库权限');
          return;
        }
        const after = await connectionServer.getSchemaList(params);
        if (!after.success || !Array.isArray(after.data) || !after.data.some(item => item.name === name)) {
          this.$message.error('创建语句已执行，但未在模式列表中找到新模式，请刷新后检查');
          return;
        }
        this.$message.success('模式创建成功');
        this.$emit('created');
        this.form.name = '';
        this.$emit('close');
      } catch (error) {
        this.$message.error('创建模式失败，请检查数据库连接和权限');
      } finally {
        this.saving = false;
      }
    },
  },
};
</script>

<style scoped>
.form-hint { color: #68728c; font-size: 12px; line-height: 18px; }
</style>
