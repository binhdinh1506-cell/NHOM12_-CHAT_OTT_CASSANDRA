package com.chatott.dto;

import java.util.UUID;

public record LoginResponse(UUID userId, String username, String displayName, String token) {
}
