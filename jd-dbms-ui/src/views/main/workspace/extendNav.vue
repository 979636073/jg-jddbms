<script>
import GlobalExtendComponents from "@/views/main/workspace/components/extend/GlobalExtend.vue"
import executiveLog from "@/views/main/workspace/components/extend/executiveLog.vue"
import saveList from "@/views/main/workspace/components/extend/saveList.vue"
export default {
  name: 'extendNav',
  components: { GlobalExtendComponents, executiveLog, saveList },
  data() {
    return {
      currentWorkspaceExtend: null,
      extendConfig: [
        {
          code: 'info',
          title: '信息',
          icon: '\ue8e8',
          components: 'GlobalExtendComponents',
        },
        {
          code: 'executiveLog',
          title: '执行记录',
          icon: '\ue8ad',
          components: 'executiveLog',
        },
        {
          code: 'saveList',
          title: '保存记录',
          icon: '\ue619',
          components: 'saveList',
        },
      ],
    }
  },
  methods: {
    changeExtend(item) {
      if (this.currentWorkspaceExtend === item.code) {
        this.currentWorkspaceExtend = null
        return;
      }
      this.currentWorkspaceExtend = item.code
    }
  }
}
</script>

<template>
  <div class="extendNavBox" :style="{ width: currentWorkspaceExtend ? '300px' : '38px' }">
    <component
      :is="
        extendConfig.find(item => item.code === currentWorkspaceExtend)
        ? extendConfig.find(item => item.code === currentWorkspaceExtend).components : ''
      "
      style="border-right: 1px solid rgba(211, 211, 212, 0.4); width: calc(100% - 38px);"
    ></component>
    <div class="workspaceExtendNav">
      <el-tooltip
        v-for="item in extendConfig"
        :key="item.code"
        class="item"
        effect="dark"
        :content="item.title"
        placement="left">
        <div class="rightBarFront" @click="changeExtend(item)">
          <i class="icon iconfont" :class="{ activeIconBox: item.code === currentWorkspaceExtend }">
            {{ item.icon }}
          </i>
        </div>
      </el-tooltip>
    </div>
  </div>

</template>

<style scoped lang="scss">
.extendNavBox {
  background: #fff;
  margin-left: 5px;
  display: flex;
  z-index: 9;
  .workspaceExtendNav {
    flex-shrink: 0;
    width: 38px;
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 5px 0px;
    .rightBarFront {
      margin: 2px 0px;
      & > i {
        height: 32px;
        width: 32px;
        display: flex;
        align-items: center;
        justify-content: center;
        border-radius: 4px;
        cursor: pointer;
        font-size: 18px;
        &:hover {
          color: rgb(0, 111, 255);
        }
      }
      .activeIconBox {
        color: rgb(0, 111, 255);
      }
    }
  }
}
</style>
