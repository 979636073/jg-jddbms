<template>
  <div id="tags-view-container" class="tags-view-container">
    <scroll-pane ref="scrollPane" class="tags-view-wrapper" @scroll="handleScroll">
      <router-link
        v-for="(tag, index) in visitedViews"
        ref="tag"
        :key="tag.path"
        :class="isActive(tag) ? 'active' : ''"
        :to="{ path: tag.path, query: tag.query, fullPath: tag.fullPath }"
        tag="span"
        class="tags-view-item"
        :style="activeStyle(tag)"
        @click.middle.native="!isAffix(tag) ? closeSelectedTag(tag) : ''"
      >
        <!-- @contextmenu.prevent.native="openMenu(tag,$event)" -->
        <span class="badge">{{ index + 1 }}</span>
        {{ getViewTitle(tag) }}
        <span
          v-if="!isAffix(tag)"
          class="el-icon-close"
          @click.prevent.stop="closeSelectedTag(tag)"
        />
      </router-link>
      <el-popover
        placement="right"
        trigger="click"
        class="elpop"
        v-model="showPop"
        popper-class="elpop"
      >
        <template #default>
          <div
            class="btns"
            style="display:flex; justify-content: space-around; align-itemsL center;"
          >
            <el-button
              size="mini"
              type="primary"
              @click="routerLinkFn('look')"
              style="padding: 10px 50px"
            >新建窗口</el-button>
            <el-button
              size="mini"
              type="primary"
              @click="routerLinkFn('query')"
              style="padding: 10px 50px"
            >新建查询</el-button>
          </div>
        </template>

        <i id="iconColor" slot="reference" class="el-icon-circle-plus"></i>
      </el-popover>
    </scroll-pane>
    <ul v-show="visible" :style="{ left: left + 'px', top: top + 'px' }" class="contextmenu">
      <li @click="refreshSelectedTag(selectedTag)">
        <i class="el-icon-refresh-right"></i> 刷新页面
      </li>
      <li v-if="!isAffix(selectedTag)" @click="closeSelectedTag(selectedTag)">
        <i class="el-icon-close"></i> 关闭当前
      </li>
      <li @click="closeOthersTags">
        <i class="el-icon-circle-close"></i> 关闭其他
      </li>
      <li v-if="!isFirstView()" @click="closeLeftTags">
        <i class="el-icon-back"></i> 关闭左侧
      </li>
      <li v-if="!isLastView()" @click="closeRightTags">
        <i class="el-icon-right"></i> 关闭右侧
      </li>
      <li @click="closeAllTags(selectedTag)">
        <i class="el-icon-circle-close"></i> 全部关闭
      </li>
    </ul>
  </div>
</template>

<script>
import ScrollPane from "./ScrollPane";
import path from "path";
import { v4 as uuidv4 } from "uuid";
import { mapGetters } from "vuex";
import sqlServer from "@/api/main/sql";

export default {
  components: { ScrollPane },

  data() {
    return {
      showPop: false,
      visible: false,
      top: 0,
      left: 0,
      selectedTag: {},
      affixTags: [],
      flag: false,
      pageId: null
    };
  },
  computed: {
    ...mapGetters(["dataInfo", "sessionObj", "isCloseAll"]),
    visitedViews() {
      if (this.$store.getters.visitedViews2.length) {
        // 连接数据源后 从仓库获取窗口数据
        let visitedData = JSON.parse(localStorage.getItem("saveVisitedViews2"));
        if (!visitedData) {
          this.$store.getters.visitedViews2.forEach(item => {
            item.matched = []; // 因为仓库数据【visitedViews2】不可扩展  无法深拷贝 因此置空matched做缓存
          });
          localStorage.setItem(
            // 存储初始化时新建窗口数据
            "saveVisitedViews2",
            JSON.stringify(this.$store.getters.visitedViews2)
          );
        }
        return this.$store.getters.visitedViews2;
      } else if (!this.$route.query?.isShow) {
        // 刷新后从缓存获取
        let visitedData = JSON.parse(localStorage.getItem("saveVisitedViews2"));
        if (visitedData) {
          if (!this.$store.state.jdTagsView.visitedViews.length) {
            this.$store.state.jdTagsView.visitedViews = visitedData;
          }
          if (this.$route.params.id !== visitedData[0].params.id) {
            // 如果选中的是新建查询 在刷新后 替换路由ID为上次缓存窗口数据
            this.$router.push({
              path: "/workspace/look/" + visitedData[0].params.id
            });
          }
          console.log(visitedData, this.$route, "visitedData");
        }
        return visitedData;
      }
    },
    routes() {
      return this.$store.state.permission.routes;
    },
    theme() {
      return this.$store.state.settings.theme;
    },
    titleMap() {
      return this.$store.state.jdTagsView.titleMap;
    },
    getViewTitle() {
      return function(item) {
        let id = item.params.id;
        let title = "";

        this.titleMap.forEach(item => {
          if (item.id == id) {
            title = item.title;
          }
        });
        return title || item.meta.title;
      };
    }
  },
  watch: {
    isCloseAll: {
      handler(val) {
        // console.log(val, this.visitedViews, "isCloseAll");
        if (Object.keys(this.sessionObj).length) {
          this.sessionObj.isAllDelete = this.isCloseAll;
          sqlServer.delSession(this.sessionObj).then(res => {
            if (res.data[0].success) {
              this.$nextTick(() => {
                let arr = this.visitedViews?.filter(
                  item => item.title == "新建查询"
                );
                arr?.forEach(view => {
                  this.$jdTab.closePage(view).then(({ visitedViews }) => {
                    this.toLastView(visitedViews, view);
                    this.$store.dispatch("getSessionId", {});
                  });
                });
                this.$store.commit("SET_ISCLOSEALL", false);
              });
            }
          });
        } else {
          this.$nextTick(() => {
            let arr = this.visitedViews?.filter(
              item => item.title == "新建查询"
            );
            arr?.forEach(view => {
              this.$jdTab.closePage(view).then(({ visitedViews }) => {
                this.toLastView(visitedViews, view);
              });
            });
            this.$store.commit("SET_ISCLOSEALL", false);
          });
          console.log(this.visitedViews, "this.visitedViews");
        }
      },
      deep: true,
      immediate: true
    },
    $route() {
      this.addTags();
      this.moveToCurrentTag();
    },
    visible(value) {
      if (value) {
        document.body.addEventListener("click", this.closeMenu);
      } else {
        document.body.removeEventListener("click", this.closeMenu);
      }
    }
  },
  mounted() {
    if (this.$store.getters.visitedViews2.length) {
      this.activeStyle(this.$store.getters.visitedViews2[0]);
    }

    let createData = JSON.parse(sessionStorage.getItem("createData"));
    if (!createData) {
      this.$router.push({
        path: "/workspace"
      });
    }
  },
  methods: {
    isActive(route) {
      return route.path === this.$route.path;
    },
    activeStyle(tag) {
      if (!this.isActive(tag)) return {};
      return {
        "background-color": this.theme,
        "border-color": this.theme
      };
    },
    isAffix(tag) {
      return (tag.meta && tag.meta.affix) || this.visitedViews?.length == 1;
    },
    isFirstView() {
      try {
        return (
          this.selectedTag.fullPath === "/index" ||
          this.selectedTag.fullPath === this.visitedViews[1].fullPath
        );
      } catch (err) {
        return false;
      }
    },
    isLastView() {
      try {
        return (
          this.selectedTag.fullPath ===
          this.visitedViews[this.visitedViews.length - 1].fullPath
        );
      } catch (err) {
        return false;
      }
    },
    filterAffixTags(routes, basePath = "/") {
      let tags = [];
      routes.forEach(route => {
        if (route.meta && route.meta.affix) {
          const tagPath = path.resolve(basePath, route.path);
          tags.push({
            fullPath: tagPath,
            path: tagPath,
            name: route.name,
            meta: { ...route.meta }
          });
        }
        if (route.children) {
          const tempTags = this.filterAffixTags(route.children, route.path);
          if (tempTags.length >= 1) {
            tags = [...tags, ...tempTags];
          }
        }
      });
      return tags;
    },
    initTags() {
      const affixTags = (this.affixTags = this.filterAffixTags(this.routes));
      for (const tag of affixTags) {
        // Must have tag name
        if (tag.name) {
          this.$store.dispatch("jdTagsView/addVisitedView", tag);
        }
      }
    },
    async routerLinkFn(type) {
      this.pageId = uuidv4();
      if (type !== "look") {
        await this.$store
          .dispatch("workspaceData/createConsole", {
            pageId: this.pageId,
            dataSourceId: this.dataInfo.dataSource.id,
            dataSourceName: this.dataInfo.dataSource.alias,
            databaseType: this.dataInfo.dataSource.type
          })
          .then(res => {
            this.$router.push({
              path: "/workspace/query/" + this.pageId
            });
          });
      } else {
        this.$router.push({ path: `/workspace/${type}/` + this.pageId });
      }
      this.showPop = !this.showPop;
    },
    addTags() {
      // debugger
      const { name } = this.$route;
      if (name) {
        this.$store.dispatch("jdTagsView/addView", this.$route);
        if (this.$route.meta.link) {
          this.$store.dispatch("jdTagsView/addIframeView", this.$route);
        }
        // 如果选中新建查询后再刷新 窗口ID对不上 因此只保存新建窗口数据
        if (this.$store.getters.visitedViews2?.length) {
          this.$store.getters.visitedViews2.forEach(item => {
            if (
              item.params.id == this.$route.params.id &&
              item.title != "新建查询"
            ) {
              item.matched = [];
              localStorage.setItem("saveVisitedViews2", JSON.stringify([item]));
            }
          });
        }
      }
      return false;
    },
    moveToCurrentTag() {
      const tags = this.$refs.tag;
      if (!tags) return;
      this.$nextTick(() => {
        for (const tag of tags) {
          if (tag.to.path === this.$route.path) {
            this.$refs.scrollPane.moveToTarget(tag);
            // when query is different then update
            if (tag.to.fullPath !== this.$route.fullPath) {
              this.$store.dispatch("jdTagsView/updateVisitedView", this.$route);
            }
            break;
          }
        }
      });
    },
    refreshSelectedTag(view) {
      this.$jdTab.refreshPage(view);
      if (this.$route.meta.link) {
        this.$store.dispatch("jdTagsView/delIframeView", this.$route);
      }
    },
    closeSelectedTag(view) {
      console.log(view, "点击关闭tag");
      // if (this.isActive(view)) {
      this.$jdTab.closePage(view).then(({ visitedViews }) => {
        this.toLastView(visitedViews, view);
        this.$store.commit("SET_ISCLOSEALL", false);
      });
      // }
    },
    closeRightTags() {
      this.$jdTab.closeRightPage(this.selectedTag).then(visitedViews => {
        if (!visitedViews.find(i => i.fullPath === this.$route.fullPath)) {
          this.toLastView(visitedViews);
        }
      });
    },
    closeLeftTags() {
      this.$jdTab.closeLeftPage(this.selectedTag).then(visitedViews => {
        if (!visitedViews.find(i => i.fullPath === this.$route.fullPath)) {
          this.toLastView(visitedViews);
        }
      });
    },
    closeOthersTags() {
      this.$router.push(this.selectedTag.fullPath).catch(() => {});
      this.$jdTab.closeOtherPage(this.selectedTag).then(() => {
        this.moveToCurrentTag();
      });
    },
    closeAllTags(view) {
      this.$jdTab.closeAllPage().then(({ visitedViews }) => {
        if (this.affixTags.some(tag => tag.path === this.$route.path)) {
          return;
        }
        this.toLastView(visitedViews, view);
      });
    },
    toLastView(visitedViews, view) {
      // debugger
      const latestView = visitedViews.slice(-1)[0];

      if (latestView) {
        this.$router.push(latestView.fullPath);
      } else {
        // now the default is to redirect to the home page if there is no tags-view,
        // you can adjust it according to your needs.
        if (view.name === "Dashboard") {
          // to reload home page
          this.$router.replace({ path: "/redirect" + view.fullPath });
        } else {
          this.$router.push("/");
        }
      }
    },
    openMenu(tag, e) {
      const menuMinWidth = 105;
      const offsetLeft = this.$el.getBoundingClientRect().left; // container margin left
      const offsetWidth = this.$el.offsetWidth; // container width
      const maxLeft = offsetWidth - menuMinWidth; // left boundary
      const left = e.clientX - offsetLeft + 15; // 15: margin right

      if (left > maxLeft) {
        this.left = maxLeft;
      } else {
        this.left = left;
      }

      this.top = e.clientY;
      this.visible = true;
      this.selectedTag = tag;
    },
    closeMenu() {
      this.visible = false;
    },
    handleScroll() {
      this.closeMenu();
    }
  }
};
</script>

<style lang="scss" scoped>
::v-deep .el-popover .elpop {
  height: 80px !important;
  overflow: hidden !important;
}

.badge {
  width: 17px;
  height: 17px;
  display: inline-block;
  background: #409eff;
  line-height: 19px;
  border-radius: 50%;
  color: #fff;
  text-align: center;
}
#iconColor {
  color: #409eff;
  cursor: pointer;
  line-height: 35px;
  margin-left: 5px;
}
.tags-view-container {
  height: 42px;
  width: 100%;
  background: #fff;
  border-bottom: 1px solid #d8dce5;
  box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.12), 0 0 3px 0 rgba(0, 0, 0, 0.04);
  .tags-view-wrapper {
    .tags-view-item {
      display: inline-block;
      position: relative;
      cursor: pointer;
      height: 26px;
      line-height: 26px;
      border: 1px solid #d8dce5;
      color: #495060;
      background: #fff;
      padding: 0 8px;
      font-size: 12px;
      margin-left: 5px;
      margin-top: 4px;
      &:first-of-type {
        margin-left: 15px;
      }
      &:last-of-type {
        margin-right: 15px;
      }
      &.active {
        background-color: #42b983;
        color: #fff;
        border-color: #42b983;
        &::before {
          content: "";
          background: #fff;
          display: inline-block;
          width: 8px;
          height: 8px;
          border-radius: 50%;
          position: relative;
          margin-right: 2px;
        }
      }
    }
  }
  .contextmenu {
    margin: 0;
    background: #fff;
    z-index: 3000;
    position: absolute;
    list-style-type: none;
    padding: 5px 0;
    border-radius: 4px;
    font-size: 12px;
    font-weight: 400;
    color: #333;
    box-shadow: 2px 2px 3px 0 rgba(0, 0, 0, 0.3);
    li {
      margin: 0;
      padding: 7px 16px;
      cursor: pointer;
      &:hover {
        background: #eee;
      }
    }
  }
}
</style>

<style lang="scss">
//reset element css of el-icon-close
.tags-view-wrapper {
  .tags-view-item {
    .el-icon-close {
      width: 16px;
      height: 16px;
      vertical-align: 2px;
      border-radius: 50%;
      text-align: center;
      transition: all 0.3s cubic-bezier(0.645, 0.045, 0.355, 1);
      transform-origin: 100% 50%;
      &:before {
        transform: scale(0.6);
        display: inline-block;
        vertical-align: -3px;
      }
      &:hover {
        background-color: #b4bccc;
        color: #fff;
      }
    }
  }
}
::v-deep .el-scrollbar__wrap {
  height: 45px;
}
::v-deep .el-popover .el-popper .elpop {
  height: 80px !important;
  overflow: hidden !important;
}
</style>
