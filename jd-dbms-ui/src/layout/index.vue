<template>
  <div :class="classObj" class="app-wrapper" :style="{'--current-color': theme}">
    <div v-if="device==='mobile'&&sidebar.opened&&!isStandaloneTool" class="drawer-bg" @click="handleClickOutside"/>
    <sidebar v-if="!sidebar.hide&&!isStandaloneTool" class="sidebar-container"/>
    <div :class="{hasTagsView:needTagsView&&!isStandaloneTool,sidebarHide:sidebar.hide||isStandaloneTool}" class="main-container">
      <div v-if="!isStandaloneTool" :class="{'fixed-header':fixedHeader}">
        <navbar/>
        <tags-view v-if="needTagsView"/>
      </div>
      <app-main/>
      <right-panel v-if="!isStandaloneTool">
        <settings/>
      </right-panel>
    </div>
  </div>
</template>

<script>
import RightPanel from '@/components/RightPanel'
import { AppMain, Navbar, Settings, Sidebar, TagsView } from './components'
import ResizeMixin from './mixin/ResizeHandler'
import { mapState } from 'vuex'
import variables from '@/assets/styles/variables.module.scss'
import { getInfo } from '@/api/login'
import { getToken } from '@/utils/auth'

export default {
  name: 'Layout',
  components: {
    AppMain,
    Navbar,
    RightPanel,
    Settings,
    Sidebar,
    TagsView
  },
  mixins: [ResizeMixin],
  data() {
    return { standaloneAuthTimer: null, standaloneAuthPending: false }
  },
  computed: {
    isStandaloneTool() {
      return Boolean(this.$route.meta.standaloneTool)
    },
    ...mapState({
      theme: state => state.settings.theme,
      sideTheme: state => state.settings.sideTheme,
      sidebar: state => state.app.sidebar,
      device: state => state.app.device,
      needTagsView: state => state.settings.tagsView,
      fixedHeader: state => state.settings.fixedHeader
    }),
    classObj() {
      return {
        hideSidebar: !this.sidebar.opened || this.isStandaloneTool,
        openSidebar: this.sidebar.opened && !this.isStandaloneTool,
        withoutAnimation: this.sidebar.withoutAnimation,
        mobile: this.device === 'mobile',
        'standalone-tool': this.isStandaloneTool
      }
    },
    variables() {
      return variables;
    }
  },
  watch: {
    isStandaloneTool(active) {
      if (active) this.startStandaloneAuthChecks()
      else this.stopStandaloneAuthChecks()
    }
  },
  mounted() {
    if (this.isStandaloneTool) this.startStandaloneAuthChecks()
  },
  beforeUnmount() {
    this.stopStandaloneAuthChecks()
  },
  methods: {
    startStandaloneAuthChecks() {
      if (this.standaloneAuthTimer) return
      this.standaloneAuthTimer = setInterval(this.checkStandaloneAuth, 60000)
      window.addEventListener('focus', this.checkStandaloneAuth)
      document.addEventListener('visibilitychange', this.checkStandaloneAuth)
      this.checkStandaloneAuth()
    },
    stopStandaloneAuthChecks() {
      clearInterval(this.standaloneAuthTimer)
      this.standaloneAuthTimer = null
      window.removeEventListener('focus', this.checkStandaloneAuth)
      document.removeEventListener('visibilitychange', this.checkStandaloneAuth)
    },
    async checkStandaloneAuth() {
      if (!this.isStandaloneTool || this.standaloneAuthPending) return
      if (!getToken()) {
        window.location.replace(`/login?redirect=${encodeURIComponent(this.$route.fullPath)}`)
        return
      }
      this.standaloneAuthPending = true
      try {
        await getInfo()
      } catch (error) {
        // 401 由请求拦截器统一清除登录状态并跳转；临时网络故障保留当前页面。
      } finally {
        this.standaloneAuthPending = false
      }
    },
    handleClickOutside() {
      this.$store.dispatch('app/closeSideBar', { withoutAnimation: false })
    }
  }
}
</script>

<style lang="scss" scoped>
  @import "~@/assets/styles/mixin.scss";
  @import "~@/assets/styles/variables.scss";

  .app-wrapper {
    @include clearfix;
    position: relative;
    height: 100%;
    width: 100%;

    &.mobile.openSidebar {
      position: fixed;
      top: 0;
    }
  }

  .drawer-bg {
    background: #000;
    opacity: 0.3;
    width: 100%;
    top: 0;
    height: 100%;
    position: absolute;
    z-index: 999;
  }

  .fixed-header {
    position: fixed;
    top: 0;
    right: 0;
    z-index: 9;
    width: calc(100% - #{$base-sidebar-width});
    transition: width 0.28s;
  }

  .hideSidebar .fixed-header {
    width: calc(100% - 54px);
  }

  .sidebarHide .fixed-header {
    width: 100%;
  }

  .mobile .fixed-header {
    width: 100%;
  }

  .standalone-tool ::v-deep .app-main {
    min-height: 100vh;
  }
</style>
