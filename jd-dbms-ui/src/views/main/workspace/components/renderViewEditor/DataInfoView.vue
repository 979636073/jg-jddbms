<template>
  <div>
    <el-table
      size="mini"
      :data="tableData"
      row-key="key"
      height="calc(100% - 300px)"
    >
      <el-table-column
        v-for="(item, index) in columns"
        :key="index"
        :prop="item.dataIndex"
        :label="item.title"
        :width="item.width"
        :fixed="item.fixed"
      >
      </el-table-column>
    </el-table>
  </div>
</template>

<script>
export default {
  data() {
    return {
      columns: [
        {
          title: "Q_NAME",
          dataIndex: "name",
          width: "150px",

        },
        {
          title: "ROW_ID",
          dataIndex: "columnType",
          width: "150px",

        },
        {
          title: "MSGID",
          dataIndex: "columnSize",
          width: "150px",

        },
        {
          title: "CORRID",
          dataIndex: "nullable",
          width: "150px",

        },
        {
          title: "PRIORITY",
          dataIndex: "name",
          width: "120px",
        },
        {
          title: "STATE",
          dataIndex: "columnType",
          width: "150px",

        },
        {
          title: "DELAY",
          dataIndex: "columnSize",
          width: "150px",

        },
        {
          title: "EXPIRATION",
          dataIndex: "nullable",
          width: "150px",

        },
        {
          title: "ENQ_TIME",
          dataIndex: "name",
          width: "150px",

        },
        {
          title: "ENQ_UID",
          dataIndex: "columnType",
          width: "150px",

        },
        {
          title: "ENQ_TID",
          dataIndex: "columnSize",
          width: "150px",

        },
        {
          title: "DEQ_TIME",
          dataIndex: "nullable",
          width: "150px",

        },
        {
          title: "DEQ_UID",
          dataIndex: "nullable",
          width: "150px",

        },
        {
          title: "DEQ_TID",
          dataIndex: "name",
          width: "150px",

        },
        {
          title: "RETRY_COUNT",
          dataIndex: "columnType",
          width: "150px",
        },
        {
          title: "EXCEPTION_QSCHEMA",
          dataIndex: "columnSize",
          width: "180px",
        },
        {
          title: "EXCEPTION_QUEUE",
          dataIndex: "nullable",
          width: "180px",
        },
        {
          title: "CSCN",
          dataIndex: "columnType",
          width: "150px",

        },
        {
          title: "DSCN",
          dataIndex: "columnSize",
          width: "150px",

        },
        {
          title: "CHAIN_NO",
          dataIndex: "nullable",
          width: "150px",

        },
        {
          title: "LOVAL_ORDER_NO",
          dataIndex: "nullable",
          width: "150px",
        },
        {
          title: "TIME_MANAGER_INFO",
          dataIndex: "columnType",
          width: "180px",
        },
        {
          title: "STEP_NO",
          dataIndex: "columnSize",
          width: "150px",

        },
        {
          title: "USER_DATA",
          dataIndex: "nullable",
          width: "150px",

        },
        {
          title: "QURUR_ID",
          dataIndex: "nullable",
          width: "150px",

        },
      ],
    };
  },
};
</script>

<style>
</style>