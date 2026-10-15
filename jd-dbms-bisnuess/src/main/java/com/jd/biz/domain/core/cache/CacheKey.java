package com.jd.biz.domain.core.cache;

import org.springframework.util.StringUtils;
import com.alibaba.fastjson2.JSON;
import java.util.Arrays;

public class CacheKey {

    public static String getLoginUserKey(Long userId) {
        return "login_user_" + userId;
    }

    public static String getDataSourceKey(Long dataSourceId) {
        return "schemas_datasourceId_" + dataSourceId;
    }

    public static String getDataBasesKey(Long dataSourceId) {
        return "databases_datasourceId_" + dataSourceId;
    }

    public static String getSchemasKey(Long dataSourceId, String databaseName) {

        return "databases_datasourceId_" + dataSourceId + "_databaseName_" + databaseName;
    }

    public static String getTableKey(Long dataSourceId, String databaseName, String schemaName) {
        StringBuffer stringBuffer = new StringBuffer("tables_dataSourceId_" + dataSourceId);
        if (!StringUtils.isEmpty(databaseName)) {
            stringBuffer.append("_databaseName_" + databaseName);
        }
        if (!StringUtils.isEmpty(schemaName)) {
            stringBuffer.append("_schemaName_" + schemaName);
        }
        return stringBuffer.toString();
    }

    public static String getColumnKey(Long dataSourceId, String databaseName, String schemaName,String tableName) {
        return getColumnPrefix(dataSourceId) + JSON.toJSONString(Arrays.asList(databaseName, schemaName, tableName));
    }

    public static String getColumnPrefix(Long dataSourceId) {
        return "columns_v2_datasource_" + dataSourceId + ":";
    }

    public static String getRelationKey(Long dataSourceId, String databaseName, String schemaName, String tableName, String direction) {
        return getRelationPrefix(dataSourceId) + JSON.toJSONString(Arrays.asList(databaseName, schemaName, tableName, direction));
    }

    public static String getRelationPrefix(Long dataSourceId) {
        return "relations_v1_datasource_" + dataSourceId + ":";
    }
}
