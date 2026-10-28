package com.chatott.repository;

import com.chatott.model.UserAccount;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class UserRepository {

    private final CqlSession session;
    private final PreparedStatement insertByUsername;
    private final PreparedStatement insertUser;
    private final PreparedStatement selectByUsername;
    private final PreparedStatement selectNameById;
    private final PreparedStatement selectByUid;
    private final PreparedStatement selectById;

    public UserRepository(CqlSession session) {
        this.session = session;

        try { session.execute("ALTER TABLE users ADD uid text"); } catch (Exception ignored) {}
        try { session.execute("ALTER TABLE users_by_username ADD uid text"); } catch (Exception ignored) {}
        try { session.execute("CREATE INDEX IF NOT EXISTS idx_users_uid ON users (uid)"); } catch (Exception ignored) {}

        this.insertByUsername = session.prepare(
                "INSERT INTO users_by_username (username, user_id, password, display_name, uid) VALUES (?, ?, ?, ?, ?) IF NOT EXISTS");
        this.insertUser = session.prepare(
                "INSERT INTO users (user_id, username, password, display_name, created_at, uid) VALUES (?, ?, ?, ?, ?, ?)");

        this.selectByUsername = session.prepare(
                "SELECT username, user_id, password, display_name, uid FROM users_by_username WHERE username = ?");
        this.selectNameById = session.prepare(
                "SELECT display_name FROM users WHERE user_id = ?");

        // THÊM 'ALLOW FILTERING' VÀO ĐÂY ĐỂ ĐẢM BẢO CASSANDRA LUÔN TÌM ĐƯỢC
        this.selectByUid = session.prepare(
                "SELECT username, user_id, password, display_name, uid FROM users WHERE uid = ? ALLOW FILTERING");

        this.selectById = session.prepare(
                "SELECT username, user_id, password, display_name, uid FROM users WHERE user_id = ?");
    }

    public boolean create(UserAccount u) {
        boolean applied = session.execute(insertByUsername.bind(
                u.username(), u.userId(), u.passwordHash(), u.displayName(), u.uid())).wasApplied();
        if (applied) {
            session.execute(insertUser.bind(
                    u.userId(), u.username(), u.passwordHash(), u.displayName(), Instant.now(), u.uid()));
        }
        return applied;
    }

    public Optional<UserAccount> findByUsername(String username) {
        Row r = session.execute(selectByUsername.bind(username)).one();
        if (r == null) return Optional.empty();
        return Optional.of(mapRowToUserAccount(r));
    }

    public Optional<String> findDisplayName(UUID userId) {
        Row r = session.execute(selectNameById.bind(userId)).one();
        return r == null ? Optional.empty() : Optional.ofNullable(r.getString("display_name"));
    }

    public Optional<UserAccount> findByUid(String uid) {
        Row r = session.execute(selectByUid.bind(uid)).one();
        if (r == null) return Optional.empty();
        return Optional.of(mapRowToUserAccount(r));
    }

    public Optional<UserAccount> findById(UUID userId) {
        Row r = session.execute(selectById.bind(userId)).one();
        if (r == null) return Optional.empty();
        return Optional.of(mapRowToUserAccount(r));
    }

    private UserAccount mapRowToUserAccount(Row r) {
        String uid = r.getString("uid");
        if (uid == null) {
            return new UserAccount(
                    r.getUuid("user_id"), r.getString("username"), r.getString("password"), r.getString("display_name"));
        }
        return new UserAccount(
                r.getUuid("user_id"), r.getString("username"), r.getString("password"), r.getString("display_name"), uid);
    }
}