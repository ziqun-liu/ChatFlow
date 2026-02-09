package cs6650.ziqunliu.chatflow.server.service;

import java.util.concurrent.atomic.AtomicLong;

public class RequestStatsService {
  private static final AtomicLong getRequests = new AtomicLong(0);
  private static final AtomicLong postRequests = new AtomicLong(0);

  public static void incrementGet() {
    getRequests.incrementAndGet();
  }

  public static void incrementPost() {
    postRequests.incrementAndGet();
  }

  public static long getGetCount() {
    return getRequests.get();
  }

  public static long getPostCount() {
    return postRequests.get();
  }

  public static String getStats() {
    return String.format("GET: %d, POST: %d, Total: %d",
        getGetCount(), getPostCount(), getGetCount() + getPostCount());
  }
}