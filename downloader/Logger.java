package downloader;

import java.io.*;
import java.util.Properties;

class Logger {
    private String logFileName; // 下载的文件的名字
    private Properties log;

     /**
      * 重新开始下载时，使用该构造函数
      * @param logFileName
      */
    Logger(String logFileName) {
        this.logFileName = logFileName;
        log = new Properties();
        FileInputStream fin = null;
        try {
            log.load(new FileInputStream(logFileName));
        } catch (IOException ignore) {
        } finally {
            try {
                fin.close();
            } catch (Exception ignore) {}
        }
    }

    Logger(String logFileName, String url, int threadCount) {
        this.logFileName = logFileName;
        this.log = new Properties();
        log.put("url", url);
        log.put("wroteSize", "0");
        log.put("threadCount", String.valueOf(threadCount));
        for (int i = 0; i < threadCount; i++) {
            log.put("thread_" + i, "0-0");
        }
    }


    synchronized void updateLog(int threadID, long length, long lowerBound, long upperBound) {
        log.put("thread_"+threadID, lowerBound + "-" + upperBound);
        log.put("wroteSize", String.valueOf(length + Long.parseLong(log.getProperty("wroteSize"))));

        FileOutputStream file = null;
        try {
            file = new FileOutputStream(logFileName); // 每次写时都清空文件
            log.store(file, null);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (file != null) {
                try {
                    file.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 获取区间信息
     *      ret[i][0] = threadID, ret[i][1] = lowerBoundID, ret[i][2] = upperBoundID
     * @return
     */
    long[][] getBounds() {
        long[][] bounds = new long[Integer.parseInt(log.get("threadCount").toString())][3];
        int[] index = {0};
        log.forEach((k, v) -> {
            String key = k.toString();
            if (key.startsWith("thread_")) {
                String[] interval = v.toString().split("-");
                bounds[index[0]][0] = Long.parseLong(key.substring(key.indexOf("_") + 1));
                bounds[index[0]][1] = Long.parseLong(interval[0]);
                bounds[index[0]++][2] = Long.parseLong(interval[1]);
            }
        });
       return bounds;
    }
    long getWroteSize() {
        return Long.parseLong(log.getProperty("wroteSize"));
    }
}
