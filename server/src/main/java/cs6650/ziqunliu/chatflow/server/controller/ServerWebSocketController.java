package cs6650.ziqunliu.chatflow.server.controller;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import java.io.IOException;
import cs6650.ziqunliu.chatflow.server.service.MessageValidationService;
import cs6650.ziqunliu.chatflow.server.model.dto.ChatMessageDTO;
import cs6650.ziqunliu.chatflow.server.model.response.ErrorResponse;
import cs6650.ziqunliu.chatflow.server.model.event.MessageBroadcastEvent;

// Test uri ws://<ec2 public ip>:8080/server/ws/chat/1
@javax.websocket.server.ServerEndpoint("/ws/chat/{roomId}")
public class ServerWebSocketController {

  private static final Map<String, Set<Session>> roomSessions = new ConcurrentHashMap<>();
  private static final Gson GSON = new Gson();
  private static AtomicInteger noOfMessages = new AtomicInteger();

  /**
   * If `roomId` does not exist in map, add to map.
   *
   * @param session WebSocket connection
   * @param roomId  Identifier passed in by endpoint
   * @throws IOException
   */
  @OnOpen
  public void onOpen(Session session, @PathParam("roomId") String roomId) throws IOException {
    Set<Session> sessions = roomSessions.computeIfAbsent(roomId,
        k -> ConcurrentHashMap.newKeySet());
    sessions.add(session);

    // Print onto console
    System.out.println("joined room " + roomId + ", sessionId=" + session.getId());

    // Send message
    RemoteEndpoint.Async asyncRemoteEndpoint = session.getAsyncRemote();
    asyncRemoteEndpoint.sendText("joined room " + roomId + ", sessionId=" + session.getId());
  }

  /**
   * Accept messages from the client. Serialize JSON into dto. Validate message.
   * Assemble broadcast event and broadcast.
   * @param message
   * @param session
   * @param roomId
   * @throws IOException
   */
  @OnMessage
  public void onMessage(String message, Session session, @PathParam("roomId") String roomId) {
    ChatMessageDTO dto;

    // Parse JSON content and handle error.
    try {
      // Serialize JSON from WebSocket connection into the model
      dto = GSON.fromJson(message, ChatMessageDTO.class);
    } catch (JsonParseException e) {
      ErrorResponse error = new ErrorResponse("INVALID_JSON", "JSON has wrong format", roomId);
      error.setServerTimestamp(java.time.Instant.now().toString());
      session.getAsyncRemote().sendText(GSON.toJson(error));
      return;
    }
    // System.out.println("Parsed DTO: " + GSON.toJson(dto));

    // Do validation and handle error
    // validator returns either null or an error message
    String validatorError = MessageValidationService.validate(dto);
    if (validatorError != null) {
      ErrorResponse error = new ErrorResponse("VALIDATION_FAILED", validatorError, roomId);
      error.setServerTimestamp(java.time.Instant.now().toString());
      session.getAsyncRemote().sendText(GSON.toJson(error));
      return;
    }

    MessageBroadcastEvent success = new MessageBroadcastEvent(
        "SUCCESS",
        roomId,
        java.time.Instant.now().toString(),
        dto.getUserId(),
        dto.getUsername(),
        dto.getMessage(),
        dto.getTimestamp(),
        dto.getMessageType().name());

    // Assignment 1: Just echo back to sender, no broadcasting needed
    // Use synchronous send to avoid async buffer overflow
    noOfMessages.incrementAndGet();
    System.out.println("number of mesaages:" + noOfMessages.get());
    String payload = GSON.toJson(success);
    if (session.isOpen()) {
      try {
        session.getBasicRemote().sendText(payload);  // Synchronous send
      } catch (IOException e) {
        System.err.println("Failed to send response: " + e.getMessage());
      }
    }
  }

  /**
   *
   * @param session
   */
  @OnClose
  public void onClose(Session session, @PathParam("roomId") String roomId) {
    Set<Session> sessions = roomSessions.get(roomId);
    if (sessions != null) {
      sessions.remove(session);
      if (sessions.isEmpty()) {
        roomSessions.remove(roomId);
      }
    }
  }

  @OnError
  public void onError(Session session, Throwable error) {
    String id = (session != null) ? session.getId() : "null";
    System.err.println("WS error, sessionId=" + id);
    if (error != null) {
      error.printStackTrace();
    }
  }

  private void message(Session session, String content) {

  }
}
