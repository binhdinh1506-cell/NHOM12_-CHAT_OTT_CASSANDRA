package com.chatott.config;

import com.datastax.oss.driver.api.core.CqlSession;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DatabaseInitializer {

    @Autowired
    private CqlSession cqlSession;

    @PostConstruct
    public void initializeDatabase() {
        System.out.println("--- ĐANG KIỂM TRA VÀ TẠO BẢNG CASSANDRA TỰ ĐỘNG ---");

        // 1. Tự động tạo bảng Lời mời kết bạn (Nếu chưa có)
        String createFriendRequestsTable = "CREATE TABLE IF NOT EXISTS friend_requests (" +
                "receiver_id uuid, " +
                "sender_id uuid, " +
                "sender_uid text, " +
                "sender_name text, " +
                "status text, " +
                "created_at timestamp, " +
                "PRIMARY KEY (receiver_id, sender_id))";
        cqlSession.execute(createFriendRequestsTable);

        // 2. Tự động tạo bảng Bạn bè (Nếu chưa có)
        String createFriendsTable = "CREATE TABLE IF NOT EXISTS friends (" +
                "user_id uuid, " +
                "friend_id uuid, " +
                "friend_uid text, " +
                "friend_name text, " +
                "created_at timestamp, " +
                "PRIMARY KEY (user_id, friend_id))";
        cqlSession.execute(createFriendsTable);

        // 3. Tự động thêm cột UID (Vd: Bob#01) vào bảng User cũ của bạn
        try {
            // Lệnh ALTER TABLE sẽ quăng lỗi nếu cột đã tồn tại, nên ta dùng try-catch để bọc lại
            cqlSession.execute("ALTER TABLE users ADD uid text");
            System.out.println("Đã thêm thành công cột 'uid' vào bảng users.");
        } catch (Exception e) {
            System.out.println("Cột 'uid' đã có sẵn trong bảng users, bỏ qua thêm mới.");
        }

        System.out.println("--- KHỞI TẠO CASSANDRA HOÀN TẤT ---");
    }
}