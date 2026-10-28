package com.chatott.dto;

import java.util.List;

/** name: tên nhóm (bỏ trống nếu chat 1-1). members: danh sách username (không cần ghi người tạo). */
public record CreateConversationRequest(String name, List<String> members) {
}
