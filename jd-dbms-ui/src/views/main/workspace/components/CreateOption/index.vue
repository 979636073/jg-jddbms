<script>
import sqlServer from "@/api/main/sql";
import tableServer from "@/api/main/table";
import {v4 as uuidv4} from "uuid";

export default {
  name: 'CreateOption',
  data() {
    return {
      dialogVisible: false,
      objectForm: {
        type: '',
        name: '',
        tableName: '',
        status: '',
      },
      rules: {
        name: [
          { required: true, message: '请输入名称', trigger: 'blur' },
        ],
        type: [
          { required: true, message: '请选择类型', trigger: 'change' }
        ],
        status: [
          { required: true, message: '请选择类型', trigger: 'change' }
        ]
      },
      tableList: [],
      uniqueData: null
    }
  },
  methods: {
    init(nodeData) {
      this.dialogVisible = true
      this.objectForm = { type: nodeData.groupType, name: '', tableName: '', status: '' }
      this.uniqueData = { ...nodeData.extraParams }
      if (nodeData.groupType !== 'triggers') return

      let send = {
        dataSourceId: this.uniqueData?.dataSourceId,
        databaseName: this.uniqueData?.databaseName,
        schemaName: this.uniqueData?.schemaName,
      }
      tableServer.getAllTableList(send)
        .then(res => {
          this.tableList = res.data
        })
    },
    sumbit() {
      this.$refs.form.validate((valid) => {
        if (valid) {
          const apiMap = {
            functions: 'create_function',
            procedures: 'create_procedure',
            triggers: 'create_triggers'
          }
          let send = {
            dataSourceId: this.uniqueData?.dataSourceId,
            databaseName: this.uniqueData?.databaseName,
            schemaName: this.uniqueData?.schemaName,
            tableName: this.objectForm.tableName,
            after: this.objectForm.status === 'after',
            before: this.objectForm.status === 'before',
          }
          switch (this.objectForm.type) {
            case 'functions':
              send.functionName = this.objectForm.name
              break;
            case 'procedures':
              send.procedureName = this.objectForm.name
              break;
            case 'triggers':
              if (!send.tableName) {
                this.$message.warning('请选择触发器所在的数据表')
                return
              }
              send.triggerName = this.objectForm.name
              break;
          }
          sqlServer[apiMap[this.objectForm.type]](send)
            .then(res => {
              if (!res.success || !res.data?.sql) throw new Error(res.errorMessage || '生成对象模板失败')
              let id = uuidv4()
              this.$store.dispatch('workspaceData/setDataCurrentData', {
                pageId: id,
                title: this.objectForm.name,
                type: 'createSql',
                uniqueData: {
                  ddl: res.data.sql,
                  ...this.uniqueData,
                  ...send,
                  objectType: this.objectForm.type
                },
              })
              this.dialogVisible = false
              this.$router.push({ path: '/workspace/look/' + id })
            })
            .catch(error => this.$message.error(error.message || '生成对象模板失败'))
        } else {
          return false;
        }
      });
    },
    handleClose(done) {
      this.objectForm = {
        type: '',
        name: '',
        tableName: ''
      }
      this.dialogVisible = false
      done();
    },
    changeType() {
      this.objectForm.name = ''
      this.objectForm.tableName = ''
      this.objectForm.status = ''
    }
  }
}
</script>

<template>
  <el-dialog
    title="新增数据库对象"
    v-model="dialogVisible"
    width="30%"
    :before-close="handleClose">
    <el-form ref="form" :rules="rules" :model="objectForm" label-width="100px">
      <el-form-item label="类型" prop="type">
        <el-select v-model="objectForm.type" disabled placeholder="请选择类型">
          <el-option label="函数" value="functions"></el-option>
          <el-option label="存储过程" value="procedures"></el-option>
          <el-option label="触发器" value="triggers"></el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="名称" prop="name">
        <el-input v-model="objectForm.name"></el-input>
      </el-form-item>
      <template v-if="objectForm.type === 'triggers'">
        <el-form-item label="触发器表名" required>
          <el-select v-model="objectForm.tableName" filterable placeholder="请选择表名">
            <el-option
              v-for="item in tableList"
              :key="item.name"
              :label="item.name"
              :value="item.name"
            >
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="触发选项" prop="status">
          <el-radio-group v-model="objectForm.status">
            <el-radio label="before">BEFORE</el-radio>
            <el-radio label="after">AFTER</el-radio>
          </el-radio-group>
        </el-form-item>
      </template>
    </el-form>
    <template #footer><span  class="dialog-footer">
        <el-button @click="handleClose">取 消</el-button>
        <el-button type="primary" @click="sumbit">确 定</el-button>
      </span></template>
  </el-dialog>
</template>

<style scoped lang="scss">

</style>
