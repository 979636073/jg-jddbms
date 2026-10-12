<template>
  <div class="permission-editor">
    <div class="object-panel">
      <el-select v-model="schemaName" size="small" placeholder="请选择模式" filterable :filter-method="filterSchemaOptions" @visible-change="onSchemaVisibleChange" @change="getTableData">
        <el-option v-for="item in visibleSchemaOptions" :key="item.name" :label="item.name" :value="item.name" />
      </el-select>
      <div class="object-tools">
        <el-checkbox :value="allObjectsSelected" :indeterminate="someObjectsSelected" :disabled="!objects.length || loading" @change="toggleAllObjects">全选对象</el-checkbox>
        <span>已选 {{ selectedNames.length }} / {{ objects.length }}</span>
      </div>
      <el-input v-model.trim="objectSearch" size="small" clearable placeholder="搜索对象" @input="objectPage = 1" />
      <el-scrollbar class="object-list">
        <div v-for="item in pagedObjects" :key="item.name" class="object-row" :class="{ active: focusedName === item.name }" @click="focusObject(item)">
          <span class="object-toggle" @click.stop>
            <el-checkbox :value="selectedNames.includes(item.name)" :disabled="loading" @change="checked => toggleObject(item, checked)" />
          </span>
          <span class="object-name" :title="item.name">{{ item.name }}</span>
        </div>
      </el-scrollbar>
      <div v-if="filteredObjects.length > objectPageSize" class="object-pages">
        <el-button size="mini" :disabled="objectPage === 1" @click="objectPage--">上一页</el-button>
        <span>{{ objectPage }} / {{ objectPageCount }}</span>
        <el-button size="mini" :disabled="objectPage >= objectPageCount" @click="objectPage++">下一页</el-button>
      </div>
    </div>
    <div class="permission-panel" v-loading="loading">
      <div class="permission-tools">
        <span>{{ selectedNames.length ? `所选对象的权限（${selectedNames.length} 个）` : '请选择对象' }}</span>
        <el-checkbox :value="allPrivilegesSelected" :indeterminate="somePrivilegesSelected" :disabled="!tableData.length || loading" @change="toggleAllPrivileges">全选权限</el-checkbox>
        <el-button size="mini" :disabled="!tableData.length || loading" @click="setAllPrivileges(false)">全部撤销</el-button>
      </div>
      <el-table :data="tableData" border height="calc(100% - 92px)" empty-text="请选择左侧对象">
        <el-table-column prop="desc" label="权限" min-width="180" />
        <el-table-column label="授权" min-width="110">
          <template slot-scope="scope">
            <el-checkbox v-model="scope.row.rule" :indeterminate="scope.row.mixedRule" @change="checked => changePrivilege(scope.row, 'rule', checked)" />
          </template>
        </el-table-column>
        <el-table-column label="转授" min-width="110">
          <template slot-scope="scope">
            <el-checkbox v-model="scope.row.toRule" :indeterminate="scope.row.mixedToRule" :disabled="!scope.row.rule && !scope.row.mixedRule" @change="checked => changePrivilege(scope.row, 'toRule', checked)" />
          </template>
        </el-table-column>
      </el-table>
      <div class="permission-footer">
        <span v-if="selectedNames.length > 1">不同对象的权限显示为半选；只修改勾选过的权限。</span>
        <el-button type="primary" size="small" :disabled="!hasChanges || loading" @click="submit">保存授权</el-button>
      </div>
    </div>
  </div>
</template>

<script>
import connectionServer from '@/api/main/connection';
import sqlServer from '@/api/main/sql';
import userServer from '@/api/main/user';

export default {
  props: { currentConfig: { type: Object, default: () => ({}) } },
  data() {
    return {
      schemaName: '', schemaQuery: '', options: [], objects: [], selectedNames: [], focusedName: '',
      objectSearch: '', objectPage: 1, objectPageSize: 100,
      tableData: [], originalByName: {}, changes: {}, loading: false, requestVersion: 0,
    };
  },
  computed: {
    allObjectsSelected() { return this.objects.length > 0 && this.selectedNames.length === this.objects.length; },
    someObjectsSelected() { return this.selectedNames.length > 0 && !this.allObjectsSelected; },
    allPrivilegesSelected() { return this.tableData.length > 0 && this.tableData.every(row => row.rule && !row.mixedRule); },
    somePrivilegesSelected() { return this.tableData.some(row => row.rule || row.mixedRule) && !this.allPrivilegesSelected; },
    hasChanges() { return Object.keys(this.changes).length > 0; },
    visibleSchemaOptions() {
      const keyword = this.schemaQuery.toLowerCase();
      const matches = keyword ? this.options.filter(item => item.name.toLowerCase().includes(keyword)) : this.options;
      const visible = matches.slice(0, 100);
      if (this.schemaName && !visible.some(item => item.name === this.schemaName)) {
        const selected = this.options.find(item => item.name === this.schemaName);
        if (selected) visible.push(selected);
      }
      return visible;
    },
    filteredObjects() {
      const keyword = this.objectSearch.toLowerCase();
      return keyword ? this.objects.filter(item => item.name.toLowerCase().includes(keyword)) : this.objects;
    },
    objectPageCount() { return Math.max(1, Math.ceil(this.filteredObjects.length / this.objectPageSize)); },
    pagedObjects() {
      const start = (this.objectPage - 1) * this.objectPageSize;
      return this.filteredObjects.slice(start, start + this.objectPageSize);
    },
  },
  watch: {
    'currentConfig.uniqueData.dataSourceId'() { this.reset(); this.getDataBaseTree(); },
    'currentConfig.title'() { this.reset(); this.getDataBaseTree(); },
  },
  mounted() { this.getDataBaseTree(); },
  methods: {
    filterSchemaOptions(query) { this.schemaQuery = query || ''; },
    onSchemaVisibleChange(visible) { if (!visible) this.schemaQuery = ''; },
    reset() {
      this.requestVersion++;
      this.schemaName = ''; this.schemaQuery = ''; this.objects = []; this.selectedNames = []; this.focusedName = '';
      this.objectSearch = ''; this.objectPage = 1;
      this.tableData = []; this.originalByName = {}; this.changes = {}; this.loading = false;
    },
    async getDataBaseTree() {
      if (!this.currentConfig?.uniqueData?.dataSourceId) return;
      try {
        const res = await connectionServer.getSchemaList({
          dataSourceId: this.currentConfig.uniqueData.dataSourceId,
          dataSourceName: this.currentConfig.uniqueData.databaseName, refresh: true,
        });
        if (res.success) this.options = res.data || [];
      } catch (error) { this.$message.error('模式查询失败'); }
    },
    async getTableData(schemaName) {
      const version = ++this.requestVersion;
      this.objects = []; this.selectedNames = []; this.tableData = [];
      this.objectSearch = ''; this.objectPage = 1;
      this.changes = {}; this.originalByName = {}; this.focusedName = '';
      if (!schemaName) return;
      this.loading = true;
      try {
        const allObjects = [];
        let pageNo = 1;
        const pageSize = 1000;
        while (true) {
          const res = await sqlServer.getTableList({
            dataSourceId: this.currentConfig.uniqueData.dataSourceId,
            dataSourceName: this.currentConfig.uniqueData.databaseName,
            databaseType: this.currentConfig.uniqueData.databaseType,
            schemaName, refresh: pageNo === 1, requestType: 1, pageNo, pageSize,
          });
          if (version !== this.requestVersion) return;
          if (!res.success) throw new Error(res.errorMessage || '对象查询失败');
          const page = res.data?.data || [];
          allObjects.push(...page);
          if (!page.length || allObjects.length >= Number(res.data?.total || 0) || page.length < pageSize) break;
          pageNo++;
        }
        this.objects = allObjects;
      } catch (error) {
        if (version === this.requestVersion) this.$message.error(error.message || '对象查询失败');
      } finally { if (version === this.requestVersion) this.loading = false; }
    },
    focusObject(item) {
      if (this.loading) return;
      this.focusedName = item.name; this.selectedNames = [item.name]; this.loadPermissions();
    },
    toggleObject(item, checked) {
      this.selectedNames = checked ? [...this.selectedNames, item.name] : this.selectedNames.filter(name => name !== item.name);
      this.focusedName = this.selectedNames[0] || ''; this.loadPermissions();
    },
    toggleAllObjects(checked) {
      this.selectedNames = checked ? this.objects.map(item => item.name) : [];
      this.focusedName = this.selectedNames[0] || ''; this.loadPermissions();
    },
    async loadPermissions() {
      const version = ++this.requestVersion;
      this.changes = {}; this.tableData = []; this.originalByName = {};
      if (!this.selectedNames.length) return;
      this.loading = true;
      try {
        const originals = {};
        for (let offset = 0; offset < this.selectedNames.length; offset += 200) {
          const names = this.selectedNames.slice(offset, offset + 200);
          const res = await userServer.queryObjAllRoles({
            dataSourceId: this.currentConfig.uniqueData.dataSourceId,
            userName: this.currentConfig.title, schemaName: this.schemaName, tableNames: names,
          });
          if (version !== this.requestVersion) return;
          if (!res.success) throw new Error(res.errorMessage || '权限查询失败');
          names.forEach(name => { originals[name] = res.data?.[name] || []; });
        }
        this.originalByName = originals;
        const names = [...new Set(this.selectedNames.flatMap(name => originals[name].map(row => row.desc)))].filter(name => name !== 'ALL').sort();
        this.tableData = names.map(desc => {
          const entries = this.selectedNames.map(name => originals[name].find(row => row.desc === desc));
          const granted = entries.filter(row => row && row.rule).length;
          const transferable = entries.filter(row => row && row.toRule).length;
          return { desc, rule: granted === entries.length, mixedRule: granted > 0 && granted < entries.length,
            toRule: transferable === entries.length, mixedToRule: transferable > 0 && transferable < entries.length };
        });
      } catch (error) {
        if (version === this.requestVersion) this.$message.error(error.message || '权限查询失败');
      } finally { if (version === this.requestVersion) this.loading = false; }
    },
    changePrivilege(row, field, checked) {
      row[field] = checked;
      row[field === 'rule' ? 'mixedRule' : 'mixedToRule'] = false;
      const change = { ...(this.changes[row.desc] || {}), [field]: checked };
      if (field === 'rule' && !checked) {
        row.toRule = false; row.mixedToRule = false; change.toRule = false;
      }
      if (field === 'toRule' && checked) {
        row.rule = true; row.mixedRule = false; change.rule = true;
      }
      this.$set(this.changes, row.desc, change);
    },
    toggleAllPrivileges(checked) { this.setAllPrivileges(checked); },
    setAllPrivileges(checked) { this.tableData.forEach(row => this.changePrivilege(row, 'rule', checked)); },
    async submit() {
      if (!this.hasChanges || this.loading) return;
      this.loading = true;
      const failed = [];
      let succeeded = 0;
      const objectRoleChanges = this.selectedNames.map(tableName => {
        const original = this.originalByName[tableName] || [];
        const updated = original.filter(row => row.desc !== 'ALL').map(row => {
          const change = this.changes[row.desc];
          if (!change) return { ...row };
          const next = { ...row, ...change };
          if (!next.rule) next.toRule = false;
          return next;
        });
        return { tableName, oldObjectRoleData: original.filter(row => row.desc !== 'ALL'), newObjectRoleData: updated };
      });
      for (let offset = 0; offset < objectRoleChanges.length; offset += 200) {
        const chunk = objectRoleChanges.slice(offset, offset + 200);
        try {
          const res = await userServer.saveObjectRoles({
            dataSourceId: this.currentConfig.uniqueData.dataSourceId,
            userName: this.currentConfig.title, schemaName: this.schemaName, objectRoleChanges: chunk,
          });
          const items = res.data?.items || [];
          succeeded += items.filter(item => item.status === 'SUCCESS').length;
          failed.push(...items.filter(item => item.status !== 'SUCCESS').map(item => `${item.name}: ${item.message || '授权失败'}`));
          if (!res.success && !items.length) throw new Error(res.errorMessage || '授权失败');
        } catch (error) { failed.push(`${chunk.map(item => item.tableName).join('、')}: ${error.message || '授权失败'}`); }
      }
      this.loading = false;
      if (succeeded) this.$message.success(`${succeeded} 个对象的权限已更新`);
      if (failed.length) this.$message.error(failed.join('; '));
      this.$emit('permissions-changed');
      this.loadPermissions();
    },
  },
};
</script>

<style lang="scss" scoped>
.permission-editor { display: flex; height: 100%; min-height: 0; }
.object-panel { width: 25%; min-width: 190px; display: flex; flex-direction: column; min-height: 0; }
.object-tools { display: flex; justify-content: space-between; align-items: center; padding: 10px 3px; font-size: 12px; color: #68728c; }
.object-list { flex: 1; min-height: 0; }
.object-row { display: flex; align-items: center; gap: 8px; min-height: 30px; padding: 0 7px; cursor: pointer; }
.object-row.active { color: #006fff; background: #f0f5ff; }
.object-pages { display: flex; align-items: center; justify-content: space-between; padding: 5px 0; font-size: 12px; }
.object-toggle { display: flex; align-items: center; }
.object-name { overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.permission-panel { flex: 1; min-width: 0; padding-left: 15px; }
.permission-tools { height: 42px; display: flex; align-items: center; gap: 14px; }
.permission-tools span { margin-right: auto; font-size: 13px; }
.permission-footer { height: 50px; display: flex; align-items: center; justify-content: flex-end; gap: 12px; color: #68728c; font-size: 12px; }
@media (max-width: 900px) {
  .permission-editor { flex-direction: column; }
  .object-panel { width: 100%; min-width: 0; flex: 0 0 210px; }
  .permission-panel { width: 100%; min-height: 0; padding-left: 0; }
}
</style>
