package com.jd.spi.model;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DmpCommandTest {

    @Test
    public void shouldKeepCredentialOnlyInProcessArguments() {
        DmpCommand command = new DmpCommand(
                "exp",
                "system/secret@127.0.0.1:1521/orcl",
                Arrays.asList("file=D:/backup/test.dmp", "FULL=Y")
        );

        assertEquals("system/secret@127.0.0.1:1521/orcl", command.toProcessArguments().get(1));
        assertFalse(command.toSafeString().contains("secret"));
        assertTrue(command.toSafeString().contains("******"));
        assertTrue(command.toSafeString().contains("FULL=Y"));
    }
}
