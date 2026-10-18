<template>
  <div ref="viewport" class="el-tree virtual-resource-tree" role="tree" :style="{ height }" @scroll.passive="onScroll">
    <div class="virtual-tree-canvas" :style="{ height: rows.length * itemSize + 'px' }">
      <div v-for="(node, index) in visibleRows" :key="node.key" class="el-tree-node"
        :class="{ 'is-current': highlightCurrent && node.isCurrent, 'is-expanded': node.expanded, 'is-leaf': node.isLeaf }"
        role="treeitem" tabindex="0" :aria-level="node.level" :aria-selected="node.isCurrent"
        :aria-expanded="node.isLeaf ? undefined : node.expanded"
        :style="{ top: (start + index) * itemSize + 'px', height: itemSize + 'px' }"
        @click="clickNode(node, $event)" @contextmenu.prevent="$emit('node-contextmenu', $event, node.data, node, $event.currentTarget)"
        @keydown.enter.prevent="clickNode(node, $event)" @keydown.space.prevent="clickNode(node, $event)"
        @keydown.right.prevent="expand(node)" @keydown.left.prevent="collapse(node)">
        <div class="el-tree-node__content" :style="{ height: itemSize + 'px', paddingLeft: (node.level - 1) * 18 + 'px' }">
          <span class="el-tree-node__expand-icon" :class="{ 'is-leaf': node.isLeaf, expanded: node.expanded }"
            aria-hidden="true" @click.stop="toggle(node)">
            <i :class="node.loading ? 'el-icon-loading' : 'el-icon-caret-right'" />
          </span>
          <slot :node="node" :data="node.data"><span>{{ node.label }}</span></slot>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { computed, ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { createResourceTreeStore, flattenVisibleTree } from './model'

export default {
  name: 'VirtualResourceTree',
  compatConfig: { MODE: 3 },
  props: {
    data: { type: Array, default: () => [] },
    props: { type: Object, default: () => ({}) },
    nodeKey: { type: String, required: true },
    load: Function,
    filterNodeMethod: Function,
    lazy: Boolean,
    highlightCurrent: Boolean,
    expandOnClickNode: { type: Boolean, default: true },
    itemSize: { type: Number, default: 28 },
    height: { type: String, default: '100%' }
  },
  emits: ['node-click', 'node-contextmenu', 'node-expand', 'node-collapse'],
  setup(props, { emit, expose }) {
    const viewport = ref(null)
    const scrollTop = ref(0)
    const viewportHeight = ref(560)
    const store = createResourceTreeStore({ data: props.data, key: props.nodeKey,
      props: props.props, lazy: props.lazy, load: props.load, filterNodeMethod: props.filterNodeMethod })
    const rows = computed(() => flattenVisibleTree(store))
    const start = computed(() => Math.max(0, Math.min(Math.floor(scrollTop.value / props.itemSize) - 5,
      rows.value.length - Math.ceil(viewportHeight.value / props.itemSize) - 10)))
    const visibleRows = computed(() => rows.value.slice(start.value, start.value + Math.ceil(viewportHeight.value / props.itemSize) + 10))
    const expand = node => { if (!node.isLeaf) node.expand(() => emit('node-expand', node.data, node)) }
    const collapse = node => { node.collapse(); emit('node-collapse', node.data, node) }
    const toggle = node => node.expanded ? collapse(node) : expand(node)
    const clickNode = (node, event) => {
      store.setCurrentNode(node)
      if (props.expandOnClickNode) toggle(node)
      const wasExpanded = node.expanded
      emit('node-click', node.data, node, event)
      if (wasExpanded !== node.expanded) emit(node.expanded ? 'node-expand' : 'node-collapse', node.data, node)
    }
    watch(() => props.data, data => store.setData(data))
    const onScroll = event => { scrollTop.value = event.target.scrollTop }
    let observer
    onMounted(() => {
      observer = new ResizeObserver(() => { viewportHeight.value = viewport.value.clientHeight })
      observer.observe(viewport.value)
      viewportHeight.value = viewport.value.clientHeight
    })
    onBeforeUnmount(() => observer?.disconnect())
    expose({ store, getNode: key => store.getNode(key), filter: value => store.filter(value),
      setCurrentKey: key => store.setCurrentNodeKey(key), getCurrentKey: () => store.currentNode?.key })
    return { viewport, rows, start, visibleRows, onScroll, clickNode, expand, collapse, toggle }
  }
}
</script>

<style scoped>
.virtual-resource-tree { overflow: auto; min-height: 0; }
.virtual-tree-canvas { position: relative; min-width: 100%; }
.el-tree-node { position: absolute; left: 0; width: 100%; }
.el-tree-node__content { display: flex; align-items: center; cursor: pointer; }
.el-tree-node__expand-icon { display: inline-flex; flex: 0 0 24px; justify-content: center; padding: 0; }
.el-tree-node__expand-icon.expanded { transform: rotate(90deg); }
.el-tree-node__expand-icon.is-leaf { visibility: hidden; }
.is-current > .el-tree-node__content { background: var(--el-color-primary-light-9); }
</style>
