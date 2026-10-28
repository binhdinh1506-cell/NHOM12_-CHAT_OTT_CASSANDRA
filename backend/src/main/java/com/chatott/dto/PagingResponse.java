package com.chatott.dto;

import java.util.List;

/**
 * messages: sắp xếp MỚI -> CŨ (đúng thứ tự clustering key của Cassandra).
 * nextPageToken: PagingState mã hoá Base64; null nghĩa là đã hết lịch sử.
 * queryTimeMs: thời gian thực thi truy vấn Cassandra (đo ở backend) để demo độ trễ.
 */
public record PagingResponse(
        List<MessageDTO> messages,
        String nextPageToken,
        boolean hasMore,
        int pageSize,
        double queryTimeMs) {
}
