package com.chatott.repository;

import com.chatott.dto.ConversationDTO;
import com.chatott.model.Conversation;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BatchStatementBuilder;
import com.datastax.oss.driver.api.core.cql.DefaultBatchType;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.BatchStatement;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Truy cập 2 bảng: conversations (metadata + thành viên) và user_conversations (danh sách phòng của từng user). */
@Component
public class ConversationRepository {

    private final CqlSession session;
    private final PreparedStatement selectConv;
    private final PreparedStatement insertConv;
    private final PreparedStatement updateConv;
    private final PreparedStatement insertUserConv;
    private final PreparedStatement deleteUserConv;
    private final PreparedStatement selectUserConvs;

    public ConversationRepository(CqlSession session) {
        this.session = session;
        this.selectConv = session.prepare(
                "SELECT conversation_id, last_updated, last_message, member_names FROM conversations WHERE conversation_id = ?");
        this.insertConv = session.prepare(
                "INSERT INTO conversations (conversation_id, last_updated, last_message, member_names) VALUES (?, ?, ?, ?)");
        this.updateConv = session.prepare(
                "UPDATE conversations SET last_updated = ?, last_message = ? WHERE conversation_id = ?");
        this.insertUserConv = session.prepare(
                "INSERT INTO user_conversations (user_id, last_updated, conversation_id, conversation_name, last_message) "
                        + "VALUES (?, ?, ?, ?, ?)");
        this.deleteUserConv = session.prepare(
                "DELETE FROM user_conversations WHERE user_id = ? AND last_updated = ? AND conversation_id = ?");
        this.selectUserConvs = session.prepare(
                "SELECT conversation_id, conversation_name, last_message, last_updated "
                        + "FROM user_conversations WHERE user_id = ? LIMIT 100");
    }

    public Optional<Conversation> findById(UUID id) {
        Row r = session.execute(selectConv.bind(id)).one();
        if (r == null) {
            return Optional.empty();
        }
        Map<UUID, String> names = r.getMap("member_names", UUID.class, String.class);
        return Optional.of(new Conversation(
                r.getUuid("conversation_id"),
                r.getInstant("last_updated"),
                r.getString("last_message"),
                names == null ? Map.of() : names));
    }

    /** Tạo hội thoại mới: ghi bảng conversations + 1 dòng user_conversations cho mỗi thành viên. */
    public void insert(Conversation c) {
        String last = c.lastMessage() == null ? "" : c.lastMessage();
        BatchStatementBuilder batch = BatchStatement.builder(DefaultBatchType.LOGGED);
        batch.addStatement(insertConv.bind(c.conversationId(), c.lastUpdated(), last, c.memberNames()));
        for (Map.Entry<UUID, String> m : c.memberNames().entrySet()) {
            batch.addStatement(insertUserConv.bind(
                    m.getKey(), c.lastUpdated(), c.conversationId(), m.getValue(), last));
        }
        session.execute(batch.build());
    }

    /**
     * Cập nhật "tin nhắn cuối" khi có tin mới.
     * last_updated là CLUSTERING KEY của user_conversations nên không thể UPDATE tại chỗ:
     * phải DELETE dòng cũ rồi INSERT dòng mới (đây là cách làm chuẩn trong Cassandra).
     */
    public void touch(Conversation conv, String lastMessage, Instant newUpdated) {
        BatchStatementBuilder batch = BatchStatement.builder(DefaultBatchType.LOGGED);
        boolean sameInstant = newUpdated.equals(conv.lastUpdated());
        for (Map.Entry<UUID, String> m : conv.memberNames().entrySet()) {
            if (!sameInstant) {
                batch.addStatement(deleteUserConv.bind(m.getKey(), conv.lastUpdated(), conv.conversationId()));
            }
            batch.addStatement(insertUserConv.bind(
                    m.getKey(), newUpdated, conv.conversationId(), m.getValue(), lastMessage));
        }
        batch.addStatement(updateConv.bind(newUpdated, lastMessage, conv.conversationId()));
        session.execute(batch.build());
    }

    /** Danh sách phòng chat của user, đã sắp theo last_updated DESC nhờ clustering order. */
    public List<ConversationDTO> listForUser(UUID userId) {
        // LinkedHashMap + putIfAbsent: nếu có dòng "mồ côi" do ghi đồng thời thì chỉ giữ dòng mới nhất
        Map<UUID, ConversationDTO> unique = new LinkedHashMap<>();
        for (Row r : session.execute(selectUserConvs.bind(userId))) {
            UUID id = r.getUuid("conversation_id");
            unique.putIfAbsent(id, new ConversationDTO(
                    id, r.getString("conversation_name"), r.getString("last_message"), r.getInstant("last_updated")));
        }
        return new ArrayList<>(unique.values());
    }
}
