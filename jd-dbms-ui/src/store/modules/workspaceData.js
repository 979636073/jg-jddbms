import historyServer from "@/api/main/history"
import workspace from "./workspace"
// workspaceData

const state = {
    dataInfo: {},
    dataBaseInfo: {},
    dataSourceData: [],
    dataCurrentData: new Map(),
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
    currendChange({ commit }) {
        commit('CHANGE_FLAG')
    },
    createConsole({ commit }, params) {
        return new Promise((resolve) => {
            const newConsole = {
                ...params,
                name: params.name || 'new console1',
                ddl: params.ddl || '',
                status: 'DRAFT',
                operationType: params.operationType || 'console',
                type: params.databaseType,
            };
            historyServer.createConsole(newConsole).then((res) => {
                let data = {
                    pageId: params.pageId,
                    id: res.data,
                    title: newConsole.name,
                    type: newConsole.operationType,
                    uniqueData: newConsole,
                }
                actions.setDataCurrentData({ commit }, data)
                workspace.state.activeConsoleId = data.id
                resolve()
            })
        })

    }
}

export default {
    namespaced: true,
    state,
    mutations,
    actions
}
