package com.easy.cas.client.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Reads CAS client credentials from authorize.properties. */
public final class PropertiesUtil {
    private static final Properties PROPERTIES = loadProperties();

    private PropertiesUtil() {
    }

    public static String getClientId() {
        return getValue("JDDBMS_SSO_CLIENT_ID", "sso.client_id");
    }

    public static String getClientSecret() {
        return getValue("JDDBMS_SSO_CLIENT_SECRET", "sso.client_secret");
    }

    private static String getValue(String environmentName, String propertyName) {
        String environmentValue = System.getenv(environmentName);
        return environmentValue == null
                ? PROPERTIES.getProperty(propertyName, "").trim()
                : environmentValue.trim();
    }

    private static Properties loadProperties() {
        Properties properties = new Properties();
        try (InputStream input = PropertiesUtil.class.getClassLoader().getResourceAsStream("authorize.properties")) {
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException ignored) {
            // Missing optional CAS configuration leaves credentials blank.
        }
        return properties;
    }
}
