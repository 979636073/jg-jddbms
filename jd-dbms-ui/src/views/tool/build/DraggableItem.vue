<template>
  <el-col v-if="element.layout === 'colFormItem'" :span="element.span"
    :class="['drawing-item', { 'active-from-item': activeId === element.formId, 'unfocus-bordered': formConf.unFocusedComponentBorder }]"
    @click.stop="$emit('activeItem', element)">
    <el-form-item :label-width="element.labelWidth ? `${element.labelWidth}px` : null" :label="element.label" :required="element.required">
      <render :key="element.renderKey" :conf="element" @update:model-value="element.defaultValue = $event" />
    </el-form-item>
    <span class="drawing-item-copy" title="复制" @click.stop="$emit('copyItem', element, drawingList)"><i class="el-icon-copy-document" /></span>
    <span class="drawing-item-delete" title="删除" @click.stop="$emit('deleteItem', index, drawingList)"><i class="el-icon-delete" /></span>
  </el-col>
  <el-col v-else-if="element.layout === 'rowFormItem'" :span="element.span">
    <el-row :gutter="element.gutter" :class="['drawing-row-item', { 'active-from-item': activeId === element.formId }]"
      @click.stop="$emit('activeItem', element)">
      <span class="component-name">{{ element.componentName }}</span>
      <draggable :list="element.children" item-key="formId" :animation="340" group="componentsGroup" class="drag-wrapper"
        :tag="element.type === 'flex' ? 'el-row' : 'div'" :component-data="{ justify: element.justify, align: element.align }">
        <template #item="{ element: child, index: childIndex }">
          <draggable-item :element="child" :index="childIndex" :drawing-list="element.children" :active-id="activeId" :form-conf="formConf"
            @active-item="$emit('activeItem', $event)" @copy-item="(item, list) => $emit('copyItem', item, list)"
            @delete-item="(itemIndex, list) => $emit('deleteItem', itemIndex, list)" />
        </template>
      </draggable>
      <span class="drawing-item-copy" title="复制" @click.stop="$emit('copyItem', element, drawingList)"><i class="el-icon-copy-document" /></span>
      <span class="drawing-item-delete" title="删除" @click.stop="$emit('deleteItem', index, drawingList)"><i class="el-icon-delete" /></span>
    </el-row>
  </el-col>
</template>

<script>
import draggable from 'vuedraggable'
import render from '@/utils/generator/render'

export default {
  name: 'DraggableItem',
  components: { draggable, render },
  props: ['element', 'index', 'drawingList', 'activeId', 'formConf'],
  emits: ['activeItem', 'copyItem', 'deleteItem']
}
</script>
