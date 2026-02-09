package cs6650.ziqunliu.chatflow.server.model.event;

import cs6650.ziqunliu.chatflow.server.model.response.SuccessResponse;

public class MessageBroadcastEvent extends SuccessResponse {
  private String userId;
  private String username;
  private String clientTimestamp;
  private String messageType;

  public MessageBroadcastEvent(String status, String roomId, String serverTimestamp,
      String userId, String username, String message, String clientTimestamp, String messageType) {
    super(status, roomId);
    setServerTimestamp(serverTimestamp);
    setMessage(message);
    this.userId = userId;
    this.username = username;
    this.clientTimestamp = clientTimestamp;
    this.messageType = messageType;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getClientTimestamp() {
    return clientTimestamp;
  }

  public void setClientTimestamp(String clientTimestamp) {
    this.clientTimestamp = clientTimestamp;
  }

  public String getMessageType() {
    return messageType;
  }

  public void setMessageType(String messageType) {
    this.messageType = messageType;
  }
}
