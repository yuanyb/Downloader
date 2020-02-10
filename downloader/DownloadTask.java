package downloader;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.atomic.AtomicBoolean;

public class DownloadTask extends Thread {
    private final String url;
    private long lowerBound; // 下载的文件区间
    private long upperBound;
    private AtomicBoolean canceled;
    private DownloadFile downloadFile;

    public DownloadTask(String url, long lowerBound, long upperBound, DownloadFile downloadFile, AtomicBoolean canceled) {
        this.url = url;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.canceled = canceled;
        this.downloadFile = downloadFile;
    }

    @Override
    public void run() {
        ReadableByteChannel input = null;
        try {
            ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024); // 1MB
            input = connect();
            System.out.println("* [" + Thread.currentThread() + "]连接成功，开始下载...");

            int len = 0;
            while (!canceled.get() && (len = input.read(buffer)) > 0) {
                downloadFile.write(lowerBound, buffer);
                lowerBound += len;
                buffer.clear();
            }
            if (!canceled.get()) {
                System.out.println("* [" + Thread.currentThread() + "]下载完成");
            }
        } catch (IOException e) {
            canceled.set(true);
            System.err.println("x [" + Thread.currentThread() + "]遇到错误[" + e.getMessage() + "]，结束下载");
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 连接WEB服务器，并返回一个数据通道
     * @return 返回通道
     * @throws IOException
     */
    private ReadableByteChannel connect() throws IOException {
        HttpURLConnection conn = (HttpURLConnection)new URL(url).openConnection();
        conn.setConnectTimeout(3000);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Range", "bytes=" + lowerBound + "-" + upperBound);
        conn.connect();

        int statusCode = conn.getResponseCode();
        if (HttpURLConnection.HTTP_PARTIAL != statusCode) {
            conn.disconnect();
            throw new IOException("Server exception,status code:" + statusCode);
        }

        return Channels.newChannel(conn.getInputStream());
    }
}
