package com.jd.biz.http.response;

import lombok.Data;

@Data
public class InviteQrCodeResponse {

    private String wechatQrCodeUrl;

    private String tip;

}
