package org.Memo.DTO;
import lombok.*;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SummaryModel {
    private String article;

    private String moodKeywords;

    /** 新增：日期拆分（字符串），来源于东八区时间 */
    private String year;   // 例如 "2025"
    private String month;  // 例如 "05"
    private String date;   // 例如 "15"

}
