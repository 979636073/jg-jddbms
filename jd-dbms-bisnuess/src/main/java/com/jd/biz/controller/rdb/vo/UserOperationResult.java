package com.jd.biz.controller.rdb.vo;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class UserOperationResult {
    private final List<Item> items = new ArrayList<>();

    public boolean isSuccess() {
        return items.stream().noneMatch(item -> "FAILED".equals(item.status)
                || "ROLLBACK_FAILED".equals(item.status));
    }

    public void add(String name, String action, String status, String message) {
        items.add(new Item(name, action, status, message));
    }

    @Getter
    public static class Item {
        private final String name;
        private final String action;
        private String status;
        private String message;

        private Item(String name, String action, String status, String message) {
            this.name = name;
            this.action = action;
            this.status = status;
            this.message = message;
        }

        public void update(String status, String message) {
            this.status = status;
            this.message = message;
        }
    }
}
