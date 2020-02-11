package downloader;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

public class Downloader {
    private static final int DEFAULT_THREAD_COUNT = 4;  // 默认线程数量
    private AtomicBoolean canceled; // 取消状态，如果有一个子线程出现异常，则取消整个下载任务
    private DownloadFile file; // 下载的文件对象
    private String storageLocation;
    private final int threadCount; // 线程数量
    private long fileSize; // 文件大小
    private final String url;
    private long beginTime; // 开始时间
    private Logger logger;

    public Downloader(String url) {
        this(url, DEFAULT_THREAD_COUNT);
    }

    public Downloader(String url, int threadCount) {
        this.url = url;
        this.threadCount = threadCount;
        this.canceled = new AtomicBoolean(false);
        this.storageLocation = url.substring(url.lastIndexOf('/')+1);
        this.logger = new Logger(storageLocation + ".log", url, threadCount);
    }

    public void start() {
        boolean reStart = Files.exists(Path.of(storageLocation + ".log"));
        if (reStart) {
            logger = new Logger(storageLocation + ".log");
            System.out.printf("* 继续上次下载进度[已下载：%.2fMB]：%s\n", logger.getWroteSize() / 1014.0 / 1024, url);
        } else {
            System.out.println("* 开始下载：" + url);
        }
        if (-1 == (this.fileSize = getFileSize()))
            return;
        System.out.printf("* 文件大小：%.2fMB\n", fileSize / 1024.0 / 1024);

        this.beginTime = System.currentTimeMillis();
        try {
            this.file = new DownloadFile(storageLocation, fileSize, logger);
            if (reStart) {
                file.setWroteSize(logger.getWroteSize());
            }
            // 分配线程下载
            dispatcher(reStart);
            // 循环打印进度
            printDownloadProgress();
        } catch (IOException e) {
            System.err.println("x 创建文件失败[" + e.getMessage() + "]");
        }
    }

    /**
     * 分配器，决定每个线程下载哪个区间的数据
     */
    private void dispatcher(boolean reStart) {
        long blockSize = fileSize / threadCount; // 每个线程要下载的数据量
        long lowerBound = 0, upperBound = 0;
        long[][] bounds = null;
        int threadID = 0;
        if (reStart) {
           bounds = logger.getBounds();
        }
        for (int i = 0; i < threadCount; i++) {
            if (reStart) {
                threadID = (int)(bounds[i][0]);
                lowerBound = bounds[i][1];
                upperBound = bounds[i][2];
            } else {
                threadID = i;
                lowerBound = i * blockSize;
                // fileSize-1 !!!!! fu.ck，找了一下午的错
                upperBound = (i == threadCount - 1) ? fileSize-1 : lowerBound + blockSize;
            }
            new DownloadTask(url, lowerBound, upperBound, file, canceled, threadID).start();
        }
    }

    /**
     * 循环打印进度，直到下载完毕，或任务被取消
     */
    private void printDownloadProgress() {
        long downloadedSize = file.getWroteSize();
        int i = 0;
        long lastSize = 0; // 三秒前的下载量
        while (!canceled.get() && downloadedSize < fileSize) {
            if (i++ % 4 == 3) { // 每3秒打印一次
                System.out.printf("下载进度：%.2f%%, 已下载：%.2fMB，当前速度：%.2fMB/s\n",
                        downloadedSize / (double)fileSize * 100 ,
                        downloadedSize / 1024.0 / 1024,
                        (downloadedSize - lastSize) / 1024.0 / 1024 / 3);
                lastSize = downloadedSize;
                i = 0;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignore) {}
            downloadedSize = file.getWroteSize();
        }
        file.close();
        if (canceled.get()) {
            try {
                Files.delete(Path.of(storageLocation));
            } catch (IOException ignore) {
            }
            System.err.println("x 下载失败，任务已取消");
        } else {
            System.out.println("* 下载成功，本次用时"+ (System.currentTimeMillis() - beginTime) / 1000 +"秒");
        }
    }

    /**
     * @return 要下载的文件的尺寸
     */
    private long getFileSize() {
        if (fileSize != 0) {
            return fileSize;
        }
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection)new URL(url).openConnection();
            conn.setConnectTimeout(3000);
            conn.setRequestMethod("HEAD");
            conn.connect();
            System.out.println("* 连接服务器成功");
        } catch (MalformedURLException e) {
            throw new RuntimeException("URL错误");
        } catch (IOException e) {
            System.err.println("x 连接服务器失败["+ e.getMessage() +"]");
            return -1;
        }
        return conn.getContentLengthLong();
    }

    public static void main(String[] args) throws IOException {
        new Downloader("http://js.xiazaicc.com//down2/ucliulanqi_downcc.zip").start();
    }
}

