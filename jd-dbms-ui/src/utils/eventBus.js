// Keep the existing workspace event contract without creating a Vue component instance.
export function createEventBus() {
  const listeners = new Map()
  const bus = {
    $on(event, handler) {
      if (!listeners.has(event)) listeners.set(event, new Set())
      listeners.get(event).add(handler)
      return bus
    },
    $off(event, handler) {
      if (event === undefined) listeners.clear()
      else if (!handler) listeners.delete(event)
      else listeners.get(event)?.delete(handler)
      return bus
    },
    $emit(event, ...args) {
      Array.from(listeners.get(event) || []).forEach(handler => handler(...args))
      return bus
    },
    $once(event, handler) {
      const once = (...args) => {
        bus.$off(event, once)
        handler(...args)
      }
      return bus.$on(event, once)
    }
  }
  return bus
}
