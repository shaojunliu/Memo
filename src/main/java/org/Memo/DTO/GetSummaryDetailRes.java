package org.Memo.DTO;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GetSummaryDetailRes {

    private String articleTitle;

    /** 代码字段名 summaryType，出参键名按你的要求映射为 "summmaryType"（3 个 m） */
    @JsonProperty("summmaryType")
    private String summaryType;

    /** 北京时间（东八区）当天 00:00 的毫秒时间戳 */
    @JsonProperty("creatTime")
    private Long createTime;

    /** 业务日期字符串：yyyy-MM-dd（如 2025-05-15） */
    @JsonProperty("creatDate")
    private String createDate;

    /** 文章正文 */
    @JsonProperty("context")
    private String content;

    private String moodKeywords;
    private String actionKeywords;
    private String memoryPoint;
    private String analyzeResult;
}
