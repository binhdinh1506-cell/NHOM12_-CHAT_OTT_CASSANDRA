package com.chatott.dto;

import java.util.UUID;

/** senderId (nếu client gửi kèm) bị bỏ qua: server lấy người gửi từ token để chống giả mạo. */
public record SendMessageRequest(UUID conversationId, String content) {
}
