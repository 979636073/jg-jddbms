import request from '@/utils/request'

/** 获取用户列表 */
function getUserList(data) {
  return request({
    url: '/api/rdb/user/list',
    method: 'get',
    params: data
  })
}

/** 获取数据库用户的详情 */
function getUserDetails(data) {
  return request({
    url: '/api/rdb/user/query',
    method: 'get',
    params: data
  })
}

function getRoleGrantsList(data) {
  return request({
    url: '/api/rdb/user/role',
    method: 'get',
    params: data
  })
}

//获取用户角色列表
function getUserRoleList(data) {
  return request({
    url: '/api/rdb/user/allRole',
    method: 'get',
    params: data
  })
}
// 修改用户信息
function editUsermodify(data) {
  return request({
    url: '/api/rdb/user/modify',
    method: 'post',
    data
  })
}

// 删除用户
function deleteUser(data) {
  return request({
    url: '/api/rdb/user/dropUser',
    method: 'post',
    data
  })
}

// 新增用户
function addUserRole(data) {
  return request({
    url: '/api/rdb/user/createUser',
    method: 'post',
    data
  })
}
// 修改用户角色
function editUserRole(data) {
  return request({
    url: '/api/rdb/user/addOrDelUserRole',
    method: 'post',
    data
  })
}

// 对象权限
function queryObjAllRole (data) {
  return  request({
    url: '/api/rdb/user/allObjectRole',
    method: 'post',
    data
  })
}

// 对象权限保存
function  saveObjectRole(data) {
  return  request({
    url: '/api/rdb/user/addObjectRole',
    method: 'post',
    data
  })
}

// 修改密码获取sql
function  upatePassword(data) {
  return  request({
    url: '/api/rdb/user/managePassWord',
    method: 'post',
    data
  })
}

// 修改密码获取sql
function lockUser(data) {
  return  request({
    url: '/api/rdb/user/lockUser',
    method: 'post',
    data
  })
}



export default {
  lockUser,
  getUserList,
  getUserDetails,
  getRoleGrantsList,
  getUserRoleList,
  editUsermodify,
  deleteUser,
  addUserRole,
  editUserRole,
  queryObjAllRole,
  saveObjectRole,
  upatePassword,
}
