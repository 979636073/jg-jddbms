package com.jd.biz.controller.rdb;

import com.jd.biz.controller.data.source.request.DataSourceBaseRequest;
import com.jd.biz.controller.rdb.converter.RdbWebConverter;
import com.jd.biz.controller.rdb.request.DmlTableRequest;
import com.jd.biz.domain.api.param.DlExecuteParam;
import com.jd.biz.domain.core.converter.CommandConverter;
import com.jd.spi.model.Command;
import org.junit.Test;
import org.mapstruct.factory.Mappers;

import static org.junit.Assert.assertFalse;

public class OptionalRequestDefaultsTest {

    @Test
    public void omittedRefreshDefaultsToFalse() {
        assertFalse(new DataSourceBaseRequest().getRefresh());
    }

    @Test
    public void tableQueryDefaultsQueryTemplateThroughConverters() {
        DmlTableRequest request = new DmlTableRequest();
        DlExecuteParam param = Mappers.getMapper(RdbWebConverter.class).request2param(request);
        Command command = Mappers.getMapper(CommandConverter.class).param2model(param);
        assertFalse(command.getQueryTemplate());
    }
}
