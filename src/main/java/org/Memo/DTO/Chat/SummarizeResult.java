package org.Memo.DTO.Chat;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
/**
 * 用于接收 Agent /summarize/day 接口返回结果
 * 对应 Agent 的 JSON 响应结构：
 * {
 *   "article": "...",
 *   "moodKeywords": "专注, 放松, 感恩",
 *   "model": "gpt-4.1-mini",
 *   "tokenUsageJson": "{...}"
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SummarizeResult {

    /** 每日总结文章正文 */
    private String article;

    /** 今日情绪关键词（英文逗号分隔，如 "专注, 放松, 感恩"） */
    private String moodKeywords;

    /** 使用的模型名（可选） */
    private String model;

    /** token 用量信息，Agent 返回的 JSON 字符串 */
    private String tokenUsageJson;
}
