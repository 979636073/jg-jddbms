const { createRenderer } = require('vue')

// Exercise actual Vue 3 option initialization, reactivity and unmount hooks without a DOM.
// Layout-dependent mounted hooks are covered by the opt-in browser regressions.
exports.mountOptions = (component, props = {}) => {
  const renderer = createRenderer({
    createComment: () => ({}), insert() {}, remove() {}, parentNode() {}, nextSibling() {}
  })
  const app = renderer.createApp({ ...component, mounted: undefined, render: () => null }, props)
  const vm = app.mount({})
  return { vm, unmount: () => app.unmount() }
}
