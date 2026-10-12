import historyServer from "@/api/main/history"
import workspace from "./workspace"
// workspaceData

const state = {
    dataInfo: {},
    dataBaseInfo: {},
    dataSourceData: [],
    dataCurrentData: new Map(),
    dirtyPageIds: {},
    changeFlag: 0,
}
const mutations = {
    SET_DATA_INFO(state, dataInfo) {
        state.dataInfo = dataInfo
    },
    SET_DATA_BASE_INFO(state, dataBaseInfo) {
        state.dataBaseInfo = dataBaseInfo
    },
    SET_DATA_SOURCE_DATA(state, data) {
        state.dataSourceData = data
    },
    SET_DATA_CURRENT_DATA(state, data) {
        state.dataCurrentData.set(data.pageId, data)
    },
    SET_PAGE_DIRTY(state, { pageId, dirty }) {
        state.dirtyPageIds = { ...state.dirtyPageIds, [pageId]: dirty }
    },
    CHANGE_FLAG(state) {
        state.changeFlag += 1
    }
}
const actions = {
    setDataInfo({ commit }, dataInfo) {
        commit('SET_DATA_INFO', dataInfo)
    },
    setDataBaseInfo({ commit }, dataBaseInfo) {
        commit('SET_DATA_BASE_INFO', dataBaseInfo)
    },
    setDataSourceData({ commit }, data) {
        commit('SET_DATA_SOURCE_DATA', data)
    },
    setDataCurrentData({ commit }, data) {
        commit('SET_DATA_CURRENT_DATA', data)
        commit('CHANGE_FLAG')
    },
    setPageDirty({ commit }, data) {
        commit('SET_PAGE_DIRTY', data)
    },
    currendChange({ commit }) {
        commit('CHANGE_FLAG')
    },
    async createConsole({ commit }, params) {
        const newConsole = {
            ...params,
            name: params.name || 'new console1',
            ddl: params.ddl || '',
            status: 'DRAFT',
            operationType: params.operationType || 'console',
            type: params.databaseType,
        };
        const res = await historyServer.createConsole(newConsole);
        if (!res.success) throw new Error(res.errorMessage || '新建查询失败');
        const data = {
            pageId: params.pageId,
            id: res.data,
            title: newConsole.name,
            type: newConsole.operationType,
            uniqueData: newConsole,
        };
        actions.setDataCurrentData({ commit }, data);
        workspace.state.activeConsoleId = data.id;
        return data;
    }
}

export default {
    namespaced: true,
    state,
    mutations,
    actions
}
