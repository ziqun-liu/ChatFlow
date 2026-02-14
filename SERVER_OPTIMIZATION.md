# Server Optimization Guide

## 问题诊断

你的服务器代码本身没有问题，但Tomcat配置需要优化以支持更多并发WebSocket连接。

## 当前症状

```
java.io.IOException: Connection reset
```

这表明服务器在高负载下主动断开连接。

## 服务器端优化方案

### 方案1: 优化Tomcat配置 (推荐)

SSH到你的EC2实例，然后：

#### 1. 找到Tomcat的server.xml

```bash
# 通常在这些位置之一:
/var/lib/tomcat9/conf/server.xml
/opt/tomcat/conf/server.xml
/usr/local/tomcat/conf/server.xml

# 或者查找:
sudo find / -name server.xml 2>/dev/null
```

#### 2. 编辑server.xml

```bash
sudo nano /path/to/server.xml
```

找到 `<Connector>` 标签并修改：

```xml
<Connector port="8080" 
           protocol="HTTP/1.1"
           connectionTimeout="20000"
           maxThreads="300"
           maxConnections="300"
           acceptCount="150"
           minSpareThreads="50"
           redirectPort="8443" />
```

**关键参数说明**:
- `maxThreads="300"`: 最大工作线程数（默认200，增加到300）
- `maxConnections="300"`: 最大并发连接数（默认10000，但WebSocket更严格）
- `acceptCount="150"`: 队列等待数
- `minSpareThreads="50"`: 最小空闲线程

#### 3. 增加JVM内存

创建或编辑 `setenv.sh`:

```bash
sudo nano /path/to/tomcat/bin/setenv.sh
```

添加：

```bash
#!/bin/bash
export CATALINA_OPTS="$CATALINA_OPTS -Xms512m"
export CATALINA_OPTS="$CATALINA_OPTS -Xmx1536m"
export CATALINA_OPTS="$CATALINA_OPTS -XX:MaxMetaspaceSize=256m"
export CATALINA_OPTS="$CATALINA_OPTS -XX:+UseG1GC"
```

给文件执行权限：
```bash
sudo chmod +x /path/to/tomcat/bin/setenv.sh
```

#### 4. 增加系统文件描述符限制

```bash
# 检查当前限制
ulimit -n

# 临时增加
ulimit -n 8192

# 永久增加（编辑 /etc/security/limits.conf）
sudo nano /etc/security/limits.conf
```

添加这些行：
```
*  soft  nofile  8192
*  hard  nofile  65536
tomcat soft nofile 8192
tomcat hard nofile 65536
```

#### 5. 重启Tomcat

```bash
# 方式1: 如果是systemd服务
sudo systemctl restart tomcat9
sudo systemctl status tomcat9

# 方式2: 如果是手动启动
sudo /path/to/tomcat/bin/shutdown.sh
sleep 5
sudo /path/to/tomcat/bin/startup.sh

# 检查日志
tail -f /path/to/tomcat/logs/catalina.out
```

### 方案2: 升级EC2实例 (如果预算允许)

t2.micro规格：
- 1 vCPU
- 1 GB RAM
- **非常有限的资源**

建议升级到：
- **t2.small**: 1 vCPU, 2 GB RAM ($0.023/hour)
- **t3.small**: 2 vCPU, 2 GB RAM ($0.0208/hour, 更好的性能)

升级步骤：
1. 在AWS Console停止实例
2. Actions → Instance Settings → Change Instance Type
3. 选择 t2.small 或 t3.small
4. 启动实例

### 方案3: 添加WebSocket专用配置

创建文件 `META-INF/context.xml` 在你的WAR包中：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Context>
    <!-- WebSocket configuration -->
    <Parameter name="org.apache.tomcat.websocket.BLOCKING_SEND_TIMEOUT" 
               value="60000" />
    <Parameter name="org.apache.tomcat.websocket.ASYNC_SEND_TIMEOUT" 
               value="60000" />
</Context>
```

将此文件放在：
```
/Users/ziqunliu/Desktop/cs6650/ChatFlow/server/src/main/webapp/META-INF/context.xml
```

## 服务器端代码优化（可选）

### 优化1: 减少广播开销

当前你的代码对每个session都广播，如果是assignment 1，可以只echo回发送者：

```java
@OnMessage
public void onMessage(String message, Session session, @PathParam("roomId") String roomId) {
    // ... 验证代码 ...
    
    // Assignment 1 只需要echo back，不需要广播
    MessageBroadcastEvent success = new MessageBroadcastEvent(...);
    String payload = GSON.toJson(success);
    
    // 只发送给当前session
    if (session.isOpen()) {
        session.getAsyncRemote().sendText(payload);
    }
}
```

### 优化2: 添加心跳检测

```java
@OnOpen
public void onOpen(Session session, @PathParam("roomId") String roomId) {
    session.setMaxIdleTimeout(120000); // 2分钟超时
    // ... 现有代码 ...
}
```

## 测试计划

### Phase 1: 验证服务器配置生效

```bash
# SSH到EC2
ssh -i your-key.pem ec2-user@54.148.180.35

# 检查Tomcat进程的内存
ps aux | grep tomcat

# 检查文件描述符
cat /proc/$(pgrep -f tomcat)/limits | grep "open files"
```

### Phase 2: 渐进式负载测试

1. **测试1**: 10 connections, 1K messages
   ```java
   POOL_SIZE = 1, NUM_ROOMS = 10, TOTAL_MESSAGES = 1000
   ```

2. **测试2**: 20 connections, 10K messages
   ```java
   POOL_SIZE = 1, NUM_ROOMS = 20, TOTAL_MESSAGES = 10000
   ```

3. **测试3**: 40 connections, 50K messages
   ```java
   POOL_SIZE = 2, NUM_ROOMS = 20, TOTAL_MESSAGES = 50000
   ```

4. **测试4**: 100 connections, 500K messages
   ```java
   POOL_SIZE = 5, NUM_ROOMS = 20, TOTAL_MESSAGES = 500000
   ```

### Phase 3: 监控服务器

在运行client时，在另一个终端监控：

```bash
# 监控CPU和内存
top

# 监控网络连接
watch -n 1 'netstat -an | grep 8080 | wc -l'

# 监控Tomcat日志
tail -f /path/to/tomcat/logs/catalina.out
```

## 预期结果

### 优化前（当前状态）
- 可能支持: 20-40 个连接
- 症状: Connection reset errors

### 优化后（server.xml + 内存）
- 应该支持: 100-200 个连接
- 稳定性: 显著提升

### 升级实例后（t2.small）
- 应该支持: 200-400 个连接
- 吞吐量: 2-4倍提升

## 快速修复（如果无法优化服务器）

如果你无法SSH到EC2或修改配置，client端最简单的做法：

```java
// ClientMain.java
private static final int POOL_SIZE = 1;  // 每个房间只用1个连接
public static final int NUM_ROOMS = 20;  // 总共20个连接
```

这样只用20个连接应该任何Tomcat都能承受。

虽然吞吐量会降低，但程序能跑通，满足assignment的基本要求。

## 总结

**立即操作**:
1. 修改client: `POOL_SIZE = 2` (40个总连接)
2. 编译运行: `mvn clean package && java -jar target/client-1.0-SNAPSHOT.jar`
3. 如果还有Connection reset → 降到 `POOL_SIZE = 1` (20个总连接)

**后续优化**:
1. 优化Tomcat配置（maxThreads, maxConnections）
2. 增加JVM内存
3. 考虑升级EC2实例

你的代码架构是好的，只是服务器容量问题！
