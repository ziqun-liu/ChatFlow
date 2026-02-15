# ChatFlow Client Benchmarking Results

## Test Environment

- **Instance Type**: AWS EC2 t3.micro
- **CPU**: 2 vCPU
- **Memory**: 1GB RAM
- **Total Messages**: 500,000
- **Number of Rooms**: 20
- **Connections per Room**: 5 (100 total connections)
- **Server**: WebSocket endpoint at ws://54.148.180.35:8080/server/ws/chat

## Run Benchmarks

To collect data for the Little's Law Analysis table below:

1. **Modify `ClientMain.java`**: Change the `NUM_SENDERS` constant to test different thread counts
   ```java
   private static final int NUM_SENDERS = 80;  // Change this value
   ```

2. **Build the project**:
   ```bash
   cd client-part1
   mvn clean install
   ```

3. **Run the benchmark**:
   ```bash
   java -jar target/client-part1-1.0-SNAPSHOT.jar
   ```

4. **Record the output**: Look for the "MAIN PHASE" summary line:
   ```
   MAIN PHASE
   success=499921, fail=79, attempts=500483, totalProcessed=500000
   connectionsCreated=3154, reconnections=3053
   wallTimeSec=123.157, throughputMsgPerSec=4059.21
   ```

5. **Extract throughput**: Record the `throughputMsgPerSec` value in the table below

6. **Repeat**: Test with thread counts: 10, 20, 40, 80, 160, 200

## Little's Law Analysis: λ = L / W

**Assumption**: Average service time (W) = 20ms (0.02 seconds) per message

**Example**: 800 threads / 0.02s = 4,600 msg/s

### EC2 Thread Count Benchmarking (t3.micro, 2 vCPU, 1GB RAM)

| Threads | Throughput (msg/s) | Little's Law Prediction | Actual/Predicted | Notes |
| ------- | ------------------ | ----------------------- | ---------------- | ----- |
| 10      | 2548               | 500                     | 509.6%           |       |
| 20      | 2082               | 1,000                   | 208.2%           |       |
| 40      | 1049               | 2,000                   | 52.45%           |       |
| 80      | 4059               | 4,000                   | 100.71%          |       |
| 160     | 363                | 8,000                   | 4.54%            |       |
| 200     | 379                | 10,000                  | 3.79%            |       |

**How to fill the table**:

1. **Throughput**: Copy `throughputMsgPerSec` from program output
2. **Little's Law Prediction**: Already calculated (Threads / 0.02)
3. **Actual/Predicted**: Calculate as `(Actual Throughput / Predicted) × 100%`
4. **Notes**: Add observations (e.g., "Near-perfect match", "Server saturated", "Connection refused")

### Analysis 

####  Connection is unstable. None of these thread options run until all connections are closed
