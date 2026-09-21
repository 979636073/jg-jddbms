import { Message } from "element-ui";
import historyServer from "@/api/main/history"
import connectionServer from "@/api/main/connection";
const workspace = {
  state: {
    isCloseAll:false,
    sessionObj:{}, // 关闭新建查询窗口入参
    dataSourceList: [],
    databaseList: [],
    // 当前选中数据表
    currentTable: null,
    // 控制台tab列表
    workspaceTabList: [],
    // 当前选中控制台ID
    activeConsoleId: '',
    // 当前显示控制台选中数据源
    currentConnectionDetails: null,
  },

  mutations: {
    SET_ISCLOSEALL :(state, isCloseAll) =>{
      state.isCloseAll = isCloseAll
    },
    SET_SESSIONIDDATE :(state, sessionObj) =>{
      state.sessionObj = sessionObj
    },
    SET_DATASOURCELIST: (state, dataSourceList) => {
      state.dataSourceList = dataSourceList
    },
    SET_DATABASELIST: (state, databaseList) => {
      state.databaseList = databaseList
    },
    SET_CURRENTTABLE: (state, currentTable) => {
      state.currentTable = currentTable
    },
    SET_WORKSPACETABLIST: (state, workspaceTabList) => {
      state.workspaceTabList = workspaceTabList
    },
    SET_ACTIVECONSOLEID: (state, activeConsoleId) => {
      state.activeConsoleId = activeConsoleId
    },
    SET_CURRENTCONNECTIONDETAILS: (state, currentConnectionDetails) => {
      state.currentConnectionDetails = currentConnectionDetails
    },
  },

  actions: {
    getSessionId({ commit },send){
      commit('SET_SESSIONIDDATE',send)
    },
    async getDataSourceList({ commit, state }) {
      let res = await connectionServer.getList()
      commit('SET_DATASOURCELIST', res.data.data)
    },
    createConsole({ commit, state }, params) {
      console.log(commit,state,params,'ceshi');
      
      const workspaceTabList = state.workspaceTabList;
      const currentConnectionDetails = state.currentConnectionDetails;
      const newConsole = {
        ...params,
        name: params.name || 'new console1',
        ddl: params.ddl || '',
        status: 'DRAFT',
        operationType: params.operationType || 'console',
        type: params.databaseType,
        supportDatabase: currentConnectionDetails?.supportDatabase,
        supportSchema: currentConnectionDetails?.supportSchema
      };

      return new Promise((resolve) => {
        // if ((workspaceTabList?.length || 0) >= 20) {
        //   Message.warning('最多只能打开20个控制台');
        //   return;
        // }
        historyServer.createConsole(newConsole).then((res) => {
          const newList = [
            ...(workspaceTabList || []),
            {
              id: res.data,
              title: newConsole.name,
              type: newConsole.operationType,
              uniqueData: newConsole,
            },
          ];
          commit('SET_WORKSPACETABLIST', newList)
          commit('SET_ACTIVECONSOLEID', res.data)
          resolve(res.data)
        })
      });
    },
    addWorkspaceTab({ commit, state }, params) {
      const workspaceTabList = state.workspaceTabList;
      if (workspaceTabList?.findIndex((item) => item?.id === params?.id) !== -1) {
        commit('SET_ACTIVECONSOLEID', params.id)
        return;
      }

      const newList = [...(workspaceTabList || []), params];
      commit('SET_WORKSPACETABLIST', newList)
      commit('SET_ACTIVECONSOLEID', params.id)
    },
  }
}

export default workspace
