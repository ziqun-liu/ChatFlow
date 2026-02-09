package cs6650.ziqunliu.chatflow.server.service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import cs6650.ziqunliu.chatflow.server.model.dto.ChatMessageDTO;
import cs6650.ziqunliu.chatflow.server.model.MessageType;

public class MessageValidationService {

  public static String validate(ChatMessageDTO jsonMessage) {

    if (jsonMessage == null) {
      return "Incoming message is null.";
    }

    // Validate userId: must be between 1 and 100_000
    String userIdStr = jsonMessage.getUserId();
    if (userIdStr == null) {
      return "userId missing";
    }
    try {
      long userId = Long.parseLong(userIdStr);
      if (userId < 1 || userId > 100000) {
        return "userId must be between 1 and 100,000";
      }
    } catch (NumberFormatException e) {
      return "userId invalid";  // Can be wrong format or long overflow
    }

    // Validate username: must be between 3 and 20 char's
    String username = jsonMessage.getUsername();
    if (username == null) {
      return "username missing";
    }
    if (username.length() < 3 || username.length() > 20) {
      return "username must be between 3 and 20";
    }
    for (int i = 0; i < username.length(); i++) {
      char ch = username.charAt(i);
      if (!isAlphanumeric(ch)) {
        return "username invalid";
      }
    }

    // Validate message: must be between 1 and 500
    String message = jsonMessage.getMessage();
    if (message == null) {
      return "message missing";
    }
    if (message.length() < 1 || message.length() > 500) {
      return "message must be between 1 and 500";
    }

    // Validate timestmap: must be ISO-8601
    String timestamp = jsonMessage.getTimestamp();
    if (timestamp == null)
      return "Timestamp missing";
    try {
      Instant.parse(timestamp);
    } catch (DateTimeParseException e) {
      return "Timestamp invalid";
    }

    // Validate messageType: must be one of {TEXT, JOIN, LEAVE}
    MessageType messageType = jsonMessage.getMessageType();
    if (messageType == null)
      return "messageType invalid";

    return null;
  }

  private static boolean isAlphanumeric(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
  }

}
