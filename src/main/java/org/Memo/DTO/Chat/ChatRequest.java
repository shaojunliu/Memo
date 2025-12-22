package org.Memo.DTO.Chat;

import lombok.Data;
import org.Memo.DTO.SummaryModel;
import org.Memo.Service.ChatRecordService;

import java.util.HashMap;
import java.util.List;

@Data
public class ChatRequest {
    private String openid;
    private String message;
    private List<ChatRecordService.MsgItem> preChat;
    private List<SummaryModel> preDailySummary;
    private HashMap<String,String> args;
}
