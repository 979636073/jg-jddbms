package com.jd.biz.http.response;

import com.jd.biz.http.model.EsTableSchema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class EsTableSchemaResponse {

    private List<EsTableSchema> tableSchemas;
}
