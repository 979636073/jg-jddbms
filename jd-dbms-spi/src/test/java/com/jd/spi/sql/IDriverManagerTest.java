package com.jd.spi.sql;

import org.junit.After;
import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class IDriverManagerTest {
    private static final String ORACLE_TIMEZONE_AS_REGION = "oracle.jdbc.timezoneAsRegion";
    private final String originalTimezoneAsRegion = System.getProperty(ORACLE_TIMEZONE_AS_REGION);

    @After
    public void restoreTimezoneProperty() {
        if (originalTimezoneAsRegion == null) {
            System.clearProperty(ORACLE_TIMEZONE_AS_REGION);
        } else {
            System.setProperty(ORACLE_TIMEZONE_AS_REGION, originalTimezoneAsRegion);
        }
    }

    @Test
    public void shouldUseTimezoneOffsetForOracleByDefault() {
        System.clearProperty(ORACLE_TIMEZONE_AS_REGION);
        Properties properties = new Properties();

        IDriverManager.applyConnectionDefaults("jdbc:oracle:thin:@localhost:1521/orcl", properties);

        assertEquals("false", properties.getProperty(ORACLE_TIMEZONE_AS_REGION));
        assertEquals("false", System.getProperty(ORACLE_TIMEZONE_AS_REGION));
    }

    @Test
    public void shouldPreserveExplicitOracleTimezoneMode() {
        System.setProperty(ORACLE_TIMEZONE_AS_REGION, "true");
        Properties properties = new Properties();
        properties.setProperty(ORACLE_TIMEZONE_AS_REGION, "true");

        IDriverManager.applyConnectionDefaults("jdbc:oracle:thin:@localhost:1521/orcl", properties);

        assertEquals("true", properties.getProperty(ORACLE_TIMEZONE_AS_REGION));
        assertEquals("true", System.getProperty(ORACLE_TIMEZONE_AS_REGION));
    }

    @Test
    public void shouldNotAddOraclePropertiesToOtherDatabases() {
        Properties properties = new Properties();

        IDriverManager.applyConnectionDefaults("jdbc:dm://localhost:5236", properties);

        assertFalse(properties.containsKey(ORACLE_TIMEZONE_AS_REGION));
    }
}
