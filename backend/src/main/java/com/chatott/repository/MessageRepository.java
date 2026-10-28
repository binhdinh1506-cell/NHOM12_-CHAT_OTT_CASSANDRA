package com.chatott.repository;

import com.chatott.model.Message;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.uuid.Uuids;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Truy cập bảng messages bằng Cassandra Java Driver v4.
 *
 * Mọi truy vấn đều có WHERE conversation_id = ? (Partition Key) nên Cassandra
 * chỉ cần đọc ĐÚNG 1 partition, không scan toàn bảng.
 */
@Component
public class MessageRepository {

    /** Kết quả 1 trang: danh sách dòng + PagingState của trang kế tiếp (null nếu hết). */
    public record MessagePage(List<Message> rows, ByteBuffer nextPagingState) {
    }

    private final CqlSession session;
    private final PreparedStatement insertStmt;
    private final PreparedStatement selectPageStmt;
    private final PreparedStatement softDeleteStmt;

    public MessageRepository(CqlSession session) {
        this.session = session;
        this.insertStmt = session.prepare(
                "INSERT INTO messages (conversation_id, created_at, message_id, sender_id, content, is_deleted) "
                        + "VALUES (?, ?, ?, ?, ?, false)");
        this.selectPageStmt = session.prepare(
                "SELECT conversation_id, created_at, message_id, sender_id, content, is_deleted "
                        + "FROM messages WHERE conversation_id = ?");
        // IF sender_id = ?  (lightweight transaction) có 2 tác dụng:
        //  1) UPDATE trong Cassandra là upsert -> điều kiện này chặn việc "đẻ" ra dòng mới nếu message không tồn tại
        //  2) chỉ người gửi mới được xóa tin của chính mình
        this.softDeleteStmt = session.prepare(
                "UPDATE messages SET is_deleted = true "
                        + "WHERE conversation_id = ? AND created_at = ? AND message_id = ? IF sender_id = ?");
    }

    public void insert(Message m) {
        session.execute(insertStmt.bind(
                m.conversationId(), m.createdAt(), m.messageId(), m.senderId(), m.content()));
    }

    /**
     * PHÂN TRANG BẰNG PAGING STATE.
     *
     * @param pageSize    số dòng mỗi trang (fetch size) - cố định 20
     * @param pagingState null cho trang đầu; với các trang sau là token do Cassandra trả về ở lần gọi trước
     *
     * Cassandra lưu trong PagingState vị trí (clustering key) của dòng kế tiếp,
     * nên trang sau được đọc tiếp từ đúng vị trí đó trong partition (keyset pagination),
     * KHÔNG dùng OFFSET -> độ trễ không tăng theo độ sâu của trang,
     * và tin nhắn mới chèn vào đầu partition không làm lệch các trang cũ.
     */
    public MessagePage findPage(UUID conversationId, int pageSize, ByteBuffer pagingState) {
        BoundStatement stmt = selectPageStmt.bind(conversationId)
                .setPageSize(pageSize)
                .setPagingState(pagingState);

        ResultSet rs = session.execute(stmt);

        // Chỉ đọc các dòng đã có trong trang hiện tại, KHÔNG để driver tự động tải trang kế tiếp.
        int inPage = rs.getAvailableWithoutFetching();
        List<Message> rows = new ArrayList<>(inPage);
        for (int i = 0; i < inPage; i++) {
            rows.add(map(rs.one()));
        }
        // null nếu đã hết dữ liệu trong partition
        ByteBuffer next = rs.getExecutionInfo().getPagingState();
        return new MessagePage(rows, next);
    }

    /**
     * Soft delete: chỉ đặt is_deleted = true, dữ liệu lịch sử vẫn còn trên đĩa.
     * created_at được suy ra từ message_id (timeuuid) nên API chỉ cần messageId + conversationId.
     *
     * @return true nếu xóa thành công; false nếu không tìm thấy hoặc không phải tin của requester
     */
    public boolean softDelete(UUID conversationId, UUID messageId, UUID requesterId) {
        Instant createdAt = Instant.ofEpochMilli(Uuids.unixTimestamp(messageId));
        return session.execute(softDeleteStmt.bind(conversationId, createdAt, messageId, requesterId))
                .wasApplied();
    }

    private static Message map(Row r) {
        return new Message(
                r.getUuid("conversation_id"),
                r.getInstant("created_at"),
                r.getUuid("message_id"),
                r.getUuid("sender_id"),
                r.getString("content"),
                r.getBoolean("is_deleted"));   // null -> false
    }
}
