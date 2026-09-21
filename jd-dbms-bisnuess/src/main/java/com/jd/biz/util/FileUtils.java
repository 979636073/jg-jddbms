package com.jd.biz.util;

import com.jd.common.tools.base.excption.BusinessException;

import javax.activation.MimetypesFileTypeMap;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Locale;

/**
 * FileUtil
 *
 * @author lzy
 **/
public class FileUtils {

    public enum ConfigFile {
        // navicat连接信息文件
        NCX,
        // dbeaver连接信息文件
        DBP
    }

    public static String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf(".");
        if (dotIndex > 0) {
            return fileName.substring(dotIndex + 1).toLowerCase();
        } else {
            return "";
        }
    }


    public static String getBlobType(byte[] bytes) {
        byte[] header = new byte[]{bytes[0],bytes[1],bytes[2],bytes[3]};
        StringBuilder stringBuilder = new StringBuilder();
        for (byte b : header) {
            stringBuilder.append(String.format("%02x", b));
        }
        return stringBuilder.toString().toUpperCase(Locale.ROOT);
    }


    private static MimetypesFileTypeMap mimetypesFileTypeMap;

    public static void downLoadFile(File file, HttpServletRequest request, HttpServletResponse response, String fileName) {
        if (file != null && file.exists() && file.length() > 0L) {
            ServletOutputStream outputStream = null;
            RandomAccessFile randomAccessFile = null;
            try {
                randomAccessFile = new RandomAccessFile(file, "r");
                outputStream = response.getOutputStream();
                long length = randomAccessFile.length();
                String range = request.getHeader("Range");
                long start = 0L;
                long end = 0L;
                if (range != null && range.startsWith("bytes=")) {
                    String[] values = range.split("=")[1].split("-");
                    start = Long.parseLong(values[0]);
                    if (values.length >1) {
                        end = Long.parseLong(values[1]);
                    }
                }
                int requestSize;
                if (end != 0L && end > start) {
                    requestSize = Long.valueOf(end - start + 1L).intValue();
                } else {
                    requestSize = 2147483647;
                }
                setResponse(fileName, length, request, response);
                randomAccessFile.seek(start);
                byte[] buffer;
                for (int needSize = requestSize; needSize > 0; needSize -= buffer.length) {
                    buffer = new byte[1024];
                    int len = randomAccessFile.read(buffer);
                    if (needSize < buffer.length) {
                        outputStream.write(buffer, 0, needSize);
                    } else {
                        outputStream.write(buffer, 0, len);
                        if (len < buffer.length) {
                            break;
                        }
                    }
                }
                outputStream.flush();
            } catch (Exception e) {
                throw new BusinessException(e.getMessage());
            } finally {
                if (outputStream != null) {
                    try {
                        outputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (randomAccessFile != null) {
                    try {
                        randomAccessFile.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public static void setResponse(String fileName, long contentLength, HttpServletRequest request, HttpServletResponse response) {
        response.setContentType(getContentType(fileName));
        boolean isPreview = "preview".equalsIgnoreCase(request.getParameter("source"));
        try {
            response.addHeader("Content-Disposition", (!isPreview ? "attachment; " : " ") + "filename*=utf-8'zh_cn'" + URLEncoder.encode(fileName,"UTF-8"));
            response.setHeader("Accept-Ranges", "bytes");
            String range = request.getHeader("Range");
            if (range == null) {
                response.setHeader("Content-Length", String.valueOf(contentLength));
            } else {
                response.setStatus(206);
                long requestStart = 0L;
                long requestEnd = 0L;
                String[] ranges = range.split("=");
                if (ranges.length > 1) {
                    String[] rangeData = ranges[1].split("-");
                    requestStart = Long.parseLong(rangeData[0]);
                    if (rangeData.length >1) {
                        requestEnd = Long.parseLong(rangeData[1]);
                    }
                }
                long length = 0L;
                if (requestEnd > 0L) {
                    length = requestEnd - requestStart + 1L;
                    response.setHeader("Content-Length", String.valueOf(length));
                    response.setHeader("Content-Range", "bytes " + requestStart + "-" + requestEnd + "/" + contentLength);
                } else {
                    length = contentLength - requestStart;
                    response.setHeader("Content-Length", String.valueOf(length));
                    response.setHeader("Content-Range", "bytes " + requestStart + "-" + (contentLength - 1L) + "/" + contentLength);
                }
            }
        } catch (UnsupportedEncodingException e) {
            throw new BusinessException("response响应失败！");
        }
    }

    public static String getContentType(String fileName) {
        if (mimetypesFileTypeMap == null) {
            mimetypesFileTypeMap = new MimetypesFileTypeMap();
        }
        return mimetypesFileTypeMap.getContentType(fileName);
    }
}
