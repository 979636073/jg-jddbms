package com.jd.spi.util;

import com.jd.spi.enums.DataTypeEnum;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SqlUtilsDmlValueTest {

    @Test
    public void shouldEscapeQuotesAndPreserveEmptyText() {
        assertEquals("'O''Brien'", SqlUtils.getSqlValue("O'Brien", DataTypeEnum.STRING.getCode()));
        assertEquals("''", SqlUtils.getSqlValue("", DataTypeEnum.STRING.getCode()));
    }

    @Test
    public void shouldTreatEmptyNonTextValueAsNull() {
        assertNull(SqlUtils.getSqlValue("", DataTypeEnum.NUMERIC.getCode()));
        assertNull(SqlUtils.getSqlValue(null, DataTypeEnum.STRING.getCode()));
    }
}
