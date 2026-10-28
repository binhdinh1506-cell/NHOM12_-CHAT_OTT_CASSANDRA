package com.chatott.config;

import com.chatott.model.Conversation;
import com.chatott.model.Message;
import com.chatott.model.UserAccount;
import com.chatott.repository.ConversationRepository;
import com.chatott.repository.MessageRepository;
import com.chatott.repository.UserRepository;
import com.chatott.service.ConversationService;
import com.datastax.oss.driver.api.core.uuid.Uuids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Nạp dữ liệu mẫu theo định mức đề bài:
 *   - 5 người dùng (mật khẩu 123456)
 *   - 10 cuộc hội thoại, mỗi cuộc 200 tin nhắn  => 2.000 bản ghi
 * Chạy lặp lại an toàn: hội thoại đã tồn tại sẽ được bỏ qua.
 *
 * ID cố định để dễ dùng trong CQLSH:
 *   user         a0000000-0000-0000-0000-00000000000{1..5}
 *   conversation c0000000-0000-0000-0000-00000000000{1..10}   (số 1 = phòng DEMO)
 */
@Component
@ConditionalOnProperty(name = "app.seed", havingValue = "true")
public class DataSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    public static final UUID DEMO_CONVERSATION = convId(1);

    private static final String[][] USERS = {
            {"alice", "Alice Nguyễn"}, {"bob", "Bob Trần"}, {"charlie", "Charlie Lê"},
            {"diana", "Diana Phạm"}, {"evan", "Evan Hoàng"}
    };

    private record Spec(String name, List<Integer> members) {
    }

    private static final List<Spec> SPECS = List.of(
            new Spec("Nhóm Demo NoSQL", List.of(0, 1, 2, 3, 4)),   // phòng DEMO: ai cũng tham gia
            new Spec(null, List.of(0, 1)),
            new Spec(null, List.of(0, 2)),
            new Spec(null, List.of(1, 3)),
            new Spec(null, List.of(2, 4)),
            new Spec(null, List.of(3, 0)),
            new Spec("Nhóm Đồ án Cassandra", List.of(0, 1, 2)),
            new Spec("Lớp CNTT K20", List.of(0, 3, 4)),
            new Spec("Gia đình", List.of(1, 4)),
            new Spec("Team Backend", List.of(0, 1, 2, 3)));

    private static final String[] PHRASES = {
            "Hôm nay deadline đồ án NoSQL rồi nhé", "Cassandra chia partition theo conversation_id đúng không?",
            "Mình vừa chạy thử phân trang, nhanh thật", "Ai review giúp mình phần Paging State với",
            "Tối nay họp nhóm lúc 8h nha", "Đã push code lên repo rồi", "Docker của mình chạy ổn rồi",
            "Nhớ seed dữ liệu trước khi demo", "Cuộn lên là tải thêm 20 tin cũ", "Ok, mình xem ngay",
            "Cảm ơn bạn nhiều!", "Để mình kiểm tra lại bằng cqlsh", "Tin nhắn mới nằm đầu partition nhờ clustering DESC",
            "Có lỗi CORS không nhỉ?", "Không sao, mình xử lý được", "Thầy hỏi về tombstone thì trả lời sao?",
            "Soft delete chỉ đổi is_deleted thôi", "Ăn trưa chưa?", "Mai nộp báo cáo nhé", "Đã hiểu, cảm ơn!"
    };

    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final PasswordEncoder encoder;
    private final int messagesPerConversation;

    public DataSeeder(UserRepository userRepository,
                      ConversationRepository conversationRepository,
                      MessageRepository messageRepository,
                      PasswordEncoder encoder,
                      @Value("${app.seed-messages-per-conversation:200}") int messagesPerConversation) {
        this.userRepository = userRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.encoder = encoder;
        this.messagesPerConversation = messagesPerConversation;
    }

    @Override
    public void run(String... args) {
        // 1) Người dùng
        String hash = encoder.encode("123456");
        List<UserAccount> users = new ArrayList<>();
        for (int i = 0; i < USERS.length; i++) {
            UserAccount u = new UserAccount(userId(i + 1), USERS[i][0], hash, USERS[i][1]);
            users.add(u);
            userRepository.create(u);      // trả về false nếu đã có -> bỏ qua
        }

        // 2) Hội thoại + tin nhắn
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        int created = 0;
        for (int i = 0; i < SPECS.size(); i++) {
            UUID convId = convId(i + 1);
            if (conversationRepository.findById(convId).isPresent()) {
                continue;
            }
            Spec spec = SPECS.get(i);
            List<UserAccount> members = spec.members().stream().map(users::get).toList();
            Random rnd = new Random(i + 1L);
            // Phòng DEMO (i = 0) có tin cuối mới nhất; các phòng sau lùi dần 30 phút để thứ tự danh sách ổn định
            Instant end = now.minus(Duration.ofMinutes(1L + i * 30L));

            Instant lastAt = end;
            String lastContent = "";
            for (int n = 1; n <= messagesPerConversation; n++) {
                // tin thứ n cách tin cuối (n = max) đúng (max - n) phút
                Instant at = end.minus(Duration.ofMinutes(messagesPerConversation - n));
                UserAccount sender = members.get(rnd.nextInt(members.size()));
                String content = "#" + n + " — " + PHRASES[rnd.nextInt(PHRASES.length)];
                UUID messageId = Uuids.startOf(at.toEpochMilli());   // TimeUUID khớp với created_at
                messageRepository.insert(new Message(convId, at, messageId, sender.userId(), content, false));
                lastAt = at;
                lastContent = content;
            }
            conversationRepository.insert(new Conversation(
                    convId, lastAt, lastContent, ConversationService.memberNames(spec.name(), members)));
            created++;
            log.info("Seed: hội thoại {} ({}) - {} tin nhắn", i + 1, convId, messagesPerConversation);
        }
        log.info("Seed xong: {} hội thoại mới. Đăng nhập thử: alice / 123456. Phòng DEMO = {}",
                created, DEMO_CONVERSATION);
    }

    private static UUID userId(int i) {
        return UUID.fromString(String.format("a0000000-0000-0000-0000-%012d", i));
    }

    private static UUID convId(int i) {
        return UUID.fromString(String.format("c0000000-0000-0000-0000-%012d", i));
    }
}
