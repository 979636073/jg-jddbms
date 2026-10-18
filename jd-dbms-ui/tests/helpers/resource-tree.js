const { createResourceTreeStore } = require('../../src/components/VirtualResourceTree/model.js')

module.exports = options => {
  const store = createResourceTreeStore({ ...options, key: options.nodeKey })
  return { store, getNode: key => store.getNode(key), filter: value => store.filter(value),
    setCurrentKey: key => store.setCurrentNodeKey(key), getCurrentKey: () => store.currentNode?.key }
}
