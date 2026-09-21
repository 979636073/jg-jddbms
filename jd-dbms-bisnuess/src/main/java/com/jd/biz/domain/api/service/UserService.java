package com.jd.biz.domain.api.service;

import com.jd.biz.domain.api.model.User;
import com.jd.biz.domain.api.param.user.UserCreateParam;
import com.jd.biz.domain.api.param.user.UserPageQueryParam;
import com.jd.biz.domain.api.param.user.UserSelector;
import com.jd.biz.domain.api.param.user.UserUpdateParam;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.common.tools.base.wrapper.result.PageResult;

import java.util.List;

/**
 * 用户服务
 *
 * @author Jiaju Zhuang
 */
public interface UserService {

    /**
     * 查询用户信息
     *
     * @param id
     * @return
     */
    DataResult<User> query(Long id);

    /**
     * gen
     * @param userName
     * @return
     */
    DataResult<User> query(String userName);

    /**
     * List Query Data
     *
     * @param idList
     * @return
     */
    ListResult<User> listQuery(List<Long> idList);

    /**
     * 查询用户信息
     *
     * @param param
     * @return
     */
    PageResult<User> pageQuery(UserPageQueryParam param, UserSelector selector);

    /**
     * 更新用户信息
     * @param user
     * @return
     */
    DataResult<Long> update(UserUpdateParam user);

    /**
     * 删除用户
     * @param id
     * @return
     */
   ActionResult delete(Long id);

    /**
     * 创建一个用户
     * @param user
     * @return
     */
    DataResult<Long> create(UserCreateParam user);
}
