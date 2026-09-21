<script>
import historyService from "@/api/main/history"
import { approximateList } from "@/utils/index"
import { workspaceTabConfig } from "@/views/main/workspace/constants/workspace"
export default {
  name: 'saveList',
  data() {
    return {
      dialogVisible: false,
      searching: false,
      searchValue: '',
      searchedList: null,
      consoleList: [],
      workspaceTabConfig,
      editData: null,
    }
  },
  mounted() {
    this.getSavedConsoleList()
  },
  methods: {
    getSavedConsoleList() {
      let send = {
        pageNo: 1,
        pageSize: 100,
        status: 'RELEASE',
      }
      historyService.getConsoleList(send)
        .then((res) => {
          this.consoleList = res?.data.data
        });
    },
    onBlur() {
      if (!this.searchValue) {
        this.searching = false
        this.searchedList = null
      }
    },

    onChange(value) {
      if (this.consoleList?.length) {
        this.searchedList = approximateList(this.consoleList, value)
      }
    },
    editSaved(data) {
      this.editData = data
    },

    openConsole(item) {
      const params = {
        id: item.id,
        tabOpened: 'y',
      };
      historyService.updateSavedConsole(params).then(() => {
        this.$store.dispatch('addWorkspaceTab', {
          id: item.id,
          type: item.operationType,
          title: item.name,
          uniqueData: {
            dataSourceId: item.dataSourceId,
            dataSourceName: item.dataSourceName,
            databaseType: item.type,
            databaseName: item.databaseName,
            schemaName: item.schemaName,
            status: item.status,
            ddl: item.ddl,
            connectable: item.connectable,
          },
        })
      });
    },
    editSubmit() {
      const params = {
        id: this.editData.id,
        name: this.editData.name,
      };
      historyService.updateSavedConsole(params).then(() => {
        this.getSavedConsoleList();
        this.editData = null
      });
    },
    deleteSaved(data) {
      historyService.deleteSavedConsole(data.id).then(() => {
        this.getSavedConsoleList();
      });
    },

    handleContextMenu(item) {
      this.$contextmenu.destroy();
      this.$contextmenu({
        items: [
          {
            label: "打开",
            onClick: () => {
              this.openConsole(item)
            },
          },
          {
            label: "重命名",
            onClick: () => {
              this.editSaved(item)
            },
          },
          {
            label: "删除",
            onClick: () => {
              this.deleteSaved(item)
            },
          }
        ],
        event,
        customClass: "resource-context-menu",
        zIndex: 999,
        minWidth: 100,
      });
      return false;
    },
    handleClose(done) {
      done();
    },
  }
}
</script>

<template>
  <div class="save_list">
    <div class="saveModule">
      <div ref="leftModuleTitleRef" class="leftModuleTitle">
        <div v-if="searching" class="leftModuleTitleSearch">
          <el-input
            size="mini"
            placeholder="搜索"
            prefix-icon="el-icon-search"
            v-model="searchValue"
            @blur="onBlur"
            @input="onChange"
          >
          </el-input>
        </div>
        <div v-else class="leftModuleTitleText">
          <div class="modelName">保存记录</div>
          <div class="iconBox">
            <div class="searchIcon" @click="() => searching = true">
              <i class="icon iconfont">&#xe600;</i>
            </div>
          </div>
        </div>
      </div>
      <div ref="saveBoxListRef" class="saveBoxList">
        <div v-for="item in searchedList || consoleList" :key="item.id" class="loadingContent">
          <div
            @dblclick="openConsole(item)"
            @contextmenu.prevent="handleContextMenu(item)"
            class="saveItem"
          >
            <div class="saveItemText" v-if="workspaceTabConfig[item.operationType]">
              <div class="iconBox">
                <i class="icon iconfont">{{ workspaceTabConfig[item.operationType].icon }}</i>
              </div>
              <div class="itemName" v-html="item.name" />
            </div>
          </div>
        </div>
      </div>
    </div>

    <el-dialog
      title="重命名"
      :visible.sync="editData"
      width="800"
      :before-close="handleClose"
      :close-on-click-modal="false"
    >
      <el-input v-if="editData" v-model="editData.name" placeholder="请输入名称"></el-input>
      <span slot="footer" class="dialog-footer">
        <el-button type="primary" @click="editSubmit">确定</el-button>
        <el-button @click="editData = null">取消</el-button>
      </span>
    </el-dialog>
  </div>
</template>
<style lang="scss" scoped>
$color-primary: #1677ff; // 品牌主色
$color-primary-bg: #e6f4ff; // 选中背景色，如下拉框选中的颜色
$color-primary-hover: rgba(27, 28, 33, 0.18); // 主色悬浮色
$color-primary-active: #0958d9; // 主色激活态
$color-border: rgba(211, 211, 212, 0.4);
$color-hover-bg: rgba(0, 0, 0, 0.03);
.saveModule {
  flex-shrink: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
}
.save_list {
  font-size: 12px;
  color: rgba(35, 36, 41, 0.88);
}
.leftModuleTitle {
  flex-shrink: 0;
  margin-bottom: 4px;
  padding: 0px 10px;
  height: 32px;
  display: flex;
  align-items: center;
  border-bottom: 1px solid $color-border;
  .leftModuleTitleText {
    width: 100%;
    display: flex;
    justify-content: space-between;
    align-items: center;
    height: 26px;
    .modelName {
      font-weight: bold;
      line-height: 100%;
    }
    .iconBox {
      display: flex;
      align-items: center;
      height: 100%;
    }
    .refreshIcon {
      margin-right: 10px;
    }
    .refreshIcon,
    .searchIcon {
      cursor: pointer;
      height: 100%;
      display: flex;
      align-items: center;
      &:hover {
        color: $color-primary;
      }
    }
  }
  .leftModuleTitleSearch {
    width: 100%;
    height: 26px;
  }
}

.loadingContent {
  height: auto;
  padding-bottom: 4px;
}

.saveBoxList {
  flex: 1;
  height: 0px;
  padding: 0px 4px;
  overflow-y: hidden;
  &:hover {
    overflow-y: auto;
  }
}

.leftModuleTitleShadow {
  // 地步加一点模糊
  box-shadow: 0px 1px 2px 0px $color-border;
}

.saveItem {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0px 6px;
  height: 26px;
  line-height: 26px;
  border-radius: 4px;
  user-select: none;
  cursor: pointer;
  .saveItemText {
    width: 0px;
    flex: 1;
    display: flex;
    align-items: center;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
    .iconBox {
      width: 20px;
      flex-shrink: 0;
    }
    .itemName {
      flex: 1;
      overflow: hidden;
      white-space: nowrap;
      text-overflow: ellipsis;
    }
  }

  &:hover {
    background-color: $color-hover-bg;
    color: $color-primary;
  }
}

</style>
