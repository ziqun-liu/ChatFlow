package cs6650.ziqunliu.chatflow.client;

import cs6650.ziqunliu.chatflow.client.model.ChatMessage;
import cs6650.ziqunliu.chatflow.client.metrics.Metrics;
import cs6650.ziqunliu.chatflow.client.model.MessageType;
import cs6650.ziqunliu.chatflow.client.websocket.ClientWebSocketEndpoint;
import cs6650.ziqunliu.chatflow.client.websocket.ConnectionManager;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ClientMain {


  private static final int WARMUP_THREADS = 32;
  private static final int WARMUP_MSG_PER_THREAD = 1000;

  private static final int NUM_SENDERS = 32;

  private static final int POOL_SIZE = 20;
  private static final String WS_URI = "ws://34.229.12.201:8080/server/ws/chat";


  public static void main(String[] args) throws Exception {

    Metrics metrics = new Metrics();
    ConnectionManager manager = new ConnectionManager(WS_URI, POOL_SIZE, metrics);

    manager.connectAll();
    if (!manager.awaitAllOpen(10, TimeUnit.SECONDS)) {
      manager.closeAll();
      throw new RuntimeException("Connections timeout");
    }

    metrics.start();

    // 40 messages
    for (int i = 0; i < 40; i++) {
      int roomId = (i % POOL_SIZE) + 1;

      ChatMessage msg = new ChatMessage(007, "user007", "Message from 007", roomId,
          MessageType.TEXT.name(), Instant.now().toString());

      try {
        manager.sendMessage(msg);
      } catch (IOException e) {
      }
    }

    metrics.stop();

    manager.closeAll();

    System.out.println(metrics.summary(""));

    /* Minimum viable loop
    // Instantiate ClientWebSocketEndpoint
    ClientWebSocketEndpoint endpoint = new ClientWebSocketEndpoint(metrics, wsUri);

    // 1. connect
    endpoint.connect();

    // 2. await onOpen
    endpoint.awaitOpen(1, TimeUnit.SECONDS);

    metrics.start();
    for (int i = 0; i < 5; i++) {
      metrics.incSendAttempts();
      endpoint.sendText(msg.toJson());
      metrics.incSuccess();
      Thread.sleep(200);
    }
    metrics.stop();

    Thread.sleep(500);

    // 4. close
    endpoint.close();

    System.out.println(metrics.summary("MINIMAL LOOP"));
  }

     */

  /*
  // Warmup
  private static void runWarmup() throws InterruptedException {
    Metrics warmupMetrics = new Metrics();
    CountDownLatch warmupLatch = new CountDownLatch(WARMUP_THREADS);

    warmupMetrics.start();

    for (int i = 0; i < WARMUP_THREADS; i++) {
      Thread t = new Thread(() -> {
        try {
          for (int k = 0; k < WARMUP_MSG_PER_THREAD; k++) {
            warmupMetrics.incSendAttempts();
            warmupMetrics.incSuccess();
          }
        } finally {
          warmupLatch.countDown();
        }
      }, "warmup_thread-" + i);

      t.start();
    }  // End for loop

    warmupLatch.await();
    warmupMetrics.stop();
    System.out.println(warmupMetrics.summary("WARMUP"));

    long expected = (long) WARMUP_THREADS * WARMUP_MSG_PER_THREAD;
    long processes = warmupMetrics.getTotalProcessed();
    if (processes != expected) {
      System.out.println(
          "WARMUP WARNING: total processed = " + processes + ", expected = " + expected);
    }
  }

  // Main phase
  private static void runMainPhase() throws InterruptedException {
    Metrics metrics = new Metrics();

    // 1. Queue with bound
    BlockingQueue<ChatMessage> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);

    // 2. Start sender threads
    CountDownLatch doneLatch = new CountDownLatch(NUM_SENDERS);
    Thread[] workers = new Thread[NUM_SENDERS];
    for (int i = 0; i < NUM_SENDERS; i++) {
      workers[i] = new Thread(new SenderWorker(queue, POISON, metrics, doneLatch), "sender-" + i);
      workers[i].start();
    }

    // 3. Start metrics
    metrics.start();

    // 4. Start producer threads
    MessageGenerator generator = new MessageGenerator();
    Thread producer = new Thread(() -> {
      try {
        for (int i = 0; i < TOTAL_MESSAGES; i++) {
          ChatMessage msg = generator.next();
          queue.put(msg);
        }
        // 5. Send poison pill after producer finishes
        for (int i = 0; i < NUM_SENDERS; i++) {
          queue.put(POISON);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }, "producer");

    producer.start();

    // 6. Wait for producer to finish
    producer.join();

    // 7. Wait for all sender threads to exit
    doneLatch.await();

    // 8. stop metrics and output
    metrics.stop();
    System.out.println(metrics.summary("MAIN"));

    // 9. Verify
    long processed = metrics.getTotalProcessed();
    if (processed != TOTAL_MESSAGES) {
      System.out.println(
          "MAIN WARNING: total processed = " + processed + ", total expected = " + TOTAL_MESSAGES);
    }
  }
  */

  }
}
