package org.Memo.DTO.Chat;

import lombok.Data;

@Data
public class PreDailySummary {
    private String summaryDate;
    private String summaryTitle;
    private String summaryContent;
    private String summaryActionKeyWords;
    private String summaryMoodKeyWords;
}
