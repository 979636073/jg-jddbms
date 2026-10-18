import { reactive } from 'vue'
import TreeStore from 'element-plus/es/components/tree/src/model/tree-store.mjs'

// Temporary migration boundary: retain the workspace's lazy-node API while rendering
// only the viewport. Element Plus is pinned and the node contract is regression tested.
export function createResourceTreeStore(options) {
  const store = reactive(new TreeStore({
    defaultCheckedKeys: [], defaultExpandedKeys: [], props: {}, ...options
  }))
  store.initialize()
  // Element Plus's general-purpose filter is asynchronous and expands ancestors.
  // Workspace search must remain synchronous and preserve the user's expansion state.
  store.filter = value => {
    const visit = node => {
      const ownMatch = !store.filterNodeMethod || store.filterNodeMethod(value, node.data, node)
      let childMatch = false
      for (const child of node.childNodes) childMatch = visit(child) || childMatch
      node.visible = ownMatch || childMatch
      return node.visible
    }
    store.root.childNodes.forEach(visit)
  }
  return store
}

export function flattenVisibleTree(store) {
  const rows = []
  const visit = node => {
    if (!node.visible) return
    rows.push(node)
    if (node.expanded) node.childNodes.forEach(visit)
  }
  store.root.childNodes.forEach(visit)
  return rows
}
