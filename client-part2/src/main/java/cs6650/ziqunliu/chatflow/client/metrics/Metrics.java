package cs6650.ziqunliu.chatflow.client.metrics;

import cs6650.ziqunliu.chatflow.client.model.LatencyRecord;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

public class Metrics {

  private final AtomicLong success = new AtomicLong(0);
  private final AtomicLong fail = new AtomicLong(0);
  private final AtomicLong sendAttempts = new AtomicLong(0);
  private final AtomicLong reconnections = new AtomicLong(0);
  private final AtomicLong connectionsCreated = new AtomicLong(0);

  private volatile long startNs = 0L;
  private volatile long endNs = 0L;

  // Per-message latency records (lock-free, thread-safe)
  private final ConcurrentLinkedQueue<LatencyRecord> latencyRecords = new ConcurrentLinkedQueue<>();

  public void start() {
    startNs = System.nanoTime();
    endNs = 0L;
  }

  public void stop() {
    endNs = System.nanoTime();
  }

  public void incSuccess() {
    success.incrementAndGet();
  }

  public void incFail() {
    fail.incrementAndGet();
  }

  public void incSendAttempts() {
    sendAttempts.incrementAndGet();
  }

  public void incSendAttempts(long n) {
    sendAttempts.addAndGet(n);
  }

  public void incReconnections() {
    reconnections.incrementAndGet();
  }

  public void incConnectionsCreated() {
    connectionsCreated.incrementAndGet();
  }

  public long getConnectionsCreated() {
    return connectionsCreated.get();
  }

  public long getReconnections() {
    return reconnections.get();
  }

  public long getSendAttempts() {
    return sendAttempts.get();
  }

  public long getFail() {
    return fail.get();
  }

  public long getSuccess() {
    return success.get();
  }

  public long getTotalProcessed() {
    return getSuccess() + getFail();
  }

  public double elapsedSeconds() {
    long end = (endNs == 0L) ? System.nanoTime() : endNs;
    if (startNs == 0L) {
      return 0.0;
    }
    return (end - startNs) / 1_000_000_000.0;
  }

  public double throughputMsgPerSec() {
    double s = elapsedSeconds();
    if (s <= 0.0) {
      return 0.0;
    }
    return getSuccess() / s;
  }

  public String summary(String label) {
    return label + "\n" + "success=" + getSuccess() + ", fail=" + getFail() + ", attempts="
        + getSendAttempts() + ", totalProcessed=" + getTotalProcessed() + "\n"
        + "connectionsCreated=" + getConnectionsCreated() + ", reconnections=" + getReconnections()
        + "\n" + "wallTimeSec=" + String.format("%.3f", elapsedSeconds()) + ", throughputMsgPerSec="
        + String.format("%.2f", throughputMsgPerSec());
  }

  // ========== Per-Message Metrics Methods ==========

  public void recordLatency(LatencyRecord record) {
    latencyRecords.add(record);
  }

  public List<LatencyRecord> getLatencyRecords() {
    return new ArrayList<>(latencyRecords);
  }

  /**
   * Write all latency records to CSV file.
   */
  public void writeCsv(String filename) throws IOException {
    List<LatencyRecord> records = getLatencyRecords();
    try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {
      pw.println("timestamp,messageType,latency,statusCode,roomId");
      for (LatencyRecord r : records) {
        pw.println(r.toCsvLine());
      }
    }
    System.out.println("CSV written: " + filename + " (" + records.size() + " records)");
  }

  /**
   * Print detailed statistical analysis of latency data.
   */
  public void printStatistics() {
    List<LatencyRecord> records = getLatencyRecords();
    if (records.isEmpty()) {
      System.out.println("No latency records to analyze.");
      return;
    }

    // Extract latencies and sort (exclude failures with latency = -1)
    long[] latencies = records.stream()
        .filter(r -> r.getLatencyMs() >= 0)
        .mapToLong(LatencyRecord::getLatencyMs)
        .toArray();
    Arrays.sort(latencies);

    if (latencies.length == 0) {
      System.out.println("No successful messages to analyze.");
      return;
    }

    int n = latencies.length;
    long sum = 0;
    for (long l : latencies) sum += l;

    double mean = (double) sum / n;
    long median = latencies[n / 2];
    long p95 = latencies[(int) (n * 0.95)];
    long p99 = latencies[(int) (n * 0.99)];
    long min = latencies[0];
    long max = latencies[n - 1];

    System.out.println();
    System.out.println("========================================");
    System.out.println("  Latency Statistics");
    System.out.println("========================================");
    System.out.printf("  Total records : %,d%n", n);
    System.out.printf("  Mean          : %.2f ms%n", mean);
    System.out.printf("  Median        : %d ms%n", median);
    System.out.printf("  P95           : %d ms%n", p95);
    System.out.printf("  P99           : %d ms%n", p99);
    System.out.printf("  Min           : %d ms%n", min);
    System.out.printf("  Max           : %d ms%n", max);
    System.out.println("========================================");

    // Throughput per room
    Map<Integer, Integer> roomCounts = new HashMap<>();
    Map<String, Integer> typeCounts = new HashMap<>();
    for (LatencyRecord r : records) {
      roomCounts.merge(r.getRoomId(), 1, Integer::sum);
      typeCounts.merge(r.getMessageType(), 1, Integer::sum);
    }

    System.out.println();
    System.out.println("========================================");
    System.out.println("  Throughput Per Room");
    System.out.println("========================================");
    roomCounts.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(e -> System.out.printf("  Room %2d : %,d messages%n", e.getKey(), e.getValue()));
    System.out.println("========================================");

    System.out.println();
    System.out.println("========================================");
    System.out.println("  Message Type Distribution");
    System.out.println("========================================");
    typeCounts.forEach((type, count) ->
        System.out.printf("  %-6s : %,d (%.1f%%)%n", type, count, 100.0 * count / n));
    System.out.println("========================================");
  }
}
















