package com.jd.biz.migration;

import com.alibaba.fastjson2.JSON;
import com.jd.biz.domain.api.enums.DataSourceAction;
import com.jd.biz.domain.api.model.DataSource;
import com.jd.common.tools.base.wrapper.result.DataResult;
import com.jd.common.tools.base.excption.BusinessException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController
@RequestMapping("/api/schema-sql-transfer")
public class SchemaSqlTransferController {
    private final MigrationConnections connections;
    private final SchemaSqlTransfer schema;
    private final TransferTaskService tasks;
    public SchemaSqlTransferController(MigrationConnections connections, SchemaSqlTransfer schema, TransferTaskService tasks) {
        this.connections = connections; this.schema = schema; this.tasks = tasks;
    }

    @GetMapping("/tables")
    public DataResult<Map<String, Object>> tables(@RequestParam Long dataSourceId, @RequestParam String schemaName,
            @RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int size) throws Exception {
        DataSource source = connections.authorized(dataSourceId, DataSourceAction.METADATA_READ);
        return DataResult.of(schema.tables(source, schemaName, search, page, size));
    }

    @PostMapping("/preview")
    public DataResult<SchemaSqlTransfer.Preview> preview(@RequestPart("file") MultipartFile file) throws Exception {
        return DataResult.of(schema.preview(file.getBytes()));
    }

    public static class ExportRequest {
        public Long dataSourceId; public String schemaName; public List<String> tables;
        public boolean selectAll; public String search;
    }

    @PostMapping("/export")
    public DataResult<Long> export(@RequestBody ExportRequest request) throws Exception {
        return DataResult.of(tasks.exportSchema(request.dataSourceId, request.schemaName, request.tables,
                request.selectAll, request.search));
    }

    @PostMapping("/import")
    public DataResult<Long> importFile(@RequestParam Long dataSourceId, @RequestParam String schemaName,
            @RequestParam String tables, @RequestParam String scope, @RequestPart("file") MultipartFile file) throws Exception {
        List<String> selected = JSON.parseArray(tables, String.class);
        if (!"structure".equals(scope) && !"all".equals(scope)) throw new BusinessException("请选择导入范围");
        return DataResult.of(tasks.importSchema(dataSourceId, schemaName, file.getBytes(), selected, "all".equals(scope)));
    }
}
