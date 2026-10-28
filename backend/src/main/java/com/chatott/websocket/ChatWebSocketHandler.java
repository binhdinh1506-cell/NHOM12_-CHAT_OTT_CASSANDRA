package com.chatott.websocket;

import com.chatott.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kênh realtime: WebSocket chỉ truyền SỰ KIỆN (tin mới, tin bị xóa, phòng mới).
 * Việc lưu trữ (persistence) do REST API + Cassandra đảm nhiệm.
 *
 * Mỗi user mở 1 kết nối: ws://host:8080/ws/chat?token=...
 * Server đẩy sự kiện tới tất cả kết nối của các thành viên trong phòng.
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);
    private static final String ATTR_USER = "userId";

    private final AuthService authService;
    private final ObjectMapper mapper;
    private final Map<UUID, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(AuthService authService, ObjectMapper mapper) {
        this.authService = authService;
        this.mapper = mapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams().getFirst("token");
        Optional<UUID> user = authService.resolve(token);
        if (user.isEmpty()) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Unauthorized"));
            return;
        }
        session.getAttributes().put(ATTR_USER, user.get());
        sessionsByUser.computeIfAbsent(user.get(), k -> ConcurrentHashMap.newKeySet()).add(session);
        log.info("WS connected: user={} (tổng kết nối của user: {})", user.get(), sessionsByUser.get(user.get()).size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object u = session.getAttributes().get(ATTR_USER);
        if (u instanceof UUID userId) {
            Set<WebSocketSession> set = sessionsByUser.get(userId);
            if (set != null) {
                set.remove(session);
                if (set.isEmpty()) {
                    sessionsByUser.remove(userId);
                }
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Client không gửi dữ liệu nghiệp vụ qua WebSocket (chỉ nhận sự kiện), bỏ qua ping/pong thủ công.
    }

    /** Đẩy sự kiện {type, data} tới mọi kết nối của các user trong danh sách. */
    public void publish(Collection<UUID> userIds, String type, Object data) {
        final String json;
        try {
            json = mapper.writeValueAsString(Map.of("type", type, "data", data));
        } catch (Exception e) {
            log.error("Không serialize được sự kiện WS", e);
            return;
        }
        TextMessage payload = new TextMessage(json);
        for (UUID userId : userIds) {
            Set<WebSocketSession> sessions = sessionsByUser.get(userId);
            if (sessions == null) {
                continue;
            }
            for (WebSocketSession s : sessions) {
                if (!s.isOpen()) {
                    continue;
                }
                try {
                    synchronized (s) {          // WebSocketSession.sendMessage không thread-safe
                        s.sendMessage(payload);
                    }
                } catch (Exception e) {
                    log.warn("Gửi WS thất bại tới user {}: {}", userId, e.getMessage());
                }
            }
        }
    }
}
