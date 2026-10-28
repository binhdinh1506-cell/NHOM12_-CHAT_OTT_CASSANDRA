package com.chatott.service;

import com.chatott.dto.LoginResponse;
import com.chatott.exception.ApiException;
import com.chatott.model.UserAccount;
import com.chatott.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Đăng ký / đăng nhập. Token phiên lưu trong bộ nhớ (đủ cho đồ án; khởi động lại server phải đăng nhập lại). */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder encoder;
    private final Map<String, UUID> tokens = new ConcurrentHashMap<>();

    public AuthService(UserRepository userRepository, PasswordEncoder encoder) {
        this.userRepository = userRepository;
        this.encoder = encoder;
    }

    public LoginResponse register(String username, String password, String displayName) {
        username = normalize(username);
        if (username.length() < 3 || password == null || password.length() < 4) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Username tối thiểu 3 ký tự, mật khẩu tối thiểu 4 ký tự");
        }
        String name = (displayName == null || displayName.isBlank()) ? username : displayName.trim();
        UserAccount u = new UserAccount(UUID.randomUUID(), username, encoder.encode(password), name);
        if (!userRepository.create(u)) {
            throw new ApiException(HttpStatus.CONFLICT, "Username đã tồn tại");
        }
        return issueToken(u);
    }

    public LoginResponse login(String username, String password) {
        UserAccount u = userRepository.findByUsername(normalize(username))
                .filter(acc -> password != null && encoder.matches(password, acc.passwordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Sai tên đăng nhập hoặc mật khẩu"));
        return issueToken(u);
    }

    public Optional<UUID> resolve(String token) {
        return token == null ? Optional.empty() : Optional.ofNullable(tokens.get(token));
    }

    private LoginResponse issueToken(UserAccount u) {
        String token = UUID.randomUUID().toString();
        tokens.put(token, u.userId());
        return new LoginResponse(u.userId(), u.username(), u.displayName(), token);
    }

    private static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }
}
