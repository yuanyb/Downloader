package downloader;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.atomic.AtomicBoolean;

class DownloadTask extends Thread {
    private final String url;
    private long lowerBound; // 下载的文件区间
    private long upperBound;
    private AtomicBoolean canceled;
    private DownloadFile downloadFile;
    private int threadId;

    DownloadTask(String url, long lowerBound, long upperBound, DownloadFile downloadFile,
                        AtomicBoolean canceled, int threadID) {
        this.url = url;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.canceled = canceled;
        this.downloadFile = downloadFile;
        this.threadId = threadID;
    }

    @Override
    public void run() {
        ReadableByteChannel input = null;
        try {
            ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024 * 2); // 2MB
            input = connect();
            System.out.println("* [线程" + threadId + "]连接成功，开始下载...");

            int len;
            while (!canceled.get() && lowerBound <= upperBound) {
                buffer.clear();
                len = input.read(buffer);
                downloadFile.write(lowerBound, buffer, threadId, upperBound);
                lowerBound += len;
            }
            if (!canceled.get()) {
                System.out.println("* [线程" + threadId + "]下载完成" + ": " + lowerBound + "-" + upperBound);
            }
        } catch (IOException e) {
            canceled.set(true);
            System.err.println("x [线程" + threadId + "]遇到错误[" + e.getMessage() + "]，结束下载");
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
     * @throws IOException 网络连接错误
     */
    private ReadableByteChannel connect() throws IOException {
        HttpURLConnection conn = (HttpURLConnection)new URL(url).openConnection();
        conn.setConnectTimeout(3000);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Range", "bytes=" + lowerBound + "-" + upperBound);
//        System.out.println("thread_"+ threadId +": " + lowerBound + "-" + upperBound);
        conn.connect();

        int statusCode = conn.getResponseCode();
        if (HttpURLConnection.HTTP_PARTIAL != statusCode) {
            conn.disconnect();
            throw new IOException("状态码错误：" + statusCode);
        }

        return Channels.newChannel(conn.getInputStream());
    }
}
