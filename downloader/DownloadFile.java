package downloader;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicInteger;

public class DownloadFile {
    private final RandomAccessFile file;
    private final FileChannel channel; // 线程安全类
    private AtomicInteger wroteSize; // 已写入的长度

    public DownloadFile(String fileName, long fileSize) throws IOException {
        this.wroteSize = new AtomicInteger(0);
        this.file = new RandomAccessFile(fileName, "rw");
        file.setLength(fileSize);
        channel = file.getChannel();
    }

    /**
     * 写数据
     * @param offset 写偏移量
     * @param buffer 数据
     * @throws IOException
     */
    public void write(long offset, ByteBuffer buffer) throws IOException {
        buffer.flip();
        int length = buffer.limit();
        while (buffer.hasRemaining()) {
            channel.write(buffer, offset);
        }
        wroteSize.addAndGet(length);
    }

    private void log() {
        // todo 断点续传
    }

    /**
     * 获得已经下载的数据量，为了直到何时结束整个任务，以及统计信息
     * @return
     */
    public long getWroteSize() {
        return wroteSize.get();
    }

    /**
     * 关闭文件
     */
    public void close() {
        try {
            file.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
