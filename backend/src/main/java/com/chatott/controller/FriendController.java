package com.chatott.controller;

import com.chatott.model.Friend;
import com.chatott.model.FriendRequest;
import com.chatott.model.UserAccount;
import com.chatott.repository.FriendRepository;
import com.chatott.repository.FriendRequestRepository;
import com.chatott.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/friends")
public class FriendController {

    @Autowired
    private FriendRepository friendRepository;

    @Autowired
    private FriendRequestRepository requestRepository;

    @Autowired
    private UserRepository userRepository;

    // [API MỚI THÊM] LẤY THÔNG TIN CỦA CHÍNH MÌNH (Để hiện UID lên màn hình Frontend)
    @GetMapping("/me/{userId}")
    public ResponseEntity<?> getMyProfile(@PathVariable UUID userId) {
        Optional<UserAccount> user = userRepository.findById(userId);
        if (user.isPresent()) {
            return ResponseEntity.ok(user.get());
        }
        return ResponseEntity.status(404).body("Lỗi: Không tìm thấy tài khoản");
    }

    // 1. TÌM KIẾM NGƯỜI DÙNG BẰNG UID
    @GetMapping("/search")
    public ResponseEntity<?> searchUserByUid(@RequestParam String uid) {
        Optional<UserAccount> user = userRepository.findByUid(uid);
        if (user.isPresent()) {
            return ResponseEntity.ok(user.get());
        }
        return ResponseEntity.status(404).body("Không tìm thấy người dùng với UID này.");
    }

    // 2. GỬI LỜI MỜI KẾT BẠN
    @PostMapping("/request")
    public ResponseEntity<?> sendRequest(@RequestParam UUID senderId, @RequestParam UUID receiverId) {
        UserAccount sender = userRepository.findById(senderId).orElseThrow();

        FriendRequest req = new FriendRequest();
        req.setReceiverId(receiverId);
        req.setSenderId(senderId);
        req.setSenderUid(sender.uid());
        req.setSenderName(sender.displayName());
        req.setStatus("PENDING");
        req.setCreatedAt(Instant.now());

        requestRepository.save(req);
        return ResponseEntity.ok("Đã gửi lời mời kết bạn");
    }

    // 3. LẤY DANH SÁCH LỜI MỜI
    @GetMapping("/requests/{userId}")
    public ResponseEntity<List<FriendRequest>> getRequests(@PathVariable UUID userId) {
        return ResponseEntity.ok(requestRepository.findAllByReceiverId(userId));
    }

    // 4. CHẤP NHẬN LỜI MỜI
    @PostMapping("/accept")
    public ResponseEntity<?> acceptRequest(@RequestParam UUID receiverId, @RequestParam UUID senderId) {
        UserAccount receiver = userRepository.findById(receiverId).orElseThrow();
        UserAccount sender = userRepository.findById(senderId).orElseThrow();

        // Chiều 1
        Friend f1 = new Friend();
        f1.setUserId(receiverId);
        f1.setFriendId(senderId);
        f1.setFriendUid(sender.uid());
        f1.setFriendName(sender.displayName());
        f1.setCreatedAt(Instant.now());
        friendRepository.save(f1);

        // Chiều 2
        Friend f2 = new Friend();
        f2.setUserId(senderId);
        f2.setFriendId(receiverId);
        f2.setFriendUid(receiver.uid());
        f2.setFriendName(receiver.displayName());
        f2.setCreatedAt(Instant.now());
        friendRepository.save(f2);

        // Xóa lời mời
        requestRepository.deleteByReceiverIdAndSenderId(receiverId, senderId);

        return ResponseEntity.ok("Kết bạn thành công");
    }

    // 5. HỦY KẾT BẠN
    @DeleteMapping("/{userId}/{friendId}")
    public ResponseEntity<?> unfriend(@PathVariable UUID userId, @PathVariable UUID friendId) {
        friendRepository.deleteByUserIdAndFriendId(userId, friendId);
        friendRepository.deleteByUserIdAndFriendId(friendId, userId);
        return ResponseEntity.ok("Đã hủy kết bạn");
    }

    // 6. LẤY DANH SÁCH BẠN BÈ
    @GetMapping("/list/{userId}")
    public ResponseEntity<List<Friend>> getFriendsList(@PathVariable UUID userId) {
        return ResponseEntity.ok(friendRepository.findAllByUserId(userId));
    }
}