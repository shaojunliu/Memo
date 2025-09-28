package org.Memo.Repo;

import org.Memo.Entity.ChatRecord;
import org.Memo.Entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRecordRepository extends JpaRepository<ChatRecord, Long> {
}
