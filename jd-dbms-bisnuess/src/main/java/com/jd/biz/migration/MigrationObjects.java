package com.jd.biz.migration;

import com.alibaba.druid.DbType;
import com.jd.spi.util.SqlUtils;
import com.jd.common.tools.base.excption.BusinessException;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;
import static com.jd.biz.migration.MigrationModel.*;
import static com.jd.biz.migration.MigrationDialect.*;
import static com.jd.biz.migration.MigrationMetadata.prepare;

/** Non-table definitions are explicitly reviewed, never presumed portable PL/SQL. */
final class MigrationObjects {
    static void assess(Connection source, Config config, Mapping mapping, TablePlan plan) throws SQLException {
        try (PreparedStatement query = prepare(source,
                "SELECT DISTINCT REFERENCED_OWNER, REFERENCED_NAME FROM ALL_DEPENDENCIES WHERE OWNER = ? AND NAME = ?", plan.sourceSchema, plan.source);
             ResultSet rows = query.executeQuery()) {
            while (rows.next()) {
                String owner = rows.getString(1), name = rows.getString(2);
                if (owner == null || systemSchema(owner) || (owner.equals(plan.sourceSchema) && name.equals(plan.source))) continue;
                Mapping dependency = config.tables.stream().filter(m -> sourceSchema(config, m).equals(owner) && m.source.equals(name)).findFirst()
                        .orElseThrow(() -> new BusinessException("对象引用未选中的业务依赖，请加入依赖对象，不能自动指向旧库"));
                plan.dependencies.add(qualified(sourceSchema(config, dependency), dependency.source));
            }
        }
        for (String key : mapping.dependencies) if (!plan.dependencies.contains(key)) plan.dependencies.add(key);
        if (!mapping.ddlConfirmed) throw new BusinessException("请审阅目标定义并确认转换；序列起点、业务依赖、触发器状态和方言需要人工核对");
        if ("PACKAGE".equals(plan.type)) {
            if (mapping.manualDdl == null || mapping.manualDdl.length() > 96 * 1024) throw new BusinessException("包规范与包体定义合计不能超过 96KB");
            List<String> parts = SqlUtils.parse(mapping.manualDdl, DbType.oracle);
            boolean body = plan.sourceDdl.toUpperCase(Locale.ROOT).contains("PACKAGE BODY");
            if (parts.size() != (body ? 2 : 1)) throw new BusinessException("包规范与包体必须成对转换，使用独立行 / 分隔，不能遗漏包体");
            plan.targetDdl = reviewedSql(parts.get(0), plan);
            if (body) { TablePlan bodyPlan = new TablePlan(); bodyPlan.type = "PACKAGE BODY"; bodyPlan.targetSchema = plan.targetSchema; bodyPlan.target = plan.target;
                plan.afterDdl.add(reviewedSql(parts.get(1), bodyPlan)); }
        } else plan.targetDdl = reviewedSql(mapping.manualDdl, plan);
        // A disabled source trigger must not silently become enabled.
        if ("TRIGGER".equals(plan.type)) {
            try (PreparedStatement query = prepare(source, "SELECT STATUS, TABLE_OWNER, TABLE_NAME FROM ALL_TRIGGERS WHERE OWNER = ? AND TRIGGER_NAME = ?", plan.sourceSchema, plan.source);
                 ResultSet rows = query.executeQuery()) {
                if (!rows.next()) throw new BusinessException("无法读取源触发器状态");
                String owner = rows.getString(2), name = rows.getString(3);
                Mapping table = config.tables.stream().filter(m -> "TABLE".equals(m.type) && sourceSchema(config, m).equals(owner) && m.source.equals(name)).findFirst()
                        .orElseThrow(() -> new BusinessException("触发器所属表必须同时选择"));
                String onTarget = "\\bON\\s+" + Pattern.quote(qualified(targetSchema(config, table), table.target)) + "(?=\\s)";
                if (!Pattern.compile(onTarget, Pattern.CASE_INSENSITIVE).matcher(plan.targetDdl).find()) throw new BusinessException("触发器 ON 必须指向已选择的目标表，不允许挂到源表或其他对象");
                plan.triggerEnabled = triggerEnabled(rows.getString(1));
                if (!plan.triggerEnabled) plan.afterDdl.add("ALTER TRIGGER " + qualified(plan.targetSchema, plan.target) + " DISABLE");
            }
        }
    }

    static String reviewedSql(String ddl, TablePlan plan) {
        if (ddl == null || ddl.length() > 96 * 1024) throw new BusinessException("请填写不超过 96KB 的目标 DDL");
        List<String> statements = SqlUtils.parse(ddl, DbType.oracle);
        if (statements.size() != 1) throw new BusinessException("每个对象只允许一条 CREATE 定义，不能提交批量脚本");
        String sql = statements.get(0).trim();
        String root = "CREATE\\s+" + Pattern.quote(plan.type) + "\\s+" + Pattern.quote(qualified(plan.targetSchema, plan.target));
        if (!Pattern.compile("^" + root + "(?=\\s|\\()", Pattern.CASE_INSENSITIVE).matcher(sql).find()) {
            throw new BusinessException("目标定义必须以 CREATE 对象类型 \"目标模式\".\"目标名称\" 开头；不允许 OR REPLACE、匿名块或修改其他对象");
        }
        return sql;
    }

    static void order(Assessment report) {
        List<TablePlan> pending = new ArrayList<>(report.tables), ordered = new ArrayList<>();
        // Tables first; triggers last even when the catalog omits their referenced table.
        pending.sort(Comparator.comparingInt(p -> "TABLE".equals(p.type) ? 0 : "TRIGGER".equals(p.type) ? 2 : 1));
        Set<String> done = new HashSet<>();
        while (!pending.isEmpty()) {
            TablePlan next = pending.stream().filter(p -> done.containsAll(p.dependencies)).findFirst().orElse(null);
            if (next == null) { pending.forEach(p -> p.blockers.add("依赖未选择或形成循环，请调整对象范围/人工依赖")); ordered.addAll(pending); break; }
            ordered.add(next); done.add(qualified(next.sourceSchema, next.source)); pending.remove(next);
        }
        report.tables = ordered;
    }

    static void assertCreatePrivilege(Connection target, TablePlan plan, String targetType) throws SQLException {
        if (!"ORACLE".equals(targetType)) return;
        String type = Arrays.asList("PACKAGE", "FUNCTION").contains(plan.type) ? "PROCEDURE" : plan.type;
        boolean own = plan.targetSchema.equalsIgnoreCase(target.getMetaData().getUserName());
        String needed = "CREATE " + (own ? "" : "ANY ") + type;
        try (PreparedStatement query = prepare(target, "SELECT PRIVILEGE FROM SESSION_PRIVS"); ResultSet rows = query.executeQuery()) {
            while (rows.next()) { String privilege = rows.getString(1); if (needed.equals(privilege) || (own && ("CREATE ANY " + type).equals(privilege))) return; }
        }
        throw new BusinessException("目标账号缺少 " + needed + " 权限，请由管理员准备；系统不会自动授权");
    }

    static void verify(Connection target, TablePlan plan) throws SQLException {
        try (PreparedStatement query = prepare(target,
                "SELECT STATUS FROM ALL_OBJECTS WHERE OWNER = ? AND OBJECT_NAME = ? AND OBJECT_TYPE = ?", plan.targetSchema, plan.target, plan.type);
             ResultSet rows = query.executeQuery()) {
            if (!rows.next() || !"VALID".equalsIgnoreCase(rows.getString(1))) throw new BusinessException("目标对象不存在或编译无效，请人工查看编译错误并转换；未验收");
        }
        if ("TRIGGER".equals(plan.type)) {
            try (PreparedStatement query = prepare(target, "SELECT STATUS FROM ALL_TRIGGERS WHERE OWNER=? AND TRIGGER_NAME=?", plan.targetSchema, plan.target); ResultSet rows = query.executeQuery()) {
                if (!rows.next() || !Objects.equals(plan.triggerEnabled, triggerEnabled(rows.getString(1)))) throw new BusinessException("目标触发器启用状态与源端不一致，未验收");
            }
        }
        if ("PACKAGE".equals(plan.type) && !plan.afterDdl.isEmpty()) {
            try (PreparedStatement query = prepare(target, "SELECT STATUS FROM ALL_OBJECTS WHERE OWNER=? AND OBJECT_NAME=? AND OBJECT_TYPE='PACKAGE BODY'", plan.targetSchema, plan.target); ResultSet rows = query.executeQuery()) {
                if (!rows.next() || !"VALID".equals(rows.getString(1))) throw new BusinessException("目标包体不存在或编译失败，未验收");
            }
        }
        if ("VIEW".equals(plan.type)) {
            try (PreparedStatement query = prepare(target, "SELECT * FROM " + qualified(plan.targetSchema, plan.target) + " WHERE 1=0"); ResultSet ignored = query.executeQuery()) { }
        }
    }

    static boolean triggerEnabled(String status) {
        if ("Y".equalsIgnoreCase(status) || "ENABLED".equalsIgnoreCase(status)) return true;
        if ("N".equalsIgnoreCase(status) || "DISABLED".equalsIgnoreCase(status)) return false;
        throw new BusinessException("无法识别触发器启用状态，不能猜测或忽略副作用");
    }
}
