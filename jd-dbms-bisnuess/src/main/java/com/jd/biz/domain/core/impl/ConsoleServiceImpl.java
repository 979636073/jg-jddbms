package com.jd.biz.domain.core.impl;

import com.jd.biz.domain.api.param.ConsoleCloseParam;
import com.jd.biz.domain.api.param.ConsoleConnectParam;
import com.jd.biz.domain.api.service.ConsoleService;
import com.jd.biz.domain.repository.entity.HistoryUserLogDO;
import com.jd.biz.domain.repository.mapper.HistoryUserLogMapper;
import com.jd.common.core.domain.model.LoginUser;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.utils.SecurityUtil;
import com.jd.spi.sql.Chat2DBContext;
import com.jd.spi.sql.SQLExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author moji
 * @version DataSourceCoreServiceImpl.java, v 0.1 2022年09月23日 15:51 moji Exp $
 * @date 2022/09/23
 */
@Service
public class ConsoleServiceImpl implements ConsoleService {

    @Resource
    private HistoryUserLogMapper historyUserLogMapper;



    @Override
    public ActionResult createConsole(ConsoleConnectParam param) {
        Chat2DBContext.getDBManage().connectDatabase(Chat2DBContext.getConnection(),param.getDatabaseName());
        return ActionResult.isSuccess();
    }

    @Override
    public ActionResult closeConsole(ConsoleCloseParam param) {
        LoginUser loginUser = SecurityUtil.getLoginUser();
        historyUserLogMapper.updateStatus(loginUser.getUserId());
        return ActionResult.isSuccess();
    }

}
