package com.chatott.model;

import java.text.Normalizer;
import java.util.UUID;
import java.util.regex.Pattern;

public record UserAccount(UUID userId, String username, String passwordHash, String displayName, String uid) {

    // Constructor nạp chồng
    public UserAccount(UUID userId, String username, String passwordHash, String displayName) {
        this(userId, username, passwordHash, displayName, generateCleanUid(displayName));
    }

    // Hàm tiện ích: Xóa dấu tiếng Việt và khoảng trắng để tạo UID chuẩn
    private static String generateCleanUid(String name) {
        if (name == null) name = "User";

        // 1. Gỡ bỏ dấu tiếng Việt
        String temp = Normalizer.normalize(name, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        String noAccent = pattern.matcher(temp).replaceAll("")
                .replace('đ', 'd').replace('Đ', 'D');

        // 2. Xóa toàn bộ khoảng trắng và ký tự đặc biệt (chỉ giữ lại chữ và số)
        String cleanName = noAccent.replaceAll("[^a-zA-Z0-9]", "");

        // 3. Xử lý trường hợp người dùng nhập tên toàn emoji hoặc ký tự lạ
        if (cleanName.isEmpty()) {
            cleanName = "User";
        }

        // 4. Trả về định dạng chuẩn: TenKhongDau#1234
        return cleanName + "#" + String.format("%04d", new java.util.Random().nextInt(10000));
    }
}