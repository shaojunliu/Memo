package org.Memo.DTO.Chat;

import lombok.Data;

@Data
public class ChatResponse {
    private String reply;   // Agent 返回的文本
    private String traceId; // 可选：链路ID
}
