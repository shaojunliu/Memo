package org.Memo.DTO.Chat;

import lombok.Data;

import java.util.List;

@Data
public class ChatRequest {
    private String openid;
    private String message;
    private List<PreChat> preChat;
    private List<PreDailySummary> preDailySummary;
}
