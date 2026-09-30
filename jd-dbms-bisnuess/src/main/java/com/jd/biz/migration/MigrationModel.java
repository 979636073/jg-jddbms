package com.jd.biz.migration;

import java.util.ArrayList;
import java.util.List;

/** Snapshots contain metadata and user-entered filter criteria, never credentials or copied business rows. */
public final class MigrationModel {
    private MigrationModel() { }

    public static class Config {
        public String name;
        public Long sourceId;
        public Long targetId;
        public String sourceSchema;
        public String targetSchema;
        public String scope = "ALL";
        public String conflict = "STOP";
        public int fetchSize = 100;
        public int commitSize = 500;
        public int rowsPerSecond;
        public int parallelism = 1;
        public String targetTablespace;
        public String errorPolicy = "STOP";
        public List<Mapping> tables = new ArrayList<>();
    }

    public static class Mapping {
        public String source;
        public String target;
        public String sourceSchema;
        public String targetSchema;
        public String type = "TABLE";
        public String targetTablespace;
        public String scope;
        public String conflict;
        public List<Filter> filters = new ArrayList<>();
        public List<FieldMapping> columns = new ArrayList<>();
        public String manualDdl;
        public boolean ddlConfirmed;
        public List<String> dependencies = new ArrayList<>();
    }

    public static class FieldMapping {
        public String source;
        public String target;
        public String targetType;
        public String sourceAttribute;
        public boolean conversionConfirmed;
    }

    public static class Filter {
        public String column;
        public String operator = "EQ";
        public String value;
    }

    public static class Column {
        public String name;
        public String targetName;
        public String sourceType;
        public String targetType;
        public String kind;
        public Integer precision;
        public Integer scale;
        public int length;
        public String unit;
        public boolean nullable;
        public String defaultValue;
        public String comment;
        public String sourceExpression;
    }

    public static class TablePlan {
        public String source;
        public String target;
        public String sourceSchema;
        public String targetSchema;
        public String type = "TABLE";
        public String targetTablespace;
        public String scope;
        public String conflict;
        public List<Filter> filters = new ArrayList<>();
        public List<String> dependencies = new ArrayList<>();
        public Boolean triggerEnabled;
        public boolean targetExists;
        public boolean skipped;
        public boolean rebuild;
        public String previousTargetDdl;
        public String sourceDdl;
        public String targetDdl;
        public String tableComment;
        public List<String> afterDdl = new ArrayList<>();
        public List<DdlOperation> postDdl = new ArrayList<>();
        public List<Column> columns = new ArrayList<>();
        public List<String> primaryKey = new ArrayList<>();
        public List<String> blockers = new ArrayList<>();
    }

    public static class DdlOperation {
        public String name;
        public String type;
        public String sql;
        public boolean foreignKey;
        public List<String> columns = new ArrayList<>();
        public String expression;
        public String referenceSchema;
        public String referenceTable;
        public List<String> referenceColumns = new ArrayList<>();
        public String deleteRule;
        public boolean unique;
    }

    public static class Assessment {
        public String direction;
        public String sourceVersion;
        public String targetVersion;
        public String sourceDriver;
        public String targetDriver;
        public String fingerprint;
        public long checkedAt;
        public List<String> warnings = new ArrayList<>();
        public List<TablePlan> tables = new ArrayList<>();

        public boolean isReady() {
            return !tables.isEmpty() && tables.stream().allMatch(table -> table.blockers.isEmpty());
        }
    }

    public static class ObjectResult {
        public String source;
        public String target;
        public String stage = "WAITING";
        public long readRows;
        public long committedRows;
        public long sourceRows;
        public long targetRows;
        public boolean created;
        public long reusedRows;
        public String sourceBaseline;
        public String targetBaseline;
        public String sourceDefinition;
        public List<String> appliedDdl = new ArrayList<>();
        public String verification = "NOT_CHECKED";
        public String message;
    }

    public static class Task {
        public Long id;
        public Long parentTaskId;
        public Config config;
        public String sourceName;
        public String targetName;
        public String direction;
        public String status;
        public String stage = "CONFIGURATION";
        public Assessment assessment;
        public String resumeFingerprint;
        public String resumeObject;
        public long resumeCheckedAt;
        public int attempts = 1;
        public long createdAt;
        public long startedAt;
        public long finishedAt;
        public long committedRows;
        public long streamedBytes;
        public int inFlightCommits;
        public String message;
        public List<ObjectResult> objects = new ArrayList<>();
        public List<String> logs = new ArrayList<>();
    }

    public static class StartRequest {
        public String fingerprint;
        public boolean offlineConfirmed;
        public boolean reviewConfirmed;
        public boolean targetChangeConfirmed;
        public String sourceObject;
    }
}
