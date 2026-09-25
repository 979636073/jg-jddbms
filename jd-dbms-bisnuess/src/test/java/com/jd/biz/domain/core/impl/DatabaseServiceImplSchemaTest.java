package com.jd.biz.domain.core.impl;

import com.jd.biz.domain.api.param.SchemaQueryParam;
import com.jd.biz.domain.core.cache.CacheManage;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.spi.model.Schema;
import org.junit.Test;

import java.util.Collections;

import static com.jd.biz.domain.core.cache.CacheKey.getSchemasKey;
import static org.junit.Assert.assertTrue;

public class DatabaseServiceImplSchemaTest {

    @Test
    public void shouldUseCachedSchemasWhenRefreshIsOmitted() {
        long dataSourceId = -99999999L;
        String databaseName = "SCHEMA_REFRESH_TEST";
        CacheManage.put(getSchemasKey(dataSourceId, databaseName), Collections.emptyList());
        SchemaQueryParam param = SchemaQueryParam.builder()
                .dataSourceId(dataSourceId).dataBaseName(databaseName).build();

        ListResult<Schema> result = new DatabaseServiceImpl().querySchema(param);

        assertTrue(result.getData().isEmpty());
    }
}
