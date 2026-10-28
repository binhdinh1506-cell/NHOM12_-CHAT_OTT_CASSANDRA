package com.chatott.controller;

import com.chatott.dto.ConversationDTO;
import com.chatott.dto.CreateConversationRequest;
import com.chatott.service.ConversationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    /** Danh sách phòng chat của người dùng hiện tại (mới hoạt động nhất lên đầu). */
    @GetMapping
    public List<ConversationDTO> list(@RequestAttribute("userId") UUID userId) {
        return conversationService.list(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationDTO create(@RequestAttribute("userId") UUID userId,
                                  @RequestBody CreateConversationRequest req) {
        return conversationService.create(userId, req.name(), req.members());
    }
}
