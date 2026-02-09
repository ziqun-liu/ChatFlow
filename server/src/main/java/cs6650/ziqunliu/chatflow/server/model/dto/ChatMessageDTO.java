package cs6650.ziqunliu.chatflow.server.model.dto;

import cs6650.ziqunliu.chatflow.server.model.MessageType;
import java.util.Objects;

public class ChatMessageDTO {
  private String userId;
  private String username;
  private String message;
  private String timestamp;
  private MessageType messageType;

  public ChatMessageDTO() {
  }

  public ChatMessageDTO(String userId, String username, String message, String  timestamp,
      MessageType messageType) {
    this.userId = userId;
    this.username = username;
    this.message = message;
    this.timestamp = timestamp;
    this.messageType = messageType;
  }

  public String getUserId() {
    return userId;
  }

  public String getUsername() {
    return username;
  }

  public String getMessage() {
    return message;
  }

  public String getTimestamp() {
    return timestamp;
  }

  public MessageType getMessageType() {
    return messageType;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public void setTimestamp(String timestamp) {
    this.timestamp = timestamp;
  }

  public void setMessageType(MessageType messageType) {
    this.messageType = messageType;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ChatMessageDTO that = (ChatMessageDTO) o;
    return Objects.equals(userId, that.userId) && Objects.equals(username,
        that.username) && Objects.equals(message, that.message) && Objects.equals(
        timestamp, that.timestamp) && Objects.equals(messageType, that.messageType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(userId, username, message, timestamp, messageType);
  }

  @Override
  public String toString() {
    return "ChatMessageDTO{" +
        "userId='" + userId + '\'' +
        ", userName='" + username + '\'' +
        ", message='" + message + '\'' +
        ", timestamp=" + timestamp +
        ", messageType=" + messageType +
        '}';
  }
}
