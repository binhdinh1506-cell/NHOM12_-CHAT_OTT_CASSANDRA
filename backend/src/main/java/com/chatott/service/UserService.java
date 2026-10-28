package com.chatott.service;

import com.chatott.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tra cứu tên hiển thị người gửi (có cache để không phải query lại cho mỗi tin nhắn). */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final Map<UUID, String> nameCache = new ConcurrentHashMap<>();

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public String displayName(UUID userId) {
        String cached = nameCache.get(userId);
        if (cached != null) {
            return cached;
        }
        String name = userRepository.findDisplayName(userId).orElse("Người dùng ẩn");
        nameCache.put(userId, name);
        return name;
    }
}
