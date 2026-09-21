package com.jd.biz.controller.rdb.request;

import com.jd.common.tools.base.wrapper.request.PageQueryRequest;
import com.jd.biz.controller.data.source.request.DataSourceBaseRequestInfo;
import javax.validation.constraints.NotNull;

import com.jd.spi.model.TableObjectRoleData;
import com.jd.spi.model.TableRoleData;
import lombok.Data;

import java.util.List;
import java.util.Map;


/**
 * @author moji
 * @version ConnectionQueryRequest.java, v 0.1 2022年09月16日 14:23 moji Exp $
 * @date 2022/09/16
 */
@Data
public class TableBriefQueryRequest extends PageQueryRequest implements DataSourceBaseRequestInfo {

    
    private static final long serialVersionUID = -364547173428396332L;
    /**
     * 数据源id
     */
    @NotNull
    private Long dataSourceId;
    /**
     * DB名称
     */
    private String databaseName;

    /**
     * 表所在空间，pg,oracle需要，mysql不需要
     */
    private String schemaName;

    /**
     * 模糊搜索词
     */
    private String searchKey;

    /**
     * if true, refresh the cache
     */
    private boolean refresh;

    /**
     * 返回的结果集类型 1：表列表  2：表列表 + 表列表详情
     */
    private Integer requestType;

    /**
     * 解锁解锁的用户名
     */
    private String lockName;

    /**
     * 加解锁
     */
    private Boolean isLock;

    /**
     * 新密码
     */
    private String newPassWord;

    /**
     * 用户名
     */
    private String userName;

    /**
     * 用户绑定角色
     */
    private List<TableRoleData> roleList;

    /**
     * 新
     */
    List<TableRoleData> newRoles;
    /**
     * 旧
     */
    List<TableRoleData> oldRoles;

    /**
     * 文件路径
     */
    private String path;

    /**
     * 数据库类型
     */
    private String dbType;

    /**
     * 默认表空间
     */
    private String defaultTableSpace;

    /**
     * 临时表空间
     */
    private String tempTableSpace;

    private List<String> userNames;

    private String tableName;


    private Boolean isRefreshCache = Boolean.FALSE;

    private List<TableObjectRoleData> oldObjectRoleData;
    private List<TableObjectRoleData> newObjectRoleData;

}
