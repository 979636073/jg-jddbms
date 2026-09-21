package com.jd.web.controller.system;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import com.jd.common.config.HzbConfig;

/**
 * 首页
 *
 * @author ruoyi
 */
@Controller // 1.修改普通Controller
public class SysIndexController
{
    /** 系统基础配置 */
    @Autowired
    private HzbConfig ruoyiConfig;

    /**
     * 访问首页，提示语
     */
    @RequestMapping(value = {"/","index"})
    public String index()
    {
        return "redirect:index.html"; // 3. 修改成首页
    }
}
