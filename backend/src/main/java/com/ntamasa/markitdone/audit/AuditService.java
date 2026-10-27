package com.ntamasa.markitdone.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntamasa.markitdone.logging.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Service for recording audit log entries.
 *
 * <p>Provides two modes of recording:
 *
 * <ul>
 *   <li>{@code record} - requires an existing transaction, fails fast if called outside one
 *   <li>{@code recordStandalone} - always runs in its own transaction
 * </ul>
 */
@Service
public class AuditService {

  private static final Logger logger = LoggerFactory.getLogger(AuditService.class);

  private final AuditLogRepository auditLogRepository;
  private final ObjectMapper objectMapper;

  /**
   * Create an AuditService.
   *
   * @param auditLogRepository the audit log repository
   * @param objectMapper the Jackson ObjectMapper bean
   */
  public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
    this.auditLogRepository = auditLogRepository;
    this.objectMapper = objectMapper;
  }

  /**
   * Record an audit log entry within an existing transaction.
   *
   * <p>This method MUST be called within an existing transaction and will fail fast if not.
   *
   * @param action the audit action
   * @param userId the ID of the user performing the action (may be null)
   * @param entityType the type of entity being modified
   * @param entityId the ID of the entity being modified
   * @param before the state before the change (will be converted to JSON)
   * @param after the state after the change (will be converted to JSON)
   * @throws org.springframework.transaction.IllegalTransactionStateException if called outside a
   *     transaction
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void record(
      AuditAction action,
      UUID userId,
      String entityType,
      Object entityId,
      Object before,
      Object after) {
    recordInternal(action, userId, entityType, entityId, before, after);
  }

  /**
   * Record an audit log entry in a standalone transaction.
   *
   * <p>This method always runs in its own transaction, regardless of whether one exists.
   *
   * @param action the audit action
   * @param userId the ID of the user performing the action (may be null)
   * @param entityType the type of entity being modified
   * @param entityId the ID of the entity being modified
   * @param before the state before the change (will be converted to JSON)
   * @param after the state after the change (will be converted to JSON)
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordStandalone(
      AuditAction action,
      UUID userId,
      String entityType,
      Object entityId,
      Object before,
      Object after) {
    recordInternal(action, userId, entityType, entityId, before, after);
  }

  private void recordInternal(
      AuditAction action,
      UUID userId,
      String entityType,
      Object entityId,
      Object before,
      Object after) {
    Instant occurredAt = Instant.now();
    String requestId = extractRequestId();
    String ipAddress = extractIpAddress();

    String beforeJson = objectToJsonString(before);
    String afterJson = objectToJsonString(after);

    AuditLogEntry entry =
        new AuditLogEntry(
            occurredAt,
            userId,
            action.toString(),
            entityType,
            entityId != null ? entityId.toString() : null,
            requestId,
            ipAddress,
            beforeJson,
            afterJson);

    auditLogRepository.save(entry);

    // Log the audit event at INFO level
    logger.info(
        "audit {} user={} {}={}",
        action,
        userId,
        entityType != null ? entityType : "unknown",
        entityId != null ? entityId : "unknown");
  }

  /**
   * Extract the request ID from the current request context.
   *
   * @return the request ID, or null if not in a request context
   */
  private String extractRequestId() {
    return RequestIdFilter.current();
  }

  /**
   * Extract the client IP address from the current request context.
   *
   * <p>Returns the first value of X-Forwarded-For header if present (behind proxy), otherwise the
   * remote address.
   *
   * @return the IP address, or null if not in a request context
   */
  private String extractIpAddress() {
    try {
      ServletRequestAttributes attributes =
          (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
      if (attributes == null) {
        return null;
      }

      HttpServletRequest request = attributes.getRequest();
      String forwardedFor = request.getHeader("X-Forwarded-For");
      if (forwardedFor != null && !forwardedFor.isEmpty()) {
        // Return the first IP in the X-Forwarded-For list
        return forwardedFor.split(",")[0].trim();
      }

      return request.getRemoteAddr();
    } catch (Exception e) {
      // If we can't extract the IP, just return null
      return null;
    }
  }

  /**
   * Convert an object to a JSON string.
   *
   * @param obj the object to convert (may be null)
   * @return the JSON string representation, or null if the input is null
   */
  private String objectToJsonString(Object obj) {
    if (obj == null) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(obj);
    } catch (Exception e) {
      logger.warn("Failed to serialize object to JSON", e);
      return null;
    }
  }
}
