# ChatFlow Design Document

## 1. System Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         Load Test Client                         │
│  ┌────────────┐    ┌──────────────────────────────────────┐    │
│  │  Producer  │───▶│      BlockingQueue (500K cap)        │    │
│  │  Thread    │    └──────────────────────────────────────┘    │
│  └────────────┘              │                                   │
│                               ▼                                  │
│         ┌──────────────────────────────────────────┐            │
│         │    40 SenderWorker Threads (Consumers)   │            │
│         └──────────────────────────────────────────┘            │
│                     │    │    │    │                             │
└─────────────────────┼────┼────┼────┼─────────────────────────────┘
                      │    │    │    │
                   WebSocket Connections (40 total)
                      │    │    │    │
┌─────────────────────┼────┼────┼────┼─────────────────────────────┐
│                     ▼    ▼    ▼    ▼                             │
│              ┌─────────────────────────┐                          │
│              │   Tomcat 9 Container    │                          │
│              │  (Multi-threaded Pool)  │                          │
│              └─────────────────────────┘                          │
│                          │                                        │
│         ┌────────────────┼────────────────┐                      │
│         ▼                ▼                ▼                       │
│    Room 1-20        Room 1-20        Room 1-20                   │
│  (2 conn/room)    (2 conn/room)    (2 conn/room)                │
│                                                                   │
│  ServerWebSocketController (@ServerEndpoint)                     │
│         │                                                         │
│         ├─ MessageValidator (validation logic)                   │
│         ├─ RequestStatsService (metrics tracking)                │
│         └─ ConcurrentHashMap<roomId, Set<Session>>               │
│                                                                   │
└───────────────────────────────────────────────────────────────────┘
```

### Connection Topology
- **20 chat rooms**: Each room identified by roomId (1-20)
- **2 connections per room**: Total 40 persistent WebSocket connections
- **Round-robin distribution**: SenderWorkers use modulo routing to balance load

## 2. Major Classes and Relationships

### Server Components

```
ServerWebSocketController
├── @ServerEndpoint("/ws/chat/{roomId}")
├── ConcurrentHashMap<Integer, Set<Session>> rooms
├── MessageValidationService validator
└── RequestStatsService statsService

Lifecycle:
  @OnOpen    → Add session to room
  @OnMessage → Validate → Broadcast to room
  @OnClose   → Remove session from room
  @OnError   → Log and cleanup
```

**Key Classes**:
- `ServerWebSocketController`: Main WebSocket endpoint, manages room sessions
- `MessageValidationService`: Validates userId, roomId, timestamp, message format
- `RequestStatsService`: Thread-safe metrics using `AtomicLong`
- `ChatMessageDTO`: Data transfer object for client messages
- `SuccessResponse/ErrorResponse`: Standardized JSON responses

### Client Components

```
ClientMain (orchestrator)
  │
  ├─ Warmup Phase
  │   └─ 32 threads × 1 connection × 1000 msgs
  │
  └─ Main Phase
      ├─ Producer (1 thread)
      │   └─ Generates 500K messages → BlockingQueue
      │
      ├─ SenderWorkers (40 threads)
      │   └─ Consume from queue → Send via ConnectionManager
      │
      └─ ConnectionManager[] (20 instances, 1 per room)
          └─ ClientWebSocketEndpoint[] (pool of 2 per room)
              └─ Session (javax.websocket)
```

**Key Classes**:
- `ClientMain`: Phase orchestration, connection initialization
- `ConnectionManager`: Connection pool per room, retry logic with exponential backoff
- `ClientWebSocketEndpoint`: WebSocket session wrapper, response handling
- `Producer`: Single-threaded message generator
- `SenderWorker`: Consumer thread, sends messages with retries
- `Metrics`: Thread-safe statistics using `AtomicLong` and `ConcurrentLinkedQueue`
- `MessageGenerator`: Random message/user/room generation

## 3. Threading Model

### Server (Tomcat Container)
```
Tomcat Thread Pool (default ~200 threads)
  ├─ Acceptor Thread: Accepts incoming connections
  └─ Worker Threads: Handle WebSocket messages
      └─ One thread per message (non-blocking I/O)
```

**Concurrency Safety**:
- `ConcurrentHashMap` for room sessions (thread-safe reads/writes)
- `Collections.synchronizedSet()` for session sets per room
- `AtomicLong` counters in RequestStatsService
- Stateless message validation (no shared mutable state)

### Client

#### Warmup Phase
```
ExecutorService (32 fixed threads)
  └─ Each thread:
      1. Creates 1 WebSocket connection to assigned room
      2. Sends 1000 messages
      3. Closes connection
      4. CountDownLatch.countDown()
```

#### Main Phase
```
┌─ Producer Thread (1)
│   └─ Generate 500K msgs → BlockingQueue.put()
│
├─ ExecutorService (40 fixed threads)
│   └─ SenderWorker threads (40)
│       └─ BlockingQueue.take() → ConnectionManager.sendMessage()
│           └─ Round-robin across 2 connections per room
│               └─ Retry with exponential backoff (5 attempts)
│
└─ Main Thread
    └─ Wait for Producer + all SenderWorkers to finish
```

**Synchronization Mechanisms**:
- `BlockingQueue`: Producer-consumer coordination
- `CountDownLatch`: Phase completion signaling
- `AtomicInteger`: Round-robin connection selection
- `synchronized` blocks: WebSocket send operations
- `CountDownLatch` per endpoint: Connection readiness

## 4. WebSocket Connection Management

### Initialization Strategy: Gradual Connection
```java
for (int roomId = 1; roomId <= 20; roomId++) {
    managers[roomId].connectAll();  // 2 connections
    managers[roomId].awaitAllOpen(10s timeout);
    Thread.sleep(50ms);  // Reduce server pressure
}
```

**Rationale**: Sequential room connection prevents overwhelming server with 40 simultaneous handshakes.

### Connection Pool Per Room
```
ConnectionManager (roomId=5)
  ├─ ClientWebSocketEndpoint[0] → ws://server:8080/server/ws/chat/5
  └─ ClientWebSocketEndpoint[1] → ws://server:8080/server/ws/chat/5
      
Round-robin selection: index = (counter++) % poolSize
```

### Retry Logic with Exponential Backoff
```
Attempt 1: Send message
  └─ Fail → Wait 100ms
Attempt 2: Reconnect + Send
  └─ Fail → Wait 200ms
Attempt 3: Reconnect + Send
  └─ Fail → Wait 400ms
Attempt 4: Reconnect + Send
  └─ Fail → Wait 800ms
Attempt 5: Reconnect + Send
  └─ Fail → Record failure, move on
```

**Configuration**:
- `MAX_RETRIES = 5`
- `BASE_BACKOFF_MS = 100`
- `RESPONSE_TIMEOUT_MS = 2000` (per message)

### Graceful Reconnection
```java
if (session == null || !session.isOpen()) {
    endpoint.connect();
    endpoint.awaitOpen(2s timeout);
    metrics.incReconnections();
}
```

## 5. Little's Law Analysis

**Little's Law**: `L = λ × W`
- `L` = Average number of messages in system
- `λ` = Arrival rate (messages/second)
- `W` = Average time in system (seconds)

### Observed Performance (Part 2 Results)

**Measurements**:
- Total messages: 500,000
- Wall time: ~11.0 seconds
- Mean latency (W): ~3 ms = 0.003 seconds
- Throughput (λ): 500,000 / 11.0 = ~45,455 msg/s

**Little's Law Calculation**:
```
L = λ × W
L = 45,455 msg/s × 0.003 s
L ≈ 136 messages in-flight concurrently
```

### Validation Against System Design

**Theoretical Maximum In-Flight**:
```
40 connections × 1 msg/connection = 40 messages
(if each connection sends synchronously)
```

**Observed**: ~136 messages in-flight suggests:
1. Queue buffering: Messages waiting in BlockingQueue
2. Network buffering: Messages in TCP/WebSocket buffers
3. Server processing: Messages awaiting broadcast

**Bottleneck Analysis**:
- Client capacity: 40 workers × 45,455/40 = ~1,136 msg/s per worker ✓
- Connection pool: 40 connections handle 45,455 msg/s = ~1,136 msg/s per connection ✓
- Queue capacity: 500,000 slots >> 136 in-flight ✓

### Predictions for Different Configurations

| Config | Workers | Connections | Predicted λ (msg/s) | Predicted L |
|--------|---------|-------------|---------------------|-------------|
| Current | 40 | 40 | 45,000 | 135 |
| Scale Up | 80 | 80 | 90,000 | 270 |
| Scale Down | 20 | 20 | 22,500 | 67 |

**Assumptions**:
- Linear scaling (no server saturation)
- Mean latency remains ~3ms
- Network capacity sufficient

### Performance Optimization Insights

**Current Bottleneck**: Client worker count
- 40 workers × 1,136 msg/s/worker = 45,455 msg/s
- Adding workers increases throughput linearly until server saturation

**Server Capacity Estimate**:
- Tomcat default thread pool: ~200 threads
- Assuming 5ms server processing time: 200 / 0.005 = 40,000 msg/s per thread
- Server can handle >> 45,000 msg/s (not bottleneck)

**Optimal Configuration**:
- Match workers to connections (1:1 ratio)
- Connection pool size = Expected concurrency / (Latency × Throughput goal)
- For 100,000 msg/s target with 3ms latency: Need ~300 connections

---

**Document Version**: 1.0  
**Last Updated**: February 2025  
**Course**: CS6650 Distributed Systems
