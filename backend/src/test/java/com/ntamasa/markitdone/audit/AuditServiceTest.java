package com.ntamasa.markitdone.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for AuditService. */
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

  @Mock private AuditLogRepository auditLogRepository;

  private ObjectMapper objectMapper;
  private AuditService auditService;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    auditService = new AuditService(auditLogRepository, objectMapper);
  }

  @Test
  void recordStoresEntryWithAction() {
    // Setup: Mock the repository to return the saved entry
    AuditLogEntry savedEntry = new AuditLogEntry();
    savedEntry.setId(UUID.randomUUID());
    when(auditLogRepository.save(any(AuditLogEntry.class))).thenReturn(savedEntry);

    UUID userId = UUID.randomUUID();
    Object beforeState = new TestPayload("before");
    Object afterState = new TestPayload("after");

    // Act: Call recordStandalone which doesn't require an active transaction
    auditService.recordStandalone(
        AuditAction.USER_REGISTERED, userId, "User", "user123", beforeState, afterState);

    // Assert: Verify the entry was saved
    ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
    verify(auditLogRepository).save(captor.capture());

    AuditLogEntry capturedEntry = captor.getValue();
    assertThat(capturedEntry.getAction()).isEqualTo("USER_REGISTERED");
    assertThat(capturedEntry.getUserId()).isEqualTo(userId);
    assertThat(capturedEntry.getEntityType()).isEqualTo("User");
    assertThat(capturedEntry.getEntityId()).isEqualTo("user123");
    assertThat(capturedEntry.getAfterState()).isNotNull();
  }

  @Test
  void recordStoresJsonSnapshot() {
    // Setup
    AuditLogEntry savedEntry = new AuditLogEntry();
    when(auditLogRepository.save(any(AuditLogEntry.class))).thenReturn(savedEntry);

    TestPayload payload = new TestPayload("test-value");

    // Act
    auditService.recordStandalone(
        AuditAction.CATEGORY_CREATED, UUID.randomUUID(), "Category", "cat1", null, payload);

    // Assert
    ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
    verify(auditLogRepository).save(captor.capture());

    AuditLogEntry entry = captor.getValue();
    String afterState = entry.getAfterState();
    assertThat(afterState).isNotNull();
    assertThat(afterState).contains("\"value\"").contains("test-value");
  }

  @Test
  void recordWithNullBeforeState() {
    // Setup
    AuditLogEntry savedEntry = new AuditLogEntry();
    when(auditLogRepository.save(any(AuditLogEntry.class))).thenReturn(savedEntry);

    // Act
    auditService.recordStandalone(
        AuditAction.USER_REGISTERED,
        UUID.randomUUID(),
        "User",
        "user123",
        null,
        new TestPayload("after"));

    // Assert
    ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
    verify(auditLogRepository).save(captor.capture());

    AuditLogEntry entry = captor.getValue();
    assertThat(entry.getBeforeState()).isNull();
    assertThat(entry.getAfterState()).isNotNull();
  }

  @Test
  void recordWithNullUserId() {
    // Setup
    AuditLogEntry savedEntry = new AuditLogEntry();
    when(auditLogRepository.save(any(AuditLogEntry.class))).thenReturn(savedEntry);

    // Act - user_id can be null for standalone events
    auditService.recordStandalone(
        AuditAction.LOGIN_FAILED, null, "LoginAttempt", "attempt1", null, null);

    // Assert
    ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
    verify(auditLogRepository).save(captor.capture());

    AuditLogEntry entry = captor.getValue();
    assertThat(entry.getUserId()).isNull();
  }

  @Test
  void recordWithNullEntityId() {
    // Setup
    AuditLogEntry savedEntry = new AuditLogEntry();
    when(auditLogRepository.save(any(AuditLogEntry.class))).thenReturn(savedEntry);

    // Act
    auditService.recordStandalone(
        AuditAction.TOKEN_REFRESHED, UUID.randomUUID(), "Token", null, null, null);

    // Assert
    ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
    verify(auditLogRepository).save(captor.capture());

    AuditLogEntry entry = captor.getValue();
    assertThat(entry.getEntityId()).isNull();
  }

  @Test
  void recordSetsTimestamp() {
    // Setup
    AuditLogEntry savedEntry = new AuditLogEntry();
    when(auditLogRepository.save(any(AuditLogEntry.class))).thenReturn(savedEntry);

    // Act
    auditService.recordStandalone(
        AuditAction.USER_REGISTERED, UUID.randomUUID(), "User", "user1", null, null);

    // Assert
    ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
    verify(auditLogRepository).save(captor.capture());

    AuditLogEntry entry = captor.getValue();
    assertThat(entry.getOccurredAt()).isNotNull();
    assertThat(entry.getOccurredAt()).isBeforeOrEqualTo(java.time.Instant.now());
  }

  /** Test payload class for serialization tests. */
  private static class TestPayload {
    public String value;

    TestPayload(String value) {
      this.value = value;
    }
  }
}
