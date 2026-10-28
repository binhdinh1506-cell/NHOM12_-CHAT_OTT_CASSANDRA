package com.chatott.repository;

import com.chatott.model.Friend;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class FriendRepository {
    private final CqlSession session;
    private final PreparedStatement insertFriend;
    private final PreparedStatement selectByUser;
    private final PreparedStatement deleteFriend;

    public FriendRepository(CqlSession session) {
        this.session = session;
        this.insertFriend = session.prepare("INSERT INTO friends (user_id, friend_id, friend_uid, friend_name, created_at) VALUES (?, ?, ?, ?, ?)");
        this.selectByUser = session.prepare("SELECT * FROM friends WHERE user_id = ?");
        this.deleteFriend = session.prepare("DELETE FROM friends WHERE user_id = ? AND friend_id = ?");
    }

    public void save(Friend f) {
        session.execute(insertFriend.bind(f.getUserId(), f.getFriendId(), f.getFriendUid(), f.getFriendName(), f.getCreatedAt()));
    }

    public List<Friend> findAllByUserId(UUID userId) {
        List<Friend> list = new ArrayList<>();
        for (Row r : session.execute(selectByUser.bind(userId))) {
            Friend f = new Friend();
            f.setUserId(r.getUuid("user_id"));
            f.setFriendId(r.getUuid("friend_id"));
            f.setFriendUid(r.getString("friend_uid"));
            f.setFriendName(r.getString("friend_name"));
            f.setCreatedAt(r.getInstant("created_at"));
            list.add(f);
        }
        return list;
    }

    public void deleteByUserIdAndFriendId(UUID userId, UUID friendId) {
        session.execute(deleteFriend.bind(userId, friendId));
    }
}