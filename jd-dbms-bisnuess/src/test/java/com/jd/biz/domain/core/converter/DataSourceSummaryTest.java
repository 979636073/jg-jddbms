package com.jd.biz.domain.core.converter;

import com.jd.biz.domain.api.model.DataSource;
import com.jd.biz.domain.repository.entity.DataSourceDO;
import org.junit.Test;
import org.mapstruct.factory.Mappers;

import java.util.Collections;

import static org.junit.Assert.*;

public class DataSourceSummaryTest {
    @Test
    public void listingDoesNotDecryptStoredPasswords() {
        DataSourceDO row = new DataSourceDO();
        row.setId(42L);
        row.setAlias("saved connection");
        row.setKind("PRIVATE");
        row.setUserId(1L);
        row.setPassword("v2:unreadable-with-current-key");
        DataSourceConverter converter = Mappers.getMapper(DataSourceConverter.class);

        DataSource summary = converter.do2summary(Collections.singletonList(row)).get(0);

        assertEquals(row.getId(), summary.getId());
        assertEquals(row.getAlias(), summary.getAlias());
        assertEquals(row.getKind(), summary.getKind());
        assertEquals(row.getUserId(), summary.getUserId());
        assertNull(summary.getPassword());
        assertEquals("v2:unreadable-with-current-key", row.getPassword());
    }
}
