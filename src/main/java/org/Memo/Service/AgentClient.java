package org.Memo.Service;

import org.Memo.DTO.Chat.SummarizeResult;

public interface AgentClient {
    String chat(String userId, String message);

    SummarizeResult summarizeDay(String userId, String packedText);
}
