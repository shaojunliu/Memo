package org.Memo.DTO.Chat;

import lombok.Data;

@Data
public class ChatRequest {
    private String userId;
    private String message;
    // 可选：会话ID、情绪阶段、上下文等
    // getters/setters/constructors
}
