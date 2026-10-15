package com.jd.framework.web.service;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.*;

public class TokenServiceSecretTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void persistedSecretSurvivesServiceRestart() throws Exception {
        Path profile = temporary.newFolder("profile").toPath();
        Path secretFile = profile.resolve(".secrets/token-secret");
        Files.createDirectories(secretFile.getParent());
        byte[] original = (testSecret() + "\n").getBytes(StandardCharsets.UTF_8);
        Files.write(secretFile, original);
        TokenService first = service("", profile);
        first.initializeSecret();
        String token = sign(first);
        TokenService restarted = service("  ", profile);
        restarted.initializeSecret();
        assertEquals("qa-user", restarted.getUsernameFromToken(token));
        assertArrayEquals(original, Files.readAllBytes(secretFile));
    }

    @Test public void explicitSecretDoesNotRequireLocalFile() throws Exception {
        TokenService service = service(testSecret(), temporary.newFolder("explicit").toPath());
        service.initializeSecret();
        assertEquals("qa-user", service.getUsernameFromToken(sign(service)));
    }

    @Test public void missingOrEmptySecretFailsAtStartup() throws Exception {
        Path profile = temporary.newFolder("missing").toPath();
        assertInvalid(service("", profile));
        Path secretFile = profile.resolve(".secrets/token-secret");
        Files.createDirectories(secretFile.getParent());
        Files.write(secretFile, new byte[0]);
        assertInvalid(service("", profile));
        assertInvalid(service("%%%", profile));
    }

    private void assertInvalid(TokenService service) {
        try { service.initializeSecret(); fail("Invalid signing configuration must fail at startup"); }
        catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("JDDBMS_TOKEN_SECRET")); }
    }

    private TokenService service(String secret, Path profile) throws Exception {
        TokenService service = new TokenService();
        for (String fieldName : new String[]{"secret", "profile"}) {
            Field field = TokenService.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(service, "secret".equals(fieldName) ? secret : profile.toString());
        }
        return service;
    }

    private String sign(TokenService service) throws Exception {
        Method method = TokenService.class.getDeclaredMethod("createToken", Map.class);
        method.setAccessible(true);
        return (String) method.invoke(service, Collections.singletonMap("sub", "qa-user"));
    }

    private String testSecret() {
        byte[] bytes = new byte[64];
        java.util.Arrays.fill(bytes, (byte) 23);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
