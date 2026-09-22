package com.jd.common.tools.base.wrapper.result;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.common.tools.base.wrapper.Result;
import com.jd.common.tools.base.wrapper.result.web.WebPageResult;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertFalse;

public class ResultSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void shouldNotSerializeInternalErrorDetails() throws Exception {
        List<Result<?>> results = Arrays.asList(
                ActionResult.fail("test.error", "failed", "java.lang.RuntimeException: secret"),
                DataResult.error("test.error", "failed"),
                ListResult.error("test.error", "failed"),
                PageResult.error("test.error", "failed"),
                WebPageResult.error("test.error", "failed")
        );

        for (Result<?> result : results) {
            result.errorDetail("java.lang.RuntimeException: secret");
            String json = objectMapper.writeValueAsString(result);
            assertFalse(json.contains("errorDetail"));
            assertFalse(json.contains("RuntimeException"));
        }
    }
}
