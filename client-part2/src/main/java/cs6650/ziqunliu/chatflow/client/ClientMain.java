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
  private static final int WARMUP_CONNECT_RETRIES = 3;
  private static final long WARMUP_RETRY_DELAY_MS = 1000;
  private static final long WARMUP_STAGGER_DELAY_MS = 150;

  private static final int TOTAL_MESSAGES = 500_000;
  private static final int NUM_SENDERS = 80;  // Reduce concurrency to test
  private static final int QUEUE_CAPACITY = 500_000;

  private static final int POOL_SIZE = 1;  // connections per room
  public static final int NUM_ROOMS = 20;
  private static final String WS_URI = "ws://54.148.180.35:8080/server/ws/chat";

  public static void main(String[] args) throws Exception {
    java.util.logging.Logger.getLogger("org.glassfish.tyrus").setLevel(java.util.logging.Level.OFF);
    runWarmup();
    runMainPhase();
  }

  private static void runWarmup() throws InterruptedException {
    System.out.println("WARMUP start ...");
    Metrics warmupMetrics = new Metrics();

    ExecutorService warmupPool = Executors.newFixedThreadPool(WARMUP_THREADS);
    CountDownLatch doneLatch = new CountDownLatch(WARMUP_THREADS);

    warmupMetrics.start();

    // 错峰启动线程：每隔 150ms 启动一个
    for (int t = 0; t < WARMUP_THREADS; t++) {
      final int threadId = t;

      Runnable task = () -> {
        int roomId = (threadId % NUM_ROOMS) + 1;
        ConnectionManager manager = new ConnectionManager(WS_URI + "/" + roomId, 1, warmupMetrics);

        // 连接重试：最多 3 次
        boolean connected = false;
        for (int attempt = 1; attempt <= WARMUP_CONNECT_RETRIES; attempt++) {
          try {
            System.out.println("Warmup thread-" + threadId + " connecting to room " + roomId 
                + " (attempt " + attempt + "/" + WARMUP_CONNECT_RETRIES + ")");
            
            // 尝试连接
            manager.connectAll();
            
            // 等待连接打开
            if (!manager.awaitAllOpen(10, TimeUnit.SECONDS)) {
              throw new IOException("awaitAllOpen timeout");
            }
            
            // 连接成功
            System.out.println("Warmup thread-" + threadId + " connected successfully to room " + roomId);
            connected = true;
            break;  // 跳出重试循环
            
          } catch (IOException | InterruptedException e) {
            // 连接失败
            System.err.println(
                "Warmup thread-" + threadId + " connection failed (attempt " + attempt + "/" + WARMUP_CONNECT_RETRIES + "): " 
                + e.getClass().getSimpleName() + ": " + e.getMessage()
            );
            
            // 关闭所有连接
            manager.closeAll();
            
            // 如果还有重试机会，等待后重试
            if (attempt < WARMUP_CONNECT_RETRIES) {
              try {
                System.out.println("Warmup thread-" + threadId + " waiting " + WARMUP_RETRY_DELAY_MS + "ms before retry...");
                Thread.sleep(WARMUP_RETRY_DELAY_MS);
              } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
              }
            }
          }
        }

        // 检查是否连接成功
        if (!connected) {
          System.err.println("Warmup thread-" + threadId + " failed after " + WARMUP_CONNECT_RETRIES + " attempts, skipping warmup messages");
          doneLatch.countDown();
          return;
        }

        // 发送 warmup 消息
        try {
          for (int i = 0; i < WARMUP_MSG_PER_THREAD; i++) {
            ChatMessage msg = MessageGenerator.next();
            msg = new ChatMessage(
                msg.getUserId(), 
                msg.getUsername(), 
                msg.getMessage(), 
                roomId,
                msg.getMessageType(), 
                msg.getTimestamp()
            );

            try {
              manager.sendMessage(msg);
            } catch (IOException ignored) {
            }
          }
        } catch (Exception e) {
          System.err.println(
              "Warmup thread-" + threadId + " error during message sending: "
              + e.getClass().getSimpleName() + ": " + e.getMessage()
          );
        } finally {
          manager.closeAll();
          doneLatch.countDown();
        }
      };

      warmupPool.submit(task);
      
      // 错峰启动：每隔 150ms 启动下一个线程
      if (t < WARMUP_THREADS - 1) {
        Thread.sleep(WARMUP_STAGGER_DELAY_MS);
      }
    }

    doneLatch.await();
    warmupMetrics.stop();

    warmupPool.shutdownNow();
    warmupPool.awaitTermination(5, TimeUnit.SECONDS);
    
    System.out.println("WARMUP done.");
    System.out.println(warmupMetrics.summary("WARMUP"));
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

    // Waits for senders to exit (with timeout)
    boolean finished = sendersDoneLatch.await(120, TimeUnit.SECONDS);
    if (!finished) {
      System.err.println("\n=== ERROR: Senders did not finish within 120 seconds! ===");
      System.err.println("Remaining queue size: " + queue.size());
      System.err.println(metrics.summary("TIMEOUT"));
      System.err.println("Forcing shutdown...");
    }
    System.out.println("5: All senders completed");
    // Close 20 rooms and their connections
    for (int roomId = 1; roomId <= NUM_ROOMS; roomId++) {
      managers[roomId].closeAll();
    }
    System.out.println("6");
    metrics.stop();
    senderPool.shutdownNow();
    senderPool.awaitTermination(5, TimeUnit.SECONDS);

    System.out.println(metrics.summary("MAIN PHASE"));
  }

  /**
   * Create a number of room managers. Connect all and await open. Uses gradual connection to avoid
   * overwhelming the server.
   */
  private static ConnectionManager[] initManagers(Integer poolSize, Metrics metrics)
      throws IOException, InterruptedException {
    ConnectionManager[] managers = new ConnectionManager[NUM_ROOMS + 1];

    System.out.println(
        "Initializing " + NUM_ROOMS + " rooms with " + POOL_SIZE + " connections each (" + (
            NUM_ROOMS * POOL_SIZE) + " total)...");

    // Initialize managers/rooms
    for (int roomId = 1; roomId <= NUM_ROOMS; roomId++) {
      managers[roomId] = new ConnectionManager(WS_URI + "/" + roomId, POOL_SIZE, metrics);
    }

    // Connect gradually - room by room to reduce server pressure
    for (int roomId = 1; roomId <= NUM_ROOMS; roomId++) {
      System.out.print("Connecting room " + roomId + "/" + NUM_ROOMS + "...");
      managers[roomId].connectAll();

      // Await this room's connections before moving to next
      if (!managers[roomId].awaitAllOpen(10, TimeUnit.SECONDS)) {
        throw new IOException("awaitAllOpen timeout: room=" + roomId);
      }
      System.out.println(
          " ✓ (" + (roomId * POOL_SIZE) + "/" + (NUM_ROOMS * POOL_SIZE) + " total connections)");

      // Small delay between rooms to reduce server pressure
      if (roomId < NUM_ROOMS) {
        Thread.sleep(50);  // 50ms delay between rooms
      }
    }

    System.out.println("All " + (NUM_ROOMS * POOL_SIZE) + " connections established successfully!");
    return managers;
  }

}
