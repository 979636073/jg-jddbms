package com.jd.common.tools.common.util;

import cn.hutool.core.lang.Validator;
import com.jd.common.tools.common.exception.NeedLoggedInBusinessException;
import com.jd.common.tools.common.model.Context;
import com.jd.common.tools.common.model.LoginUser;

import com.jd.common.utils.SecurityUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 上下文工具类
 *
 * @author Jiaju Zhuang
 */
@Slf4j
public class ContextUtils {

    /**
     * 存储context
     */
    private static final ThreadLocal<Context> CONTEXT_THREAD_LOCAL = new ThreadLocal<>();

    /**
     * 获取用户id
     *
     * @return
     */
    public static Long getUserId() {
        return SecurityUtil.getUserId();
    }

    /**
     * 获取用户信息
     *
     * @return 可能返回为空
     */
    public static LoginUser queryLoginUser() {
        // 去登录信息获取
        Context context = queryContext();
        if (context == null) {
            return null;
        }
        if (context.getLoginUser() == null) {
            return null;
        }
        return context.getLoginUser();
    }

    /**
     * 获取用户信息
     *
     * @return 拿不到会抛出重新登陆异常
     */
    public static LoginUser getLoginUser() {
        // 去登录信息获取
        com.jd.common.core.domain.model.LoginUser loginUser = SecurityUtil.getLoginUser();
        if (Validator.isNotEmpty(loginUser)) {
            LoginUser user = new LoginUser();
            user.setAdmin(loginUser.getAdmin());
            user.setId(loginUser.getUserId());
            user.setNickName(loginUser.getUser().getNickName());
            user.setRoleCode("");
            user.setToken(loginUser.getToken());
            return user;
        }
        // 判断用户必须登录
        throw new NeedLoggedInBusinessException();
    }

    /**
     * 查询上下文
     *
     * @return SaTokenWebMvcConfigurer的拦截器，其他地方调用至少 会返回一个Context ，且里面至少有tokenValue
     */
    public static Context queryContext() {
        return CONTEXT_THREAD_LOCAL.get();
    }

    /**
     * 设置上下文 设置上下文
     *
     * @param context
     * @return
     */
    public static void setContext(Context context) {
        CONTEXT_THREAD_LOCAL.set(context);
    }

    /**
     * 移除上下文
     */
    public static void removeContext() {
        CONTEXT_THREAD_LOCAL.remove();
    }
}
