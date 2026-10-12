package com.jd.biz.migration;

import com.jd.common.tools.base.wrapper.result.DataResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/table-transfer")
public class TableFileTransferController {
    private final TransferTaskService tasks;
    public TableFileTransferController(TransferTaskService tasks) { this.tasks = tasks; }

    public static class ExportRequest {
        public Long dataSourceId;
        public String schema;
        public String table;
        public String format;
    }

    @PostMapping("/export")
    public DataResult<Long> exportFile(@RequestBody ExportRequest request) {
        return DataResult.of(tasks.exportTable(request.dataSourceId, request.schema, request.table, request.format));
    }

    @PostMapping("/import")
    public DataResult<Long> importFile(@RequestParam Long dataSourceId, @RequestParam String schema,
            @RequestParam String table, @RequestParam String format, @RequestPart("file") MultipartFile file) throws Exception {
        return DataResult.of(tasks.importTable(dataSourceId, schema, table, format, file.getBytes()));
    }
}
