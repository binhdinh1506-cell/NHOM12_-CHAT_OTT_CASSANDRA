-- =====================================================================
-- CÁC LỆNH DEMO TRÊN CQLSH
-- Mở:  docker exec -it cassandra cqlsh
-- Phòng DEMO (200 tin nhắn): c0000000-0000-0000-0000-000000000001
-- =====================================================================
USE chat_ott;

-- 1) Chứng minh tin vừa gửi nằm đúng partition (conversation_id), mới nhất ở đầu.
--    WRITETIME = thời điểm Cassandra ghi (micro-giây).
SELECT conversation_id, created_at, content, WRITETIME(content)
FROM messages
WHERE conversation_id = c0000000-0000-0000-0000-000000000001
LIMIT 5;

-- 2) Partition key được băm ra token -> quyết định node lưu dữ liệu.
--    Mọi dòng của cùng 1 phòng có CÙNG token (cùng 1 partition).
SELECT token(conversation_id) AS partition_token, conversation_id, created_at
FROM messages
WHERE conversation_id = c0000000-0000-0000-0000-000000000001
LIMIT 3;

-- 3) Đếm số tin trong phòng DEMO (kỳ vọng 200 + tin mới gửi khi demo)
SELECT COUNT(*) FROM messages WHERE conversation_id = c0000000-0000-0000-0000-000000000001;

-- 4) Tin ở mọi phòng: 10 phòng x 200 tin
SELECT conversation_id, last_message FROM conversations;

-- 5) Mô phỏng phân trang của backend ngay trên cqlsh: 20 dòng/trang, bấm phím để xem trang kế
PAGING 20;
SELECT created_at, content FROM messages
WHERE conversation_id = c0000000-0000-0000-0000-000000000001;
PAGING OFF;

-- 6) TRACING: xem Cassandra chỉ đọc 1 partition, không scan toàn bảng
TRACING ON;
SELECT created_at, content FROM messages
WHERE conversation_id = c0000000-0000-0000-0000-000000000001
LIMIT 20;
TRACING OFF;

-- 7) Sau khi demo "Xóa": dữ liệu KHÔNG mất vật lý, chỉ đổi cờ is_deleted
SELECT created_at, content, is_deleted FROM messages
WHERE conversation_id = c0000000-0000-0000-0000-000000000001
  AND is_deleted = true ALLOW FILTERING;

-- 8) Cassandra cấm truy vấn không có partition key (chứng minh nguyên tắc query-driven):
--    SELECT * FROM messages WHERE content = 'abc';          -- LỖI: cần ALLOW FILTERING (scan toàn bảng)
--    SELECT * FROM messages WHERE created_at > '2025-01-01'; -- LỖI tương tự

-- Lệnh shell (chạy ngoài cqlsh) xem node nào giữ partition của phòng DEMO:
--   docker exec -it cassandra nodetool getendpoints chat_ott messages c0000000-0000-0000-0000-000000000001
-- Xem thống kê kích thước partition:
--   docker exec -it cassandra nodetool tablestats chat_ott.messages
