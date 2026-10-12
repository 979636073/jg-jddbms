package com.jd.biz.migration;

import com.jd.biz.domain.api.enums.DataSourceAction;
import com.jd.biz.domain.api.model.DataSource;
import com.jd.biz.domain.api.param.TaskCreateParam;
import com.jd.biz.domain.api.param.TaskUpdateParam;
import com.jd.biz.domain.api.service.TaskService;
import com.jd.common.tools.base.excption.BusinessException;
import com.jd.common.tools.common.util.ContextUtils;
import org.springframework.stereotype.Service;
import javax.annotation.PreDestroy;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.List;

/** Bounded worker queue shared by table and schema file transfers. */
@Service
public class TransferTaskService {
    private final TaskService tasks;
    private final MigrationConnections connections;
    private final TableFileTransfer files;
    private final SchemaSqlTransfer schemaSql;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(8), runnable -> {
                Thread thread = new Thread(runnable, "jddbms-transfer"); thread.setDaemon(true); return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    public TransferTaskService(TaskService tasks, MigrationConnections connections, TableFileTransfer files, SchemaSqlTransfer schemaSql) {
        this.tasks = tasks; this.connections = connections; this.files = files; this.schemaSql = schemaSql;
    }

    @PreDestroy public void shutdown() { executor.shutdown(); }

    public Long exportTable(Long id, String schema, String table, String format) {
        TableFileTransfer.Format kind = TableFileTransfer.Format.parse(format);
        MigrationDialect.quote(schema); MigrationDialect.quote(table);
        DataSource source = connections.authorized(id, DataSourceAction.DATA_READ);
        Long taskId = create(id, schema, table, "TABLE_EXPORT", "导出 " + schema + "." + table + " · " + kind.name());
        submit(taskId, () -> {
            byte[] content = files.exportFile(source, schema, table, kind.name());
            Path path = outputPath(taskId, kind.extension);
            Files.write(path, content);
            finish(taskId, path.toAbsolutePath().toString(), "导出完成：" + content.length + " 字节");
        });
        return taskId;
    }

    public Long importTable(Long id, String schema, String table, String format, byte[] content) throws Exception {
        TableFileTransfer.Format kind = TableFileTransfer.Format.parse(format);
        MigrationDialect.quote(schema); MigrationDialect.quote(table);
        if (content == null || content.length == 0 || content.length > TableFileTransfer.MAX_FILE_BYTES) throw new BusinessException("请选择不超过 50 MiB 的文件");
        DataSource target = connections.authorized(id, DataSourceAction.DATA_WRITE);
        Long taskId = create(id, schema, table, "TABLE_IMPORT", "导入 " + schema + "." + table + " · " + kind.name());
        Path upload = outputPath(taskId, "upload");
        try { Files.write(upload, content); }
        catch (Exception error) { update(taskId, "ERROR", "1", null, "暂存导入文件失败"); throw error; }
        try { submit(taskId, () -> {
            try {
                int rows = files.importFile(target, schema, table, kind.name(), Files.readAllBytes(upload));
                finish(taskId, null, "导入完成：" + rows + " 行");
            } finally { Files.deleteIfExists(upload); }
        }); } catch (RuntimeException error) { Files.deleteIfExists(upload); throw error; }
        return taskId;
    }

    public Long exportSchema(Long id, String schema, List<String> names, boolean selectAll, String search) throws Exception {
        DataSource source = connections.authorized(id, DataSourceAction.DATA_READ);
        List<String> selected = selectAll ? schemaSql.tableNames(source, schema, search) : names;
        if (selected == null || selected.isEmpty()) throw new BusinessException("请选择要导出的表");
        Long taskId = create(id, schema, null, "SCHEMA_SQL_EXPORT", "导出模式 SQL · " + schema + " · " + selected.size() + " 张表");
        submit(taskId, () -> {
            byte[] content = schemaSql.exportSql(source, schema, selected, SchemaSqlTransfer.Options.fullExport(), progress(taskId));
            Path path = outputPath(taskId, "sql"); Files.write(path, content);
            finish(taskId, path.toAbsolutePath().toString(), "模式 SQL 导出完成：" + selected.size() + " 张表");
        });
        return taskId;
    }

    public Long importSchema(Long id, String schema, byte[] content, List<String> names, boolean withData) throws Exception {
        connections.authorized(id, DataSourceAction.SCHEMA_CHANGE);
        if (withData) connections.authorized(id, DataSourceAction.DATA_WRITE);
        DataSource target = connections.authorized(id, DataSourceAction.METADATA_READ);
        SchemaSqlTransfer.Preview preview = schemaSql.preview(content);
        if (names == null || names.isEmpty() || !preview.tables.containsAll(names)) throw new BusinessException("请选择 SQL 文件中的表");
        SchemaSqlTransfer.Options options = new SchemaSqlTransfer.Options();
        options.dataRows = withData;
        boolean nativeObjects = preview.sourceType.equals(target.getType()) && preview.schema.equals(schema);
        options.indexes = nativeObjects; options.constraints = nativeObjects; options.triggers = nativeObjects;
        options.views = nativeObjects; options.procedures = nativeObjects;
        Long taskId = create(id, schema, null, "SCHEMA_SQL_IMPORT", "导入模式 SQL · " + schema + " · " + names.size() + " 张表");
        Path upload = outputPath(taskId, "upload");
        try { Files.write(upload, content); }
        catch (Exception error) { update(taskId, "ERROR", "1", null, "暂存导入文件失败"); throw error; }
        try { submit(taskId, () -> {
            try {
                int rows = schemaSql.importSql(target, schema, Files.readAllBytes(upload), names, options, progress(taskId));
                finish(taskId, null, "模式 SQL 导入完成：" + names.size() + " 张表、" + rows + " 行数据");
            } finally { Files.deleteIfExists(upload); }
        }); } catch (RuntimeException error) { Files.deleteIfExists(upload); throw error; }
        return taskId;
    }

    public Long create(Long id, String schema, String table, String type, String name) {
        TaskCreateParam task = new TaskCreateParam();
        task.setDataSourceId(id); task.setSchemaName(schema); task.setTableName(table);
        task.setUserId(ContextUtils.getUserId()); task.setTaskType(type); task.setTaskName(name); task.setTaskProgress("0.01");
        return tasks.create(task).getData();
    }

    public void submit(Long id, TransferWork work) {
        try {
            executor.execute(() -> {
                update(id, "PROCESSING", "0.1", null, "正在处理文件与数据库数据");
                try { work.run(); }
                catch (Exception error) { update(id, "ERROR", "1", null, MigrationConnections.safeError(error)); }
            });
        } catch (RuntimeException rejected) {
            update(id, "ERROR", "1", null, "后台任务队列已满，请稍后重试");
            throw new BusinessException("后台任务队列已满，请稍后重试");
        }
    }

    public void finish(Long id, String path, String message) { update(id, "FINISH", "1", path, message); }

    private SchemaSqlTransfer.Progress progress(Long id) {
        final int[] previous = { 10 };
        return (fraction, message) -> {
            int percent = 10 + (int) Math.floor(Math.max(0, Math.min(1, fraction)) * 85);
            if (percent > previous[0]) { previous[0] = percent; update(id, "PROCESSING", String.valueOf(percent / 100.0), null, message); }
        };
    }

    private void update(Long id, String status, String progress, String path, String message) {
        TaskUpdateParam update = new TaskUpdateParam(); update.setId(id); update.setTaskStatus(status);
        update.setTaskProgress(progress); update.setDownloadUrl(path);
        update.setContent(message.getBytes(StandardCharsets.UTF_8)); tasks.updateStatus(update);
    }

    public static Path outputPath(Long id, String extension) throws Exception {
        Path directory = new File(System.getProperty("java.io.tmpdir"), "jddbms-transfer-tasks").toPath();
        Files.createDirectories(directory);
        return directory.resolve("task-" + id + "." + extension);
    }

    @FunctionalInterface public interface TransferWork { void run() throws Exception; }
}
