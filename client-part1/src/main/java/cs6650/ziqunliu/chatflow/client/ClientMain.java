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
import java.util.concurrent.atomic.AtomicInteger;

public class ClientMain {

  private static final int WARMUP_THREADS = 32;
  private static final int WARMUP_MSG_PER_THREAD = 200;

  private static final int TOTAL_MESSAGES = 500_000;
  private static final int NUM_SENDERS = 120;
  private static final int QUEUE_CAPACITY = 500_000;

  private static final int POOL_SIZE = 5;  // connections per room
  public static final int NUM_ROOMS = 20;
  // Use localhost when server runs locally; use ws://YOUR_SERVER:8080/server/ws/chat for remote
  private static final String WS_URI = "ws://localhost:8080/server/ws/chat";
  private static ConnectionManager[] managers = null;
  private static AtomicInteger numberOfMessages = new AtomicInteger();
  public static void main(String[] args) throws Exception {
    try {
      Metrics metrics = new Metrics();
      metrics.start();
      managers = initManagers(POOL_SIZE, metrics);
    } catch (Exception e) {
      throw new RuntimeException("initManagers failed: " + e.getMessage(), e);
    }
    if (managers != null) {
      // java.util.logging.Logger.getLogger("org.glassfish.tyrus").setLevel(java.util.logging.Level.OFF);
      System.out.println("before warm up");
      runWarmup();
      runMainPhase();
    }
  }

  private static void runWarmup() throws InterruptedException {
    System.out.println("WARMUP start ...");
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

        try {
          // System.out.println("Thread" + threadId);
          managers[roomId - 1].connectAll();
          System.out.println("After connectAll() " + threadId);

          if (!managers[roomId - 1].awaitAllOpen(100, TimeUnit.MILLISECONDS)) {
            System.err.println(
                "warmup awaitAllOpen timeout: thread=" + threadId + ", room=" + roomId);
            return;
          }

          for (int i = 0; i < WARMUP_MSG_PER_THREAD; i++) {
            System.out.println("Thread " + threadId + " message " + i);
            ChatMessage msg = MessageGenerator.next();
            msg = new ChatMessage(msg.getUserId(), msg.getUsername(), msg.getMessage(), roomId,
                msg.getMessageType(), msg.getTimestamp());

            try {
              managers[roomId - 1].sendMessage(msg);
              numberOfMessages.incrementAndGet();
            } catch (IOException ignored) {
              System.err.println(
                  "warmup IO exception: thread=" + threadId + ", room=" + roomId + ", error="
                      + ignored.getClass().getSimpleName() + ": " + ignored.getMessage());
              ignored.printStackTrace();
            }
          }

        } catch (Exception e) {
          System.err.println(
              "warmup exception: thread=" + threadId + ", room=" + roomId + ", error="
                  + e.getClass().getSimpleName() + ": " + e.getMessage());
          e.printStackTrace();

        } finally {
          managers[roomId - 1].closeAll();
          doneLatch.countDown();
        }
      };

      warmupPool.submit(task);
    }

    doneLatch.await();
    warmupMetrics.stop();

    warmupPool.shutdownNow();
    warmupPool.awaitTermination(5, TimeUnit.SECONDS);
    // Close warmup connections so we don't hold 100 extra connections during main phase
    for (int r = 0; r < NUM_ROOMS; r++) {
      if (managers[r] != null) managers[r].closeAll();
    }
    System.out.println("WARMUP done.");
    System.out.println(warmupMetrics.summary("WARMUP"));
    System.out.println("number of messages:" + numberOfMessages.get());
  }

  /**
   *
   * @throws InterruptedException
   */
  private static void runMainPhase() throws InterruptedException {
    Metrics metrics = new Metrics();
    metrics.start();

    // Create rooms and connections
    final ConnectionManager[] managers;
    try {
      managers = initManagers(POOL_SIZE, metrics);
    } catch (Exception e) {
      throw new RuntimeException("initManagers failed: " + e.getMessage(), e);
    }
    System.out.println("1");

    final BlockingQueue<ChatMessage> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);

    ExecutorService senderPool = Executors.newFixedThreadPool(NUM_SENDERS);
    CountDownLatch sendersDoneLatch = new CountDownLatch(NUM_SENDERS);
    System.out.println("2");

    // 1. Start consumer SenderWorkers. senderPool blocks at take() until producer puts messages.
    for (int i = 0; i < NUM_SENDERS; i++) {
      senderPool.submit(new SenderWorker(i, queue, managers, metrics, sendersDoneLatch));
    }
    System.out.println("3");

    // 2. Start the producer. Producer uses single dedicated thread generates all messages.
    Thread producer = new Thread(new Producer(queue, TOTAL_MESSAGES, NUM_SENDERS), "producer");
    producer.start();
    System.out.println("4: Producer started");

    // The main thread waits for producer to finish.
    producer.join();
    System.out.println(
        "4.5: Producer finished, queue size=" + queue.size() + ", waiting for " + NUM_SENDERS
            + " senders...");

    // Waits for senders to exit (no hard cap — let all messages finish)
    boolean finished = sendersDoneLatch.await(600, TimeUnit.SECONDS);

    for (int c = 0; c < managers.length; c++) {
      //System.out.println("cm index:" + c);
      if (managers[c] != null) {
        System.out.println("cm log:" + managers[c].getRoomId() + ":epsessionnull:" +managers[c].epSessionNull.get() + ":epsessionnotopen:" + managers[c].epSessionNotOpen.get())
        ;
      }
    } if (!finished) {
      System.err.println("\n=== ERROR: Senders did not finish within 120 seconds! ===");
      System.err.println("Remaining queue size: " + queue.size());
      System.err.println(metrics.summary("TIMEOUT"));
      System.err.println("Forcing shutdown...");
    }
    System.out.println("5: All senders completed");
    // Close 20 rooms and their connections
    for (int roomId = 1; roomId <= NUM_ROOMS; roomId++) {
      managers[roomId - 1].closeAll();
    }
    System.out.println("6");
    metrics.stop();
    senderPool.shutdownNow();
    senderPool.awaitTermination(5, TimeUnit.SECONDS);

    System.out.println(metrics.summary("MAIN PHASE, NUM_SENDERS=" + NUM_SENDERS));
  }

  /**
   * Create a number of room managers. Connect all and await open. Uses gradual connection to avoid
   * overwhelming the server.
   */
  private static ConnectionManager[] initManagers(Integer poolSize, Metrics metrics)
      throws IOException, InterruptedException {
    ConnectionManager[] managers = new ConnectionManager[NUM_ROOMS];

    System.out.println(
        "Initializing " + NUM_ROOMS + " rooms with " + POOL_SIZE + " connections each (" + (
            NUM_ROOMS * POOL_SIZE) + " total)...");

    // Initialize managers/rooms
    for (int roomId = 1; roomId <= NUM_ROOMS; roomId++) {
      System.out.println("initialazing room id:" + roomId);
      managers[roomId - 1] = new ConnectionManager(WS_URI + "/" + roomId, POOL_SIZE, metrics);
      System.out.println("initialzed manager idx:" + (roomId - 1) + ":" + managers[roomId - 1]);
    }

    // Connect gradually - room by room to reduce server pressure
    for (int roomId = 1; roomId <= NUM_ROOMS; roomId++) {
      long t0 = System.nanoTime();
      System.out.print("Connecting room " + roomId + "/" + NUM_ROOMS + "...");
      managers[roomId - 1].connectAll();

      // Await this room's connections before moving to next
      if (!managers[roomId - 1].awaitAllOpen(10, TimeUnit.SECONDS)) {
        throw new IOException("awaitAllOpen timeout: room=" + roomId);
      }
      long ms = (System.nanoTime() - t0) / 1_000_000;
      System.out.println(
          " ✓ (" + (roomId * POOL_SIZE) + "/" + (NUM_ROOMS * POOL_SIZE) + " total, " + ms + "ms)");

      // Small delay between rooms to reduce server pressure
      if (roomId < NUM_ROOMS) {
        Thread.sleep(50);  // 50ms delay between rooms
      }
    }

    System.out.println("All " + (NUM_ROOMS * POOL_SIZE) + " connections established successfully!");
    return managers;
  }

}
