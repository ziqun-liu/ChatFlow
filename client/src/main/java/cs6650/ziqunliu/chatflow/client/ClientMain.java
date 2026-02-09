package cs6650.ziqunliu.chatflow.client;

import static cs6650.ziqunliu.chatflow.client.model.ChatMessage.POISON;

import cs6650.ziqunliu.chatflow.client.generator.MessageGenerator;
import cs6650.ziqunliu.chatflow.client.model.ChatMessage;
import cs6650.ziqunliu.chatflow.client.metrics.Metrics;
import cs6650.ziqunliu.chatflow.client.worker.SenderWorker;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;

public class ClientMain {

  private static final int WARMUP_THREADS = 32;
  private static final int WARMUP_MSG_PER_THREAD = 1000;

  private static final int TOTAL_MESSAGES = 500_000;
  private static final int QUEUE_CAPACITY = 20_000;

  private static final int NUM_SENDERS = 32;

  public static void main(String[] args) throws Exception {
    runWarmup();
    runMainPhase();
  }

  private static void runWarmup() throws InterruptedException {
    Metrics warmupMetrics = new Metrics();
    CountDownLatch warmupLatch = new CountDownLatch(WARMUP_THREADS);

    warmupMetrics.start();

    for (int i=0; i<WARMUP_THREADS; i++) {
      Thread t = new Thread(() -> {
        try {
          for (int k=0; k<WARMUP_MSG_PER_THREAD; k++) {
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
      System.out.println("WARMUP WARNING: total processed = " + processes + ", expected = " + expected);
    }
  }

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
      System.out.println("MAIN WARNING: total processed = " + processed + ", total expected = " + TOTAL_MESSAGES);
    }
  }


}
