package com.ntamasa.markitdone.recurrence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ntamasa.markitdone.audit.AuditLogEntry;
import com.ntamasa.markitdone.audit.AuditLogRepository;
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
import com.ntamasa.markitdone.todo.dto.TodoRequest;
import com.ntamasa.markitdone.user.User;
import com.ntamasa.markitdone.user.UserRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class SeriesOccurrenceDeleteIntegrationTest extends AbstractPostgresIntegrationTest {

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
  @Autowired private JdbcTemplate jdbcTemplate;

  private User newUser() {
    User user = testUsers.create("occ-del-" + UUID.randomUUID() + "@example.com");
    user.setTimezone(ZONE.getId());
    return userRepository.save(user);
  }

  private static LocalDate today() {
    return LocalDate.now(ZONE);
  }

  /** A daily series of five occurrences starting today. */
  private SeriesDto createFiveDaily(User user) {
    RuleRequest rule =
        new RuleRequest(
            Frequency.EVERY_N_DAYS, 1, null, null, today(), EndType.AFTER_COUNT, 5, null);
    return seriesService.create(
        user.getId(),
        new SeriesRequest("Gym", null, LocalTime.of(18, 0), Priority.HIGH, null, rule));
  }

  private List<Todo> rows(UUID seriesId) {
    return todoRepository
        .findBySeriesIdAndOccurrenceDateGreaterThanEqual(seriesId, LocalDate.of(2000, 1, 1))
        .stream()
        .sorted(Comparator.comparing(Todo::getOccurrenceDate))
        .toList();
  }

  private Todo dayOf(UUID seriesId, int day) {
    return rows(seriesId).stream()
        .filter(t -> t.getOccurrenceDate().equals(today().plusDays(day - 1)))
        .findFirst()
        .orElseThrow();
  }

  private List<AuditLogEntry> auditEntries(UUID userId, String action) {
    return auditLogRepository.findByUserIdOrderByOccurredAtDesc(userId).stream()
        .filter(e -> e.getAction().equals(action))
        .toList();
  }

  @Test
  @DisplayName(
      "deleting day 3 of a daily series removes the row, records the exclusion and keeps the series")
  void deleteDayThreeRecordsExclusion() {
    User user = newUser();
    SeriesDto series = createFiveDaily(user);
    UUID seriesId = series.id();
    Todo day3 = dayOf(seriesId, 3);

    todoService.delete(user.getId(), day3.getId());

    assertThat(todoRepository.findById(day3.getId())).isEmpty();
    assertThat(exclusionRepository.existsById(new SeriesExclusionId(seriesId, today().plusDays(2))))
        .isTrue();
    assertThat(rows(seriesId))
        .extracting(Todo::getOccurrenceDate)
        .containsExactly(today(), today().plusDays(1), today().plusDays(3), today().plusDays(4));
    assertThat(seriesRepository.findById(seriesId).orElseThrow().getStatus())
        .isEqualTo(SeriesStatus.ACTIVE);
  }

  @Test
  @DisplayName(
      "after the materialised date is set back before day 3, materialising does not recreate day 3")
  void materialisationDoesNotRecreateDeletedOccurrence() {
    User user = newUser();
    SeriesDto series = createFiveDaily(user);
    UUID seriesId = series.id();
    todoService.delete(user.getId(), dayOf(seriesId, 3).getId());
    LocalDate day2 = today().plusDays(1);
    jdbcTemplate.update(
        "update recurrence_series set materialized_until = ? where id = ?", day2, seriesId);

    int created =
        tx.execute(
            s ->
                materializationService.materialize(
                    seriesRepository.findById(seriesId).orElseThrow(),
                    materializationService.horizon(ZONE)));

    assertThat(created).isZero();
    assertThat(rows(seriesId))
        .extracting(Todo::getOccurrenceDate)
        .doesNotContain(today().plusDays(2));
  }

  @Test
  @DisplayName("after 5 daily, deleting day 2 leaves 4 rows and the last one is still day 5")
  void endCountCountsSlotsNotRows() {
    User user = newUser();
    SeriesDto series = createFiveDaily(user);
    UUID seriesId = series.id();

    todoService.delete(user.getId(), dayOf(seriesId, 2).getId());

    List<Todo> remaining = rows(seriesId);
    assertThat(remaining).hasSize(4);
    assertThat(remaining.get(remaining.size() - 1).getOccurrenceDate())
        .isEqualTo(today().plusDays(4));
  }

  @Test
  @DisplayName("deleting a one-off todo still records TODO_DELETED and no occurrence entry")
  void deleteOneOffUnchanged() {
    User user = newUser();
    TodoDto created =
        todoService.create(
            user.getId(),
            new TodoRequest("Once", null, today().plusDays(1), null, Priority.LOW, null));

    todoService.delete(user.getId(), created.id());

    assertThat(todoRepository.findById(created.id())).isEmpty();
    assertThat(auditEntries(user.getId(), "TODO_DELETED")).hasSize(1);
    assertThat(auditEntries(user.getId(), "OCCURRENCE_DELETED")).isEmpty();
  }

  @Test
  @DisplayName("deleting an occurrence audits OCCURRENCE_DELETED with the series and the date")
  void occurrenceDeleteAudited() {
    User user = newUser();
    SeriesDto series = createFiveDaily(user);
    Todo day3 = dayOf(series.id(), 3);

    todoService.delete(user.getId(), day3.getId());

    List<AuditLogEntry> entries = auditEntries(user.getId(), "OCCURRENCE_DELETED");
    assertThat(entries).hasSize(1);
    AuditLogEntry entry = entries.get(0);
    assertThat(entry.getEntityId()).isEqualTo(day3.getId().toString());
    assertThat(entry.getBeforeState()).contains("Gym", today().plusDays(2).toString());
    assertThat(entry.getAfterState())
        .contains(series.id().toString(), today().plusDays(2).toString());
    assertThat(auditEntries(user.getId(), "TODO_DELETED")).isEmpty();
  }

  @Test
  @DisplayName("deleting the same slot again does not create a duplicate exclusion")
  void sameSlotTwiceNoDuplicateExclusion() {
    User user = newUser();
    SeriesDto series = createFiveDaily(user);
    UUID seriesId = series.id();
    LocalDate day3Date = today().plusDays(2);
    todoService.delete(user.getId(), dayOf(seriesId, 3).getId());
    Todo second =
        todoRepository.saveAndFlush(
            Todo.occurrenceOf(seriesRepository.findById(seriesId).orElseThrow(), day3Date));

    todoService.delete(user.getId(), second.getId());

    assertThat(exclusionRepository.findExcludedDates(seriesId)).containsExactly(day3Date);
    assertThat(todoRepository.findById(second.getId())).isEmpty();
  }
}
