package com.chatott.model;

import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;
import java.time.Instant;
import java.util.UUID;

@Table("friend_requests")
public class FriendRequest {
    @PrimaryKeyColumn(name = "receiver_id", type = PrimaryKeyType.PARTITIONED)
    private UUID receiverId;

    @PrimaryKeyColumn(name = "sender_id", type = PrimaryKeyType.CLUSTERED)
    private UUID senderId;

    @Column("sender_uid")
    private String senderUid;

    @Column("sender_name")
    private String senderName;

    @Column("status")
    private String status;

    @Column("created_at")
    private Instant createdAt;

    public UUID getReceiverId() { return receiverId; }
    public void setReceiverId(UUID receiverId) { this.receiverId = receiverId; }
    public UUID getSenderId() { return senderId; }
    public void setSenderId(UUID senderId) { this.senderId = senderId; }
    public String getSenderUid() { return senderUid; }
    public void setSenderUid(String senderUid) { this.senderUid = senderUid; }
    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}