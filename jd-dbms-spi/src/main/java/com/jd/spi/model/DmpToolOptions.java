package com.jd.spi.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DmpToolOptions {
    private String toolFamily;
    private String targetVersion;
    private String directoryName;
    /** Null means the native tool's full/default content. */
    private List<String> contentOptions;
}
