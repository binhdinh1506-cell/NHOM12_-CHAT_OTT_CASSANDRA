package com.chatott.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** memberNames: user_id -> tên phòng hiển thị riêng cho user đó. */
public record Conversation(
        UUID conversationId,
        Instant lastUpdated,
        String lastMessage,
        Map<UUID, String> memberNames) {
}
