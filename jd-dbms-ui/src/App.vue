<template>
  <div id="app">
    <router-view />
    <theme-picker />
  </div>
</template>

<script>
import ThemePicker from "@/components/ThemePicker";

export default {
  name: "App",
  components: { ThemePicker },
  metaInfo() {
    return {
      title: this.$store.state.settings.dynamicTitle && this.$store.state.settings.title,
      titleTemplate: title => {
        return title ? `${title} - ${process.env.VUE_APP_TITLE}` : process.env.VUE_APP_TITLE
      }
    }
  }
};
</script>
<style scoped lang='scss'>
#app {
  background: #F2F3FA;
  width: 100%;
  height: 100%;
  //  ::-webkit-scrollbar {
  //   display: none;
  // }
}
#app .theme-picker {
  display: none;
}
</style>
<style>
@font-face {
  font-family: 'iconfont'; /* Project id 3633546 */
  src: url('~@/assets/font/iconfont.woff2') format('woff2'), url('~@/assets/font/iconfont.woff') format('woff'),
  url('~@/assets/font/iconfont.ttf') format('truetype');
}

@font-face {
  font-family: 'iconfont'; /* Project id 3633546 */
  src: url('~@/assets/font_jd/iconfont.woff2') format('woff2'), url('~@/assets/font_jd/iconfont.woff') format('woff'),
  url('~@/assets/font_jd/iconfont.ttf') format('truetype');
}

.iconfont {
  font-family: 'iconfont' !important;
  font-size: 14px;
  font-style: normal;
  user-select: none;
  -webkit-font-smoothing: antialiased;
  -webkit-text-stroke-width: 0.2px;
  -moz-osx-font-smoothing: grayscale;
}
</style>
