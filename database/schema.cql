-- =====================================================================
-- ĐỀ TÀI 09: PHÂN HỆ LƯU TRỮ LỊCH SỬ TIN NHẮN CHAT OTT - APACHE CASSANDRA
-- Query-driven modeling: mỗi bảng phục vụ đúng 1 truy vấn chính.
-- =====================================================================

CREATE KEYSPACE IF NOT EXISTS chat_ott WITH replication = {
    'class': 'SimpleStrategy',
    'replication_factor': 1
};

USE chat_ott;

-- ---------------------------------------------------------------------
-- A. BẢNG CỐT LÕI: messages
--    Truy vấn: "Lấy N tin nhắn mới nhất / cũ hơn của 1 cuộc hội thoại"
--    Partition Key  = conversation_id  -> toàn bộ tin nhắn của 1 phòng
--                                         nằm trên CÙNG 1 partition vật lý
--    Clustering Key = created_at DESC, message_id DESC
--                                      -> tin mới nhất nằm đầu partition,
--                                         đọc tuần tự (sequential read)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS messages (
    conversation_id uuid,
    created_at      timestamp,
    message_id      timeuuid,          -- tránh trùng khi 2 tin cùng timestamp
    sender_id       uuid,
    content         text,
    is_deleted      boolean,
    PRIMARY KEY ((conversation_id), created_at, message_id)
) WITH CLUSTERING ORDER BY (created_at DESC, message_id DESC);

-- ---------------------------------------------------------------------
-- B. users: tra cứu theo user_id (lấy display_name của người gửi)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    user_id      uuid PRIMARY KEY,
    username     text,
    password     text,                 -- BCrypt hash, KHÔNG lưu plain text
    display_name text,
    created_at   timestamp
);

-- B'. users_by_username: tra cứu theo username khi đăng nhập
--     (Cassandra không cho WHERE theo cột không phải khóa -> tạo bảng riêng)
CREATE TABLE IF NOT EXISTS users_by_username (
    username     text PRIMARY KEY,
    user_id      uuid,
    password     text,
    display_name text
);

-- ---------------------------------------------------------------------
-- C. user_conversations: danh sách phòng chat của 1 user,
--    sắp theo hoạt động gần nhất (last_updated DESC)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_conversations (
    user_id           uuid,
    last_updated      timestamp,
    conversation_id   uuid,
    conversation_name text,
    last_message      text,
    PRIMARY KEY ((user_id), last_updated, conversation_id)
) WITH CLUSTERING ORDER BY (last_updated DESC, conversation_id DESC);

-- ---------------------------------------------------------------------
-- D. conversations: metadata + thành viên của 1 phòng.
--    member_names: user_id -> tên phòng hiển thị riêng cho từng người
--    (chat 1-1 thì mỗi bên thấy tên của đối phương)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS conversations (
    conversation_id uuid PRIMARY KEY,
    last_updated    timestamp,
    last_message    text,
    member_names    map<uuid, text>
);
