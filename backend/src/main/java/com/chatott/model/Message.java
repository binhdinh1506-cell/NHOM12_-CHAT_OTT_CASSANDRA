package com.chatott.model;

import java.time.Instant;
import java.util.UUID;

/** Một dòng trong bảng messages. */
public record Message(
        UUID conversationId,
        Instant createdAt,
        UUID messageId,
        UUID senderId,
        String content,
        boolean deleted) {
}
