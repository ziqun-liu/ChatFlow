package cs6650.ziqunliu.chatflow.client.websocket;

import cs6650.ziqunliu.chatflow.client.metrics.Metrics;
import java.io.IOException;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;


/**
 * Each instance represents a WebSocket connection session.
 */
@ClientEndpoint
public class ClientWebSocketEndpoint {

  private static final WebSocketContainer SHARED_CONTAINER = ContainerProvider.getWebSocketContainer();

  private final Object connectLock = new Object();
  public Metrics metrics;
  public URI serverUri;
  public volatile Session session;
  public CountDownLatch openLatch = new CountDownLatch(1);
  
  // Fields for response waiting
  private volatile String lastResponse;
  private volatile CountDownLatch responseLatch;


  public ClientWebSocketEndpoint(Metrics metrics, URI serverUri) {
    this.metrics = metrics;
    this.serverUri = serverUri;
  }

  public void connect() throws IOException {
    // Use lock to ensure only one thread is connecting at one moment
    synchronized (connectLock) {
      // Every reconnection instantiates a new CountDownLatch
      this.openLatch = new CountDownLatch(1);

      try {
        SHARED_CONTAINER.connectToServer(this, serverUri);
      } catch (DeploymentException e) {
        throw new IOException(e);
      }
    }
  }

  public boolean awaitOpen(long time, TimeUnit unit) throws InterruptedException {
    return openLatch.await(time, unit);
  }

  public void sendText(String text) throws IOException {
    if (this.session == null || !session.isOpen()) {
      throw new IOException("Session not open");
    }

    synchronized (this) {
      this.session.getBasicRemote().sendText(text);
    }
  }

  /**
   * Send a message and wait for server response (ACK).
   * @param text The message to send
   * @param timeoutMs Timeout in milliseconds
   * @return Server response, or null if timeout
   */
  public String sendAndWait(String text, long timeoutMs) throws IOException, InterruptedException {
    if (this.session == null || !session.isOpen()) {
      throw new IOException("Session not open");
    }

    synchronized (this) {
      // Create a new latch for this request
      this.responseLatch = new CountDownLatch(1);
      this.lastResponse = null;
      
      // Send the message
      this.session.getBasicRemote().sendText(text);
      
      // Wait for response
      boolean received = responseLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
      
      // Log timeout warning (only first few to avoid spam)
      if (!received) {
        System.err.println("[TIMEOUT] No response after " + timeoutMs + "ms for " + serverUri);
      }
      
      return received ? lastResponse : null;
    }
  }

  public void close() {
    if (session != null) {
      try {
        this.session.close();
      } catch (IOException ignored) {
      }
    }
  }

  @OnOpen
  public void onOpen(Session session) {
    this.session = session;
    this.session.setMaxIdleTimeout(0);
    this.openLatch.countDown();
    this.metrics.incConnectionsCreated();
  }

  @OnMessage
  public void onMessage(String message) {
    // Store the response
    this.lastResponse = message;
    
    // Release any waiting thread
    CountDownLatch latch = this.responseLatch;
    if (latch != null) {
      latch.countDown();
    }
  }

  @OnError
  public void onError(Session session, Throwable throwable) {
    System.err.println(
        "ws error [" + serverUri + "]: " + throwable.getClass().getSimpleName() + ": "
            + throwable.getMessage());
    this.openLatch.countDown();
  }

  @OnClose
  public void onClose(Session session, CloseReason closeReason) {
    this.session = null;
    this.openLatch.countDown();
  }
}
