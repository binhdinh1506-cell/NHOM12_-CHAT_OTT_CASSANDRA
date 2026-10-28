package com.chatott.config;

import com.chatott.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/** Kiểm tra header "Authorization: Bearer <token>" và gắn userId vào request. */
public class AuthInterceptor implements HandlerInterceptor {

    private final AuthService authService;

    public AuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String header = request.getHeader("Authorization");
        String token = (header != null && header.startsWith("Bearer ")) ? header.substring(7).trim() : null;
        Optional<UUID> userId = authService.resolve(token);
        if (userId.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getOutputStream().write(
                    "{\"error\":\"Chưa đăng nhập hoặc phiên đã hết hạn\"}".getBytes(StandardCharsets.UTF_8));
            return false;
        }
        request.setAttribute("userId", userId.get());
        return true;
    }
}
