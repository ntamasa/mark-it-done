package com.ntamasa.markitdone.recurrence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ntamasa.markitdone.audit.AuditLogEntry;
import com.ntamasa.markitdone.audit.AuditLogRepository;
import com.ntamasa.markitdone.audit.AuditService;
import com.ntamasa.markitdone.recurrence.dto.RuleRequest;
import com.ntamasa.markitdone.recurrence.dto.SeriesRequest;
import com.ntamasa.markitdone.support.AbstractPostgresIntegrationTest;
import com.ntamasa.markitdone.support.TestUsers;
import com.ntamasa.markitdone.todo.Priority;
import com.ntamasa.markitdone.todo.Todo;
import com.ntamasa.markitdone.todo.TodoRepository;
import com.ntamasa.markitdone.user.User;
import com.ntamasa.markitdone.user.UserRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

class MaterializationJobIntegrationTest extends AbstractPostgresIntegrationTest {

  private static final ZoneId ZONE = ZoneId.of("Europe/Budapest");

  @Autowired private SeriesService seriesService;
  @Autowired private MaterializationService materializationService;
  @Autowired private RecurrenceSeriesRepository seriesRepository;
  @Autowired private SeriesExclusionRepository exclusionRepository;
  @Autowired private TodoRepository todoRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private AuditLogRepository auditLogRepository;
  @Autowired private AuditService auditService;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private TestUsers testUsers;
  @Autowired private JdbcTemplate jdbc;

  private MaterializationJob job() {
    return new MaterializationJob(
        seriesRepository, userRepository, materializationService, auditService, transactionManager);
  }

  private User newUser() {
    User user = testUsers.create("job-" + UUID.randomUUID() + "@example.com");
    user.setTimezone(ZONE.getId());
    return userRepository.save(user);
  }

  private static LocalDate today() {
    return LocalDate.now(ZONE);
  }

  private UUID createDaily(User user) {
    RuleRequest rule =
        new RuleRequest(Frequency.EVERY_N_DAYS, 1, null, null, today(), EndType.NEVER, null, null);
    return seriesService
        .create(
            user.getId(),
            new SeriesRequest("Daily", null, LocalTime.of(9, 0), Priority.LOW, null, rule))
        .id();
  }

  private List<Todo> rows(UUID seriesId) {
    return todoRepository.findBySeriesIdAndOccurrenceDateGreaterThanEqual(
        seriesId, LocalDate.of(2000, 1, 1));
  }

  private void setBack(UUID seriesId, LocalDate newUntil) {
    todoRepository.deleteAfter(seriesId, newUntil);
    jdbc.update(
        "update recurrence_series set materialized_until = ? where id = ?", newUntil, seriesId);
  }

  private List<AuditLogEntry> materialized(User user) {
    return auditLogRepository.findByUserIdOrderByOccurredAtDesc(user.getId()).stream()
        .filter(e -> "SERIES_MATERIALIZED".equals(e.getAction()))
        .toList();
  }

  @Test
  @DisplayName("a series moved back 10 days is refilled by one run with one audit row")
  void refillsAndAudits() {
    User user = newUser();
    UUID id = createDaily(user);
    LocalDate horizon = materializationService.horizon(ZONE);
    int full = rows(id).size();
    setBack(id, horizon.minusDays(10));
    assertThat(rows(id)).hasSize(full - 10);

    job().runOnce();

    assertThat(rows(id)).hasSize(full);
    assertThat(seriesRepository.findById(id).orElseThrow().getMaterializedUntil())
        .isEqualTo(horizon);
    List<AuditLogEntry> audits = materialized(user);
    assertThat(audits).hasSize(1);
    assertThat(audits.get(0).getEntityId()).isEqualTo(id.toString());
    assertThat(audits.get(0).getAfterState())
        .contains("\"created\": 10")
        .contains(horizon.minusDays(9).toString())
        .contains(horizon.toString());
  }

  @Test
  @DisplayName("a second run creates nothing and adds no audit row")
  void secondRunIsNoop() {
    User user = newUser();
    UUID id = createDaily(user);
    setBack(id, materializationService.horizon(ZONE).minusDays(10));
    MaterializationJob job = job();
    job.runOnce();
    int rowsAfterFirst = rows(id).size();

    job.runOnce();

    assertThat(rows(id)).hasSize(rowsAfterFirst);
    assertThat(materialized(user)).hasSize(1);
  }

  @Test
  @DisplayName("a cancelled series is skipped")
  void cancelledSkipped() {
    User user = newUser();
    UUID id = createDaily(user);
    setBack(id, materializationService.horizon(ZONE).minusDays(10));
    jdbc.update("update recurrence_series set status = 'CANCELLED' where id = ?", id);
    int before = rows(id).size();

    job().runOnce();

    assertThat(rows(id)).hasSize(before);
    assertThat(materialized(user)).isEmpty();
  }

  @Test
  @DisplayName("an excluded date is not recreated")
  void excludedNotRecreated() {
    User user = newUser();
    UUID id = createDaily(user);
    LocalDate horizon = materializationService.horizon(ZONE);
    LocalDate excluded = horizon.minusDays(5);
    setBack(id, horizon.minusDays(10));
    exclusionRepository.saveAndFlush(new SeriesExclusion(id, excluded));

    job().runOnce();

    assertThat(rows(id)).extracting(Todo::getOccurrenceDate).doesNotContain(excluded);
    assertThat(materialized(user)).hasSize(1);
    assertThat(materialized(user).get(0).getAfterState()).contains("\"created\": 9");
  }

  @Test
  @DisplayName("a broken series does not stop the others")
  void brokenSeriesIsolated() {
    User user = newUser();
    UUID broken = createDaily(user);
    UUID healthy = createDaily(user);
    LocalDate horizon = materializationService.horizon(ZONE);
    setBack(broken, horizon.minusDays(10));
    setBack(healthy, horizon.minusDays(10));
    jdbc.update("update recurrence_series set interval_days = null where id = ?", broken);
    int brokenBefore = rows(broken).size();

    int total = job().runOnce();

    assertThat(total).isGreaterThanOrEqualTo(10);
    assertThat(rows(healthy).size()).isEqualTo(rows(broken).size() + 10);
    assertThat(rows(broken)).hasSize(brokenBefore);
    assertThat(materialized(user)).hasSize(1);
    jdbc.update("update recurrence_series set status = 'CANCELLED' where id = ?", broken);
  }
}
