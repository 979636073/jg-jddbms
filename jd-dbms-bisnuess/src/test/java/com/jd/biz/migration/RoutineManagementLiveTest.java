package com.jd.biz.migration;

import com.jd.biz.domain.api.model.DataSource;
import com.jd.plugin.dm.DMMetaData;
import com.jd.plugin.oracle.OracleMetaData;
import com.jd.spi.MetaData;
import com.jd.spi.model.Trigger;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Locale;
import java.util.UUID;

import static com.jd.biz.migration.MigrationDialect.qualified;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Opt-in live metadata regression. Creates and removes only unique QA objects. */
public class RoutineManagementLiveTest {
    private final MigrationConnections connections = new MigrationConnections();

    @Test
    public void dmProcedureDetailContainsSource() throws Exception {
        DataSource dm = dm();
        String schema = "JQDMO_260926", name = name("P");
        try (Connection connection = connections.open(dm)) {
            boolean created = false;
            try {
                execute(connection, "CREATE OR REPLACE PROCEDURE " + qualified(schema, name)
                        + " AS BEGIN NULL; END;");
                created = true;
                String body = new DMMetaData().procedure(connection, "", schema, name).getProcedureBody();
                assertTrue("DM procedure source must be readable", body != null && body.contains(name));
                assertTrue(new DMMetaData().procedures(connection, "", schema).stream()
                        .anyMatch(item -> name.equals(item.getProcedureName())));
            } finally {
                if (created) execute(connection, "DROP PROCEDURE " + qualified(schema, name));
            }
        }
    }

    @Test
    public void triggerListAndExecutableDetailWorkForDmAndOracle() throws Exception {
        verifyTrigger(dm(), new DMMetaData(), "JQDMO_260926", "DM");
        DataSource oracle = new MigrationLiveTest().environment("ORACLE");
        verifyTrigger(oracle, new OracleMetaData(), "JQORO_260926", "ORACLE");
        verifyProcedureList(oracle, new OracleMetaData(), "JQORO_260926");
    }

    private void verifyProcedureList(DataSource source, MetaData metadata, String schema) throws Exception {
        String function = name("F"), procedure = name("P");
        try (Connection connection = connections.open(source)) {
            boolean functionCreated = false, procedureCreated = false;
            try {
                execute(connection, "CREATE OR REPLACE FUNCTION " + qualified(schema, function)
                        + " RETURN NUMBER AS BEGIN RETURN 1; END;");
                functionCreated = true;
                execute(connection, "CREATE OR REPLACE PROCEDURE " + qualified(schema, procedure)
                        + " AS BEGIN NULL; END;");
                procedureCreated = true;
                assertTrue(metadata.procedures(connection, "", schema).stream()
                        .anyMatch(item -> procedure.equals(item.getProcedureName())));
                assertFalse(metadata.procedures(connection, "", schema).stream()
                        .anyMatch(item -> function.equals(item.getProcedureName())));
            } finally {
                try {
                    if (procedureCreated) execute(connection, "DROP PROCEDURE " + qualified(schema, procedure));
                } finally {
                    if (functionCreated) execute(connection, "DROP FUNCTION " + qualified(schema, function));
                }
            }
        }
    }

    private void verifyTrigger(DataSource source, MetaData metadata, String schema, String type) throws Exception {
        String table = name("T"), trigger = name("R");
        boolean tableCreated = false, triggerCreated = false;
        try (Connection connection = connections.open(source)) {
            try {
                execute(connection, "CREATE TABLE " + qualified(schema, table) + " (ID NUMBER, VAL NUMBER)");
                tableCreated = true;
                execute(connection, "CREATE OR REPLACE TRIGGER " + qualified(schema, trigger)
                        + " BEFORE INSERT ON " + qualified(schema, table)
                        + " FOR EACH ROW BEGIN :NEW.VAL := NVL(:NEW.VAL, 0) + 1; END;");
                triggerCreated = true;
                assertTrue(type + " schema trigger list must include object without table filter",
                        metadata.triggers(connection, "", schema, null).stream()
                                .anyMatch(item -> trigger.equals(item.getTriggerName())));
                Trigger detail = metadata.trigger(connection, "", schema, trigger);
                assertTrue(type + " trigger detail must provide executable DDL",
                        detail.getTriggerBody() != null
                                && detail.getTriggerBody().toUpperCase(Locale.ROOT).contains("CREATE OR REPLACE TRIGGER"));
            } finally {
                try {
                    if (triggerCreated) execute(connection, "DROP TRIGGER " + qualified(schema, trigger));
                } finally {
                    if (tableCreated) execute(connection, "DROP TABLE " + qualified(schema, table)
                            + ("ORACLE".equals(type) ? " PURGE" : ""));
                }
            }
        }
    }

    private DataSource dm() {
        Assume.assumeTrue("1".equals(System.getenv("JDDBMS_MIGRATION_LIVE")));
        File root = new File(System.getProperty("user.dir"));
        if (root.getName().equals("jd-dbms-bisnuess")) root = root.getParentFile();
        System.setProperty("user.dir", root.getAbsolutePath());
        MigrationLiveTest helper = new MigrationLiveTest();
        DataSource source = new DataSource();
        source.setType("DM");
        source.setDriverConfig(helper.driver("DM"));
        source.setUrl(MigrationLiveTest.hostUrl(System.getenv("JDDBMS_META_DB_URL")));
        source.setUserName(System.getenv("JDDBMS_META_DB_USERNAME"));
        source.setPassword(System.getenv("JDDBMS_META_DB_PASSWORD"));
        return source;
    }

    private static String name(String prefix) {
        return "JD_QA_" + prefix + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private static void execute(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement()) { statement.execute(sql); }
    }
}
