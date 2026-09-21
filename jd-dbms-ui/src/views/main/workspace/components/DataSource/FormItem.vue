<script>
export default {
  name: 'FormItem',
  props: {
    FormConfig: {
      type: Array,
    },
    dataSourceInfo: {
      type: Object,
    }
  },
  data() {
    return {
      readonlyInput: true
    }
  },
  methods: {
    cancelReadOnly() {
      this.readonlyInput = false;
    }
  }
}
</script>

<template>
  <div class="form_item">
    <el-form-item
      v-for="dataSource in FormConfig"
      :key="dataSource.name"
      :label="dataSource.labelNameCN"
      style="width: 100%;"
      :style="dataSource.styles"
    >
      <el-input
        v-if="dataSource.inputType === 'input'"
        v-model="dataSourceInfo[dataSource.name]"
        :readonly="readonlyInput"
        @focus="cancelReadOnly()"
        autocomplete="off"
      ></el-input>
      <el-input
        v-if="dataSource.inputType === 'password'"
        placeholder="请输入密码"
        v-model="dataSourceInfo[dataSource.name]"
        :readonly="readonlyInput"
        @focus="cancelReadOnly()"
        autocomplete="off"
        show-password
      ></el-input>

      <template v-else-if="dataSource.inputType === 'select'">
        <el-select
          v-model="dataSourceInfo[dataSource.name]"
          placeholder="请选择"
        >
          <el-option
            v-for="item in dataSource.selects"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          >
          </el-option>
        </el-select>
        <div
          v-if="
            dataSource.selects.find(item => item.value === dataSourceInfo[dataSource.name]) &&
            dataSource.selects.find(item => item.value === dataSourceInfo[dataSource.name]).items &&
            dataSource.selects.find(item => item.value === dataSourceInfo[dataSource.name]).items.length
          "
          style="margin-left: -80px; margin-top: 18px; width: 100%;"
        >
          <el-form-item
            v-for="(dataSourceChild, IndexChild) in dataSource.selects.find(item => item.value === dataSourceInfo[dataSource.name]).items"
            :key="dataSourceChild.name"
            :label="dataSourceChild.labelNameCN"
            style="width: 100%;"
            :style="{ ...dataSourceChild.styles, marginBottom: IndexChild === dataSource.selects.find(item => item.value === dataSourceInfo[dataSource.name]).items.length - 1 ? 0 : '18px' }"
          >
            <el-input
              v-if="dataSourceChild.inputType === 'input'"
              v-model="dataSourceInfo[dataSourceChild.name]"
              :readonly="readonlyInput"
              @focus="cancelReadOnly()"
            ></el-input>
            <el-input
              v-if="dataSourceChild.inputType === 'password'"
              placeholder="请输入密码"
              v-model="dataSourceInfo[dataSourceChild.name]"
              :readonly="readonlyInput"
              @focus="cancelReadOnly()"
              show-password
            ></el-input>
            <el-select
              v-else-if="dataSourceChild.inputType === 'select'"
              v-model="dataSourceInfo[dataSourceChild.name]"
              placeholder="请选择"
            >
              <el-option
                v-for="item in dataSourceChild.selects"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              >
              </el-option>
            </el-select>
          </el-form-item>
        </div>
      </template>
    </el-form-item>
  </div>
</template>

<style scoped lang="scss">

</style>
