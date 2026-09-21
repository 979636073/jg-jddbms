package com.jd.biz.controller.redis;

import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.common.tools.base.wrapper.result.ListResult;
import com.jd.biz.controller.redis.request.KeyCreateRequest;
import com.jd.biz.controller.redis.request.KeyDeleteRequest;
import com.jd.biz.controller.redis.request.KeyQueryRequest;
import com.jd.biz.controller.redis.request.KeyUpdateRequest;
import com.jd.biz.controller.redis.vo.KeyVO;
import org.springframework.web.bind.annotation.*;

/**
 * redis key运维类
 *
 * @author moji
 * @version MysqlTableManageController.java, v 0.1 2022年09月16日 17:41 moji Exp $
 * @date 2022/09/16
 */
@RequestMapping("/api/redis/key")
@RestController
public class RedisKeyManageController {

    /**
     * 查询当前DB下的key列表
     *
     * @param request
     * @return
     */
    @GetMapping("/list")
    public ListResult<KeyVO> list(KeyQueryRequest request) {
        return null;
    }

    /**
     * 新增Key
     *
     * @param request
     * @return
     */
    @PostMapping("/create")
    public ActionResult create(@RequestBody KeyCreateRequest request) {
        return null;
    }

    /**
     * 修改key信息
     *
     * @param request
     * @return
     */
    @RequestMapping(value = "/update",method = {RequestMethod.POST, RequestMethod.PUT})
    public ActionResult update(@RequestBody KeyUpdateRequest request) {
        return null;
    }


    /**
     * 删除key
     *
     * @param request
     * @return
     */
    @DeleteMapping("/delete")
    public ActionResult delete(@RequestBody KeyDeleteRequest request) {
        return null;
    }
}
