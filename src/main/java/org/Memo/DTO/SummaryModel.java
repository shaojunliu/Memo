package org.Memo.DTO;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SummaryModel {
    private String articleId;
    private String articleTitle;
    private String article;

    private String moodKeywords;
    private String actionKeywords;

    /** 新增：日期拆分（字符串），来源于东八区时间 */
    private long creatTime;
    private String year;   // 例如 "2025"
    private String month;  // 例如 "05"
    private String date;   // 例如 "15"
    private String summaryType; //“daily”

}
