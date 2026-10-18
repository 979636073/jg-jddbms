// Reuse the application's exact bundled Vue runtime, including Element Plus's
// injection context. This is an opt-in Webpack browser test helper, not app code.
exports.runtime = `
  const application = document.querySelector('#app').__vue_app__;
  const chunks = window[Object.keys(window).find(key => key.startsWith('webpackChunk'))];
  let Vue, requireAppModule;
  chunks.push([['qa-runtime-' + Date.now()], {}, require => {
    requireAppModule = require;
    const id = Object.keys(require.m).find(id => /node_modules\\/vue\\/dist\\/vue\\.runtime\\.esm-bundler\\.js$/.test(id));
    if (!id) throw Error('Run this regression against the native Vue 3 development build');
    Vue = require(id);
  }]);
  const createTestApp = options => {
    const app = Vue.createApp(options);
    Object.assign(app._context.components, application._context.components);
    Object.assign(app.config.globalProperties, application.config.globalProperties);
    Object.assign(app._context.provides, application._context.provides);
    return app;
  };
`
