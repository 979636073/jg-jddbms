package com.jd.biz.migration;

import com.alibaba.fastjson2.JSON;
import com.jd.biz.domain.api.model.DataSource;
import com.jd.biz.domain.core.util.DataSourceCipher;
import com.jd.spi.model.KeyValue;
import org.junit.Assume;
import org.junit.Test;
import java.sql.*;
import java.util.*;

/** Opt-in, user-authorized grants restricted to the existing dedicated Oracle QA user. */
public class MigrationQaSetupTest {
    @Test public void provisionDedicatedOracleQaPrivileges() throws Exception {
        Assume.assumeTrue("1".equals(System.getenv("JDDBMS_MIGRATION_PROVISION_QA")));
        MigrationLiveTest environment = new MigrationLiveTest(); DataSource qa = environment.environment("ORACLE"); MigrationConnections connections = new MigrationConnections();
        if (!"JQORO_260926".equals(qa.getUserName())) throw new IllegalStateException("Not the dedicated QA user");
        List<String> needed = Arrays.asList("CREATE VIEW", "CREATE SEQUENCE", "CREATE SYNONYM", "CREATE PROCEDURE", "CREATE TRIGGER", "CREATE TYPE"); Set<String> existing = new HashSet<>();
        try (Connection session = connections.open(qa); Statement query = session.createStatement(); ResultSet rows = query.executeQuery("SELECT PRIVILEGE FROM SESSION_PRIVS")) { while (rows.next()) existing.add(rows.getString(1)); }
        if (existing.containsAll(needed)) { System.out.println("Oracle QA privileges already complete"); return; }
        DataSource admin = oracleAdministrator(environment, qa, connections);
        try (Connection session = connections.open(admin); Statement ddl = session.createStatement()) {
            for (String privilege : needed) if (!existing.contains(privilege)) ddl.execute("GRANT " + privilege + " TO \"JQORO_260926\"");
            System.out.println("User-authorized Oracle QA grants applied only to JQORO_260926");
        }
    }

    static DataSource oracleAdministrator(MigrationLiveTest environment, DataSource qa, MigrationConnections connections) throws Exception {
        if (System.getenv("JDDBMS_QA_ORACLE_ADMIN_USERNAME") != null) {
            DataSource admin = new DataSource(); admin.setType("ORACLE"); admin.setDriverConfig(environment.driver("ORACLE"));
            admin.setUrl(System.getenv("JDDBMS_QA_ORACLE_ADMIN_URL")); admin.setUserName(System.getenv("JDDBMS_QA_ORACLE_ADMIN_USERNAME"));
            admin.setPassword(System.getenv("JDDBMS_QA_ORACLE_ADMIN_PASSWORD"));
            return admin;
        }
        DataSource meta = new DataSource(); meta.setType("DM"); meta.setDriverConfig(environment.driver("DM")); meta.setUrl(MigrationLiveTest.hostUrl(System.getenv("JDDBMS_META_DB_URL")));
        meta.setUserName(System.getenv("JDDBMS_META_DB_USERNAME")); meta.setPassword(System.getenv("JDDBMS_META_DB_PASSWORD"));
        try (Connection store = connections.open(meta); PreparedStatement query = store.prepareStatement("SELECT URL, USER_NAME, PASSWORD, EXTEND_INFO FROM DATA_SOURCE WHERE TYPE='ORACLE' AND USER_NAME IN ('SYSTEM','SYS') ORDER BY ID DESC"); ResultSet rows = query.executeQuery()) {
            while (rows.next()) {
                DataSource admin = new DataSource(); admin.setType("ORACLE"); admin.setDriverConfig(environment.driver("ORACLE")); admin.setUrl(MigrationLiveTest.hostUrl(rows.getString(1))); admin.setUserName(rows.getString(2));
                admin.setPassword(new DataSourceCipher(System.getenv("JDDBMS_DATASOURCE_ENCRYPTION_KEY"), System.getenv("JDDBMS_DATASOURCE_ENCRYPTION_LEGACY_KEYS")).decrypt(rows.getString(3)));
                admin.setExtendInfo(JSON.parseArray(rows.getString(4), KeyValue.class));
                if (!admin.getUrl().equals(qa.getUrl())) continue;
                try (Connection session = connections.open(admin)) {
                    return admin;
                } catch (SQLException error) { System.out.println("Saved Oracle administrator unavailable, SQLState=" + error.getSQLState() + ", code=" + error.getErrorCode()); }
            }
        }
        throw new IllegalStateException("No usable saved Oracle administrator for the QA instance; need an admin datasource, not a guessed password");
    }
}
