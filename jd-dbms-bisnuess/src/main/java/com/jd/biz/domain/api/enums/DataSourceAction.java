package com.jd.biz.domain.api.enums;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Actions granted on a saved database connection. */
public enum DataSourceAction {
    METADATA_READ("dbms:metadata:read"),
    DATA_READ("dbms:data:read"),
    DATA_WRITE("dbms:data:write"),
    SCHEMA_CHANGE("dbms:schema:change"),
    SQL_EXECUTE("dbms:sql:execute"),
    MONITOR_VIEW("dbms:monitor:view"),
    MIGRATION_RUN("dbms:migration:run"),
    DATASOURCE_MANAGE("dbms:datasource:manage");

    private final String platformPermission;

    DataSourceAction(String platformPermission) {
        this.platformPermission = platformPermission;
    }

    public String platformPermission() { return platformPermission; }

    public static final Set<String> DEFAULT_GRANT = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(METADATA_READ.name(), DATA_READ.name())));

    public static Set<String> parse(String value) {
        Set<String> actions = new LinkedHashSet<>();
        if (value == null || value.trim().isEmpty()) return actions;
        for (String item : value.split(",")) {
            String code = item.trim();
            if (!code.isEmpty()) actions.add(DataSourceAction.valueOf(code).name());
        }
        return actions;
    }

    public static String serialize(Set<String> requested) {
        if (requested == null || requested.isEmpty()) requested = DEFAULT_GRANT;
        Set<String> checked = new LinkedHashSet<>();
        for (String item : requested) checked.add(DataSourceAction.valueOf(item).name());
        checked.add(METADATA_READ.name());
        return String.join(",", checked);
    }
}
