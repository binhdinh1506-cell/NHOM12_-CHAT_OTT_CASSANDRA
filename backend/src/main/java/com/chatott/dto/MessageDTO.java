package com.chatott.dto;

import java.time.Instant;
import java.util.UUID;

public record MessageDTO(
        UUID messageId,
        UUID conversationId,
        UUID senderId,
        String senderName,
        String content,
        boolean deleted,
        Instant createdAt) {
}
