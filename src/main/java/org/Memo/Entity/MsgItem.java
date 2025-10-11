package org.Memo.Entity;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

/**
 * 单条消息的数据模型，用于序列化到 chat_record.msgs (JSONB 数组)
 */
public record MsgItem(
        int seq,           // 会话内序号
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant ts,        // 时间戳，直接用 Instant
        String role,       // "user" / "assistant"
        String content     // 消息内容
) {}
