<script>
import MonacoEditor from "@/components/MonacoEditor/index.vue";

export default {
  name: 'SqlPreview',
  components: {MonacoEditor},
  props: {
    title: {
      type: String,
      default: ''
    }
  },
  data() {
    return {
      sqlInfo: null,
      dialogVisible: false,
    }
  },
  methods: {
    init(sqlInfo) {
      this.dialogVisible = true
      this.sqlInfo = sqlInfo
      this.$nextTick(() => {
        this.$refs.MonacoEditor.setValue(this.sqlInfo)
      })
    },
    getValue() {
      return this.$refs.MonacoEditor.getValue()
    },
    handleClose(done) {
      done();
    },
    dialogClose() {
      this.dialogVisible = false
    }
  }
}
</script>

<template>
  <el-dialog
    :title="title"
    :visible.sync="dialogVisible"
    width="800"
    :before-close="handleClose"
    :close-on-click-modal="false"
  >
    <slot />
    <MonacoEditor ref="MonacoEditor" dom="sqlPreview" style="height: 30vh;" />
  </el-dialog>
</template>

<style scoped lang="scss">

</style>
