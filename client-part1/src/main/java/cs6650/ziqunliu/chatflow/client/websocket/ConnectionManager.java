package cs6650.ziqunliu.chatflow.client.websocket;

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
  public final AtomicInteger epSessionNull = new AtomicInteger(0);
  public final AtomicInteger epSessionNotOpen = new AtomicInteger(0);
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

  public void connectAll() throws IOException {
    for (ClientWebSocketEndpoint ep : this.endpoints) {

      if (ep.isConnected.get() == false) {
        System.out.println("in connect all: trying to connect:" + ep);
        ep.connect();
      }
    }
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
    int index = Math.floorMod(rr.getAndIncrement(), this.poolSize);

    long backoff = BASE_BACKOFF_MS;

    // Try to connect and send at most 5 times
    for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
      // Count every attempt
      this.metrics.incSendAttempts();

      try {  // Try to connect and send
        ClientWebSocketEndpoint ep = this.endpoints.get(index);

        // Graceful connection handling: reconnect if disconnected
        if (ep.session == null || !ep.session.isOpen()) {
          System.out.println("Trying to reconnect: " + roomId + ":index:" + index);
          if (ep.session == null) {
            epSessionNull.incrementAndGet();
          } else if (!ep.session.isOpen()) {
            epSessionNotOpen.incrementAndGet();
          }
          boolean ok = reconnect(index);
          if (!ok) {
            throw new IOException("Reconnect failed");
          }
        }

        ep = this.endpoints.get(index);
        if (ep.session == null || !ep.session.isOpen()) {
          throw new IOException("Session still not ready after reconnect");
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

  private boolean reconnect(int index) {
    try {
      ClientWebSocketEndpoint ep = this.endpoints.get(index);
      System.out.println("DEBUG reconnect: room=" + roomId + ", index=" + index + 
                       ", ep=" + (ep == null ? "NULL" : "not null") +
                       ", session before=" + (ep.session == null ? "NULL" : ep.session.getId()));
      if (ep.isConnected.get() == false) {
        ep.connect();
        boolean opened = ep.awaitOpen(100, TimeUnit.MILLISECONDS);
        System.out.println("DEBUG reconnect: awaitOpen returned " + opened +
            ", session after=" + (ep.session == null ? "NULL" : ep.session.getId()));

        if (!opened) {
          System.err.println("reconnect timeout: room=" + roomId + ", endpointIndex=" + index);
          return false;
        }

        this.metrics.incReconnections();
        return true;
      }
      return false;
    } catch (IOException e) {
      System.err.println(
          "reconnect io error: room=" + roomId + ", endpointIndex=" + index + ", msg="
              + e.getMessage());
      return false;
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  public void closeAll() {
    for (ClientWebSocketEndpoint ep : this.endpoints) {
      //ep.close();
    }
  }

  public int getRoomId() {
    return roomId;
  }

}
