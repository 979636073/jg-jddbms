import Vue from 'vue'
import Vuex from 'vuex'
import app from './modules/app'
import dict from './modules/dict'
import user from './modules/user'
import tagsView from './modules/tagsView'
import jdTagsView from './modules/jdTagsView'
import permission from './modules/permission'
import settings from './modules/settings'
import workspace from './modules/workspace'
import workspaceData from './modules/workspaceData'
import getters from './getters'


Vue.use(Vuex)

const store = new Vuex.Store({
  modules: {
    app,
    dict,
    user,
    tagsView,
    permission,
    settings,
    workspace,
    jdTagsView,
    workspaceData
  },
  getters
})

export default store
