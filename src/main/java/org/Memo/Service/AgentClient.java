package org.Memo.Service;

import org.Memo.DTO.Chat.PreChat;
import org.Memo.DTO.Chat.PreDailySummary;
import org.Memo.DTO.Chat.SummarizeResult;

import java.util.List;

public interface AgentClient {
    String chat(String userId, String message, List<PreChat> preChat, List<PreDailySummary> preDailySummary);

    String chatWs(String userId, String message, List<PreChat> preChat, List<PreDailySummary> preDailySummary);

    SummarizeResult summarizeDay(String userId, String packedText);
}
