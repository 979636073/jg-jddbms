<template>
  <el-dialog
    title="客户端工具管理"
    :visible.sync="dialogVisible"
    width="980px"
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
        <el-button type="primary" size="small" icon="el-icon-upload2" @click="openUpload">上传工具包</el-button>
      </div>
    </div>

    <el-alert
      title="每个 ZIP 只放一种完整工具对：expdp/impdp、exp/imp 或 dexp/dimp。跨平台工具无法在当前服务器执行检测时，可填写备用版本。"
      type="info"
      :closable="false"
      show-icon
      class="manager-alert"
    />

    <el-table v-loading="loading" :data="tools" height="430" empty-text="尚未上传客户端工具">
      <el-table-column label="数据库" width="90">
        <template slot-scope="scope">
          <el-tag size="mini" :type="scope.row.databaseType === 'ORACLE' ? 'warning' : ''">
            {{ scope.row.databaseType === 'ORACLE' ? 'Oracle' : '达梦' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="工具族" min-width="150">
        <template slot-scope="scope">{{ familyLabel(scope.row.toolFamily) }}</template>
      </el-table-column>
      <el-table-column prop="version" label="版本" width="110" />
      <el-table-column label="运行平台" width="145">
        <template slot-scope="scope">
          <div>{{ scope.row.os }} / {{ scope.row.arch }}</div>
          <div v-if="scope.row.currentPlatformCompatible === false" class="platform-warning">当前服务不可运行</div>
        </template>
      </el-table-column>
      <el-table-column label="检测状态" min-width="180" show-overflow-tooltip>
        <template slot-scope="scope">
          <el-tag size="mini" :type="scope.row.status === 'AVAILABLE' ? 'success' : 'danger'">
            {{ scope.row.status === 'AVAILABLE' ? '检测通过' : '检测失败' }}
          </el-tag>
          <span class="status-message">{{ scope.row.statusMessage }}</span>
        </template>
      </el-table-column>
      <el-table-column label="启用" width="75" align="center">
        <template slot-scope="scope">
          <el-switch
            :value="scope.row.enabled"
            :disabled="scope.row.status !== 'AVAILABLE' || changingId === scope.row.id"
            @change="value => changeEnabled(scope.row, value)"
          />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="70" align="center">
        <template slot-scope="scope">
          <el-button type="text" class="delete-action" @click="removeTool(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <span slot="footer" class="dialog-footer">
      <el-button size="small" @click="dialogVisible = false">关闭</el-button>
    </span>

    <el-dialog
      title="上传客户端工具包"
      :visible.sync="uploadVisible"
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
      </el-form>
      <span slot="footer" class="dialog-footer">
        <el-button size="small" @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" size="small" :loading="uploading" @click="submitUpload">
          {{ uploading ? '正在检测' : '上传并检测' }}
        </el-button>
      </span>
    </el-dialog>
  </el-dialog>
</template>

<script>
import { deleteDmpTool, listDmpTools, setDmpToolEnabled, uploadDmpTool } from '@/api/main/dmpTool'

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
      changingId: '',
      tools: []
    }
  },
  computed: {
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
      const formData = new FormData()
      formData.append('file', this.selectedFile)
      if (this.fallbackVersion) formData.append('version', this.fallbackVersion)
      this.uploading = true
      uploadDmpTool(formData).then(res => {
        this.$message.success(res.data && res.data.status === 'AVAILABLE' ? '工具包检测通过' : '工具包已保存，请检查检测结果')
        this.uploadVisible = false
        this.loadTools()
        this.$emit('changed')
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
::v-deep .el-upload,
::v-deep .el-upload-dragger {
  width: 100%;
}
::v-deep .el-form-item__label {
  color: #4a5565;
  font-weight: 500;
}
</style>
