package org.Memo.DTO;
import lombok.*;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DaillySummarysModel {
    /** 总条数 */
    private String DailySummarySize;

    /** 汇总列表 */
    private List<SummaryModel> summarys;
}
