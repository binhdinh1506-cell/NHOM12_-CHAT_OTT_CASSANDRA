# Phân tích: vì sao RDBMS nghẽn cổ chai khi lưu hàng triệu tin nhắn, và Cassandra giải quyết thế nào

> Dùng làm nội dung cho phần "Báo cáo & Thiết kế CSDL" (1.0đ). Hãy diễn đạt lại bằng lời của nhóm và bổ sung số liệu đo thực tế của nhóm.

## 1. Cổ chai của RDBMS (MySQL/PostgreSQL) với bảng tin nhắn khổng lồ

| Vấn đề | Giải thích |
|---|---|
| **Một bảng `messages` phình to** | Hàng trăm triệu dòng dồn vào 1 bảng + chỉ mục B-Tree `(conversation_id, created_at)`. Cây chỉ mục sâu, không vừa RAM, mỗi lần đọc phải nhảy nhiều trang đĩa (random I/O). |
| **Phân trang bằng `LIMIT ... OFFSET`** | `OFFSET 100000` buộc DB đọc và bỏ qua 100.000 dòng trước khi trả 20 dòng → càng cuộn sâu càng chậm. Nếu có tin mới chèn vào, các trang bị lệch/trùng. |
| **Ghi nhiều, đọc nhiều đồng thời** | Mỗi INSERT phải cập nhật chỉ mục B-Tree, khóa dòng/trang, ghi WAL → thông lượng ghi bị giới hạn bởi 1 node chính. |
| **Mở rộng theo chiều dọc** | Muốn chịu tải lớn hơn phải nâng cấp phần cứng. Sharding thủ công rất phức tạp (JOIN, giao dịch phân tán). |
| **Điểm lỗi đơn (master)** | Replication master–slave: master chết thì phải failover, có độ trễ và rủi ro mất dữ liệu. |
| **Thừa tính năng không cần** | Tin nhắn hầu như chỉ *ghi thêm* và *đọc theo phòng*; JOIN, ràng buộc khóa ngoại, giao dịch ACID đa bảng gần như không dùng tới nhưng vẫn tốn chi phí. |

## 2. Cassandra giải quyết

| Cơ chế Cassandra | Tác dụng với bài toán chat |
|---|---|
| **LSM-Tree (Memtable → SSTable, commit log tuần tự)** | Ghi chỉ là append tuần tự → ghi cực nhanh, không cần cập nhật chỉ mục B-Tree. |
| **Partition Key `conversation_id`** | Toàn bộ tin nhắn của 1 phòng nằm cùng 1 partition (cùng bộ node) → đọc lịch sử 1 phòng không phải gom từ nhiều nơi. |
| **Clustering Key `created_at DESC, message_id DESC`** | Dữ liệu trong partition *được sắp xếp sẵn trên đĩa*, tin mới nhất ở đầu → "20 tin mới nhất" = đọc tuần tự 20 dòng đầu, không cần sắp xếp lúc truy vấn. |
| **Paging State (keyset pagination)** | Token ghi nhớ đúng vị trí clustering key đã đọc tới → trang thứ 1 và trang thứ 1000 có chi phí như nhau (không có OFFSET), và tin mới không làm lệch trang cũ. |
| **Kiến trúc peer-to-peer, consistent hashing** | Không có master; thêm node là mở rộng ngang gần như tuyến tính cả đọc lẫn ghi. |
| **Replication factor** | Mỗi partition được sao chép nhiều node → chịu lỗi, không điểm lỗi đơn. |
| **Query-driven modeling** | Mỗi truy vấn có 1 bảng riêng (`messages`, `user_conversations`, `users_by_username`) → mọi truy vấn đều trúng 1 partition. Đánh đổi: dữ liệu lặp (denormalization), ghi nhiều bảng. |

## 3. Đánh đổi & hạn chế (nên nêu trong báo cáo để thể hiện hiểu biết)

- Không có JOIN, không giao dịch đa bảng; muốn truy vấn mới phải thiết kế bảng mới.
- `UPDATE`/`DELETE` sinh tombstone. Dự án dùng **soft delete** (`is_deleted = true`) nên không tạo tombstone cho tin nhắn, đồng thời giữ được lịch sử.
- **Partition quá lớn**: phòng chat sống nhiều năm có thể thành partition hàng trăm MB. Giải pháp thực tế: thêm *bucket* (vd. `(conversation_id, month)`) vào Partition Key. Đồ án không áp dụng vì phạm vi demo 200 tin/phòng, nhưng nên nhắc tới như hướng mở rộng.
- `user_conversations` dùng `last_updated` làm clustering key nên khi có tin mới phải DELETE dòng cũ + INSERT dòng mới (đã cài trong `ConversationRepository.touch`).

## 4. Cách đo để đưa số liệu thật vào báo cáo

- Chạy `TRACING ON;` trong cqlsh với truy vấn `LIMIT 20` → ghi lại tổng thời gian ("elapsed").
- Giao diện web hiển thị sẵn: **thời gian truy vấn Cassandra (ms)** và **tổng thời gian HTTP** cho mỗi trang tải.
- Gợi ý đối chứng: tạo bảng tương tự trên MySQL/PostgreSQL với 1–5 triệu dòng, đo `OFFSET 0` so với `OFFSET 100000`.
