package com.ntamasa.markitdone.audit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository for accessing audit log entries. */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntry, UUID> {

  /**
   * Finds all audit log entries for a given user, ordered by most recent first.
   *
   * @param userId the user ID
   * @return a list of audit log entries, ordered by occurred_at descending
   */
  @Query("SELECT a FROM AuditLogEntry a WHERE a.userId = :userId ORDER BY a.occurredAt DESC")
  List<AuditLogEntry> findByUserIdOrderByOccurredAtDesc(@Param("userId") UUID userId);
}
