package cs6650.ziqunliu.chatflow.client.websocket;

import cs6650.ziqunliu.chatflow.client.metrics.Metrics;
import java.io.IOException;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.websocket.ClientEndpoint;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;


/**
 * Each instance represents a WebSocket session. A session is a room.
 */
@ClientEndpoint
public class ClientWebSocketEndpoint {

  public Metrics metrics;
  public URI serverUri;
  public volatile Session session;
  public CountDownLatch openLatch = new CountDownLatch(1);

  private final Object connectLock = new Object();

  public ClientWebSocketEndpoint(Metrics metrics, URI serverUri) {
    this.metrics = metrics;
    this.serverUri = serverUri;
  }

  public void connect() throws IOException {
    // Use lock to ensure only one thread is connecting at one moment
    synchronized (connectLock) {
      // Every reconnection instantiates a new CountDownLatch
      this.openLatch = new CountDownLatch(1);

      WebSocketContainer container = ContainerProvider.getWebSocketContainer();
      try {
        container.connectToServer(this, serverUri);
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
    this.openLatch.countDown();
    this.metrics.incConnectionsCreated();
  }

  @OnMessage
  public void onMessage(String message) {
    // System.out.println("recv: " + message);
  }

  @OnError
  public void onError(Session session, Throwable throwable) {
  }
}
