# ChatFlow Client - Part 2 (Performance Analysis)

## Prerequisites
- Java 11+
- Maven 3.8+
- Python 3.7+ with pandas, matplotlib, numpy (for analysis)

## Build
```bash
cd client-part2
mvn clean package
```

## Configuration
Edit `ClientMain.java` to change:
- `WS_URI`: Server WebSocket endpoint (default: `ws://localhost:8080/server/ws/chat`)
- `TOTAL_MESSAGES`: Total messages to send (default: 500,000)
- `NUM_SENDERS`: Consumer worker threads (default: 40)
- `NUM_ROOMS`: Chat rooms (default: 20)

## Run
```bash
# Local server
java -jar target/client-part2-1.0-SNAPSHOT.jar

# Remote server: modify WS_URI first, then rebuild and run
```

## Output
- **Console**: Real-time progress + final statistics
- **`latency.csv`**: Per-message data (timestamp, messageType, latency, statusCode, roomId)

## Analysis & Visualization
```bash
# Install dependencies (first time only)
pip install pandas matplotlib numpy

# Generate statistics and charts
python analyze_latency.py
```

Generates:
- Console output: mean, median, p95, p99, min/max latencies
- `throughput_chart.png`: Throughput over time (10s buckets)

## What's New vs Part 1
- Per-message latency tracking (timestamp before send → timestamp on ACK)
- CSV export for all latency records
- Statistical analysis: mean, median, p95, p99, min/max
- Throughput per room breakdown
- Message type distribution
- Throughput over time visualization

## Architecture
- **Warmup Phase**: 32 threads × 1,000 messages = 32,000 messages
- **Main Phase**: Producer-consumer pattern with 40 sender workers
  - Connection pool: 20 rooms × 2 connections/room = 40 persistent connections
  - Exponential backoff retry (5 attempts, 100ms base)
  - 2-second response timeout
