package com.jd.plugin.dm;

import com.jd.spi.model.DmpCommand;
import com.jd.spi.model.DmpToolOptions;
import com.jd.spi.sql.Chat2DBContext;
import com.jd.spi.sql.ConnectInfo;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertTrue;

public class DmpContentCommandTest {
    @Test
    public void schemaExportExcludesOnlyUncheckedContent() {
        ConnectInfo info = connectInfo();
        Chat2DBContext.putContext(info);
        try {
            DmpCommand command = new DMMetaData().exportDmp(info, Collections.emptyList(), "APP",
                    new File("C:/transfer/out.log"), new File("C:/transfer/out.dmp"), "dexp", true, "SYSDBA", "secret",
                    DmpToolOptions.builder().toolFamily("DM_DEXP")
                            .contentOptions(Arrays.asList("ROWS", "INDEXES", "CONSTRAINTS", "TRIGGERS",
                                    "VIEWS", "PROCEDURE", "PACKAGE", "SEQUENCE")).build());
            List<String> args = command.toProcessArguments();
            assertTrue(args.stream().anyMatch(arg -> arg.startsWith("SCHEMAS=") && arg.contains("APP")));
            assertTrue(args.contains("file=out.dmp"));
            assertTrue(args.stream().anyMatch(arg -> arg.startsWith("DIRECTORY=") && arg.contains("transfer")));
            assertTrue(args.contains("EXCLUDE=(GRANTS)"));
        } finally {
            Chat2DBContext.removeContext();
        }
    }

    @Test
    public void schemaImportCanSkipRowsAndViews() {
        ConnectInfo info = connectInfo();
        Chat2DBContext.putContext(info);
        try {
            DmpCommand command = new DMMetaData().importDmp(info, Collections.emptyList(), "APP", "APP", "APP",
                    new File("C:/transfer/in.dmp"), new File("C:/transfer/in.log"), "dimp", Collections.emptyList(), true,
                    "SYSDBA", "secret", DmpToolOptions.builder().toolFamily("DM_DEXP")
                            .contentOptions(Arrays.asList("INDEXES", "CONSTRAINTS", "TRIGGERS",
                                    "PROCEDURE", "PACKAGE", "SEQUENCE", "GRANTS")).build());
            assertTrue(command.toProcessArguments().contains("EXCLUDE=(ROWS,VIEWS)"));
            assertTrue(command.toProcessArguments().contains("file=in.dmp"));
        } finally {
            Chat2DBContext.removeContext();
        }
    }

    private ConnectInfo connectInfo() {
        ConnectInfo info = new ConnectInfo();
        info.setDbType("DM");
        info.setHost("127.0.0.1");
        info.setPort(5237);
        return info;
    }
}
