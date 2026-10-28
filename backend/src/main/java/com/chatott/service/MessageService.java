package com.chatott.service;

import com.chatott.dto.MessageDTO;
import com.chatott.dto.PagingResponse;
import com.chatott.exception.ApiException;
import com.chatott.model.Conversation;
import com.chatott.model.Message;
import com.chatott.repository.ConversationRepository;
import com.chatott.repository.MessageRepository;
import com.chatott.repository.MessageRepository.MessagePage;
import com.chatott.websocket.ChatWebSocketHandler;
import com.datastax.oss.driver.api.core.DriverException;
import com.datastax.oss.driver.api.core.uuid.Uuids;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MessageService {

    /** Số tin nhắn mỗi trang - cố định 20 theo đặc tả. */
    public static final int PAGE_SIZE = 20;
    private static final int MAX_CONTENT_LENGTH = 2000;
    private static final String DELETED_TEXT = "";

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationService conversationService;
    private final UserService userService;
    private final ChatWebSocketHandler ws;

    public MessageService(MessageRepository messageRepository,
                          ConversationRepository conversationRepository,
                          ConversationService conversationService,
                          UserService userService,
                          ChatWebSocketHandler ws) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.conversationService = conversationService;
        this.userService = userService;
        this.ws = ws;
    }

    // ------------------------------------------------------------------ GỬI
    public MessageDTO send(UUID senderId, UUID conversationId, String content) {
        if (conversationId == null || content == null || content.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Thiếu conversationId hoặc nội dung tin nhắn");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Tin nhắn tối đa " + MAX_CONTENT_LENGTH + " ký tự");
        }
        Conversation conv = conversationService.requireMember(conversationId, senderId);

        // message_id là TimeUUID; created_at suy ra từ chính TimeUUID đó (độ chính xác mili-giây)
        // => về sau chỉ cần messageId là biết được created_at khi xóa.
        UUID messageId = Uuids.timeBased();
        Instant createdAt = Instant.ofEpochMilli(Uuids.unixTimestamp(messageId));
        String text = content.trim();

        Message m = new Message(conversationId, createdAt, messageId, senderId, text, false);
        messageRepository.insert(m);                                       // ghi vào đúng partition
        conversationRepository.touch(conv, preview(text), createdAt);      // cập nhật danh sách phòng

        MessageDTO dto = toDto(m);
        ws.publish(conv.memberNames().keySet(), "NEW_MESSAGE", dto);       // đẩy realtime
        return dto;
    }

    // ------------------------------------------------------------------ XÓA (soft delete)
    public void softDelete(UUID requesterId, UUID conversationId, UUID messageId) {
        Conversation conv = conversationService.requireMember(conversationId, requesterId);
        boolean ok;
        try {
            ok = messageRepository.softDelete(conversationId, messageId, requesterId);
        } catch (UnsupportedOperationException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "messageId không phải TimeUUID hợp lệ");
        }
        if (!ok) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy tin nhắn hoặc đây không phải tin của bạn");
        }
        ws.publish(conv.memberNames().keySet(), "MESSAGE_DELETED",
                Map.of("conversationId", conversationId, "messageId", messageId));
    }

    // ------------------------------------------------------------------ PHÂN TRANG
    /**
     * Tải lịch sử tin nhắn theo từng trang 20 tin (mới -> cũ).
     *
     * @param pageToken null/rỗng: lấy 20 tin mới nhất;
     *                  ngược lại: chuỗi Base64 chứa PagingState Cassandra trả về ở lần gọi trước.
     */
    public PagingResponse history(UUID userId, UUID conversationId, String pageToken) {
        conversationService.requireMember(conversationId, userId);

        ByteBuffer pagingState = decodeToken(pageToken);

        long t0 = System.nanoTime();
        MessagePage page;
        try {
            page = messageRepository.findPage(conversationId, PAGE_SIZE, pagingState);
        } catch (DriverException e) {
            if (pagingState != null) {   // token hỏng/giả mạo thì Cassandra từ chối
                throw new ApiException(HttpStatus.BAD_REQUEST, "pageToken không hợp lệ");
            }
            throw e;
        }
        double ms = (System.nanoTime() - t0) / 1_000_000.0;

        List<MessageDTO> messages = page.rows().stream().map(this::toDto).toList();
        String next = encodeToken(page.nextPagingState());
        return new PagingResponse(messages, next, next != null, PAGE_SIZE, Math.round(ms * 100.0) / 100.0);
    }

    // ------------------------------------------------------------------ helpers
    /** PagingState (ByteBuffer nhị phân) -> chuỗi Base64 an toàn đặt trong URL. */
    static String encodeToken(ByteBuffer state) {
        if (state == null) {
            return null;
        }
        byte[] bytes = new byte[state.remaining()];
        state.duplicate().get(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Chuỗi Base64 -> ByteBuffer để đưa lại cho driver. */
    static ByteBuffer decodeToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return ByteBuffer.wrap(Base64.getUrlDecoder().decode(token.trim()));
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "pageToken không hợp lệ");
        }
    }

    private MessageDTO toDto(Message m) {
        return new MessageDTO(
                m.messageId(), m.conversationId(), m.senderId(),
                userService.displayName(m.senderId()),
                m.deleted() ? DELETED_TEXT : m.content(),
                m.deleted(), m.createdAt());
    }

    private static String preview(String text) {
        String oneLine = text.replace('\n', ' ');
        return oneLine.length() > 80 ? oneLine.substring(0, 80) + "…" : oneLine;
    }
}
