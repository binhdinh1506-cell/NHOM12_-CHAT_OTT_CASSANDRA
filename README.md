# Đề tài 09 – Phân hệ lưu trữ lịch sử tin nhắn Chat OTT (Apache Cassandra)

Spring Boot 3 + Cassandra Java Driver v4 + WebSocket + HTML/JS thuần (Infinite Scroll).

```
ott-chat-nosql/
├── docker-compose.yml        # Cassandra 4.1 + container nạp schema tự động
├── database/
│   ├── schema.cql            # Keyspace + 5 bảng (messages: PK ((conversation_id), created_at, message_id) DESC)
│   └── demo-queries.cql      # Lệnh CQLSH dùng khi demo
├── backend/                  # Spring Boot (Maven)
│   └── src/main/java/com/chatott/
│       ├── config/           # AppConfig (CORS, auth), WebSocketConfig, DataSeeder (dummy data)
│       ├── controller/       # Auth / Conversation / Message
│       ├── service/          # MessageService (PagingState), ConversationService, AuthService
│       ├── repository/       # MessageRepository (findPage + PagingState), ...
│       └── websocket/        # ChatWebSocketHandler
├── frontend/                 # index.html, chat.html, css/, js/ (auth, chat, websocket)
└── docs/                     # Gợi ý nội dung báo cáo RDBMS vs Cassandra
```

## 1. Yêu cầu

- IntelliJ IDEA (có Maven, bật plugin **Docker** nếu muốn chạy compose trong IDE)
- JDK 17 · Docker Desktop

## 2. Chạy Cassandra

Tại thư mục gốc `ott-chat-nosql/`:

```bash
docker compose up -d
docker logs -f cassandra-init      # chờ thấy "SCHEMA OK" (lần đầu Cassandra mất ~1 phút để khởi động)
```

Kiểm tra: `docker exec -it cassandra cqlsh -e "DESCRIBE TABLES" chat_ott`
Phải thấy 5 bảng: `messages, users, users_by_username, user_conversations, conversations`.

## 3. Chạy Backend (IntelliJ)

1. `File → Open` → chọn thư mục `ott-chat-nosql` (hoặc chuột phải `backend/pom.xml → Add as Maven Project`).
2. Đặt Project SDK = JDK 17, đợi Maven tải thư viện.
3. Chạy class `com.chatott.ChatOttApplication`.
4. Lần chạy đầu `DataSeeder` tự nạp **5 user + 10 hội thoại × 200 tin = 2.000 tin nhắn** (xem log `Seed xong`).
   Chạy lại không bị nạp trùng. Muốn tắt: `app.seed: false` trong `application.yml`.

Hoặc dùng dòng lệnh: `cd backend && mvn spring-boot:run`

## 4. Mở giao diện

- http://localhost:8080 → đăng nhập `alice / 123456` (hoặc bob, charlie, diana, evan – cùng mật khẩu).
- Phòng đầu danh sách là **"Nhóm Demo NoSQL"** (200 tin, đánh số #1…#200).
- Frontend được Maven copy vào `classpath:/static` nên sửa file trong `frontend/` xong phải **Build/Rebuild** (Ctrl+F9) rồi F5 trình duyệt.

### Demo 2 máy qua LAN
- Máy chạy server: `ipconfig` (Windows) lấy IP, ví dụ 192.168.1.101; mở cổng 8080 trên Windows Firewall.
- Máy 2 vào `http://192.168.1.101:8080`, đăng nhập user khác. Frontend dùng đường dẫn tương đối nên WebSocket tự trỏ đúng IP.

## 5. API

| Method | Endpoint | Ghi chú |
|---|---|---|
| POST | `/api/auth/login`, `/api/auth/register` | trả `token` (Bearer) |
| GET | `/api/conversations` | danh sách phòng của tôi |
| POST | `/api/conversations` | `{name, members:[username]}` |
| POST | `/api/messages` | `{conversationId, content}` → 201 + `messageId, createdAt` |
| DELETE | `/api/messages/{id}?conversationId=` | soft delete (`is_deleted=true`) |
| GET | `/api/conversations/{id}/messages?pageToken=` | 20 tin/trang, trả `nextPageToken` |
| WS | `/ws/chat?token=` | sự kiện `NEW_MESSAGE`, `MESSAGE_DELETED`, `CONVERSATION_CREATED` |

Thử nhanh bằng curl:
```bash
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"123456"}' | python -c "import sys,json;print(json.load(sys.stdin)['token'])")
curl -s -H "Authorization: Bearer $TOKEN" \
  localhost:8080/api/conversations/c0000000-0000-0000-0000-000000000001/messages
```

## 6. Kịch bản demo (khớp tài liệu thiết kế)

1. **Nhắn tin realtime**: Máy 1 (alice) và Máy 2 (bob) cùng mở "Nhóm Demo NoSQL". Alice gửi tin → Bob thấy ngay, không F5.
2. **Kiểm tra partition** (terminal): `docker exec -it cassandra cqlsh` rồi chạy lệnh số 1, 2, 3 trong `database/demo-queries.cql` → tin vừa gửi nằm đầu partition, kèm `WRITETIME`.
3. **Infinite Scroll**: mở phòng → thấy tin #181–#200. F12 → Network, cuộn lên đỉnh → request `...?pageToken=...` trả #161–#180. Badge xanh trên header hiển thị thời gian Cassandra + tổng thời gian HTTP.
4. **Soft delete**: rê chuột vào tin của mình → "Xóa" → hiện "Tin nhắn này đã bị xóa"; chạy lệnh số 7 trong `demo-queries.cql` để thấy `is_deleted = true`, dữ liệu vẫn còn.

## 7. Một số điểm thiết kế đáng nêu với giảng viên

- **Keyset pagination**: PagingState lưu vị trí clustering key, không dùng OFFSET; tin mới chèn vào đầu partition không làm lệch các trang cũ.
- **`message_id` là TimeUUID** và `created_at` được suy ra từ chính nó (mili-giây) → API xóa chỉ cần `messageId` + `conversationId`.
- **Soft delete bằng `UPDATE ... IF sender_id = ?`** (lightweight transaction): không tạo dòng "ma" khi id sai, và chỉ chủ tin mới xóa được.
- **Bảng bổ sung so với tài liệu gốc** (vì Cassandra phải thiết kế theo truy vấn):
  `users_by_username` (đăng nhập theo username) và `conversations` (thành viên + tên phòng từng người, phục vụ cập nhật `user_conversations`).
- `user_conversations.last_updated` là clustering key nên khi có tin mới phải DELETE + INSERT dòng (`ConversationRepository.touch`).

## 8. Xử lý sự cố thường gặp

| Triệu chứng | Cách xử lý |
|---|---|
| Backend lỗi `Keyspace 'chat_ott' does not exist` / không kết nối được | Cassandra chưa sẵn sàng hoặc schema chưa nạp: xem `docker logs cassandra-init`; nạp tay: `docker exec -i cassandra cqlsh < database/schema.cql` |
| `NoNodeAvailableException` | Container chưa healthy (`docker ps`), chờ thêm 30–60 giây |
| Mở chat.html thấy trang trắng / 401 liên tục | Đăng nhập lại (token lưu trong RAM server, khởi động lại backend là mất) |
| Sửa frontend không thấy đổi | Rebuild project để Maven copy lại `frontend/` vào `static` |
| Máy 2 trong LAN không vào được | Mở cổng 8080 trên firewall máy chủ |
