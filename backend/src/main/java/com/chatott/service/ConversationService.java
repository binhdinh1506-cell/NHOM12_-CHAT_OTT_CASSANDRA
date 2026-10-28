package com.chatott.service;

import com.chatott.dto.ConversationDTO;
import com.chatott.exception.ApiException;
import com.chatott.model.Conversation;
import com.chatott.model.UserAccount;
import com.chatott.repository.ConversationRepository;
import com.chatott.repository.UserRepository;
import com.chatott.websocket.ChatWebSocketHandler;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final ChatWebSocketHandler ws;

    public ConversationService(ConversationRepository conversationRepository,
                               UserRepository userRepository,
                               ChatWebSocketHandler ws) {
        this.conversationRepository = conversationRepository;
        this.userRepository = userRepository;
        this.ws = ws;
    }

    public List<ConversationDTO> list(UUID userId) {
        return conversationRepository.listForUser(userId);
    }

    /** Lấy hội thoại và bảo đảm user là thành viên. */
    public Conversation requireMember(UUID conversationId, UUID userId) {
        Conversation c = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy cuộc hội thoại"));
        if (!c.memberNames().containsKey(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Bạn không thuộc cuộc hội thoại này");
        }
        return c;
    }

    public ConversationDTO create(UUID creatorId, String name, List<String> usernames) {
        List<UserAccount> members = new ArrayList<>();
        if (usernames == null || usernames.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cần ít nhất 1 thành viên khác");
        }
        for (String un : usernames) {
            if (un == null || un.isBlank()) {
                continue;
            }
            UserAccount u = userRepository.findByUsername(un.trim().toLowerCase())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy user: " + un.trim()));
            if (!u.userId().equals(creatorId) && members.stream().noneMatch(m -> m.userId().equals(u.userId()))) {
                members.add(u);
            }
        }
        if (members.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cần ít nhất 1 thành viên khác bạn");
        }
        String creatorName = userDisplayNameFor(creatorId);
        List<UserAccount> all = new ArrayList<>();
        all.add(new UserAccount(creatorId, null, null, creatorName));
        all.addAll(members);

        Conversation conv = new Conversation(
                UUID.randomUUID(), Instant.now().truncatedTo(ChronoUnit.MILLIS), "", memberNames(name, all));
        conversationRepository.insert(conv);
        ws.publish(conv.memberNames().keySet(), "CONVERSATION_CREATED", Map.of("conversationId", conv.conversationId()));
        return new ConversationDTO(conv.conversationId(), conv.memberNames().get(creatorId), "", conv.lastUpdated());
    }

    /**
     * Tên phòng hiển thị riêng cho từng thành viên:
     * nhóm có tên -> mọi người thấy tên nhóm; chat 1-1 không tên -> mỗi bên thấy tên đối phương.
     */
    public static Map<UUID, String> memberNames(String name, List<UserAccount> users) {
        Map<UUID, String> map = new LinkedHashMap<>();
        for (UserAccount u : users) {
            String shown;
            if (name != null && !name.isBlank()) {
                shown = name.trim();
            } else {
                shown = users.stream()
                        .filter(o -> !o.userId().equals(u.userId()))
                        .map(UserAccount::displayName)
                        .collect(Collectors.joining(", "));
            }
            map.put(u.userId(), shown);
        }
        return map;
    }

    private String userDisplayNameFor(UUID userId) {
        return userRepository.findDisplayName(userId).orElse("Người dùng");
    }
}
