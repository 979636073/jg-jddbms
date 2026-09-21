package com.jd.biz.domain.core.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jd.biz.domain.api.enums.DataSourceKindEnum;
import com.jd.biz.domain.api.param.datasource.DataSourceCreateParam;
import com.jd.biz.domain.repository.entity.DataSourceDO;
import com.jd.biz.domain.repository.mapper.DataSourceMapper;
import com.jd.common.core.domain.entity.SysUser;
import com.jd.common.core.domain.model.LoginUser;
import org.junit.After;
import org.junit.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DataSourceServiceImplTest {

    @After
    public void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    public void shouldScopePrivateDataSourceDeduplicationToCurrentUser() throws Exception {
        authenticate(42L);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "DataSourceServiceImplTest"),
                DataSourceDO.class);
        DataSourceServiceImpl service = new DataSourceServiceImpl();
        setBaseMapper(service, inspectingMapper(42L));

        DataSourceCreateParam param = new DataSourceCreateParam();
        param.setHost("database.internal");
        param.setPort("5236");
        param.setUserName("APP_USER");
        param.setType("DM");
        param.setKind(DataSourceKindEnum.PRIVATE.getCode());

        try {
            service.createWithPermission(param);
            fail("Expected the mapper probe to stop the method");
        } catch (QueryInspectedException expected) {
            // The mapper asserts the generated duplicate lookup before stopping the method.
        }
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

    private static DataSourceMapper inspectingMapper(Long expectedUserId) {
        return (DataSourceMapper) Proxy.newProxyInstance(
                DataSourceMapper.class.getClassLoader(),
                new Class<?>[]{DataSourceMapper.class},
                (proxy, method, args) -> {
                    if ("selectList".equals(method.getName())) {
                        @SuppressWarnings("unchecked")
                        LambdaQueryWrapper<DataSourceDO> wrapper = (LambdaQueryWrapper<DataSourceDO>) args[0];
                        String sql = wrapper.getSqlSegment();
                        Map<String, Object> values = wrapper.getParamNameValuePairs();
                        assertTrue(sql.toUpperCase(Locale.ROOT).contains("USER_ID"));
                        assertTrue(values.containsValue(expectedUserId));
                        throw new QueryInspectedException();
                    }
                    if (method.getDeclaringClass() == Object.class) {
                        return method.invoke(proxy, args);
                    }
                    return null;
                });
    }

    private static void setBaseMapper(DataSourceServiceImpl service, DataSourceMapper mapper) throws Exception {
        Field field = ServiceImpl.class.getDeclaredField("baseMapper");
        field.setAccessible(true);
        field.set(service, mapper);
    }

    private static class QueryInspectedException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
