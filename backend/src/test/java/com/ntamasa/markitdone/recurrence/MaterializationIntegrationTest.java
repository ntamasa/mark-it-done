package com.ntamasa.markitdone.recurrence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ntamasa.markitdone.audit.AuditLogEntry;
import com.ntamasa.markitdone.audit.AuditLogRepository;
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
import com.ntamasa.markitdone.todo.TodoService;
import com.ntamasa.markitdone.todo.dto.TodoDto;
import com.ntamasa.markitdone.user.User;
import com.ntamasa.markitdone.user.UserRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class MaterializationIntegrationTest extends AbstractPostgresIntegrationTest {

  private static final ZoneId ZONE = ZoneId.of("Europe/Budapest");

  @Autowired private SeriesService seriesService;
  @Autowired private MaterializationService materializationService;
  @Autowired private TodoService todoService;
  @Autowired private TodoRepository todoRepository;
  @Autowired private RecurrenceSeriesRepository seriesRepository;
  @Autowired private SeriesExclusionRepository exclusionRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private AuditLogRepository auditLogRepository;
  @Autowired private TestUsers testUsers;
  @Autowired private TransactionTemplate tx;
  @Autowired private SessionFactory sessionFactory;

  private User newUser() {
    User user = testUsers.create("mat-" + UUID.randomUUID() + "@example.com");
    user.setTimezone(ZONE.getId());
    return userRepository.save(user);
  }

  private static LocalDate today() {
    return LocalDate.now(ZONE);
  }

  private static SeriesRequest request(RuleRequest rule) {
    return new SeriesRequest("Gym", null, LocalTime.of(18, 0), Priority.HIGH, null, rule);
  }

  private static RuleRequest mwf(EndType endType, Integer count, LocalDate endDate) {
    return new RuleRequest(
        Frequency.WEEKLY,
        null,
        Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
        null,
        today(),
        endType,
        count,
        endDate);
  }

  private static RuleRequest daily(EndType endType, Integer count, LocalDate endDate) {
    return new RuleRequest(Frequency.EVERY_N_DAYS, 1, null, null, today(), endType, count, endDate);
  }

  private List<Todo> rows(UUID seriesId) {
    return todoRepository.findBySeriesIdAndOccurrenceDateGreaterThanEqual(
        seriesId, LocalDate.of(2000, 1, 1));
  }

  @Test
  @DisplayName("weekly Mon/Wed/Fri that never ends is materialised up to the horizon")
  void weeklyUpToHorizon() {
    User user = newUser();

    SeriesDto dto = seriesService.create(user.getId(), request(mwf(EndType.NEVER, null, null)));

    LocalDate horizon = materializationService.horizon(ZONE);
    RecurrenceRule rule = mwf(EndType.NEVER, null, null).toRule();
    List<LocalDate> expected = RecurrenceCalculator.slots(rule, today(), horizon);
    List<Todo> rows = rows(dto.id());
    assertThat(rows).hasSameSizeAs(expected);
    assertThat(rows)
        .allSatisfy(
            t -> {
              assertThat(t.getSeriesId()).isEqualTo(dto.id());
              assertThat(t.getOccurrenceDate()).isEqualTo(t.getDueDate());
              assertThat(t.isDetached()).isFalse();
            });
    assertThat(seriesRepository.findById(dto.id()).orElseThrow().getMaterializedUntil())
        .isEqualTo(horizon);
    assertThat(dto.status()).isEqualTo(SeriesStatus.ACTIVE);
    assertThat(dto.ruleSummary()).isEqualTo("Weekly: Mon, Wed, Fri");
    assertThat(dto.nextOccurrence())
        .isEqualTo(expected.stream().filter(d -> !d.isBefore(today())).findFirst().orElseThrow());
    assertThat(dto.nextOccurrenceTodoId()).isNotNull();
  }

  @Test
  @DisplayName("daily after 5 creates exactly 5 rows")
  void dailyAfterFive() {
    User user = newUser();

    SeriesDto dto =
        seriesService.create(user.getId(), request(daily(EndType.AFTER_COUNT, 5, null)));

    assertThat(rows(dto.id())).hasSize(5);
  }

  @Test
  @DisplayName("daily ending in 10 days creates 11 rows")
  void dailyEndingInTenDays() {
    User user = newUser();

    SeriesDto dto =
        seriesService.create(
            user.getId(), request(daily(EndType.ON_DATE, null, today().plusDays(10))));

    assertThat(rows(dto.id())).hasSize(11);
  }

  @Test
  @DisplayName("empty weekdays is rejected and creates nothing")
  void emptyWeekdaysRejected() {
    User user = newUser();
    long seriesBefore = seriesRepository.count();
    long todosBefore = todoRepository.count();
    RuleRequest rule =
        new RuleRequest(Frequency.WEEKLY, null, Set.of(), null, today(), EndType.NEVER, null, null);

    assertThatThrownBy(() -> seriesService.create(user.getId(), request(rule)))
        .isInstanceOfSatisfying(
            ApiException.class,
            ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));

    assertThat(seriesRepository.count()).isEqualTo(seriesBefore);
    assertThat(todoRepository.count()).isEqualTo(todosBefore);
  }

  @Test
  @DisplayName("an unknown category is rejected as Unknown category")
  void unknownCategory() {
    User user = newUser();
    SeriesRequest req =
        new SeriesRequest(
            "Gym", null, null, Priority.LOW, UUID.randomUUID(), daily(EndType.NEVER, null, null));

    assertThatThrownBy(() -> seriesService.create(user.getId(), req))
        .isInstanceOfSatisfying(
            ApiException.class,
            ex -> {
              assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
              assertThat(ex.getMessage()).isEqualTo("Unknown category");
            });
  }

  @Test
  @DisplayName("create audits SERIES_CREATED")
  void auditsCreation() {
    User user = newUser();

    seriesService.create(user.getId(), request(daily(EndType.AFTER_COUNT, 1, null)));

    assertThat(auditLogRepository.findByUserIdOrderByOccurredAtDesc(user.getId()))
        .extracting(AuditLogEntry::getAction)
        .contains("SERIES_CREATED");
  }

  @Test
  @DisplayName("the todo range shows occurrences with their series fields")
  void rangeShowsSeriesFields() {
    User user = newUser();
    SeriesDto dto = seriesService.create(user.getId(), request(daily(EndType.NEVER, null, null)));

    List<TodoDto> todos = todoService.range(user.getId(), today(), today().plusDays(6));

    assertThat(todos).hasSize(7);
    assertThat(todos)
        .allSatisfy(
            t -> {
              assertThat(t.seriesId()).isEqualTo(dto.id());
              assertThat(t.occurrenceDate()).isEqualTo(t.dueDate());
              assertThat(t.ruleSummary()).isEqualTo("Every day");
            });
  }

  @Test
  @DisplayName("weekly occurrences in the next 7 days carry the rule summary")
  void weeklySummaryInRange() {
    User user = newUser();
    seriesService.create(user.getId(), request(mwf(EndType.NEVER, null, null)));

    List<TodoDto> todos = todoService.range(user.getId(), today(), today().plusDays(6));

    assertThat(todos).isNotEmpty();
    assertThat(todos)
        .allSatisfy(t -> assertThat(t.ruleSummary()).isEqualTo("Weekly: Mon, Wed, Fri"));
  }

  @Test
  @DisplayName("materialising twice creates no duplicates")
  void idempotent() {
    User user = newUser();
    SeriesDto dto = seriesService.create(user.getId(), request(mwf(EndType.NEVER, null, null)));
    int before = rows(dto.id()).size();

    int created =
        tx.execute(
            s ->
                materializationService.materialize(
                    seriesRepository.findById(dto.id()).orElseThrow(),
                    materializationService.horizon(ZONE)));

    assertThat(created).isZero();
    assertThat(rows(dto.id())).hasSize(before);
  }

  @Test
  @DisplayName("materialising further advances from where it stopped without duplicates")
  void continuesFromMaterializedUntil() {
    User user = newUser();
    SeriesDto dto = seriesService.create(user.getId(), request(daily(EndType.NEVER, null, null)));
    LocalDate horizon = materializationService.horizon(ZONE);

    int created =
        tx.execute(
            s ->
                materializationService.materialize(
                    seriesRepository.findById(dto.id()).orElseThrow(), horizon.plusDays(3)));

    assertThat(created).isEqualTo(3);
    assertThat(seriesRepository.findById(dto.id()).orElseThrow().getMaterializedUntil())
        .isEqualTo(horizon.plusDays(3));
  }

  @Test
  @DisplayName("excluded dates are skipped")
  void excludedDatesSkipped() {
    User user = newUser();
    RecurrenceSeries series =
        seriesRepository.saveAndFlush(
            new RecurrenceSeries(
                user.getId(),
                "Gym",
                null,
                null,
                Priority.LOW,
                null,
                daily(EndType.AFTER_COUNT, 5, null).toRule()));
    LocalDate skipped = today().plusDays(2);
    exclusionRepository.saveAndFlush(new SeriesExclusion(series.getId(), skipped));

    int created =
        tx.execute(
            s -> materializationService.materialize(series, materializationService.horizon(ZONE)));

    assertThat(created).isEqualTo(4);
    assertThat(rows(series.getId())).extracting(Todo::getOccurrenceDate).doesNotContain(skipped);
  }

  @Test
  @DisplayName("a cancelled series is not materialised")
  void cancelledNoop() {
    User user = newUser();
    RecurrenceSeries series =
        new RecurrenceSeries(
            user.getId(),
            "Gym",
            null,
            null,
            Priority.LOW,
            null,
            daily(EndType.NEVER, null, null).toRule());
    series.cancel(today());
    RecurrenceSeries saved = seriesRepository.saveAndFlush(series);

    int created =
        tx.execute(
            s -> materializationService.materialize(saved, materializationService.horizon(ZONE)));

    assertThat(created).isZero();
    assertThat(rows(saved.getId())).isEmpty();
  }

  @Test
  @DisplayName("a page of todos from several series needs no more statements than from one")
  void noNPlusOne() {
    Statistics stats = sessionFactory.getStatistics();
    stats.setStatisticsEnabled(true);
    User one = newUser();
    seriesService.create(one.getId(), request(daily(EndType.NEVER, null, null)));
    User four = newUser();
    for (int i = 0; i < 4; i++) {
      seriesService.create(four.getId(), request(daily(EndType.NEVER, null, null)));
    }

    stats.clear();
    todoService.range(one.getId(), today(), today().plusDays(6));
    long withOne = stats.getPrepareStatementCount();
    stats.clear();
    List<TodoDto> page = todoService.range(four.getId(), today(), today().plusDays(6));
    long withFour = stats.getPrepareStatementCount();

    assertThat(page).hasSize(28);
    assertThat(withFour).isEqualTo(withOne);
  }
}
