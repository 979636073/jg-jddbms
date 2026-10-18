<script>
import sqlServer from "@/api/main/sql";
import { copyText } from "@/utils";
import { prepareSqlResult } from "@/utils/sqlResult";
import FileDialog from "../renderSearchResult/filedialog.vue";

const ROW_HEIGHT = 28;
const HEADER_HEIGHT = 32;
const COLUMN_WIDTH = 160;
export default {
  name: "SqlResultTable",
  components: { FileDialog },
  props: { queryResultData: { type: Object, required: true } },
  data() {
    return { pageNo: 1, pageSize: 200, loading: false, scrollTop: 0, scrollLeft: 0,
      viewportWidth: 800, viewportHeight: 200, selectedCell: null, selectedColumns: [],
      valueVisible: false, valueText: "", sort: {}, binaryLoading: false, columnWidths: {} };
  },
  computed: {
    rows() { return this.queryResultData.dataList || []; },
    automaticWidths() {
      const sample = this.rows.slice(0, 100);
      return (this.queryResultData.headerList || []).map((header, index) => {
        if (header.dataType === "CHAT2DB_ROW_NUMBER") return 64;
        let width = this.textWidth(header.name, true) + 44;
        for (const row of sample) {
          const value = this.isBinary(header) && row[index] != null ? "文件 查看" : this.displayValue(row[index]);
          width = Math.max(width, this.textWidth(value) + 24);
        }
        return Math.min(480, Math.max(80, Math.ceil(width)));
      });
    },
    columns() {
      let left = 0;
      return (this.queryResultData.headerList || []).map((header, index) => {
        const width = this.columnWidths[index] || this.automaticWidths[index];
        const column = { ...header, index, left, width };
        left += width;
        return column;
      });
    },
    gridWidth() { return this.columns.reduce((width, column) => width + column.width, 0); },
    gridHeight() { return HEADER_HEIGHT + this.rows.length * ROW_HEIGHT; },
    visibleColumns() {
      return this.columns.filter(column => column.left + column.width > this.scrollLeft - COLUMN_WIDTH &&
        column.left < this.scrollLeft + this.viewportWidth + COLUMN_WIDTH);
    },
    visibleRows() {
      const start = Math.max(0, Math.floor((this.scrollTop - HEADER_HEIGHT) / ROW_HEIGHT) - 3);
      const end = Math.min(this.rows.length, start + Math.ceil(this.viewportHeight / ROW_HEIGHT) + 8);
      return this.rows.slice(start, end).map((row, index) => ({ row, index: start + index }));
    },
    totalExact() {
      return this.queryResultData.totalExact !== false && this.queryResultData.fuzzyTotal != null;
    },
    total() { return this.totalExact ? Number(this.queryResultData.fuzzyTotal) : 0; },
  },
  watch: {
    queryResultData: {
      immediate: true,
      handler(result, previous) {
        if (!previous || result.originalSql !== previous.originalSql || result.params?.sql !== previous.params?.sql ||
          JSON.stringify(result.headerList) !== JSON.stringify(previous.headerList)) this.columnWidths = {};
        this.pageNo = Number(result.pageNo || 1);
        this.pageSize = Number(result.pageSize || 200);
        this.sort = { prop: result.params && result.params.orderByColumn, asc: result.params && result.params.isAsc };
        this.selectedCell = null;
        this.selectedColumns = [];
        this.valueVisible = false;
        this.loading = false;
        this.scrollTop = 0;
        this.$nextTick(() => { if (this.$refs.viewport) this.$refs.viewport.scrollTop = 0; });
      },
    },
  },
  mounted() {
    this.measure();
    this._resizeObserver = new ResizeObserver(this.measure);
    this._resizeObserver.observe(this.$refs.viewport);
  },
  beforeUnmount() {
    this.stopResize();
    this._requestId = (this._requestId || 0) + 1;
    if (this._resizeObserver) this._resizeObserver.disconnect();
    if (this._scrollFrame) cancelAnimationFrame(this._scrollFrame);
  },
  methods: {
    textWidth(value, bold = false) {
      if (!this._textContext && typeof document !== "undefined") this._textContext = document.createElement("canvas").getContext("2d");
      const text = String(value == null ? "" : value);
      if (!this._textContext) return Array.from(text).reduce((width, char) => width + (char.charCodeAt(0) > 255 ? 12 : 7), 0);
      if (!this._textFont) this._textFont = getComputedStyle(this.$el || document.body).fontFamily;
      this._textContext.font = `${bold ? "600 " : ""}12px ${this._textFont}`;
      return this._textContext.measureText(text).width;
    },
    startResize(event, column) {
      if (event.button !== 0) return;
      this._columnResize = { index: column.index, startX: event.clientX, width: column.width };
      window.addEventListener("pointermove", this.resizeColumn);
      window.addEventListener("pointerup", this.stopResize);
      window.addEventListener("pointercancel", this.stopResize);
    },
    resizeColumn(event) {
      if (!this._columnResize) return;
      const { index, startX, width } = this._columnResize;
      this.columnWidths[index] = Math.max(64, width + event.clientX - startX);
    },
    stopResize() {
      if (!this._columnResize) return;
      this._columnResize = null;
      window.removeEventListener("pointermove", this.resizeColumn);
      window.removeEventListener("pointerup", this.stopResize);
      window.removeEventListener("pointercancel", this.stopResize);
    },
    fitColumn(column) { delete this.columnWidths[column.index]; },
    measure() {
      const viewport = this.$refs.viewport;
      if (viewport) {
        this.viewportWidth = viewport.clientWidth;
        this.viewportHeight = viewport.clientHeight;
      }
    },
    onScroll(event) {
      const viewport = event.target;
      if (this._scrollFrame) return;
      this._scrollFrame = requestAnimationFrame(() => {
        this._scrollFrame = null;
        this.scrollTop = viewport.scrollTop;
        this.scrollLeft = viewport.scrollLeft;
      });
    },
    columnStyle(column) { return { left: column.left + "px", width: column.width + "px" }; },
    displayValue(value) { return value == null ? "<null>" : String(value).slice(0, 200); },
    changePage(page) { if (page !== this.pageNo) return this.fetchPage(page, this.pageSize); },
    changeSize(size) { return this.fetchPage(1, size); },
    changeSort(column, asc) {
      if (this.loading || column.dataType === "CHAT2DB_ROW_NUMBER") return;
      return this.fetchPage(1, this.pageSize, { prop: column.name, asc });
    },
    async fetchPage(pageNo, pageSize, sort = this.sort) {
      const source = this.queryResultData;
      const requestId = this._requestId = (this._requestId || 0) + 1;
      const params = { ...source.params, sql: source.params.sql, pageNo, pageSize,
        skipCount: false, pageSizeAll: false, isLog: false, executionId: undefined,
        orderByColumn: sort.prop, isAsc: sort.asc };
      this.pageNo = pageNo;
      this.pageSize = pageSize;
      this.loading = true;
      try {
        const response = await sqlServer.executeSql(params);
        if (requestId !== this._requestId || source !== this.queryResultData) return;
        const result = response.data && response.data[0];
        if (!response.success || !result || !result.success) {
          throw new Error((result && result.message) || response.errorMessage || "查询失败");
        }
        this.pageNo = Number(result.pageNo);
        this.pageSize = Number(result.pageSize);
        this.sort = sort;
        this.$emit("setQueryResultData", prepareSqlResult({ ...source, ...result,
          params: { ...params, sql: source.params.sql, sessionId: result.sessionId || params.sessionId } }));
      } catch (error) {
        if (requestId !== this._requestId || source !== this.queryResultData) return;
        this.pageNo = Number(source.pageNo || 1);
        this.pageSize = Number(source.pageSize || 200);
        this.$message.error(error.message || "查询失败，请重试");
      } finally {
        if (requestId === this._requestId) this.loading = false;
      }
    },
    selectCell(rowIndex, columnIndex) { this.selectedCell = { rowIndex, columnIndex }; },
    copyCell() {
      if (!this.selectedCell) return;
      const { rowIndex, columnIndex } = this.selectedCell;
      copyText(this.rows[rowIndex][columnIndex] == null ? "" : String(this.rows[rowIndex][columnIndex]));
    },
    copyColumns(column, event) {
      this.selectedColumns = event.shiftKey ? [...new Set([...this.selectedColumns, column.index])] : [column.index];
      const text = [this.selectedColumns.map(index => this.columns[index].name).join("\t"),
        ...this.rows.map(row => this.selectedColumns.map(index => row[index] == null ? "" : String(row[index])).join("\t"))].join("\n");
      copyText(text);
    },
    showValue(rowIndex, columnIndex) {
      if (this.isBinary(this.columns[columnIndex])) return this.showBinary(rowIndex, columnIndex);
      this.selectCell(rowIndex, columnIndex);
      this.valueText = this.rows[rowIndex][columnIndex] == null ? "<null>" : String(this.rows[rowIndex][columnIndex]);
      this.valueVisible = true;
    },
    isBinary(column) { return /BLOB|BYTE|BINARY|RAW|BFILE|IMAGE/.test(String(column.dataType).toUpperCase()); },
    async showBinary(rowIndex, columnIndex) {
      if (this.binaryLoading || this.rows[rowIndex][columnIndex] == null) return;
      this.binaryLoading = true;
      try {
        const params = this.queryResultData.params || {};
        const columns = this.columns.filter(column => column.dataType !== "CHAT2DB_ROW_NUMBER");
        // eslint-disable-next-line no-control-regex
        const name = `query_${this.columns[columnIndex].name}_${rowIndex + 1}`.replace(/[<>:"/\\|?*\x00-\x1f]/g, "_");
        await this.$refs.fileRef.open({
          dataSourceId: params.dataSourceId, databaseName: params.databaseName, schemaName: params.schemaName,
          sql: this.queryResultData.originalSql, blobColumn: this.columns[columnIndex].name,
          resultColumns: columns.map(column => column.name),
          resultValues: columns.map(column => this.rows[rowIndex][column.index]),
        }, name);
      } finally { this.binaryLoading = false; }
    },
    contextMenu(event, rowIndex, columnIndex) {
      this.selectCell(rowIndex, columnIndex);
      this.$contextmenu({ event, items: [
        { label: "复制单元格", onClick: this.copyCell },
        { label: "复制行", onClick: () => copyText(this.rows[rowIndex].map(value => value == null ? "" : String(value)).join("\t")) },
        { label: "查看字段值", onClick: () => this.showValue(rowIndex, columnIndex) },
      ] });
    },
  },
};
</script>

<template>
  <div class="sql-result-table" v-loading="loading">
    <div ref="viewport" class="result-viewport" role="grid" :aria-rowcount="rows.length" :aria-colcount="columns.length"
      @scroll.passive="onScroll" @keydown.ctrl.c.prevent="copyCell">
      <div class="result-canvas" :style="{ width: gridWidth + 'px', height: gridHeight + 'px' }">
        <div class="result-header" role="row">
          <div v-for="column in visibleColumns" :key="column.index" role="columnheader" class="result-cell header-cell"
            :class="{ selected: selectedColumns.includes(column.index) }" :style="columnStyle(column)"
            :title="column.name" @click="copyColumns(column, $event)">
            <span v-if="column.dataType !== 'CHAT2DB_ROW_NUMBER'" class="sort-icons">
              <button :disabled="loading" :class="{ active: sort.prop === column.name && sort.asc === true }"
                :aria-label="column.name + ' 升序'" @click.stop="changeSort(column, true)"><i class="el-icon-caret-top" /></button>
              <button :disabled="loading" :class="{ active: sort.prop === column.name && sort.asc === false }"
                :aria-label="column.name + ' 降序'" @click.stop="changeSort(column, false)"><i class="el-icon-caret-bottom" /></button>
            </span>
            <span class="column-name">{{ column.name }}</span>
            <span v-if="column.dataType !== 'CHAT2DB_ROW_NUMBER'" class="column-resize-handle"
              title="拖动调整列宽，双击自动适配" @pointerdown.stop.prevent="startResize($event, column)"
              @click.stop @dblclick.stop="fitColumn(column)" />
          </div>
        </div>
        <div v-for="entry in visibleRows" :key="entry.index" class="result-row" role="row"
          :style="{ top: (32 + entry.index * 28) + 'px' }">
          <div v-for="column in visibleColumns" :key="column.index" class="result-cell" role="gridcell" tabindex="0"
            :class="{ selected: selectedCell && selectedCell.rowIndex === entry.index && selectedCell.columnIndex === column.index, 'is-null': entry.row[column.index] == null }"
            :style="columnStyle(column)" :title="displayValue(entry.row[column.index])"
            @click="selectCell(entry.index, column.index)" @dblclick="showValue(entry.index, column.index)"
            @contextmenu.prevent="contextMenu($event, entry.index, column.index)">
            <template v-if="isBinary(column) && entry.row[column.index] != null">
              文件 <el-button type="text" size="small" :loading="binaryLoading" @click.stop="showBinary(entry.index, column.index)">查看</el-button>
            </template>
            <template v-else>{{ displayValue(entry.row[column.index]) }}</template>
          </div>
        </div>
      </div>
      <div v-if="!rows.length" class="result-empty">暂无数据</div>
    </div>
    <div class="result-footer">
      <span>【结果】{{ queryResultData.description || '执行成功' }}&emsp;【耗时】{{ queryResultData.duration }}ms</span>
      <span v-if="!totalExact" class="count-error">总数统计失败</span>
      <el-pagination small :disabled="loading" :current-page="pageNo" :page-size="pageSize"
        :page-sizes="[20, 50, 100, 200, 500, 1000]" :total="total"
        :layout="totalExact ? 'total, sizes, prev, pager, next, jumper' : 'sizes, prev, slot, next'"
        :page-count="totalExact ? undefined : pageNo + (queryResultData.hasNextPage ? 1 : 0)"
        @size-change="changeSize" @current-change="changePage">
        <span>第 {{ pageNo }} 页</span>
      </el-pagination>
    </div>
    <el-dialog title="字段值（只读）" v-model="valueVisible" width="650px" append-to-body>
      <el-input type="textarea" readonly :model-value="valueText" :rows="12" />
      <template #footer><span ><el-button size="small" @click="copyCell">复制</el-button><el-button size="small" @click="valueVisible = false">关闭</el-button></span></template>
    </el-dialog>
    <FileDialog ref="fileRef" />
  </div>
</template>

<style scoped lang="scss">
.sql-result-table { display: flex; flex-direction: column; flex: 1; min-height: 0; min-width: 0; font-size: 12px; color: #31435d; }
.result-viewport { flex: 1; min-height: 0; overflow: auto; border: 1px solid #dfe5ee; position: relative; }
.result-canvas { position: relative; min-width: 100%; }
.result-header { position: sticky; top: 0; height: 32px; z-index: 2; background: #f7f9fc; }
.result-row { position: absolute; left: 0; width: 100%; height: 28px; }
.result-cell { position: absolute; height: 28px; line-height: 27px; padding: 0 10px; box-sizing: border-box;
  overflow: hidden; white-space: nowrap; text-overflow: ellipsis; border-right: 1px solid #e7ebf2; border-bottom: 1px solid #e7ebf2;
  &.selected { background: #eaf2ff; }
  &:focus { outline: 1px solid #409eff; outline-offset: -1px; }
  &.is-null { color: #909399; }
}
.header-cell { height: 32px; line-height: 31px; font-weight: 600; display: flex; align-items: center; cursor: pointer; }
.column-name { overflow: hidden; text-overflow: ellipsis; }
.column-resize-handle { position: absolute; right: 0; top: 0; width: 8px; height: 100%; cursor: col-resize; touch-action: none;
  &:hover { background: rgba(64, 158, 255, 0.2); }
}
.sort-icons { display: flex; flex-direction: column; margin-right: 6px;
  button { height: 12px; padding: 0; border: 0; background: transparent; color: #c0c4cc; cursor: pointer; line-height: 12px;
    &.active { color: #409eff; }
    &:disabled { cursor: wait; }
  }
}
.result-empty { position: absolute; top: 50%; width: 100%; text-align: center; color: #909399; }
.result-footer { display: flex; align-items: center; justify-content: space-between; gap: 8px; flex-shrink: 0;
  min-height: 38px; white-space: nowrap; overflow-x: auto; border-top: 1px solid #e7ebf2;
  .count-error { color: #e6a23c; }
}
</style>
