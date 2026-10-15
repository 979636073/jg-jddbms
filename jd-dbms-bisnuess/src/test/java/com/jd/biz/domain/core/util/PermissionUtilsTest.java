package com.jd.biz.domain.core.util;

import com.jd.common.core.domain.entity.SysUser;
import com.jd.common.core.domain.model.LoginUser;
import com.jd.common.tools.common.exception.PermissionDeniedBusinessException;
import org.junit.After;
import org.junit.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;

import static org.junit.Assert.assertTrue;

public class PermissionUtilsTest {

    @After
    public void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    public void administratorCanOperateOtherUsersDataSource() {
        authenticate(1L);
        PermissionUtils.checkOperationPermission(6L);
        assertTrue(PermissionUtils.checkBaseQueryPermission(6L));
    }

    @Test
    public void ownerCanOperateOwnDataSource() {
        authenticate(6L);
        PermissionUtils.checkOperationPermission(6L);
    }

    @Test(expected = PermissionDeniedBusinessException.class)
    public void ordinaryUserCannotOperateOtherUsersDataSource() {
        authenticate(6L);
        PermissionUtils.checkOperationPermission(7L);
    }

    private static void authenticate(Long userId) {
        SysUser user = new SysUser();
        user.setUserId(userId);
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(userId);
        loginUser.setUser(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, Collections.emptyList()));
    }
}
