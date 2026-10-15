package com.jd.biz.domain.api.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/** Platform and resource decisions for one user on one connection. */
@Data
public class DataSourcePermissionPreview {
    private Long userId;
    private String userName;
    private boolean active;
    private List<ActionPermission> actions = new ArrayList<>();

    @Data
    public static class ActionPermission {
        private String code;
        private boolean platformAllowed;
        private boolean resourceAllowed;
        private boolean allowed;
        private String reason;
        private List<String> sources;
    }
}
