package com.jd.biz.domain.core.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class DataSourceCipherTest {
    private static final String KEY = "test-key-with-at-least-32-characters";

    @Test
    public void shouldEncryptWithRandomIvAndDecrypt() {
        DataSourceCipher cipher = new DataSourceCipher(KEY);

        String first = cipher.encrypt("oracle-password");
        String second = cipher.encrypt("oracle-password");

        assertTrue(first.startsWith("v2:"));
        assertFalse(first.contains("oracle-password"));
        assertNotEquals(first, second);
        assertEquals("oracle-password", cipher.decrypt(first));
    }

    @Test
    public void shouldReadLegacyDesCiphertext() throws Exception {
        String legacy = new DesUtil(DesUtil.DES_KEY).encrypt("dm-password", DesUtil.CBC);

        assertEquals("dm-password", new DataSourceCipher(KEY).decrypt(legacy));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectShortKey() {
        new DataSourceCipher("short-key");
    }
}
