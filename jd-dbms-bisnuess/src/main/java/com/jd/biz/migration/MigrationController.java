package com.jd.biz.migration;

import com.jd.biz.domain.api.model.DataSource;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.common.util.ContextUtils;
import org.springframework.web.bind.annotation.*;
import java.sql.Connection;
import java.util.*;
import static com.jd.biz.migration.MigrationModel.*;

@RestController
@RequestMapping("/api/migration")
public class MigrationController {
    private final MigrationService service;
    private final MigrationConnections connections;
    private final MigrationMetadata metadata;

    public MigrationController(MigrationService service, MigrationConnections connections, MigrationMetadata metadata) {
        this.service = service; this.connections = connections; this.metadata = metadata;
    }

    @GetMapping("/connection/{id}")
    public DataResult<Map<String, Object>> connection(@PathVariable Long id) {
        DataSource source = connections.authorized(id);
        try (Connection connection = connections.open(source)) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("id", id); info.put("name", source.getAlias()); info.put("type", source.getType());
            info.put("version", connection.getMetaData().getDatabaseProductVersion());
            info.put("driver", connection.getMetaData().getDriverVersion()); info.put("schemas", metadata.schemas(connection));
            try { info.put("tablespaces", metadata.tablespaces(connection)); } catch (Exception ignored) { info.put("tablespaces", Collections.emptyList()); }
            return DataResult.of(info);
        } catch (Exception e) { return DataResult.error("MIGRATION_CONNECTION", MigrationConnections.safeError(e)); }
    }

    @GetMapping("/objects")
    public DataResult<Map<String, Object>> objects(@RequestParam Long id, @RequestParam String schema,
            @RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        DataSource source = connections.authorized(id);
        try (Connection connection = connections.open(source)) { return DataResult.of(metadata.objects(connection, schema, search, page, size)); }
        catch (Exception e) { return DataResult.error("MIGRATION_OBJECTS", MigrationConnections.safeError(e)); }
    }

    @GetMapping("/tasks") public DataResult<Map<String, Object>> page(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "") String search) {
        return DataResult.of(service.page(ContextUtils.getUserId(), page, size, search));
    }
    @GetMapping("/definition") public DataResult<Map<String, Object>> definition(@RequestParam Long id, @RequestParam String schema, @RequestParam String name, @RequestParam String type) {
        MigrationDialect.quote(schema); MigrationDialect.quote(name);
        if (MigrationDialect.systemSchema(schema)) return DataResult.error("MIGRATION_DEFINITION", "系统模式不允许迁移");
        DataSource source = connections.authorized(id);
        try (Connection connection = connections.open(source)) {
            Map<String, Object> output = new LinkedHashMap<>(); output.put("ddl", metadata.sourceDdl(connection, schema, name, type));
            output.put("columns", "TABLE".equals(type) ? metadata.editableColumns(connection, schema, name, source.getType()) : Collections.emptyList());
            return DataResult.of(output);
        } catch (Exception error) { return DataResult.error("MIGRATION_DEFINITION", MigrationConnections.safeError(error)); }
    }
    @GetMapping("/tasks/{id}") public DataResult<Task> get(@PathVariable Long id) { return DataResult.of(service.get(id, ContextUtils.getUserId())); }
    @PostMapping("/tasks") public DataResult<Task> create(@RequestBody Config config) { return DataResult.of(service.save(null, ContextUtils.getUserId(), config)); }
    @PutMapping("/tasks/{id}") public DataResult<Task> save(@PathVariable Long id, @RequestBody Config config) { return DataResult.of(service.save(id, ContextUtils.getUserId(), config)); }
    @DeleteMapping("/tasks/{id}") public DataResult<Boolean> delete(@PathVariable Long id) { return DataResult.of(service.delete(id, ContextUtils.getUserId())); }
    @PostMapping("/tasks/{id}/precheck") public DataResult<Task> precheck(@PathVariable Long id) { return DataResult.of(service.precheck(id, ContextUtils.getUserId())); }
    @PostMapping("/tasks/{id}/start") public DataResult<Task> start(@PathVariable Long id, @RequestBody StartRequest request) { return DataResult.of(service.start(id, ContextUtils.getUserId(), request)); }
    @PostMapping("/tasks/{id}/cancel") public DataResult<Task> cancel(@PathVariable Long id) { return DataResult.of(service.cancel(id, ContextUtils.getUserId())); }
    @PostMapping("/tasks/{id}/resume-check") public DataResult<Task> resumeCheck(@PathVariable Long id) { return DataResult.of(service.reviewResume(id, ContextUtils.getUserId())); }
    @PostMapping("/tasks/{id}/object-retry-check") public DataResult<Task> objectRetryCheck(@PathVariable Long id, @RequestBody StartRequest request) { return DataResult.of(service.reviewObject(id, ContextUtils.getUserId(), request.sourceObject)); }
    @PostMapping("/tasks/{id}/resume") public DataResult<Task> resume(@PathVariable Long id, @RequestBody StartRequest request) { return DataResult.of(service.resume(id, ContextUtils.getUserId(), request)); }
    @PostMapping("/tasks/{id}/retry-draft") public DataResult<Task> retryDraft(@PathVariable Long id, @RequestBody StartRequest request) { return DataResult.of(service.retryDraft(id, ContextUtils.getUserId(), request.sourceObject)); }
    @GetMapping("/tasks/{id}/report") public DataResult<Task> report(@PathVariable Long id) { return DataResult.of(service.get(id, ContextUtils.getUserId())); }
}
