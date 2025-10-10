package org.Memo.DTO.Chat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SummarizeWsRequest {
    private String type;   // "daily_summary"
    private String text;   // 拼装好的聊天内容
}
