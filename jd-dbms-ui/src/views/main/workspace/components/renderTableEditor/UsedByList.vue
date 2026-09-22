<template>
  <div>
    <el-button size="mini" type="text" @click="refreshFn">
        <img src="@/assets/main/5-ico3.png" alt="" />
        刷新
      </el-button>
    <el-select v-model="relationTypeValue" size="mini" style="width: 130px; margin-left: 12px" @change="changeRelationType">
      <el-option label="外键关联" value="table" />
      <el-option label="视图依赖" value="view" />
    </el-select>
    <div class="isRadioBox">
      <el-tabs v-model="displayMode" type="card" size="mini">
        <el-tab-pane label="关系树" name="tree">
      <el-table
        height="700"
        :data="tableData"
        style="width: 100%; margin-bottom: 20px"
        :default-expand-all="false"
         row-key="id"
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
        v-loading="isLoading"
      >
        <el-table-column prop="schemaName" label="所属模式" sortable width="180">
        </el-table-column>
        <el-table-column prop="type" label="类型" sortable width="180">
        </el-table-column>
        <el-table-column prop="tableName" label="名称"> </el-table-column>
        <el-table-column label="列关系" min-width="220">
          <template slot-scope="scope">
            <span v-if="scope.row.column || scope.row.foreignColumnName">{{ relationLabel(scope.row) }}</span>
            <span v-else class="relation-empty">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="constraintName" label="约束名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="status" label="状态"> </el-table-column>
      </el-table>
        </el-tab-pane>
        <el-tab-pane label="ER图" name="graph">
          <div class="graph-toolbar">
            <el-button-group>
              <el-button size="mini" icon="el-icon-zoom-in" @click="zoomGraph(1.1)" />
              <el-button size="mini" icon="el-icon-zoom-out" @click="zoomGraph(0.9)" />
              <el-button size="mini" icon="el-icon-aim" @click="fitGraph" />
            </el-button-group>
            <span v-if="graphTruncated" class="graph-limit-tip">
              关系较多，仅展示前 {{ graphNodeLimit }} 个节点（其余 {{ graphTruncated }} 个可在关系树中展开查看）
            </span>
          </div>
          <div v-if="graphNodes.length" ref="graphCanvas" class="er-graph-wrap" />
          <el-empty v-else description="暂无关联关系，无法生成ER图" />
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script>
import G6 from "@antv/g6";

export default {
  name: "Used",
  props: {
    tableData: {
      type: Array,
      default: () => [],
    },
    isLoading:{
      type:Boolean,
      default:false
    },
    relationType: {
      type: String,
      default: "table"
    }
  },
  data() {
    return {
      radio: "tree",
      relationTypeValue: this.relationType,
      displayMode: "tree",
      graph: null,
    };
  },
  watch: {
    tableData: {
      handler() {
        if (this.displayMode === "graph") this.$nextTick(this.initGraph);
      },
    },
    displayMode(value) {
      if (value === "graph") this.$nextTick(this.initGraph);
      else this.destroyGraph();
    },
  },
  computed: {
    graphNodeLimit() {
      return 200;
    },
    graphTruncated() {
      return Math.max(0, this.flattenRelationCount(this.tableData) - this.graphNodeLimit);
    },
    graphNodes() {
      if (!this.tableData || !this.tableData.length) return [];
      const root = this.tableData[0];
      const nodes = [];
      const walk = (item, depth, index, parent) => {
        if (nodes.length >= this.graphNodeLimit) return;
        const node = {
          ...item,
          id: item.id || `${depth}-${index}`,
          x: depth * 280 + 24,
          y: index * 92 + 24,
          root: !parent,
          parentId: parent ? parent.id : null,
        };
        nodes.push(node);
        (item.children || []).forEach((child, childIndex) => walk(child, depth + 1, childIndex, node));
      };
      walk(root, 0, 0, null);
      return nodes;
    },
  },
  methods: {
    destroyGraph() {
      if (this.graph) {
        this.graph.destroy();
        this.graph = null;
      }
    },
    initGraph() {
      if (!this.$refs.graphCanvas || !this.graphNodes.length) return;
      this.destroyGraph();
      this.graph = new G6.Graph({
        container: this.$refs.graphCanvas,
        width: this.$refs.graphCanvas.clientWidth || 800,
        height: 520,
        fitView: true,
        fitViewPadding: 40,
        layout: { type: "dagre", rankdir: "LR", nodesep: 40, ranksep: 100 },
        modes: { default: ["drag-canvas", "zoom-canvas", "drag-node"] },
        defaultNode: {
          type: "rect",
          size: [240, 68],
          style: { radius: 5, fill: "#fff", stroke: "#dcdfe6", lineWidth: 1 },
          labelCfg: { position: "center", style: { fill: "#303133", fontSize: 13 } },
        },
        defaultEdge: {
          type: "cubic-horizontal",
          style: { stroke: "#c0c4cc", lineWidth: 2, endArrow: { path: G6.Arrow.triangle(8, 10, 0), fill: "#c0c4cc" } },
        },
      });
      const nodes = this.graphNodes.map(node => ({
        id: node.id,
        label: `${node.schemaName ? node.schemaName + "." : ""}${node.tableName}`,
        style: node.root ? { fill: "#ecf5ff", stroke: "#409eff", lineWidth: 2 } : undefined,
      }));
      const edges = this.graphNodes.filter(node => node.parentId).map(node => ({
        source: node.parentId,
        target: node.id,
        label: this.relationLabel(node),
        labelCfg: {
          autoRotate: true,
          style: {
            fill: "#606266",
            fontSize: 11,
            background: { fill: "#fff", padding: [2, 4, 2, 4], radius: 2 },
          },
        },
      }));
      this.graph.data({ nodes, edges });
      this.graph.render();
    },
    relationLabel(node) {
      const localColumn = node && (node.foreignColumnName || node.foreignColumn || node.column);
      const referencedColumn = node && (node.column || node.foreignColumnName || node.foreignColumn);
      const columns = localColumn && referencedColumn && localColumn !== referencedColumn
        ? `${localColumn} → ${referencedColumn}`
        : (localColumn || referencedColumn || "外键关联");
      return node && node.constraintName ? `${columns} (${node.constraintName})` : columns;
    },
    flattenRelationCount(items) {
      let count = 0;
      const walk = list => {
        (list || []).forEach(item => {
          count += 1;
          walk(item.children);
        });
      };
      walk(items);
      return count;
    },
    zoomGraph(ratio) {
      if (this.graph) this.graph.zoom(ratio, { x: 400, y: 260 });
    },
    fitGraph() {
      if (this.graph) this.graph.fitView(40);
    },
    changeRelationType(value) {
      this.$emit("changeRelationType", value);
    },
    refreshFn() {
      this.$emit("refreshForKe");
    },
  },
  beforeDestroy() {
    this.destroyGraph();
  },
};
</script>

<style lang='scss' scoped>
.graph-toolbar { margin: 8px 0; }
.graph-limit-tip { margin-left: 12px; color: #909399; font-size: 12px; }
.er-graph-wrap {
  width: 100%;
  height: 520px;
  overflow: hidden;
  background: #f7f9fc;
  border: 1px solid #ebeef5;
}
.relation-empty { color: #c0c4cc; }
</style>
