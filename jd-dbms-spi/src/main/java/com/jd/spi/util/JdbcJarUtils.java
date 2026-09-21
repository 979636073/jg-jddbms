
package com.jd.spi.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jipengfei
 * @version : JdbcJarUtils.java
 */
public class JdbcJarUtils {
    public static final Logger log = LoggerFactory.getLogger(JdbcJarUtils.class);

    private static final OkHttpClient async_client = new OkHttpClient.Builder()
        .dispatcher(new Dispatcher(Executors.newFixedThreadPool(20))) // 设定线程池大小
        .build();

    private static final OkHttpClient client = new OkHttpClient();
//    assets/jar/DmJdbcDriver18-8.1.2.141.jar
//    public static final String PATH = System.getProperty("user.home") + File.separator + ".jddms" + File.separator
//        + "jdbc-lib" + File.separator;
    public static final String PATH = System.getProperty("user.dir") + File.separator + "assets" + File.separator
            + "jar" + File.separator;

    static {
        File file = new File(PATH);
        if (!file.exists()) {
            file.mkdirs();
        }
    }

    public static void asyncDownload(List<String> urls) throws Exception {
        for (String url : urls) {
            String outputPath = PATH + url.substring(url.lastIndexOf("/") + 1);
            File file = new File(outputPath);
            if (file.exists()) {
                continue;
            }
            asyncDownload(url);
        }
    }

    public static void asyncDownload(String url) throws Exception {
        String outputPath = PATH + url.substring(url.lastIndexOf("/") + 1);
        File file = new File(outputPath);
        if (file.exists()) {
            file.delete();
        }
        Request request = new Request.Builder()
            .url(url)
            .build();
        async_client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    throw new IOException("Unexpected code " + response);
                }
                try (InputStream is = response.body().byteStream();
                     FileOutputStream fos = new FileOutputStream(outputPath)) {
                    byte[] buffer = new byte[2048];
                    int length;
                    while ((length = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, length);
                    }
                    fos.flush();
                }
            }
        });
    }

    public static void download(String url) throws IOException {
        String outputPath = PATH + url.substring(url.lastIndexOf("/") + 1);
        File file = new File(outputPath);
        if (file.exists()) {
            file.delete();
        }
        Request request = new Request.Builder()
            .url(url)
            .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }
            try (InputStream is = response.body().byteStream();
                 FileOutputStream fos = new FileOutputStream(outputPath)) {

                byte[] buffer = new byte[2048];
                int length;
                while ((length = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, length);
                }
                fos.flush();
            }
        }
    }

    public static String getNewFullPath(String jarPath) {
        String path = PATH + jarPath;
        File file = new File(path);
        if (file.exists()) {
            file.delete();
        }
        return getFullPath(jarPath);
    }

    public static String getFullPath(String jarPath) {
        log.info("PATH11===============："+PATH);
        String path = PATH + jarPath;
        File file = new File(path);
        if (!file.exists()) {
            String url = getDownloadUrl(jarPath);
            try {
                download(url);
            } catch (IOException e) {
                try {
                    download(url);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
        return path;
    }

    public static final String DOWNLOAD_URL_HOST = "http://192.168.0.40:9000/jundu/lib/";
    private static String getDownloadUrl(String jarPath) {
       return   DOWNLOAD_URL_HOST+jarPath;
    }
}
