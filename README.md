# ChatFlow

A high-performance distributed chat system built with WebSockets, designed for CS6650 Distributed Systems.

## Project Structure
```
ChatFlow/
├── server/              # WebSocket chat server (Tomcat + Servlet)
├── client-part1/        # Basic load testing client
├── client-part2/        # Performance analysis client with metrics
└── DESIGN.md           # Architecture and design document
```

## Quick Start

### 1. Server Setup

#### Local Development
```bash
# Build WAR file
mvn -f server/pom.xml package

# Deploy to local Tomcat
# - Copy server/target/server-1.0-SNAPSHOT.war to Tomcat webapps/
# - Rename to server.war for cleaner URLs
# - Start Tomcat

# Verify
curl http://localhost:8080/server/health
```

#### EC2 Deployment
```bash
# Build WAR locally
mvn -f server/pom.xml package

# Copy to EC2
scp -i your-key.pem server/target/server-1.0-SNAPSHOT.war ec2-user@YOUR-EC2-IP:/tmp/

# SSH into EC2
ssh -i your-key.pem ec2-user@YOUR-EC2-IP

# Clean previous deployments
sudo rm -rf /opt/tomcat9/webapps/server*
sudo rm -rf /opt/tomcat9/work/Catalina/localhost/server*

# Deploy
sudo cp /tmp/server-1.0-SNAPSHOT.war /opt/tomcat9/webapps/server.war
sudo chown ec2-user:ec2-user /opt/tomcat9/webapps/server.war

# Start Tomcat
/opt/tomcat9/bin/startup.sh

# Verify
curl http://YOUR-EC2-IP:8080/server/health
```

### 2. Client Setup

#### Part 1: Basic Load Test
```bash
cd client-part1
mvn clean package
java -jar target/client-part1-1.0-SNAPSHOT.jar
```

#### Part 2: Performance Analysis
```bash
cd client-part2

# Configure server endpoint
# Edit ClientMain.java: WS_URI = "ws://YOUR-SERVER:8080/server/ws/chat"

# Build and run
mvn clean package
java -jar target/client-part2-1.0-SNAPSHOT.jar

# Analyze results
pip install pandas matplotlib numpy
python analyze_latency.py
```

## System Architecture

**WebSocket Endpoint**: `ws://SERVER:8080/server/ws/chat/{roomId}`

**Client Load Test**:
- Warmup: 32 threads × 1,000 msgs = 32,000 messages
- Main: 40 workers × 500,000 total messages
- 20 rooms × 2 connections/room = 40 persistent connections

**Message Distribution**: 90% TEXT, 5% JOIN, 5% LEAVE

See [DESIGN.md](DESIGN.md) for detailed architecture, threading model, and performance analysis.

## Key Technologies
- **Server**: Java Servlet + javax.websocket + Tomcat 9 + Gson
- **Client**: Java 11 + Tyrus WebSocket Client + Maven
- **Analysis**: Python (pandas, matplotlib)

## Performance Metrics
The client tracks per-message latency and generates:
- Mean, median, p95, p99 response times
- Throughput per room
- Message type distribution
- Throughput timeline visualization

## Requirements
- Java 11+
- Maven 3.8+
- Tomcat 9.0.x (server)
- Python 3.7+ (analysis)

## Common Issues

**Connection refused**: Check server is running and port 8080 is accessible
```bash
# Verify server
curl http://YOUR-SERVER:8080/server/health

# Check port
netstat -tlnp | grep 8080
```

**High latency**: Increase connection pool size in ClientMain.java
```java
private static final int POOL_SIZE = 4;  // Default: 2
```

**Deployment issues**: Check Tomcat logs
```bash
sudo tail -f /opt/tomcat9/logs/catalina.out
```

## License
MIT License - CS6650 Distributed Systems
