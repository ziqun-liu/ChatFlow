package cs6650.ziqunliu.chatflow.client.metrics;

import java.util.concurrent.atomic.AtomicLong;

public class Metrics {

  private final AtomicLong success = new AtomicLong(0);
  private final AtomicLong fail = new AtomicLong(0);
  private final AtomicLong sendAttempts = new AtomicLong(0);
  private final AtomicLong reconnections = new AtomicLong(0);
  private final AtomicLong connectionsCreated = new AtomicLong(0);

  private volatile long startNs = 0L;
  private volatile long endNs = 0L;

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

  public Long getReconnections() {
    return reconnections.get();
  }

  public Long getSendAttempts() {
    return sendAttempts.get();
  }

  public Long getFail() {
    return fail.get();
  }

  public Long getSuccess() {
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
}
















