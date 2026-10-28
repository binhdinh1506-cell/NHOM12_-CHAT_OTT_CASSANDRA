package com.chatott.repository;

import com.chatott.model.FriendRequest;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class FriendRequestRepository {
    private final CqlSession session;
    private final PreparedStatement insertReq;
    private final PreparedStatement selectByReceiver;
    private final PreparedStatement deleteReq;

    public FriendRequestRepository(CqlSession session) {
        this.session = session;
        this.insertReq = session.prepare("INSERT INTO friend_requests (receiver_id, sender_id, sender_uid, sender_name, status, created_at) VALUES (?, ?, ?, ?, ?, ?)");
        this.selectByReceiver = session.prepare("SELECT * FROM friend_requests WHERE receiver_id = ?");
        this.deleteReq = session.prepare("DELETE FROM friend_requests WHERE receiver_id = ? AND sender_id = ?");
    }

    public void save(FriendRequest req) {
        session.execute(insertReq.bind(req.getReceiverId(), req.getSenderId(), req.getSenderUid(), req.getSenderName(), req.getStatus(), req.getCreatedAt()));
    }

    public List<FriendRequest> findAllByReceiverId(UUID receiverId) {
        List<FriendRequest> list = new ArrayList<>();
        for (Row r : session.execute(selectByReceiver.bind(receiverId))) {
            FriendRequest req = new FriendRequest();
            req.setReceiverId(r.getUuid("receiver_id"));
            req.setSenderId(r.getUuid("sender_id"));
            req.setSenderUid(r.getString("sender_uid"));
            req.setSenderName(r.getString("sender_name"));
            req.setStatus(r.getString("status"));
            req.setCreatedAt(r.getInstant("created_at"));
            list.add(req);
        }
        return list;
    }

    public void deleteByReceiverIdAndSenderId(UUID receiverId, UUID senderId) {
        session.execute(deleteReq.bind(receiverId, senderId));
    }
}