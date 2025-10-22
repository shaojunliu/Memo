package org.Memo.Repo;

import org.Memo.Entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("""
        SELECT u FROM User u
        WHERE u.openId = :openId
    """)
    Optional<User> findByOpenId(@Param("openId") String openId);

    @Query("""
        SELECT u.createdAt FROM User u
        WHERE u.openId = :openId
    """)
    Optional<Instant> findCreatedAtByOpenId(@Param("openId") String openId);
}