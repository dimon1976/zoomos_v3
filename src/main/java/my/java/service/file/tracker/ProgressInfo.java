// src/main/java/my/java/service/file/tracker/ProgressInfo.java
package my.java.service.file.tracker;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Класс для хранения информации о прогрессе операции.
 */
public class ProgressInfo {
    private final int totalRecords;
    private final AtomicInteger processedRecords = new AtomicInteger(0);
    private int lastReportedPercent = 0;
    private long lastDbUpdateTime = System.currentTimeMillis();
    private final long startTime = System.currentTimeMillis();

    public ProgressInfo(int totalRecords) {
        this.totalRecords = totalRecords;
    }

    public int getTotalRecords() {
        return totalRecords;
    }

    public AtomicInteger getProcessedRecords() {
        return processedRecords;
    }

    public void setProcessedRecords(int processedRecords) {
        this.processedRecords.set(processedRecords);
    }

    public int getLastReportedPercent() {
        return lastReportedPercent;
    }

    public void setLastReportedPercent(int lastReportedPercent) {
        this.lastReportedPercent = lastReportedPercent;
    }

    public long getLastDbUpdateTime() {
        return lastDbUpdateTime;
    }

    public void setLastDbUpdateTime(long lastDbUpdateTime) {
        this.lastDbUpdateTime = lastDbUpdateTime;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEstimatedTimeRemaining() {
        int processed = processedRecords.get();
        if (processed <= 0 || totalRecords <= 0) {
            return -1;
        }

        long elapsedTime = System.currentTimeMillis() - startTime;
        double recordsPerMs = processed / (double) elapsedTime;
        long remainingRecords = totalRecords - processed;

        return (long) (remainingRecords / recordsPerMs);
    }
}