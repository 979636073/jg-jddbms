package com.easy.cas.client.service;

import com.easy.cas.client.model.SsoRole;
import com.easy.cas.client.model.SsoUser;
import com.easy.cas.client.model.Tips;

import java.util.List;

/**
 * CAS 用户与角色同步服务接口。
 *
 * <p>该接口由应用实现，随附的 CAS 客户端包仅提供模型和登录客户端。</p>
 */
public interface CasSyncService {
    List<SsoRole> listRole(String clientId, String clientSecret);

    Tips sendUserAndRole(SsoUser ssoUser);

    Tips sendDelUser(SsoUser ssoUser);
}
