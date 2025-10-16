package org.Memo.DTO;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GetSummaryDetailReq {
    /** 用于区分 daily / weekly 等；当前只支持 daily */
    @NotBlank(message = "summaryType 不能为空")
    private String summaryType;

    /** 总结文章主键 id（即 daily_article_summary.id） */
    @NotNull(message = "articleId 不能为空")
    private Long articleId;

    // 未来可扩展其它过滤/鉴权字段
    // private String openid;
    // private String clientVersion;
}
