package com.chatott.controller;

import com.chatott.dto.MessageDTO;
import com.chatott.dto.PagingResponse;
import com.chatott.dto.SendMessageRequest;
import com.chatott.service.MessageService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    /** Gửi tin nhắn: 201 Created + messageId, createdAt. */
    @PostMapping("/api/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageDTO send(@RequestAttribute("userId") UUID userId, @RequestBody SendMessageRequest req) {
        return messageService.send(userId, req.conversationId(), req.content());
    }

    /** Xóa mềm: DELETE /api/messages/{messageId}?conversationId=... */
    @DeleteMapping("/api/messages/{messageId}")
    public Map<String, Boolean> delete(@RequestAttribute("userId") UUID userId,
                                       @PathVariable UUID messageId,
                                       @RequestParam UUID conversationId) {
        messageService.softDelete(userId, conversationId, messageId);
        return Map.of("success", true);
    }

    /**
     * Tải lịch sử phân trang (20 tin/trang, mới -> cũ).
     * GET /api/conversations/{id}/messages            -> 20 tin mới nhất
     * GET /api/conversations/{id}/messages?pageToken= -> 20 tin cũ hơn kế tiếp
     */
    @GetMapping("/api/conversations/{conversationId}/messages")
    public PagingResponse history(@RequestAttribute("userId") UUID userId,
                                  @PathVariable UUID conversationId,
                                  @RequestParam(required = false) String pageToken) {
        return messageService.history(userId, conversationId, pageToken);
    }
}
