package cs6650.ziqunliu.chatflow.client.websocket;

import cs6650.ziqunliu.chatflow.client.metrics.Metrics;
import java.io.IOException;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
  public AtomicBoolean isConnected = new AtomicBoolean(false);


  public ClientWebSocketEndpoint(Metrics metrics, URI serverUri) {
    this.metrics = metrics;
    this.serverUri = serverUri;
  }

  public void connect() throws IOException {
    synchronized (connectLock) {
      close();  // close any existing session so we don't leave orphaned connections
      this.session = null;
      this.isConnected.set(false);
      this.openLatch = new CountDownLatch(1);

      try {
        SHARED_CONTAINER.connectToServer(this, serverUri);
        this.isConnected.set(true);
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
    // System.out.println(text);
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
    // System.out.println("recv: " + message);
  }

  @OnError
  public void onError(Session session, Throwable throwable) {
    if (throwable == null) return;
    String msg = throwable.getMessage();
    if (msg != null && (msg.contains("Broken pipe") || msg.contains("Connection reset") || msg.contains("EOF"))) return;
    if ("EOFException".equals(throwable.getClass().getSimpleName())) return;
    if (throwable.getCause() != null && (throwable.getCause().getMessage() != null && throwable.getCause().getMessage().contains("Broken pipe"))) return;
    System.err.println("ws error [" + serverUri + "]: " + throwable.getClass().getSimpleName() + ": " + msg);
  }

  @OnClose
  public void onClose(Session session, CloseReason closeReason) {
    if (this.session == session) {
      this.session = null;
      this.isConnected.set(false);
    }
    // else: stale callback (old session closed after we already reconnected), ignore
  }
}
