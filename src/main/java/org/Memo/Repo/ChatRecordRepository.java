package org.Memo.Repo;

import org.Memo.Entity.ChatRecord;
import org.Memo.Entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.Memo.Entity.ChatRecord;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
public interface ChatRecordRepository extends JpaRepository<ChatRecord, Long> {
    Optional<ChatRecord> findBySessionId(UUID sessionId);

    /**
     * 原子地把一条消息 item 追加到 msgs 数组末尾，并更新计数/last_ts/version
     * item 是 JSON 字符串，如：
     *   {"seq":1,"ts":"2025-09-29T10:00:00Z","role":"user","content":"你好"}
     */
    @Modifying
    @Query(value = """
      UPDATE chat_record
      SET msgs = CASE
                    WHEN jsonb_typeof(msgs) = 'array' THEN msgs || CAST(:item AS jsonb)
                    ELSE jsonb_build_array(msgs) || CAST(:item AS jsonb)
                  END,
          message_count = message_count + 1,
          last_ts = :ts,
          version = version + 1
      WHERE session_id = :sid AND version = :ver
      """, nativeQuery = true)
    int appendMessage(@Param("sid") UUID sessionId,
                      @Param("item") String itemJson,
                      @Param("ts") Instant ts,
                      @Param("ver") long currentVersion);
}
