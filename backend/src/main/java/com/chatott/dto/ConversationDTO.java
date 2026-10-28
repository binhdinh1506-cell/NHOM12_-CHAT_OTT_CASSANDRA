package com.chatott.dto;

import java.time.Instant;
import java.util.UUID;

public record ConversationDTO(UUID conversationId, String name, String lastMessage, Instant lastUpdated) {
}
