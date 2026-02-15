package cs6650.ziqunliu.chatflow.client.model;

/**
 * Holds per-message latency data for CSV output and statistical analysis.
 */
public class LatencyRecord {
    private final long sendTimestamp;    // epoch millis when message was sent
    private final String messageType;    // JOIN, POST, or LEAVE
    private final long latencyMs;        // time from send to ACK
    private final String statusCode;     // "OK" or "FAIL"
    private final int roomId;

    public LatencyRecord(long sendTimestamp, String messageType, long latencyMs,
                         String statusCode, int roomId) {
        this.sendTimestamp = sendTimestamp;
        this.messageType = messageType;
        this.latencyMs = latencyMs;
        this.statusCode = statusCode;
        this.roomId = roomId;
    }

    public long getSendTimestamp() { 
        return sendTimestamp; 
    }
    
    public String getMessageType() { 
        return messageType; 
    }
    
    public long getLatencyMs() { 
        return latencyMs; 
    }
    
    public String getStatusCode() { 
        return statusCode; 
    }
    
    public int getRoomId() { 
        return roomId; 
    }

    public String toCsvLine() {
        return sendTimestamp + "," + messageType + "," + latencyMs + "," + statusCode + "," + roomId;
    }
}
