package cs6650.ziqunliu.chatflow.client.model;

/**
 * Message model of client, represents payload of client. Serialized into JSON and sent to
 * WebSocket
 */
public class ChatMessage {

  public final int userId;
  public final String username;
  public final String message;
  public final int roomId;
  public final String messageType;
  public final String timestamp;

  private ChatMessage() {
    userId = -1;
    username = null;
    message = null;
    roomId = -1;
    messageType = "POISON";
    timestamp = null;
  }

  public ChatMessage(int userId, String username, String message, int roomId, String messageType,
      String timestamp) {
    this.userId = userId;
    this.username = username;
    this.message = message;
    this.roomId = roomId;
    this.messageType = messageType;
    this.timestamp = timestamp;
  }


  public int getUserId() {
    return userId;
  }

  public String getUsername() {
    return username;
  }

  public String getMessage() {
    return message;
  }

  public int getRoomId() {
    return roomId;
  }

  public String getMessageType() {
    return messageType;
  }

  public String getTimestamp() {
    return timestamp;
  }
  public String toJson() {
    return "{" + "\"userId\":" + userId + "," + "\"username\":\"" + escape(username) + "\","
        + "\"message\":\"" + escape(message) + "\"," + "\"roomId\":" + roomId + ","
        + "\"messageType\":\"" + escape(messageType) + "\"," + "\"timestamp\":\"" + escape(
        timestamp) + "\"" + "}";
  }

  private static String escape(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  public static final ChatMessage POISON = new ChatMessage();
}