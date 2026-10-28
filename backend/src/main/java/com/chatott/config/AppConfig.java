package com.chatott.config;

import com.chatott.service.AuthService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Kết nối tới Cassandra được Spring Boot tự cấu hình từ application.yml
 * (spring.cassandra.contact-points / local-datacenter / keyspace-name) -> bean CqlSession.
 */
@Configuration
public class AppConfig implements WebMvcConfigurer {

    private final AuthService authService;

    public AppConfig(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuthInterceptor(authService))
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**");
    }

    /** Cho phép mở frontend từ IntelliJ built-in server (localhost:63342) gọi sang :8080. */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
