import { h, resolveComponent } from 'vue'

export default {
  props: ['conf'],
  emits: ['update:modelValue'],
  render() {
    const conf = JSON.parse(JSON.stringify(this.conf))
    const props = { ...conf, ...conf.props }
    delete props.props
    const slots = {}
    if (conf.vModel) {
      props.modelValue = conf.defaultValue
      props['onUpdate:modelValue'] = value => this.$emit('update:modelValue', value)
    }
    const component = name => resolveComponent(name)
    if (conf.tag === 'el-input') {
      for (const name of ['prepend', 'append']) {
        if (conf[name]) slots[name] = () => conf[name]
      }
    } else if (conf.tag === 'el-select') {
      slots.default = () => conf.options.map(option => h(component('el-option'), {
        label: option.label, value: option.value, disabled: option.disabled
      }))
    } else if (['el-radio-group', 'el-checkbox-group'].includes(conf.tag)) {
      const tag = conf.tag.replace('-group', conf.optionType === 'button' ? '-button' : '')
      slots.default = () => conf.options.map(option => h(component(tag), {
        value: option.value, border: conf.border
      }, () => option.label))
    } else if (conf.tag === 'el-upload') {
      slots.default = () => conf['list-type'] === 'picture-card'
        ? h('i', { class: 'el-icon-plus' })
        : h(component('el-button'), { size: 'small', type: 'primary', icon: 'el-icon-upload' }, () => conf.buttonText)
      if (conf.showTip) slots.tip = () => h('div', { class: 'el-upload__tip' },
        `只能上传不超过 ${conf.fileSize}${conf.sizeUnit} 的${conf.accept}文件`)
    } else if (conf.tag === 'el-button') {
      slots.default = () => conf.default
    }
    return h(component(conf.tag), props, slots)
  }
}
