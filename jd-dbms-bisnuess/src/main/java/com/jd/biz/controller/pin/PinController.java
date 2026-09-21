package com.jd.biz.controller.pin;

import com.jd.biz.domain.api.service.PinService;
import com.jd.common.tools.base.wrapper.result.ActionResult;
import com.jd.biz.controller.pin.converter.PinWebConverter;
import com.jd.biz.controller.pin.request.PinTableRequest;
import javax.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/pin")
@RestController
public class PinController {

    @Autowired
    private PinService pinService;

    @Autowired
    private PinWebConverter pinWebConverter;

    @PostMapping("/table/add")
    public ActionResult add(@Valid @RequestBody PinTableRequest request) {
        return pinService.pinTable(pinWebConverter.req2param(request));
    }

    @PostMapping("/table/delete")
    public ActionResult delete(@Valid @RequestBody PinTableRequest request) {
        return pinService.deletePinTable(pinWebConverter.req2param(request));
    }


}
