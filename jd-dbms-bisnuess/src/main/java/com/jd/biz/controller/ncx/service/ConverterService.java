package com.jd.biz.controller.ncx.service;

import com.jd.biz.controller.ncx.vo.UploadVO;

import java.io.File;

/**
 * ConverterService
 *
 * @author lzy
 **/
public interface ConverterService {

    UploadVO uploadFile(File file);

    UploadVO dbpUploadFile(File file);

    UploadVO datagripUploadFile(String text);
}
