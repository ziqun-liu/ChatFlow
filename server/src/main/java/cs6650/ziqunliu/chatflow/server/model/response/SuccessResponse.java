package cs6650.ziqunliu.chatflow.server.model.response;

public class SuccessResponse {

  private String status;
  private String roomId;
  private String message;
  private String serverTimestamp;

  public SuccessResponse(String status, String roomId) {
    this.status = status;
    this.roomId = roomId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getRoomId() {
    return roomId;
  }

  public void setRoomId(String roomId) {
    this.roomId = roomId;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public String getServerTimestamp() {
    return serverTimestamp;
  }

  public void setServerTimestamp(String serverTimestamp) {
    this.serverTimestamp = serverTimestamp;
  }

  // Keep for backward compatibility with current call site.
  public void setServerTimestmap(String serverTimestamp) {
    this.serverTimestamp = serverTimestamp;
  }
}
