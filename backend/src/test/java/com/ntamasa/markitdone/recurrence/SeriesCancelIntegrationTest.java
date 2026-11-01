package com.ntamasa.markitdone.recurrence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ntamasa.markitdone.audit.AuditLogEntry;
import com.ntamasa.markitdone.audit.AuditLogRepository;
import com.ntamasa.markitdone.audit.AuditService;
import com.ntamasa.markitdone.common.ApiException;
import com.ntamasa.markitdone.common.ErrorCode;
import com.ntamasa.markitdone.recurrence.dto.RuleRequest;
import com.ntamasa.markitdone.recurrence.dto.SeriesDto;
import com.ntamasa.markitdone.recurrence.dto.SeriesRequest;
import com.ntamasa.markitdone.support.AbstractPostgresIntegrationTest;
import com.ntamasa.markitdone.support.TestUsers;
import com.ntamasa.markitdone.todo.Priority;
import com.ntamasa.markitdone.todo.Todo;
import com.ntamasa.markitdone.todo.TodoRepository;
import com.ntamasa.markitdone.user.User;
import com.ntamasa.markitdone.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Cancelling a series in Pacific/Auckland. The clock is fixed at 2026-11-01 20:00 UTC, which is
 * 2026-11-02 09:00 in Auckland, so "today" for the user is 2026-11-02 while UTC still says
 * 2026-11-01.
 */
@Import(SeriesCancelIntegrationTest.FixedClockConfig.class)
class SeriesCancelIntegrationTest extends AbstractPostgresIntegrationTest {

  private static final ZoneId ZONE = ZoneId.of("Pacific/Auckland");
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);
  private static final LocalDate YESTERDAY = TODAY.minusDays(1);

  @TestConfiguration
  static class FixedClockConfig {
    @Bean
    @Primary
    Clock fixedClock() {
      return Clock.fixed(Instant.parse("2026-11-01T20:00:00Z"), ZoneOffset.UTC);
    }
  }

  @Autowired private SeriesService seriesService;
  @Autowired private MaterializationService materializationService;
  @Autowired private TodoRepository todoRepository;
  @Autowired private RecurrenceSeriesRepository seriesRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private AuditLogRepository auditLogRepository;
  @Autowired private AuditService auditService;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private TestUsers testUsers;
  @Autowired private JdbcTemplate jdbc;

  private User newUser() {
    User user = testUsers.create("cancel-" + UUID.randomUUID() + "@example.com");
    user.setTimezone(ZONE.getId());
    return userRepository.save(user);
  }

  private MaterializationJob job() {
    return new MaterializationJob(
        seriesRepository, userRepository, materializationService, auditService, transactionManager);
  }

  /** A daily series that started yesterday and never ends. */
  private UUID createDailyFromYesterday(User user) {
    RuleRequest rule =
        new RuleRequest(
            Frequency.EVERY_N_DAYS, 1, null, null, YESTERDAY, EndType.NEVER, null, null);
    return seriesService
        .create(
            user.getId(),
            new SeriesRequest("Gym", null, LocalTime.of(18, 0), Priority.HIGH, null, rule))
        .id();
  }

  private List<Todo> rows(UUID seriesId) {
    return todoRepository
        .findBySeriesIdAndOccurrenceDateGreaterThanEqual(seriesId, LocalDate.of(2000, 1, 1))
        .stream()
        .sorted(Comparator.comparing(Todo::getOccurrenceDate))
        .toList();
  }

  private List<AuditLogEntry> auditEntries(UUID userId, String action) {
    return auditLogRepository.findByUserIdOrderByOccurredAtDesc(userId).stream()
        .filter(e -> e.getAction().equals(action))
        .toList();
  }

  @Test
  @DisplayName(
      "cancelling keeps yesterday and today (with today's done state) and deletes every later row")
  void keepsPastAndTodayDeletesLater() {
    User user = newUser();
    UUID seriesId = createDailyFromYesterday(user);
    Todo today =
        rows(seriesId).stream()
            .filter(t -> t.getOccurrenceDate().equals(TODAY))
            .findFirst()
            .orElseThrow();
    today.setDone(true);
    todoRepository.saveAndFlush(today);

    seriesService.cancel(user.getId(), seriesId);

    List<Todo> remaining = rows(seriesId);
    assertThat(remaining).extracting(Todo::getOccurrenceDate).containsExactly(YESTERDAY, TODAY);
    Todo todayAfter = remaining.get(1);
    assertThat(todayAfter.isDone()).isTrue();
    assertThat(todayAfter.getId()).isEqualTo(today.getId());
  }

  @Test
  @DisplayName("the series becomes CANCELLED with cancelledOn equal to today in Auckland")
  void statusCancelledWithAucklandToday() {
    User user = newUser();
    UUID seriesId = createDailyFromYesterday(user);

    SeriesDto dto = seriesService.cancel(user.getId(), seriesId);

    assertThat(dto.status()).isEqualTo(SeriesStatus.CANCELLED);
    assertThat(dto.cancelledOn()).isEqualTo(TODAY);
    RecurrenceSeries stored = seriesRepository.findById(seriesId).orElseThrow();
    assertThat(stored.getStatus()).isEqualTo(SeriesStatus.CANCELLED);
    assertThat(stored.getCancelledOn()).isEqualTo(TODAY);
  }

  @Test
  @DisplayName("a second cancel returns the same body, changes nothing and adds no audit row")
  void secondCancelIdempotent() {
    User user = newUser();
    UUID seriesId = createDailyFromYesterday(user);
    SeriesDto first = seriesService.cancel(user.getId(), seriesId);
    List<UUID> idsAfterFirst = rows(seriesId).stream().map(Todo::getId).toList();

    SeriesDto second = seriesService.cancel(user.getId(), seriesId);

    assertThat(second).isEqualTo(first);
    assertThat(rows(seriesId)).extracting(Todo::getId).containsExactlyElementsOf(idsAfterFirst);
    assertThat(auditEntries(user.getId(), "SERIES_CANCELLED")).hasSize(1);
  }

  @Test
  @DisplayName("the nightly job does not recreate rows for a cancelled series")
  void nightlyJobDoesNotRecreate() {
    User user = newUser();
    UUID seriesId = createDailyFromYesterday(user);
    seriesService.cancel(user.getId(), seriesId);
    jdbc.update(
        "update recurrence_series set materialized_until = ? where id = ?",
        YESTERDAY.minusDays(30),
        seriesId);
    int before = rows(seriesId).size();

    job().runOnce();

    assertThat(rows(seriesId)).hasSize(before);
    assertThat(rows(seriesId)).extracting(Todo::getOccurrenceDate).noneMatch(d -> d.isAfter(TODAY));
    assertThat(auditEntries(user.getId(), "SERIES_MATERIALIZED")).isEmpty();
  }

  @Test
  @DisplayName("another user's series cancel and future-count are 404 Series not found")
  void otherUserGetsNotFound() {
    User owner = newUser();
    User other = newUser();
    UUID seriesId = createDailyFromYesterday(owner);

    assertThatThrownBy(() -> seriesService.cancel(other.getId(), seriesId))
        .isInstanceOf(ApiException.class)
        .hasMessage("Series not found")
        .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.NOT_FOUND));
    assertThatThrownBy(() -> seriesService.futureCount(other.getId(), seriesId))
        .isInstanceOf(ApiException.class)
        .hasMessage("Series not found")
        .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.NOT_FOUND));
    assertThat(seriesRepository.findById(seriesId).orElseThrow().getStatus())
        .isEqualTo(SeriesStatus.ACTIVE);
  }

  @Test
  @DisplayName("an unknown series id gives the same 404 Series not found")
  void unknownIdGetsNotFound() {
    User user = newUser();
    UUID unknown = UUID.randomUUID();

    assertThatThrownBy(() -> seriesService.cancel(user.getId(), unknown))
        .isInstanceOf(ApiException.class)
        .hasMessage("Series not found");
    assertThatThrownBy(() -> seriesService.futureCount(user.getId(), unknown))
        .isInstanceOf(ApiException.class)
        .hasMessage("Series not found");
  }

  @Test
  @DisplayName("future-count before cancel matches the number of removed occurrences")
  void futureCountMatchesRemoved() {
    User user = newUser();
    UUID seriesId = createDailyFromYesterday(user);
    long expected =
        rows(seriesId).stream().filter(t -> t.getOccurrenceDate().isAfter(TODAY)).count();
    assertThat(expected).isPositive();

    long count = seriesService.futureCount(user.getId(), seriesId);
    seriesService.cancel(user.getId(), seriesId);

    assertThat(count).isEqualTo(expected);
    List<AuditLogEntry> entries = auditEntries(user.getId(), "SERIES_CANCELLED");
    assertThat(entries).hasSize(1);
    assertThat(entries.get(0).getAfterState())
        .contains("\"removedOccurrences\": " + expected)
        .contains(TODAY.toString());
    assertThat(seriesService.futureCount(user.getId(), seriesId)).isZero();
  }

  @Test
  @DisplayName("the cancel audit entry carries the before snapshot and the removed count")
  void cancelAudited() {
    User user = newUser();
    UUID seriesId = createDailyFromYesterday(user);

    seriesService.cancel(user.getId(), seriesId);

    List<AuditLogEntry> entries = auditEntries(user.getId(), "SERIES_CANCELLED");
    assertThat(entries).hasSize(1);
    AuditLogEntry entry = entries.get(0);
    assertThat(entry.getEntityType()).isEqualTo("RecurrenceSeries");
    assertThat(entry.getEntityId()).isEqualTo(seriesId.toString());
    assertThat(entry.getBeforeState()).contains("Gym");
    assertThat(entry.getAfterState()).contains("removedOccurrences", "cancelledOn");
  }
}
