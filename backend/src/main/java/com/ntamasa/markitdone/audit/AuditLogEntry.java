package com.ntamasa.markitdone.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Entity representing an audit log entry.
 *
 * <p>Stores information about state-changing actions in the system, including before/after
 * snapshots for change tracking.
 */
@Entity
@Table(
    name = "audit_log",
    indexes = {@Index(name = "ix_audit_log_user_occurred", columnList = "user_id, occurred_at")})
public class AuditLogEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "action", nullable = false, length = 64)
  private String action;

  @Column(name = "entity_type", length = 64)
  private String entityType;

  @Column(name = "entity_id", length = 64)
  private String entityId;

  @Column(name = "request_id", length = 64)
  private String requestId;

  @Column(name = "ip_address", length = 45)
  private String ipAddress;

  @Column(name = "before_state")
  @JdbcTypeCode(SqlTypes.JSON)
  private String beforeState;

  @Column(name = "after_state")
  @JdbcTypeCode(SqlTypes.JSON)
  private String afterState;

  // Constructors
  public AuditLogEntry() {}

  public AuditLogEntry(
      Instant occurredAt,
      UUID userId,
      String action,
      String entityType,
      String entityId,
      String requestId,
      String ipAddress,
      String beforeState,
      String afterState) {
    this.occurredAt = occurredAt;
    this.userId = userId;
    this.action = action;
    this.entityType = entityType;
    this.entityId = entityId;
    this.requestId = requestId;
    this.ipAddress = ipAddress;
    this.beforeState = beforeState;
    this.afterState = afterState;
  }

  // Getters and Setters
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public void setOccurredAt(Instant occurredAt) {
    this.occurredAt = occurredAt;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public String getAction() {
    return action;
  }

  public void setAction(String action) {
    this.action = action;
  }

  public String getEntityType() {
    return entityType;
  }

  public void setEntityType(String entityType) {
    this.entityType = entityType;
  }

  public String getEntityId() {
    return entityId;
  }

  public void setEntityId(String entityId) {
    this.entityId = entityId;
  }

  public String getRequestId() {
    return requestId;
  }

  public void setRequestId(String requestId) {
    this.requestId = requestId;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public void setIpAddress(String ipAddress) {
    this.ipAddress = ipAddress;
  }

  public String getBeforeState() {
    return beforeState;
  }

  public void setBeforeState(String beforeState) {
    this.beforeState = beforeState;
  }

  public String getAfterState() {
    return afterState;
  }

  public void setAfterState(String afterState) {
    this.afterState = afterState;
  }
}
