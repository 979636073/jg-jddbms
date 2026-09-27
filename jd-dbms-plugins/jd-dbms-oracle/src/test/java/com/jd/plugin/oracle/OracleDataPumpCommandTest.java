package com.jd.plugin.oracle;

import com.jd.spi.model.DmpCommand;
import com.jd.spi.model.DmpToolOptions;
import com.jd.spi.sql.ConnectInfo;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertTrue;

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

    private ConnectInfo connectInfo() {
        ConnectInfo connectInfo = new ConnectInfo();
        connectInfo.setUrl("jdbc:oracle:thin:@db.example:1521/ORCL");
        return connectInfo;
    }
}
