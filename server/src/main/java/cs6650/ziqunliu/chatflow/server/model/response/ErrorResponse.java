package cs6650.ziqunliu.chatflow.server.model.response;

public class ErrorResponse {

  private String status;
  private String roomId;
  private String errorCode;
  private String message;
  private String serverTimestamp;

  public ErrorResponse(String errorCode, String message, String roomId) {
    this.status = "ERROR";
    this.errorCode = errorCode;
    this.message = message;
    this.roomId = roomId;
  }

  public String getRoomId() {
    return roomId;
  }

  public void setRoomId(String roomId) {
    this.roomId = roomId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public void setErrorCode(String errorCode) {
    this.errorCode = errorCode;
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
}
