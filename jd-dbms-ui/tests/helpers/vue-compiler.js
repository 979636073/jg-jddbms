const { parse, compileTemplate } = require('@vue/compiler-sfc')
const { parse: parseTemplate } = require('@vue/compiler-dom')

exports.parseComponent = source => {
  const result = parse(source)
  if (result.errors.length) throw result.errors[0]
  return result.descriptor
}

exports.compile = source => {
  const result = compileTemplate({ source, filename: 'regression.vue', id: 'regression',
    compilerOptions: { compatConfig: { MODE: 2 } } })
  return { ...result, ast: parseTemplate(source).children.find(node => node.type === 1), errors: result.errors.map(error => error.message || error) }
}

exports.compileToFunctions = source => ({ render: require('@vue/compat').compile(source) })

exports.attribute = (node, name) => node.props?.find(prop => prop.type === 6 && prop.name === name)?.value?.content ??
  (node.props?.some(prop => prop.type === 6 && prop.name === name) ? '' : undefined)

exports.directive = (node, name, arg) => node.props?.find(prop => prop.type === 7 && prop.name === name && (!arg || prop.arg?.content === arg))
