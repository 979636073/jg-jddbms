const MIN_WIDTH = 38;
const MIN_HEIGHT = 50;
const TRIGGER_SIZE = 8;
const elEventsWeakMap = new WeakMap();

function getElStyleAttr(element, attr) {
  const styles = window.getComputedStyle(element);
  return styles[attr];
}

function getSiblingByPosition(el, position) {
  const siblingMap = {
    left: el.previousElementSibling,
    right: el.nextElementSibling,
    bottom: el.nextElementSibling,
    top: el.previousElementSibling
  };
  return siblingMap[position];
}

function updateSize({
                      el,
                      sibling,
                      formatter = "px",
                      elSize,
                      siblingSize,
                      attr = "width"
                    }) {
  const totalSize = elSize + siblingSize;
  if (formatter === "px") {
    el.style[attr] = elSize + formatter;
    sibling.style[attr] = siblingSize + formatter;
  } else if (formatter === "flex") {
    el.style.flex = elSize / totalSize;
    sibling.style.flex = siblingSize / totalSize;
  }
}

const initResize = ({
                      el,
                      positions,
                      minWidth = MIN_WIDTH,
                      minHeight = MIN_HEIGHT,
                      triggerSize = TRIGGER_SIZE,
                      formatter = "px"
                    }) => {
  if (!el) return;
  const resizeState = {};
  const defaultCursor = getElStyleAttr(el, "cursor");
  const elStyle = el.style;

  const canLeftResize = positions.includes("left");
  const canRightResize = positions.includes("right");
  const canTopResize = positions.includes("top");
  const canBottomResize = positions.includes("bottom");

  if (!canLeftResize && !canRightResize && !canTopResize && !canBottomResize)
    return; // 未指定方向

  const pointermove = (e) => {
    if (resizeState.resizing) return;
    e.preventDefault();
    const {left, right, top, bottom} = el.getBoundingClientRect();
    const {clientX, clientY} = e;
    // 左右拉伸
    if (canLeftResize || canRightResize) {
      if (clientX - left < triggerSize) resizeState.position = "left";
      else if (right - clientX < triggerSize) resizeState.position = "right";
      else resizeState.position = "";

      if (resizeState.position === "") {
        elStyle.cursor = defaultCursor;
      } else {
        if (getSiblingByPosition(el, resizeState.position))
          elStyle.cursor = "col-resize";
        e.stopPropagation();
      }
    } else if (canTopResize || canBottomResize) {
      // 上下拉伸
      if (clientY - top < triggerSize) resizeState.position = "top";
      else if (bottom - clientY < triggerSize) resizeState.position = "bottom";
      else resizeState.position = "";

      if (resizeState.position === "") {
        elStyle.cursor = defaultCursor;
      } else {
        if (getSiblingByPosition(el, resizeState.position))
          elStyle.cursor = "row-resize";
        e.stopPropagation();
      }
    }
  };

  const pointerleave = (e) => {
    e.stopPropagation();
    resizeState.position = "";
    elStyle.cursor = defaultCursor;
    el.releasePointerCapture(e.pointerId);
  };
  // todo 只有在命中mousemove可拖拽的情况下 添加事件
  const pointerdown = (e) => {
    const {resizing, position} = resizeState;
    if (resizing || !position) return;

    if (position) e.stopPropagation(); // 如果当前节点存在拉伸方向 需要组织冒泡
    el.setPointerCapture(e.pointerId);

    const isFlex = getElStyleAttr(el.parentNode, "display") === "flex";
    if (isFlex) formatter = "flex";

    resizeState.resizing = true;
    resizeState.startMouseX = e.clientX;
    resizeState.startMouseY = e.clientY;

    const {width, height} = el.getBoundingClientRect();

    const sibling = getSiblingByPosition(el, position);
    if (!sibling) {
      console.error("未找到兄弟节点", position);
      return;
    }

    const rectSibling = sibling.getBoundingClientRect();

    const {startMouseX, startMouseY} = resizeState;
    const onDocumentMouseMove = (e) => {
      if (!resizeState.resizing) return;
      elStyle.cursor =
        canLeftResize || canRightResize ? "col-resize" : "col-row";
      const {clientX, clientY} = e;

      if (position === "left" || position === "right") {
        const offsetX = clientX - startMouseX;
        const elSize = position === "right" ? width + offsetX : width - offsetX;

        const siblingSize =
          position === "right"
            ? rectSibling.width - offsetX
            : rectSibling.width + offsetX;
        if (elSize <= minWidth || siblingSize <= minWidth) return;

        updateSize({el, sibling, elSize, siblingSize, formatter});
      } else if (position === "top" || position === "bottom") {
        const offsetY = clientY - startMouseY;
        const elSize =
          position === "bottom" ? height + offsetY : height - offsetY;

        const siblingSize =
          position === "bottom"
            ? rectSibling.height - offsetY
            : rectSibling.height + offsetY;
        if (elSize <= minHeight || siblingSize <= minHeight) return;

        updateSize({el, sibling, elSize, siblingSize, formatter});
      }
    };

    const onDocumentMouseUp = (e) => {
      document.removeEventListener("mousemove", onDocumentMouseMove);
      document.removeEventListener("mouseup", onDocumentMouseUp);
      resizeState.resizing = false;
      elStyle.cursor = defaultCursor;
    };

    document.addEventListener("mousemove", onDocumentMouseMove);
    document.addEventListener("mouseup", onDocumentMouseUp);
  };

  const bindElEvents = () => {
    el.addEventListener("pointermove", pointermove);
    el.addEventListener("pointerleave", pointerleave);
    el.addEventListener("pointerup", pointerleave);
    el.addEventListener("pointerdown", pointerdown);
  };

  const unBindElEvents = () => {
    el.removeEventListener("pointermove", pointermove);
    el.removeEventListener("pointerleave", pointerleave);
    el.removeEventListener("pointerup", pointerleave);
    el.removeEventListener("pointerdown", pointerdown);
  };

  bindElEvents();

  // 设置解绑事件
  elEventsWeakMap.set(el, unBindElEvents);
};
export const resize = {
  inserted: function (el, binding) {
    const {modifiers, value} = binding;
    const positions = Object.keys(modifiers);
    initResize({el, positions, ...value});
  },
  unbind: function (el) {
    const unBindElEvents = elEventsWeakMap.get(el);
    unBindElEvents();
  }
};
