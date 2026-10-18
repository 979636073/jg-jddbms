package com.jd.biz.migration;

import com.jd.biz.domain.api.model.DataSource;
import com.jd.spi.model.Table;
import com.jd.spi.sql.Chat2DBContext;
import org.junit.Test;
import org.junit.Assume;

import java.io.File;
import java.sql.*;
import java.util.*;
import static org.junit.Assert.*;
import static com.jd.biz.migration.MigrationDialect.qualified;

public class MaterializedViewCatalogLiveTest {
    @Test public void dmCatalog() throws Exception { verify("DM"); }
    @Test public void oracleCatalog() throws Exception { verify("ORACLE"); }
    @Test public void dmRestrictedCatalog() throws Exception {
        Assume.assumeNotNull(System.getenv("JDDBMS_QA_DM_URL"));
        verify("DM", true);
    }

    private void verify(String type) throws Exception {
        verify(type, false);
    }

    private void verify(String type, boolean restricted) throws Exception {
        Assume.assumeTrue("1".equals(System.getenv("JDDBMS_CATALOG_LIVE")));
        File root = new File(System.getProperty("user.dir"));
        if (root.getName().equals("jd-dbms-bisnuess")) root = root.getParentFile();
        System.setProperty("user.dir", root.getAbsolutePath());
        MigrationLiveTest helper = new MigrationLiveTest();
        DataSource source;
        if ("ORACLE".equals(type)) source = helper.environment(type);
        else {
            source = new DataSource(); source.setType(type); source.setDriverConfig(helper.driver(type));
            source.setUrl(MigrationLiveTest.hostUrl(System.getenv("JDDBMS_META_DB_URL")));
            source.setUserName(System.getenv("JDDBMS_META_DB_USERNAME")); source.setPassword(System.getenv("JDDBMS_META_DB_PASSWORD"));
        }
        DataSource reader = restricted ? helper.environment(type) : source;
        String schema = restricted ? reader.getUserName() : "DM".equals(type) ? "JQDMO_260926" : source.getUserName();
        String prefix = "JQA_M" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        String table = qualified(schema, prefix + "_T"), view = qualified(schema, prefix + "_V"), mv = qualified(schema, prefix + "_M");
        List<String> cleanup = new ArrayList<>();
        try (Connection c = new MigrationConnections().open(source);
             Connection catalog = new MigrationConnections().open(reader); Statement s = c.createStatement()) {
            try {
                s.execute("CREATE TABLE " + table + " (ID INT PRIMARY KEY)"); cleanup.add("DROP TABLE " + table);
                s.execute("INSERT INTO " + table + " VALUES (1)");
                s.execute("CREATE VIEW " + view + " AS SELECT ID FROM " + table); cleanup.add("DROP VIEW " + view);
                s.execute("CREATE MATERIALIZED VIEW " + mv + " REFRESH COMPLETE ON DEMAND AS SELECT ID FROM " + table);
                cleanup.add("DROP MATERIALIZED VIEW " + mv);
                try (ResultSet r = s.executeQuery("SELECT OBJECT_NAME,OBJECT_TYPE FROM ALL_OBJECTS WHERE OWNER='" + schema + "' AND OBJECT_NAME LIKE '" + prefix + "%'")) {
                    while (r.next()) System.out.println(type + " QA catalog " + r.getString(1) + " / " + r.getString(2));
                }
                List<Table> objects = Chat2DBContext.getMetaData(type).tableViews(catalog, "", schema);
                assertEquals(1, objects.stream().filter(t -> (prefix + "_V").equals(t.getName()) && "VIEW".equals(t.getTableDetails().getType())).count());
                assertEquals(1, objects.stream().filter(t -> (prefix + "_M").equals(t.getName()) && "MATERIALIZED VIEW".equals(t.getTableDetails().getType())).count());
            } finally {
                Collections.reverse(cleanup);
                for (String sql : cleanup) s.execute(sql);
            }
        }
    }
}
