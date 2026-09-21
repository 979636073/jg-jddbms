<script>
import NavBar from "./NavBar.vue";
import SideBar from "./SideBar.vue";
import JdTagsView from "@/components/JdTagsView";
import linkServerDialog from './components/linkServerDialog';

export default {
  name: "index",
  components: {
    SideBar,
    NavBar,
    JdTagsView,
    linkServerDialog,
  },
  data() {
    return {
      opened: true,
      visibleDialog:false,
      headerCollapsed: false,
    };
  },
  mounted(){
     const createData = JSON.parse(sessionStorage.getItem('createData'))
     if (this.$route.path === "/workspace" && !this.$route.query?.isShow) {
       const firstView = this.$store.getters.visitedViews2[0]
         || (JSON.parse(localStorage.getItem("saveVisitedViews2")) || [])[0]
       if (firstView && firstView.path) {
         this.$router.replace({ path: firstView.path, query: firstView.query })
         return
       }
     }
     if(!createData || this.$route.query?.isShow){
       this.visibleDialog = true
     }
  
  },
  computed: {
    key() {
      return this.$route.path;
    },
  },
  methods: {
    toggleSideBar() {
      this.opened = !this.opened;
    },
    isopenFn() {
      this.opened = false;
    },
    openSideBar() {
      this.opened = true;
    },
  },
};
</script>

<template>
  <div class="container">
    <el-container>
      <el-header :class="{ 'workspace-header-collapsed': headerCollapsed }">
        <NavBar
          @toggleSideBar="toggleSideBar"
          :opened="opened"
          :collapsed="headerCollapsed"
          @toggleHeader="headerCollapsed = !headerCollapsed"
        />
      </el-header>
      <div class="tag_list">
        <JdTagsView />
      </div>
      <transition name="fade-transform" mode="out-in">
        <keep-alive>
          <router-view :key="key" />
        </keep-alive>
      </transition>
    </el-container>
    <linkServerDialog :visibleDialog='visibleDialog'/>
  </div>
</template>

<style scoped lang="scss">
.show {
  display: block;
}
.hide {
  display: none;
}

.tag_list {
  // padding: 10px 0;
  margin-bottom: 5px;
  padding: 5px 0px 0px 8px;
  flex-shrink: 0;
  display: flex;
  min-width: 100%;
  overflow: hidden;
  // overflow-x: auto;
  .el-tag {
    margin-right: 10px;
    cursor: pointer;
    display: flex;
    align-items: center;
    i {
      flex-shrink: 0;
      margin-right: 4px;
    }
    .textBox {
      flex: 1;
      display: flex;
      align-items: center;
    }

    .text {
      flex: 1;
      width: fit-content;
      overflow: hidden;
      white-space: nowrap;
      text-overflow: ellipsis;
    }
  }
  ::v-deep .el-tag--dark.el-tag--info {
    background: #fff;
    border-color: #fff;
    color: #68728c;
    .el-tag__close,
    .el-tag__close:hover {
      background: #fff;
      color: #68728c;
    }
  }
}

::v-deep .el-header {
  padding: 0px !important;
}
::v-deep .workspace-header-collapsed {
  height: 20px !important;
  flex: 0 0 20px !important;
}
::v-deep .el-main {
  padding: 0px;
}
.container > ::v-deep .el-container {
  display: -webkit-box;
  flex: 1;
  padding-right: 5px;
  flex-direction: column;
  // position: relative;
}
::v-deep aside {
  padding: 0px 8px !important;
}

::v-deep .el-form-item--mini .el-form-item__label {
  line-height: 0px;
}
::v-deep .el-form-item--mini.el-form-item {
  margin-bottom: 17px;
}
.container {
  display: flex;
  height: 100%;
  overflow: hidden;
  &::-webkit-scrollbar {
    display: none;
  }
}

::v-deep {
  .el-button--mini {
    padding: 5px 10px;
    font-size: 14px;
  }
  .el-button--text {
    margin: 0 15px;
    position: relative;
    color: #68728c;
  }
  .el-button--primary {
    background: #006fff;
    border: 1px solid #006fff;
    color: #fff;
  }
  .el-button--primary:hover,
  .el-button--primary:focus {
    background: #2052dd;
    border: 1px solid #2052dd;
    color: #fff;
  }
  .el-button--primary.is-plain {
    background: #f4f9ff;
    border: 1px solid #006fff;
    color: #006fff;
  }
  .el-button--primary.is-plain:hover,
  .el-button--primary.is-plain:focus {
    background: #eaf2fc;
    border: 1px solid #006fff;
    color: #006fff;
  }
  .el-button--danger.is-plain {
    background: #fff6f4;
    border: 1px solid #ff5733;
    color: #ff5733;
  }
  .el-button--danger.is-plain:hover,
  .el-button--danger.is-plain:focus {
    background: #ffe9e4;
    border: 1px solid #eb4927;
    color: #ff5733;
  }
  .el-button.is-disabled,
  .el-button.is-disabled:hover,
  .el-button.is-disabled:focus {
    color: #c0c4cc;
  }
  .el-pagination {
    margin: 5px 0;
    float: right;
  }

  .el-dialog {
    background: #ffffff;
    border-radius: 5px;
    .el-dialog__header {
      height: 50px;
      background: #006fff;
      border-radius: 5px 5px 0 0;
      padding: 0 0 0 24px;
      .el-dialog__title {
        font-size: 18px;
        font-weight: 400;
        color: #ffffff;
        line-height: 50px;
      }
      .el-dialog__headerbtn .el-dialog__close {
        color: #e5f4ff;
      }
    }
  }
  .el-table {
    .delete-row {
      background: #fdd4cd;
    }
    .update-row {
      background: #f6ffed;
    }
  }

  .el-tree {
    color: #68728c;
    font-size: 14px;
  }
}
::v-deep .el-button--text {
  margin: 0 !important;
}
::v-deep .el-table--medium .el-table__cell {
  padding: 6px;
}
</style>
