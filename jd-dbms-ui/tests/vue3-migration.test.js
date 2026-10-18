const assert = require('assert')
const { createEventBus } = require('../src/utils/eventBus.js')
const { createResourceTreeStore, flattenVisibleTree } = require('../src/components/VirtualResourceTree/model.js')

// Workspace events must release the exact callback on tab disposal.
const bus = createEventBus()
const received = []
const listener = (...args) => received.push(args)
bus.$on('openLookPage', listener)
bus.$once('openLookPage', () => bus.$emit('nested', 1))
let nested = 0
bus.$on('nested', () => nested++)
bus.$emit('openLookPage', { tableName: 'QA' }, 226)
bus.$emit('openLookPage', { tableName: 'QA2' }, 227)
assert.strictEqual(nested, 1)
assert.deepStrictEqual(received, [[{ tableName: 'QA' }, 226], [{ tableName: 'QA2' }, 227]])
bus.$off('openLookPage', listener)
bus.$emit('openLookPage', 'disposed')
assert.strictEqual(received.length, 2)
bus.$off()
bus.$emit('nested')
assert.strictEqual(nested, 1)

// Flatten only visible, expanded branches; preserve loaded nodes and selection.
const store = createResourceTreeStore({ key: 'key', data: [
  { key: 'schema', label: 'APP', children: Array.from({ length: 10001 }, (_, i) => ({ key: 't' + i, label: 'T' + i })) }
], filterNodeMethod: (value, data) => !value || data.label === value })
assert.deepStrictEqual(flattenVisibleTree(store).map(node => node.key), ['schema'])
store.getNode('schema').expand()
assert.strictEqual(flattenVisibleTree(store).length, 10002)
store.setCurrentNodeKey('t10000')
store.filter('T10000')
assert.deepStrictEqual(flattenVisibleTree(store).map(node => node.key), ['schema', 't10000'])
store.getNode('schema').collapse()
store.filter('')
assert.deepStrictEqual(flattenVisibleTree(store).map(node => node.key), ['schema'])
assert.strictEqual(store.currentNode.key, 't10000')
assert.strictEqual(store.getNode('schema').childNodes.length, 10001)
console.log('Vue 3 迁移：事件清理与万节点树展开、过滤、选中状态通过')
