<template>
  <el-dialog
    title="客户端工具管理"
    v-model="dialogVisible"
    width="920px"
    append-to-body
    class="dmp-tool-manager"
  >
    <div class="manager-toolbar">
      <div>
        <div class="manager-title">Oracle / 达梦客户端工具</div>
        <div class="manager-subtitle">工具包保存在服务器持久化目录，系统按数据库、格式、平台和版本自动选择。</div>
      </div>
      <div class="toolbar-actions">
        <el-button size="small" icon="el-icon-refresh" @click="loadTools">刷新</el-button>
        <el-button v-if="isAdmin" type="primary" size="small" icon="el-icon-upload2" @click="openUpload">上传工具包</el-button>
      </div>
    </div>

    <el-alert
      title="请上传完整客户端 ZIP。每包仅包含一组 expdp/impdp、exp/imp 或 dexp/dimp；无法自动识别平台或版本时，使用上传时填写的信息。"
      type="info"
      :closable="false"
      show-icon
      class="manager-alert"
    />

    <el-table v-loading="loading" :data="tools" height="430" empty-text="尚未上传客户端工具">
      <el-table-column label="数据库" width="90">
        <template #default="scope">
          <el-tag size="small" :type="scope.row.databaseType === 'ORACLE' ? 'warning' : ''">
            {{ scope.row.databaseType === 'ORACLE' ? 'Oracle' : '达梦' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="工具族" min-width="150">
        <template #default="scope">{{ familyLabel(scope.row.toolFamily) }}</template>
      </el-table-column>
      <el-table-column prop="version" label="版本" width="110" />
      <el-table-column label="运行平台" width="145">
        <template #default="scope">
          <div>{{ scope.row.os }} / {{ scope.row.arch }}</div>
          <div v-if="scope.row.currentPlatformCompatible === false" class="platform-warning">当前服务不可运行</div>
        </template>
      </el-table-column>
      <el-table-column label="检测状态" min-width="180" show-overflow-tooltip>
        <template #default="scope">
          <el-tag size="small" :type="scope.row.status === 'AVAILABLE' ? 'success' : 'danger'">
            {{ scope.row.status === 'AVAILABLE' ? '检测通过' : '检测失败' }}
          </el-tag>
          <span class="status-message">{{ scope.row.statusMessage }}</span>
        </template>
      </el-table-column>
      <el-table-column label="启用" width="75" align="center">
        <template #default="scope">
          <el-switch
            :model-value="scope.row.enabled"
            :disabled="!isAdmin || scope.row.status !== 'AVAILABLE' || changingId === scope.row.id"
            @change="value => changeEnabled(scope.row, value)"
          />
        </template>
      </el-table-column>
      <el-table-column v-if="isAdmin" label="操作" width="115" align="center">
        <template #default="scope">
          <el-button type="text" @click="openEdit(scope.row)">修改</el-button>
          <el-button type="text" class="delete-action" :disabled="scope.row.enabled" :title="scope.row.enabled ? '请先停用，再删除该版本' : ''" @click="removeTool(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <template #footer><span  class="dialog-footer">
      <el-button size="small" @click="dialogVisible = false">关闭</el-button>
    </span></template>

    <el-dialog
      title="上传客户端工具包"
      v-model="uploadVisible"
      width="520px"
      append-to-body
      class="dmp-tool-upload"
    >
      <el-form label-position="top" size="small">
        <el-form-item label="工具包">
          <el-upload
            drag
            action=""
            accept=".zip"
            :auto-upload="false"
            :limit="1"
            :file-list="uploadFiles"
            :on-change="onFileChange"
            :on-remove="onFileRemove"
          >
            <i class="el-icon-upload" />
            <div class="el-upload__text">将 ZIP 拖到这里，或<em>点击选择</em></div>
          </el-upload>
        </el-form-item>
        <el-form-item label="备用版本（可选）">
          <el-input v-model.trim="fallbackVersion" placeholder="例如 19.3 或 8.1.3，仅自动检测失败时使用" />
        </el-form-item>
        <el-form-item label="运行平台">
          <el-select v-model="selectedPlatform" placeholder="请选择工具包的运行平台" style="width: 100%">
            <el-option v-for="item in platforms" :key="item.value" :value="item.value" :label="item.label" />
          </el-select>
          <div class="platform-help">能识别文件头时以自动检测结果为准；无法识别时使用这里选择的平台。</div>
        </el-form-item>
      </el-form>
      <template #footer><span  class="dialog-footer">
        <el-button size="small" @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" size="small" :loading="uploading" @click="submitUpload">
          {{ uploading ? '正在检测' : '上传并检测' }}
        </el-button>
      </span></template>
    </el-dialog>
    <el-dialog title="修改客户端信息" v-model="editVisible" width="480px" append-to-body>
      <el-form label-position="top" size="small">
        <el-form-item label="版本号">
          <el-input v-model.trim="editVersion" placeholder="例如 8.1.4.200" maxlength="60" />
        </el-form-item>
        <el-form-item label="运行平台">
          <el-select v-model="editPlatform" style="width: 100%">
            <el-option v-for="item in platforms" :key="item.value" :value="item.value" :label="item.label" />
          </el-select>
          <div class="platform-help">文件头能够识别运行平台时，只能使用识别到的平台。</div>
        </el-form-item>
      </el-form>
      <template #footer><span  class="dialog-footer">
        <el-button size="small" @click="editVisible = false">取消</el-button>
        <el-button type="primary" size="small" :loading="editing" @click="submitEdit">保存修改</el-button>
      </span></template>
    </el-dialog>
  </el-dialog>
</template>

<script>
import { deleteDmpTool, listDmpTools, setDmpToolEnabled, updateDmpTool, uploadDmpTool } from '@/api/main/dmpTool'

export default {
  name: 'DmpToolManager',
  props: {
    visible: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      loading: false,
      uploading: false,
      uploadVisible: false,
      uploadFiles: [],
      selectedFile: null,
      fallbackVersion: '',
      selectedPlatform: '',
      editVisible: false,
      editing: false,
      editId: '',
      editVersion: '',
      editPlatform: '',
      platforms: [
        { value: 'WINDOWS:X86_64', label: 'Windows x64' },
        { value: 'LINUX:X86_64', label: 'Linux x64' },
        { value: 'LINUX:X86', label: 'Linux x86' },
        { value: 'LINUX:ARM64', label: 'Linux ARM64' }
      ],
      changingId: '',
      tools: []
    }
  },
  computed: {
    isAdmin() {
      return (this.$store.getters.roles || []).includes('admin')
    },
    dialogVisible: {
      get() {
        return this.visible
      },
      set(value) {
        this.$emit('update:visible', value)
      }
    }
  },
  watch: {
    visible(value) {
      if (value) this.loadTools()
    }
  },
  methods: {
    familyLabel(family) {
      return {
        ORACLE_DATAPUMP: 'Data Pump（expdp/impdp）',
        ORACLE_LEGACY: '传统 EXP / IMP',
        DM_DEXP: 'DEXP / DIMP'
      }[family] || family
    },
    loadTools() {
      this.loading = true
      listDmpTools().then(res => {
        this.tools = res.data || []
      }).finally(() => {
        this.loading = false
      })
    },
    openUpload() {
      this.uploadFiles = []
      this.selectedFile = null
      this.fallbackVersion = ''
      this.selectedPlatform = ''
      this.uploadVisible = true
    },
    onFileChange(file, files) {
      this.selectedFile = file.raw
      this.uploadFiles = files.slice(-1)
    },
    onFileRemove() {
      this.selectedFile = null
      this.uploadFiles = []
    },
    submitUpload() {
      if (!this.selectedFile) {
        return this.$message.warning('请选择ZIP工具包')
      }
      if (!this.selectedPlatform) {
        return this.$message.warning('请选择运行平台')
      }
      const formData = new FormData()
      formData.append('file', this.selectedFile)
      if (this.fallbackVersion) formData.append('version', this.fallbackVersion)
      formData.append('platform', this.selectedPlatform)
      this.uploading = true
      uploadDmpTool(formData).then(res => {
        if (!res.success || !res.data) throw new Error(res.errorMessage || '客户端上传失败')
        this.$message.success(res.data && res.data.status === 'AVAILABLE' ? '工具包检测通过' : '工具包已保存，请检查检测结果')
        this.uploadVisible = false
        this.loadTools()
        this.$emit('changed')
      }).catch(error => {
        this.$message.error(error.message || '客户端上传失败')
      }).finally(() => {
        this.uploading = false
      })
    },
    changeEnabled(row, enabled) {
      this.changingId = row.id
      setDmpToolEnabled(row.id, enabled).then(() => {
        row.enabled = enabled
        this.$emit('changed')
      }).finally(() => {
        this.changingId = ''
      })
    },
    openEdit(row) {
      this.editId = row.id
      this.editVersion = row.version === 'UNKNOWN' ? '' : row.version
      this.editPlatform = `${row.os}:${row.arch}`
      this.editVisible = true
    },
    submitEdit() {
      if (!this.editVersion) return this.$message.warning('请输入版本号')
      if (!this.editPlatform) return this.$message.warning('请选择运行平台')
      this.editing = true
      updateDmpTool(this.editId, this.editVersion, this.editPlatform).then(res => {
        if (!res.success || !res.data) throw new Error(res.errorMessage || res.message || '修改失败')
        this.$message.success('客户端信息已更新')
        this.editVisible = false
        this.loadTools()
        this.$emit('changed')
      }).catch(error => {
        this.$message.error(error.message || '修改失败')
      }).finally(() => {
        this.editing = false
      })
    },
    removeTool(row) {
      this.$confirm(`删除 ${this.familyLabel(row.toolFamily)} ${row.version}？删除后无法恢复。`, '删除客户端工具', {
        type: 'warning'
      }).then(() => deleteDmpTool(row.id)).then(() => {
        this.$message.success('客户端工具已删除')
        this.loadTools()
        this.$emit('changed')
      }).catch(() => {})
    }
  }
}
</script>

<style scoped lang="scss">
.manager-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 14px;
}
.manager-title {
  color: #242f57;
  font-size: 16px;
  font-weight: 600;
}
.manager-subtitle {
  margin-top: 5px;
  color: #7c7f86;
  font-size: 12px;
}
.toolbar-actions {
  display: flex;
  gap: 8px;
}
.manager-alert {
  margin-bottom: 14px;
}
.status-message {
  margin-left: 8px;
  color: #7c7f86;
  font-size: 12px;
}
.delete-action {
  color: #f56c6c;
}
.platform-warning {
  margin-top: 2px;
  color: #e6a23c;
  font-size: 11px;
}
.platform-help {
  margin-top: 5px;
  color: #7c7f86;
  font-size: 12px;
  line-height: 1.5;
}
::v-deep .el-upload,
::v-deep .el-upload-dragger {
  width: 100%;
}
::v-deep .el-form-item__label {
  color: #4a5565;
  font-weight: 500;
}
</style>
