package org.Memo.DTO;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecordModel {

    @JsonProperty("念念天数")
    private Integer creatDays;

    @JsonProperty("不忘条数")
    private Integer recordCount;

    @JsonProperty("回响篇数")
    private Integer summaryCount;

    @JsonProperty("每日回响时间")
    @Builder.Default
    private String dailySummaryTime = "00:00"; // 每天零点（写死）
}