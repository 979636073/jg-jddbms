<template>
  <div>
    <div class="conainer_data">
      <div class="left">
        <el-select
          v-model="value"
          size="small"
          placeholder="请选择模式"
          @change="getTableData"
          filterable
          style="width: 100%"
        >
          <el-option
            v-for="item in options"
            :key="item.name"
            :label="item.name"
            :value="item.name"
          >
          </el-option>
        </el-select>
        <el-scrollbar style="height: calc(100%);margin-top:5px">
          <div
            v-for="(item, index) in tableLeftData"
            :key="index"
            class="itme_title"
            @click="clickItem(item, index)"
            :class="{ active: heightIndexs.includes(index) }"
          >
            <span class="itemName">{{ item.name }}</span>
          </div>
        </el-scrollbar>
      </div>

      <div class="right">
        <el-table :data="tableData" v-loading="loading" border height="650px" style="width: 100%">
          <el-table-column prop="desc" label="权限"> </el-table-column>
          <el-table-column prop="rule" label="授权">
            <template slot-scope="scope">
              <el-checkbox v-model="scope.row.rule" @change="()=>isAllRole(scope.row)"/>
            </template>
          </el-table-column>
          <!-- <el-table-column prop="toRule" label="转授">
            <template slot-scope="scope">
              <el-checkbox v-model="scope.row.toRule" />
            </template>
          </el-table-column> -->
        </el-table>
      </div>
      <div class="btn-grop">
        <el-button type="primary" :disabled='isDisable' @click="submit">确 定</el-button>
      </div>
    </div>
  </div>
</template>

<script>
import connectionServer from "@/api/main/connection";
import sqlServer from "@/api/main/sql";
import userServer from "@/api/main/user";

export default {
  props: {
    currentConfig: {
      type: Object,
      default: {},
    },
  },
  data() {
    return {
      value: "",
      options: [],
      tableLeftData: [],
      heightIndexs: [],
      tableData: [],
      oldTableData:[],
      selectItem:{},
      isDisable:true,
      loading:false
    };
  },
  mounted() {
    this.getDataBaseTree();
  },
  methods: {
    getDataBaseTree() {
      const params = {
        dataSourceId: this.currentConfig.uniqueData.dataSourceId,
        dataSourceName: this.currentConfig.uniqueData.databaseName,
        refresh: true,
      };
      connectionServer.getSchemaList(params).then((res) => {
        if (res.data.length > 0) {
          this.options = res.data;
        }
      });
    },
    getTableData(val) {
      const params = {
        dataSourceId: this.currentConfig.uniqueData.dataSourceId,
        dataSourceName: this.currentConfig.uniqueData.databaseName,
        databaseType: this.currentConfig.uniqueData.databaseType,
        schemaName: val,
        refresh: true,
        requestType: 2,
      };
      sqlServer.getTableList(params).then((res) => {
        if (res.success) {
          this.tableLeftData = res.data.data;
          this.heightIndexs = [];
          this.tableData = [];
        } else {
          this.$message.error('查询失败!')
        }
      });
    },
    clickItem(item, index) {
      this.selectItem = item
      this.heightIndexs = [index];
      this.loading = true;
      const params = {
        dataSourceId: this.currentConfig.uniqueData.dataSourceId,
        userName: this.currentConfig.title,
        schemaName: this.value,
        tableName: item.name,
      };
      userServer.queryObjAllRole(params).then((res) => {
        if(res.success) {
          this.tableData = res.data;
          this.oldTableData = JSON.parse(JSON.stringify(res.data));
          this.isDisable = true;
          this.loading = false;
        } else {
          this.loading = false;
          this.tableData = [];
          this.$message.error('查询失败!')
        }
      });
    },
    isAllRole(row) {
      if(row.desc == 'ALL' && row.rule == true) {
        this.tableData.forEach(item => {
          item.rule = true;
        })
      } else if(row.desc == 'ALL' && row.rule == false) {
        this.tableData.forEach(item => {
          item.rule = false;
        })
      }
      if(JSON.stringify(this.tableData) != JSON.stringify(this.oldTableData)) {
        this.isDisable = false;
      } else {
        this.isDisable = true;
      }
    },
    submit() {
      const params = {
        dataSourceId: this.currentConfig.uniqueData.dataSourceId,
        userName: this.currentConfig.title,
        schemaName: this.value,
        tableName: this.selectItem.name,
        newObjectRoleData:this.tableData.filter(item => item.desc != 'ALL'),
        oldObjectRoleData:this.oldTableData.filter(item => item.desc != 'ALL'),
      }
      userServer.saveObjectRole(params).then(res=>{
        if(res.success) {
          this.$message.success('授权成功!');
          this.clickItem(this.selectItem,this.heightIndexs)
        } else {
          this.$message.error(res.errorCode);
        }
      })
    }
  },
};
</script>

<style lang='scss' scoped>
.conainer_data {
  width: 100%;
  height: 700px;
  display: flex;
  .left {
    width: 18%;
    height: 100%;
    // margin-top: 10px;
    .itme_title {
      width: 100%;
      height: 25px;
      font-size: 14px;
      cursor: pointer;
      display: flex;
      align-items: center;
    }
  }
  .right {
    width: 82%;
    height: 100%;
    padding-left: 15px;
    position: relative;
  }
}
.active {
  color: #006fff;
  background: #f0f5ff;
  border-left: 3px solid #006fff;
  box-sizing: border-box;
}
.itemName {
  padding-left: 5px;
  font-size: 12px;
}
.btn-grop {
  position: absolute;
  right: 20px;
  bottom: 20px;
}
</style>