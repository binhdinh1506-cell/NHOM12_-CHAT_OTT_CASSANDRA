package com.chatott.model;

import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;
import java.time.Instant;
import java.util.UUID;

@Table("friends")
public class Friend {
    @PrimaryKeyColumn(name = "user_id", type = PrimaryKeyType.PARTITIONED)
    private UUID userId;

    @PrimaryKeyColumn(name = "friend_id", type = PrimaryKeyType.CLUSTERED)
    private UUID friendId;

    @Column("friend_uid")
    private String friendUid;

    @Column("friend_name")
    private String friendName;

    @Column("created_at")
    private Instant createdAt;

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getFriendId() { return friendId; }
    public void setFriendId(UUID friendId) { this.friendId = friendId; }
    public String getFriendUid() { return friendUid; }
    public void setFriendUid(String friendUid) { this.friendUid = friendUid; }
    public String getFriendName() { return friendName; }
    public void setFriendName(String friendName) { this.friendName = friendName; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}