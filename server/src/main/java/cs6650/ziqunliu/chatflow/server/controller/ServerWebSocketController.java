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
    session.setMaxIdleTimeout(0);  // don't close connections for idle; client sends continuously
    Set<Session> sessions = roomSessions.computeIfAbsent(roomId,
        k -> ConcurrentHashMap.newKeySet());
    sessions.add(session);
    // Log: connection opened (for debug: who connects)
    System.out.println("[WS] open  room=" + roomId + " sessionId=" + session.getId() + " totalInRoom=" + sessions.size());

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
    long n = noOfMessages.incrementAndGet();
    // Log every 50k messages to avoid flood at high throughput
    if (n % 50_000 == 0) {
      System.out.println("[WS] messages total=" + n);
    }
    String payload = GSON.toJson(success);
    try {
      if (session.isOpen()) {
        session.getBasicRemote().sendText(payload);
      }
    } catch (IOException e) {
      // Client often already closed (Broken pipe / Connection reset). Normal at shutdown or under load.
      if (!isClientGone(e)) {
        System.err.println("Failed to send response: " + e.getMessage());
      }
    }
  }

  /** True if error is due to client having closed the connection (do not log full stack). */
  private static boolean isClientGone(Throwable t) {
    if (t == null) return false;
    String msg = t.getMessage();
    if (msg != null && (msg.contains("Broken pipe") || msg.contains("Connection reset") || msg.contains("EOF"))) return true;
    if ("EOFException".equals(t.getClass().getSimpleName())) return true;
    if (t.getCause() != null) return isClientGone(t.getCause());
    return false;
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
      int remaining = sessions.size();
      // Log: connection closed (for debug: who disconnects, client or server side)
      System.out.println("[WS] close room=" + roomId + " sessionId=" + session.getId() + " remainingInRoom=" + remaining);
      if (sessions.isEmpty()) {
        roomSessions.remove(roomId);
      }
    }
  }

  @OnError
  public void onError(Session session, Throwable error) {
    if (error == null) return;
    // Avoid full stack trace for normal "client disconnected" errors (noise in logs)
    if (isClientGone(error)) {
      return; // or: System.err.println("WS client gone, sessionId=" + (session != null ? session.getId() : "null"));
    }
    String id = (session != null) ? session.getId() : "null";
    System.err.println("WS error, sessionId=" + id + ": " + error.getMessage());
    error.printStackTrace();
  }

  private void message(Session session, String content) {

  }
}
