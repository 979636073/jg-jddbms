<template>
  <el-color-picker
    v-model="theme"
    :predefine="['#409EFF', '#1890ff', '#304156', '#212121', '#11a983', '#13c2c2', '#6959CD', '#f5222d']"
    class="theme-picker"
    popper-class="theme-picker-dropdown"
  />
</template>

<script>
export default {
  data() {
    return { theme: '' }
  },
  computed: {
    defaultTheme() { return this.$store.state.settings.theme }
  },
  watch: {
    defaultTheme: {
      immediate: true,
      handler(value) { this.theme = value }
    },
    theme(value) {
      if (!/^#[\da-f]{6}$/i.test(value)) return
      const root = document.documentElement.style
      root.setProperty('--el-color-primary', value)
      const rgb = [1, 3, 5].map(index => parseInt(value.slice(index, index + 2), 16))
      for (const level of [3, 5, 7, 8, 9]) {
        const color = rgb.map(channel => Math.round(channel + (255 - channel) * level / 10).toString(16).padStart(2, '0')).join('')
        root.setProperty(`--el-color-primary-light-${level}`, '#' + color)
      }
      root.setProperty('--el-color-primary-dark-2', '#' + rgb.map(channel => Math.round(channel * 0.8).toString(16).padStart(2, '0')).join(''))
      this.$emit('change', value)
    }
  }
}
</script>

<style>
.theme-picker .el-color-picker__trigger {
  height: 26px;
  width: 26px;
  padding: 2px;
}
</style>
