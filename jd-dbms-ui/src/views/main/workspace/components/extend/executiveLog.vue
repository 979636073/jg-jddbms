<script>
import historyService from "@/api/main/history"
import * as monaco from 'monaco-editor';
import { copyText } from "@/utils/index"
export default {
  name: 'executiveLog',
  data() {
    return {
      count: 200,
      loading: false,
      curPageRef: {
        current: 1
      },
      dataSource: [],
    }
  },
  computed: {
    noMore () {
      return this.dataSource.length >= this.count
    },
    disabled () {
      return this.loading || this.noMore
    }
  },
  methods: {
    getHistoryList() {
      this.loading = true
      let send = {
        pageNo: this.curPageRef.current++,
        pageSize: 40,
      }
      return historyService.getHistoryList(send)
        .then((res) => {
          this.count = res.data.total
          const promiseList = res.data.data.map((item) => {
            return new Promise((resolve) => {
              // 不换行
              // const ddl = (item.ddl || '')?.replace(/\n/g, ' ');
              const ddl = item.ddl || '';
              monaco.editor.colorize(ddl, 'sql', {}).then((_res) => {
                resolve({
                  ...item,
                  nameList: [item.dataSourceName, item.databaseName, item.schemaName],
                  highlightedCode: _res,
                });
              });
            });
          });
          Promise.all(promiseList).then((_res) => {
            this.dataSource = [...this.dataSource, ..._res]
          });
          this.loading = false
        });
    },
    openSql(data) {
      this.$store.dispatch('createConsole', {
        ddl: data.ddl || '',
        dataSourceId: data.dataSourceId,
        dataSourceName: data.dataSourceName,
        databaseType: data.type,
        databaseName: data.databaseName,
        schemaName: data.schemaName,
      })
    },
    copySql(text) {
      copyText(text)
    }
  }
}
</script>

<template>
  <div class="output">
    <div class="outputTitle">执行记录</div>
    <div class="outputContent" ref="outputContentRef">
      <div
        v-infinite-scroll="getHistoryList"
        :infinite-scroll-disabled="disabled"
      >
        <div v-for="(item, index) in dataSource" :key="index" class="outputItem">
          <div class="timeBox">
            <i class="icon iconfont timeBoxIcon" :class="{failureIconBox: item.status !== 'success'}">&#xe650;</i>
            <span class="timeSpan">[{{ item.gmtCreate }}]</span>
            <span v-if="item.useTime">{{ item.useTime }}ms 执行完毕</span>
          </div>
          <div class="executedDatabaseBox">
            <el-tooltip
              class="item"
              effect="dark"
              content="打开执行记录"
              placement="left">
              <i @click="openSql(item)" class="icon iconfont">&#xe6bb;</i>
            </el-tooltip>
            <div class="executedDatabase">
              {{ item.nameList.filter((name) => name).join(' > ') }}
            </div>
          </div>
          <div class="sqlBox">
            <el-tooltip
              class="item"
              effect="dark"
              content="复制"
              placement="left">
              <i @click="copySql(item.ddl)" class="icon iconfont">&#xec7a;</i>
            </el-tooltip>
            <div class="sqlContent" v-html="item.highlightedCode" />
          </div>
        </div>
      </div>
      <p v-if="loading" style="text-align: center">加载中...</p>
      <p v-if="noMore" style="text-align: center">没有更多了</p>
    </div>
  </div>
</template>

<style scoped lang="scss">
.output {
  height: 100%;
  position: relative;
  display: flex;
  flex-direction: column;
  font-size: 12px;
  color: rgba(35, 36, 41, 0.88);
  .icon {
    width: 20px;
    height: 20px;
  }
  .outputTitle {
    position: sticky;
    top: 0;
    z-index: 1;
    display: flex;
    align-items: center;

    line-height: 32px;
    padding: 0px 10px;
    font-weight: bold;
    border-bottom: 1px solid rgba(211, 211, 212, 0.4);
    i {
      margin-right: 6px;
    }
  }
  .outputContent {
    padding: 10px;
    overflow-y: auto;
    flex: 1;
    height: 0px;
    .outputItem {
      .timeBox {
        display: flex;
        align-items: center;
      }
      .timeSpan {
        margin-right: 4px;
        font-weight: 500;
      }
      .timeBoxIcon {
        transform: rotate(90deg);
        margin-right: 4px;
        color: #49aa19;
        &:hover {
          cursor: default;
          background-color: transparent;
        }
      }
      .failureIconBox {
        color: #dc4446;
      }
      > div {
        line-height: 22px;
      }
      padding: 2px 0px;
    }
  }

  .executedDatabaseBox,.sqlBox{
    display: flex;
    .iconBox{
      margin-right: 4px;
      flex-shrink: 0;
    }
  }

  .executedDatabaseBox{
    .executedDatabase {
      color: #d89614;
      white-space: nowrap;
    }
  }
}
</style>
