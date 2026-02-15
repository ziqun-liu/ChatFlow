package cs6650.ziqunliu.chatflow.client.worker;

import cs6650.ziqunliu.chatflow.client.generator.MessageGenerator;
import cs6650.ziqunliu.chatflow.client.model.ChatMessage;
import cs6650.ziqunliu.chatflow.client.model.MessageType;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;

public class Producer implements Runnable {

  private final BlockingQueue<ChatMessage> queue;
  private final int total;
  private final int numSenders;

  private static final ChatMessage POISON = new ChatMessage(0, "poison", "poison", -1,
      MessageType.TEXT.name(), Instant.EPOCH.toString());

  public Producer(BlockingQueue<ChatMessage> queue, int total, int numSenders) {
    this.queue = queue;
    this.total = total;
    this.numSenders = numSenders;
  }

  @Override
  public void run() {
    try {
      System.out.println("Producer: Generating " + total + " messages...");
      long startTime = System.nanoTime();
      
      for (int i = 0; i < total; i++) {
        ChatMessage msg = MessageGenerator.next();
        queue.put(msg);
        
        // Progress logging
        if ((i + 1) % 100000 == 0) {
          System.out.println("Producer: Generated " + (i + 1) + "/" + total + " messages");
        }
      }
      
      System.out.println("Producer: Finished generating messages. Sending " + numSenders + " POISON messages...");
      for (int i = 0; i < numSenders; i++) {
        queue.put(POISON);
      }
      System.out.println("Producer: All done, exiting.");
      
    } catch (InterruptedException e) {
      System.err.println("Producer interrupted!");
      Thread.currentThread().interrupt();
    }
  }

}