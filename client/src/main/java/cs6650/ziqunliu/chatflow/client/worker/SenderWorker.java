package cs6650.ziqunliu.chatflow.client.worker;

import static cs6650.ziqunliu.chatflow.client.ClientMain.NUM_ROOMS;

import cs6650.ziqunliu.chatflow.client.metrics.Metrics;
import cs6650.ziqunliu.chatflow.client.model.ChatMessage;
import cs6650.ziqunliu.chatflow.client.websocket.ConnectionManager;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;

public class SenderWorker implements Runnable {

  private final int workerId;
  private final BlockingQueue<ChatMessage> queue;
  private final ConnectionManager[] managers;
  private final Metrics metrics;
  private final CountDownLatch doneLatch;

  public SenderWorker(int workerId, BlockingQueue<ChatMessage> queue, ConnectionManager[] managers,
      Metrics metrics, CountDownLatch doneLatch) {
    this.workerId = workerId;
    this.queue = queue;
    this.managers = managers;
    this.metrics = metrics;
    this.doneLatch = doneLatch;
  }

  @Override
  public void run() {
    try {
      while (true) {
        ChatMessage msg = queue.take();  // Block and wait for messages
        if (msg.getRoomId() == -1) {
          return; // poison
        }

        int roomId = msg.getRoomId();
        if (roomId < 1 || roomId > NUM_ROOMS) {
          metrics.incFail();
          continue;
        }

        try {
          managers[roomId].sendMessage(msg);
        } catch (IOException ignored) {
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      doneLatch.countDown();
    }
  }

}
