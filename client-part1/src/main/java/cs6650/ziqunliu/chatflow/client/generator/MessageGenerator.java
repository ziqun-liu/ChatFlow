package cs6650.ziqunliu.chatflow.client.generator;

import cs6650.ziqunliu.chatflow.client.model.ChatMessage;
import cs6650.ziqunliu.chatflow.client.model.MessageType;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

public class MessageGenerator {

  private static final String[] POOL = new String[]{"Hello!", "How are you?", "Anyone here?",
      "Nice to meet you.", "Good morning!", "Good night!", "What's up?", "LOL", "Sounds good.",
      "I agree.", "Let's go.", "See you later.", "Great idea.", "Thanks!", "You're welcome.",
      "No problem.", "Interesting.", "Cool!", "Awesome!", "Nice.", "Where are you from?",
      "I'm studying CS.", "Working on distributed systems.", "This is fun.", "Test message.",
      "Random chat.", "Checking in.", "Join the room.", "Leaving soon.", "Back in a minute.",
      "Any updates?", "Let's debug.", "It works.", "It fails sometimes.", "Retrying...",
      "Message queue.", "Thread pool.", "WebSocket client.", "Performance test.",
      "Throughput matters.", "Warmup phase.", "Main phase.", "Connection dropped.", "Reconnected.",
      "All good.", "Room is busy.", "Room is quiet.", "Ping.", "Pong.", "Done."};

  private static final int MAX_USER_ID = 100_000;
  private static final int MAX_ROOM_ID = 20;

  public static ChatMessage next() {
    ThreadLocalRandom r = ThreadLocalRandom.current();

    int userId = r.nextInt(1, MAX_USER_ID + 1);
    String username = "user" + userId;
    int roomId = r.nextInt(1, MAX_ROOM_ID + 1);
    String message = POOL[r.nextInt(POOL.length)];
    MessageType type = pickType(r);
    String timestamp = Instant.now().toString();

    return new ChatMessage(userId, username, message, roomId, type.name(), timestamp);
  }

  /**
   * 90% TEXT, 5% JOIN, 5% LEAVE
   *
   * @param r ThreadLocalRandom object
   * @return ENUM MessageType
   */
  private static MessageType pickType(ThreadLocalRandom r) {
    int p = r.nextInt(100);
    if (p < 90) {
      return MessageType.TEXT;
    }
    if (p < 95) {
      return MessageType.JOIN;
    }
    return MessageType.LEAVE;
  }
}
