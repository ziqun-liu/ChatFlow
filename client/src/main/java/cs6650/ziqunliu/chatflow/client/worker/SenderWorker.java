package cs6650.ziqunliu.chatflow.client.worker;

import cs6650.ziqunliu.chatflow.client.metrics.Metrics;
import cs6650.ziqunliu.chatflow.client.model.ChatMessage;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;

public class SenderWorker implements Runnable {
  private final BlockingQueue<ChatMessage> queue;
  private final ChatMessage poisonPill;
  private final Metrics metrics;
  private final CountDownLatch doneLatch;

  public SenderWorker(BlockingQueue<ChatMessage> queue, ChatMessage poisonPill, Metrics metrics, CountDownLatch doneLatch) {
    this.queue = queue;
    this.poisonPill = poisonPill;
    this.metrics = metrics;
    this.doneLatch = doneLatch;
  }


  @Override
  public void run() {
    try {
      while (true) {
        ChatMessage msg = queue.take();  // Block and wait for messages
        if (msg == poisonPill) {  // End signal
          break;
        }

        metrics.incSendAttempts();
        fakeSend(msg);
        metrics.incSuccess();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      // Being interrupted is also exit
    } finally {
      doneLatch.countDown();
    }
  }

  private void fakeSend(ChatMessage msg) {

  }
}
