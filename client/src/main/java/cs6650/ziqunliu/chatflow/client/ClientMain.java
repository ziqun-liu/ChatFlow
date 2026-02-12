package cs6650.ziqunliu.chatflow.client;

import cs6650.ziqunliu.chatflow.client.generator.MessageGenerator;
import cs6650.ziqunliu.chatflow.client.model.ChatMessage;
import cs6650.ziqunliu.chatflow.client.metrics.Metrics;
import cs6650.ziqunliu.chatflow.client.model.MessageType;
import cs6650.ziqunliu.chatflow.client.websocket.ClientWebSocketEndpoint;
import cs6650.ziqunliu.chatflow.client.websocket.ConnectionManager;
import cs6650.ziqunliu.chatflow.client.worker.Producer;
import cs6650.ziqunliu.chatflow.client.worker.SenderWorker;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.time.chrono.MinguoEra;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ClientMain {

  private static final int WARMUP_THREADS = 32;
  private static final int WARMUP_MSG_PER_THREAD = 1000;

  private static final int TOTAL_MESSAGES = 500_000;
  private static final int NUM_SENDERS = 32;
  private static final int QUEUE_CAPACITY = 20_000;

  private static final int POOL_SIZE = 20;  // number of connection sessions
  public static final int NUM_ROOMS = 20;
  private static final String WS_URI = "ws://3.84.125.88:8080/server/ws/chat";

  public static void main(String[] args) throws Exception {
    // java.util.logging.Logger.getLogger("org.glassfish.tyrus").setLevel(java.util.logging.Level.OFF);
    runWarmup();
    runMainPhase();
  }

  private static void runWarmup() throws InterruptedException {

    Metrics warmupMetrics = new Metrics();

    // Create a 32-thread thread pool
    ExecutorService warmupPool = Executors.newFixedThreadPool(WARMUP_THREADS);
    CountDownLatch doneLatch = new CountDownLatch(WARMUP_THREADS);

    warmupMetrics.start();

    // For each thread, submit a task to queue for execute
    for (int t = 0; t < WARMUP_THREADS; t++) {

      final int threadId = t;

      Runnable task = () -> {
        int roomId = (threadId % NUM_ROOMS) + 1;  // Route 32 threads to 20 rooms

        // Each thread establishes one WebSocket connection
        ConnectionManager manager = new ConnectionManager(WS_URI + "/" + roomId, 1, warmupMetrics);

        try {
          manager.connectAll();
          if (!manager.awaitAllOpen(10, TimeUnit.SECONDS)) {
            System.err.println(
                "warmup awaitAllOpen timeout: thread=" + threadId + ", room=" + roomId);
            return;
          }

          for (int i = 0; i < WARMUP_MSG_PER_THREAD; i++) {
            ChatMessage msg = MessageGenerator.next();
            msg = new ChatMessage(msg.getUserId(), msg.getUsername(), msg.getMessage(), roomId,
                msg.getMessageType(), msg.getTimestamp());

            try {
              manager.sendMessage(msg);
            } catch (IOException ignored) {
            }
          }

        } catch (Exception e) {
          System.err.println(
              "warmup exception: thread=" + threadId + ", room=" + roomId + ", error="
                  + e.getClass().getSimpleName() + ": " + e.getMessage());

        } finally {
          manager.closeAll();
          doneLatch.countDown();
        }
      };

      warmupPool.submit(task);
    }

    doneLatch.await();
    warmupMetrics.stop();

    warmupPool.shutdownNow();
    warmupPool.awaitTermination(5, TimeUnit.SECONDS);
    System.out.println(warmupMetrics.summary("WARMUP"));
  }

  private static void runMainPhase() throws InterruptedException {
    Metrics metrics = new Metrics();
    metrics.start();

    // Create rooms and connections
    final ConnectionManager[] managers;
    try {
      managers = initManagers(metrics);
    } catch (Exception e) {
      throw new RuntimeException("initManagers failed: " + e.getMessage(), e);
    }

    final BlockingQueue<ChatMessage> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);

    ExecutorService senderPool = Executors.newFixedThreadPool(NUM_SENDERS);
    CountDownLatch sendersDoneLatch = new CountDownLatch(NUM_SENDERS);

    // 1. Start consumer SenderWorkers. senderPool blocks at take() until producer puts messages.
    for (int i = 0; i < NUM_SENDERS; i++) {
      senderPool.submit(new SenderWorker(i, queue, managers, metrics, sendersDoneLatch));
    }

    // 2. Start the producer. Producer uses single dedicated thread generates all messages.
    Thread producer = new Thread(new Producer(queue, TOTAL_MESSAGES, NUM_SENDERS), "producer");
    producer.start();

    // The main thread waits for producer to finish.
    producer.join();
    // Waits for senders to exit
    sendersDoneLatch.await();

    // Close 20 rooms and their connections
    for (int roomId = 1; roomId <= NUM_ROOMS; roomId++) {
      managers[roomId].closeAll();
    }

    metrics.stop();
    senderPool.shutdownNow();
    senderPool.awaitTermination(5, TimeUnit.SECONDS);

    System.out.println(metrics.summary("MAIN PHASE"));
  }

  /**
   * Create a number of room managers. Connect all and await open.
   */
  private static ConnectionManager[] initManagers(Metrics metrics)
      throws IOException, InterruptedException {
    ConnectionManager[] managers = new ConnectionManager[NUM_ROOMS + 1];

    // Initialize managers/rooms
    for (int roomId = 1; roomId <= NUM_ROOMS; roomId++) {
      managers[roomId] = new ConnectionManager(WS_URI + "/" + roomId, POOL_SIZE, metrics);
    }

    // Connect
    for (int roomId = 1; roomId <= NUM_ROOMS; roomId++) {
      managers[roomId].connectAll();
    }

    // Await open
    for (int roomId = 1; roomId <= NUM_ROOMS; roomId++) {
      if (!managers[roomId].awaitAllOpen(10, TimeUnit.SECONDS)) {
        throw new IOException("awaitAllOpen timeout: room=" + roomId);
      }
    }

    return managers;
  }

}
