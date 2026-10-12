package com.jd.plugin.oracle;

import com.jd.spi.model.DmpCommand;
import com.jd.spi.model.DmpToolOptions;
import com.jd.spi.sql.ConnectInfo;
import com.jd.spi.sql.Chat2DBContext;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class OracleDataPumpCommandTest {

    @Test
    public void shouldBuildVersionedDataPumpExportCommand() {
        OracleMetaData metaData = new OracleMetaData();
        DmpCommand command = metaData.exportDmp(connectInfo(), Arrays.asList("USERS", "ROLES"), "APP",
                new File("export.log"), new File("export.dmp"), "expdp", false,
                "system", "secret", DmpToolOptions.builder()
                        .toolFamily("ORACLE_DATAPUMP")
                        .directoryName("JDDBMS_DMP_DIR")
                        .targetVersion("12.2")
                        .build());

        List<String> arguments = command.toProcessArguments();
        assertTrue(arguments.contains("DIRECTORY=JDDBMS_DMP_DIR"));
        assertTrue(arguments.contains("DUMPFILE=export.dmp"));
        assertTrue(arguments.contains("VERSION=12.2"));
        assertTrue(arguments.stream().anyMatch(item -> item.startsWith("TABLES=")));
    }

    @Test
    public void shouldBuildDataPumpSchemaRemapImportCommand() {
        OracleMetaData metaData = new OracleMetaData();
        DmpCommand command = metaData.importDmp(connectInfo(), Collections.emptyList(), "OLD_APP", "NEW_APP", null,
                new File("input.dmp"), new File("import.log"), "impdp", Collections.emptyList(), false,
                "system", "secret", DmpToolOptions.builder()
                        .toolFamily("ORACLE_DATAPUMP")
                        .directoryName("JDDBMS_DMP_DIR")
                        .build());

        List<String> arguments = command.toProcessArguments();
        assertTrue(arguments.contains("SCHEMAS=OLD_APP"));
        assertTrue(arguments.contains("REMAP_SCHEMA=OLD_APP:NEW_APP"));
    }

    @Test
    public void shouldKeepTableFilterWhenImportingLegacyDumpIntoAnotherSchema() {
        ConnectInfo info = connectInfo();
        info.setDbType("ORACLE");
        Chat2DBContext.putContext(info);
        try {
            DmpCommand command = new OracleMetaData().importDmp(info, Arrays.asList("EMP"), "OLD_APP", "NEW_APP", null,
                    new File("input.dmp"), new File("import.log"), "imp", Collections.emptyList(), false,
                    "system", "secret");
            List<String> arguments = command.toProcessArguments();
            assertTrue(arguments.stream().anyMatch(item -> item.startsWith("tables=(")));
            assertTrue(arguments.contains("fromuser=OLD_APP"));
            assertTrue(arguments.contains("touser=NEW_APP"));
            assertTrue(arguments.contains("IGNORE=Y"));
            assertFalse(arguments.stream().anyMatch(item -> item.startsWith("tables=OLD_APP.")));
        } finally {
            Chat2DBContext.removeContext();
        }
    }

    @Test
    public void shouldFilterSchemaContentForDataPump() {
        OracleMetaData metaData = new OracleMetaData();
        List<String> selected = new ArrayList<>(com.jd.spi.model.DmpContentOptions.ALL);
        selected.remove("ROWS"); selected.remove("CONSTRAINTS"); selected.remove("PROCEDURE");
        DmpCommand command = metaData.exportDmp(connectInfo(), Collections.emptyList(), "APP",
                new File("export.log"), new File("export.dmp"), "expdp", false,
                "system", "secret", DmpToolOptions.builder().toolFamily("ORACLE_DATAPUMP")
                        .directoryName("JDDBMS_DMP_DIR").contentOptions(selected).build());
        List<String> args = command.toProcessArguments();
        assertTrue(args.contains("CONTENT=METADATA_ONLY"));
        assertTrue(args.contains("EXCLUDE=CONSTRAINT"));
        assertTrue(args.contains("EXCLUDE=REF_CONSTRAINT"));
        assertTrue(args.contains("EXCLUDE=PROCEDURE"));
        assertTrue(args.contains("EXCLUDE=FUNCTION"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectUnfilterableLegacyObjects() {
        List<String> selected = new ArrayList<>(com.jd.spi.model.DmpContentOptions.ALL);
        selected.remove("PACKAGE");
        ConnectInfo info = connectInfo();
        info.setDbType("ORACLE");
        Chat2DBContext.putContext(info);
        try {
            new OracleMetaData().exportDmp(info, Collections.emptyList(), "APP",
                    new File("export.log"), new File("export.dmp"), "exp", false,
                    "system", "secret", DmpToolOptions.builder().toolFamily("ORACLE_LEGACY")
                            .contentOptions(selected).build());
        } finally {
            Chat2DBContext.removeContext();
        }
    }

    private ConnectInfo connectInfo() {
        ConnectInfo connectInfo = new ConnectInfo();
        connectInfo.setUrl("jdbc:oracle:thin:@db.example:1521/ORCL");
        return connectInfo;
    }
}
