package cs6650.ziqunliu.chatflow.client.websocket;

import cs6650.ziqunliu.chatflow.client.ClientMain;
import cs6650.ziqunliu.chatflow.client.metrics.Metrics;
import cs6650.ziqunliu.chatflow.client.model.ChatMessage;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Each Connection Manager contains `poolSize` connections that connect to one roomId
 */
public class ConnectionManager {

  private static final Integer MAX_RETRIES = 5;
  private static final long BASE_BACKOFF_MS = 100;

  private final Integer poolSize;  // number of connections
  private final String wsUri;  // base websocket uri, no /{roomId}
  private final List<ClientWebSocketEndpoint> endpoints = new ArrayList<>();
  private final Metrics metrics;
  private final AtomicInteger rr = new AtomicInteger(0);
  private final Integer roomId;
  private final ConcurrentLinkedQueue<ChatMessage> failedMessages = new ConcurrentLinkedQueue<>();

  public ConnectionManager(String wsUri, int poolSize, Metrics metrics) {
    if (wsUri.endsWith("/")) {
      wsUri = wsUri.substring(0, wsUri.length() - 1);
    }
    this.wsUri = wsUri;
    this.poolSize = poolSize;
    this.metrics = metrics;

    int rid = Integer.parseInt(wsUri.substring(wsUri.lastIndexOf('/') + 1));
    this.roomId = rid;

    // poolSize is number of connections. Each connection
    URI uri = URI.create(wsUri);
    for (int connectionId = 0; connectionId < poolSize; connectionId++) {
      this.endpoints.add(new ClientWebSocketEndpoint(metrics, uri));
    }
  }

  /**
   * Connect all endpoints with exponential backoff retry logic. Attempts to connect each endpoint
   * up to MAX_RETRIES times.
   *
   * @throws IOException if all retry attempts fail for any endpoint
   */
  public void connectAll() throws IOException {
    for (int i = 0; i < this.endpoints.size(); i++) {
      ClientWebSocketEndpoint ep = this.endpoints.get(i);
      boolean connected = connectWithRetry(ep, i);

      if (!connected) {
        throw new IOException(
            "Failed to connect endpoint " + i + " after " + MAX_RETRIES + " attempts for room "
                + roomId);
      }
    }
  }

  /**
   * Attempt to connect a single endpoint with exponential backoff.
   *
   * @param ep    The endpoint to connect
   * @param index The endpoint index (for logging)
   * @return true if connection successful, false otherwise
   */
  private boolean connectWithRetry(ClientWebSocketEndpoint ep, int index) {
    long backoff = BASE_BACKOFF_MS;

    for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
      try {
        ep.connect();
        return true;  // Connection initiated successfully

      } catch (IOException e) {
        if (attempt == MAX_RETRIES) {
          System.err.println(
              "connectAll failed: room=" + roomId + ", endpointIndex=" + index + ", attempt="
                  + attempt + ", error=" + e.getMessage());
          return false;
        }

        // Exponential backoff before retry
        try {
          Thread.sleep(backoff);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          return false;
        }

        backoff *= 2;  // 100ms, 200ms, 400ms, 800ms, 1600ms
      }
    }

    return false;
  }

  public boolean awaitAllOpen(long timeout, TimeUnit unit) throws InterruptedException {
    long totalTimeNs = System.nanoTime() + unit.toNanos(timeout);

    for (ClientWebSocketEndpoint ep : this.endpoints) {
      long remainingTimeNs = totalTimeNs - System.nanoTime();
      if (remainingTimeNs <= 0) {
        return false;
      } else if (!ep.awaitOpen(remainingTimeNs, TimeUnit.NANOSECONDS)) {  // one endpoint timeouts
        return false;
      }
    }

    return true;
  }

  public void sendMessage(ChatMessage chatMessage) throws IOException {
    long backoff = BASE_BACKOFF_MS;

    // Try to connect and send at most 5 times
    for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
      // Count every attempt
      this.metrics.incSendAttempts();

      try {  // Try to connect and send
        int startIndex = Math.floorMod(rr.getAndIncrement(), this.poolSize);
        ClientWebSocketEndpoint ep = null;

        // Graceful connection handling: try `poolSize` times and try to find an open connection
        for (int i = 0; i < this.poolSize; i++) {
          int index = (startIndex + i) % this.poolSize;
          ClientWebSocketEndpoint candidate = this.endpoints.get(index);

          if (candidate.session != null && candidate.session.isOpen()) {
            ep = candidate;
            break;
          }
        }

        if (ep == null) {
          throw new IOException("roomId=" + this.roomId + "No open connection available");
        }

        ep.sendText(chatMessage.toJson());
        this.metrics.incSuccess();
        return;

      } catch (IOException | RuntimeException e) {  // Exception is raised if sent failed

        if (attempt == MAX_RETRIES) {  // A. Failed at the 5th time
          // Only log occasionally to avoid spam
          if (this.metrics.getFail() % 1000 == 0) {
            System.err.println(
                "Failed to send message after " + MAX_RETRIES + " attempts to room " + roomId
                    + ", total failures: " + this.metrics.getFail());
          }
          this.metrics.incFail();
          this.failedMessages.add(chatMessage);
          return;
        }

        try {  // B. Retry with exponential backoff
          Thread.sleep(backoff);
        } catch (InterruptedException ignored) {
          Thread.currentThread().interrupt();
          this.metrics.incFail();
          return;
        }

        backoff *= 2;
      }
    }
  }
//
//  private boolean reconnect(int index) {
//    try {
//      ClientWebSocketEndpoint ep = this.endpoints.get(index);
//
//      if (ep.session != null && ep.session.isOpen()) {
//        return true;
//      }
//
//      ep.close();
//      Thread.sleep(200);
//
//      ep.connect();
//      boolean opened = ep.awaitOpen(5, TimeUnit.SECONDS);
//
//      if (!opened) {
//        System.err.println("reconnect timeout: room=" + roomId + ", endpointIndex=" + index);
//        return false;
//      }
//
//      this.metrics.incReconnections();
//      return true;
//
//    } catch (IOException e) {
//      System.err.println(
//          "reconnect io error: room=" + roomId + ", endpointIndex=" + index + ", msg="
//              + e.getMessage());
//      return false;
//    } catch (InterruptedException ignored) {
//      Thread.currentThread().interrupt();
//      return false;
//    }
//  }

  public void closeAll() {
    for (ClientWebSocketEndpoint ep : this.endpoints) {
      ep.close();
    }
  }

  public int getRoomId() {
    return roomId;
  }

}
