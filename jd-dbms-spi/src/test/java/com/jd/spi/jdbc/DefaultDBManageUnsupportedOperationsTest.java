package com.jd.spi.jdbc;

import org.junit.Assert;
import org.junit.Test;

public class DefaultDBManageUnsupportedOperationsTest {

    @Test
    public void unsupportedMutationsMustNotReportSuccess() {
        DefaultDBManage manage = new DefaultDBManage();
        assertUnsupported(() -> manage.dropDatabase(null, "DB"));
        assertUnsupported(() -> manage.modifyDatabase(null, "DB", "NEW_DB"));
        assertUnsupported(() -> manage.modifySchema(null, "DB", "SCHEMA", "NEW_SCHEMA"));
        assertUnsupported(() -> manage.updateProcedure(null, "DB", "SCHEMA", null));
    }

    private void assertUnsupported(Operation operation) {
        try {
            operation.run();
            Assert.fail("Unsupported database operation returned successfully");
        } catch (UnsupportedOperationException expected) {
            Assert.assertNotNull(expected.getMessage());
        } catch (Exception error) {
            Assert.fail("Expected UnsupportedOperationException but got " + error);
        }
    }

    private interface Operation {
        void run() throws Exception;
    }
}
