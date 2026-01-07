package org.Memo.Service;

import org.Memo.DTO.Chat.SummarizeResult;
import org.Memo.DTO.SummaryModel;

import java.util.HashMap;
import java.util.List;

public interface AgentClient {
    String chat(String userId, String message, List<ChatRecordService.MsgItemsSimple> preChat, List<SummaryModel> preDailySummary, HashMap<String, String> args);

    String chatWs(String userId, String message, List<ChatRecordService.MsgItemsSimple> preChat, List<SummaryModel> preDailySummary);

    SummarizeResult summarizeDay(String userId, String packedText);
}
