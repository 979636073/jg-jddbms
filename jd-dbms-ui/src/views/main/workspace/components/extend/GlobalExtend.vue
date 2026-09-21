<script>
import sqlServer from "@/api/main/sql"
import MonacoEditor from "@/components/MonacoEditor/index.vue";
export default {
  name: 'globalextend',
  components: {MonacoEditor},
  data() {
    return {
      ddl: '',
    }
  },
  computed: {
    currentTable() {
      return this.$store.state.workspace.currentTable
    }
  },
  watch: {
    currentTable: {
      handler(newVal) {
        if (newVal) this.viewDDL(newVal)
      },
      immediate: true,
      deep: true
    }
  },
  methods: {
    // 数据表 查看DDL
    viewDDL(nodeData) {
      let send = {
        dataSourceId: nodeData?.extraParams.dataSourceId,
        databaseName: nodeData?.extraParams.databaseName,
        schemaName: nodeData?.extraParams.schemaName,
        tableName: nodeData?.extraParams.tableName,
      }
      this.DDLTitle = 'DDL-' + nodeData?.label
      sqlServer.exportCreateTableSql(send)
        .then((res)=>{
          this.ddl = res.data
          this.$nextTick(() => {
            this.$refs.MonacoEditor.setValue(res.data)
          })
        })
    },
  }
}
</script>

<template>
  <div>
    <div v-if="currentTable && ddl" class='viewDDLBox'>
      <div class='viewDDLHeader'>{{ `${currentTable.extraParams.tableName}-DDL` }}</div>
      <MonacoEditor ref="MonacoEditor" dom="sqlPreview" style="height: 100%;" />
    </div>
    <div v-else class="noInformation">No information</div>
  </div>
</template>

<style scoped lang="scss">
.viewDDLBox {
  height: 100%;
  display: flex;
  flex-direction: column;
  font-size: 12px;
  color: rgba(35, 36, 41, 0.88);
  .viewDDLHeader {
    flex-shrink: 0;
    line-height: 32px;
    padding: 0px 10px;
    border-bottom: 1px solid rgba(211, 211, 212, 0.4);
    font-weight: bold;
  }
  .viewDDL {
    flex: 1;
  }
}

.noInformation {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: rgba(35, 36, 41, 0.88);
}
</style>
