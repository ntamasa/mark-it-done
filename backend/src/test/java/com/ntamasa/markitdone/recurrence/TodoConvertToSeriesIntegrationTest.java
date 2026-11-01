package com.ntamasa.markitdone.recurrence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ntamasa.markitdone.audit.AuditLogEntry;
import com.ntamasa.markitdone.audit.AuditLogRepository;
import com.ntamasa.markitdone.common.ApiException;
import com.ntamasa.markitdone.common.ErrorCode;
import com.ntamasa.markitdone.recurrence.dto.RuleRequest;
import com.ntamasa.markitdone.recurrence.dto.SeriesDto;
import com.ntamasa.markitdone.support.AbstractPostgresIntegrationTest;
import com.ntamasa.markitdone.support.TestUsers;
import com.ntamasa.markitdone.todo.Priority;
import com.ntamasa.markitdone.todo.Todo;
import com.ntamasa.markitdone.todo.TodoRepository;
import com.ntamasa.markitdone.user.User;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * Converting a one-off todo into a series. The clock is fixed at 2026-10-20 10:00 UTC, so the
 * horizon of the user (UTC) covers the dates used here.
 */
@Import(TodoConvertToSeriesIntegrationTest.FixedClockConfig.class)
class TodoConvertToSeriesIntegrationTest extends AbstractPostgresIntegrationTest {

  private static final LocalDate WED = LocalDate.of(2026, 10, 21);
  private static final LocalDate TUE = LocalDate.of(2026, 10, 20);
  private static final LocalDate MON_26 = LocalDate.of(2026, 10, 26);
  private static final LocalDate WED_28 = LocalDate.of(2026, 10, 28);
  private static final LocalDate MON_NOV_2 = LocalDate.of(2026, 11, 2);

  @TestConfiguration
  static class FixedClockConfig {
    @Bean
    @Primary
    Clock fixedClock() {
      return Clock.fixed(Instant.parse("2026-10-20T10:00:00Z"), ZoneOffset.UTC);
    }
  }

  @Autowired private SeriesService seriesService;
  @Autowired private TodoRepository todoRepository;
  @Autowired private RecurrenceSeriesRepository seriesRepository;
  @Autowired private AuditLogRepository auditLogRepository;
  @Autowired private TestUsers testUsers;

  private User newUser() {
    return testUsers.create("convert-" + UUID.randomUUID() + "@example.com");
  }

  private Todo oneOff(User user, LocalDate due) {
    return todoRepository.saveAndFlush(
        new Todo(
            user.getId(), "Standup", "daily sync", due, LocalTime.of(9, 30), Priority.HIGH, null));
  }

  private static RuleRequest weeklyOn(LocalDate start, DayOfWeek... days) {
    return new RuleRequest(
        Frequency.WEEKLY, null, Set.of(days), null, start, EndType.NEVER, null, null);
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
  @DisplayName("a Wednesday todo with a weekly Mon/Wed rule joins the series on its own date")
  void onSlotTodoBecomesMemberWithoutDuplicate() {
    User user = newUser();
    Todo todo = oneOff(user, WED);

    SeriesDto dto =
        seriesService.convertTodo(
            user.getId(), todo.getId(), weeklyOn(WED, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY));

    Todo converted = todoRepository.findById(todo.getId()).orElseThrow();
    assertThat(converted.getSeriesId()).isEqualTo(dto.id());
    assertThat(converted.getOccurrenceDate()).isEqualTo(WED);
    assertThat(converted.getDueDate()).isEqualTo(WED);
    assertThat(converted.isDetached()).isFalse();
    List<Todo> all = rows(dto.id());
    assertThat(all.stream().filter(t -> t.getOccurrenceDate().equals(WED)))
        .hasSize(1)
        .extracting(Todo::getId)
        .containsExactly(todo.getId());
    assertThat(all).extracting(Todo::getOccurrenceDate).contains(WED, MON_26, WED_28, MON_NOV_2);
    assertThat(all.get(0).getOccurrenceDate()).isEqualTo(WED);
    assertThat(all.get(1).getOccurrenceDate()).isEqualTo(MON_26);
    assertThat(all.get(2).getOccurrenceDate()).isEqualTo(WED_28);
  }

  @Test
  @DisplayName(
      "a Tuesday todo with a weekly Monday rule stays on Tuesday, detached, and Mondays are generated")
  void offSlotTodoIsDetached() {
    User user = newUser();
    Todo todo = oneOff(user, TUE);

    SeriesDto dto =
        seriesService.convertTodo(user.getId(), todo.getId(), weeklyOn(TUE, DayOfWeek.MONDAY));

    Todo converted = todoRepository.findById(todo.getId()).orElseThrow();
    assertThat(converted.getSeriesId()).isEqualTo(dto.id());
    assertThat(converted.getDueDate()).isEqualTo(TUE);
    assertThat(converted.getOccurrenceDate()).isEqualTo(TUE);
    assertThat(converted.isDetached()).isTrue();
    List<Todo> all = rows(dto.id());
    assertThat(all).extracting(Todo::getOccurrenceDate).contains(TUE, MON_26, MON_NOV_2);
    assertThat(all.stream().filter(t -> t.getOccurrenceDate().equals(TUE)))
        .extracting(Todo::getId)
        .containsExactly(todo.getId());
    assertThat(all.stream().filter(t -> !t.getOccurrenceDate().equals(TUE)))
        .allSatisfy(t -> assertThat(t.isDetached()).isFalse());
  }

  @Test
  @DisplayName("a done todo stays done after conversion")
  void doneStaysDone() {
    User user = newUser();
    Todo todo = oneOff(user, WED);
    todo.setDone(true);
    todoRepository.saveAndFlush(todo);

    SeriesDto dto =
        seriesService.convertTodo(
            user.getId(), todo.getId(), weeklyOn(WED, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY));

    Todo converted = todoRepository.findById(todo.getId()).orElseThrow();
    assertThat(converted.isDone()).isTrue();
    assertThat(converted.getDoneAt()).isNotNull();
    assertThat(rows(dto.id()).stream().filter(t -> t.getOccurrenceDate().equals(WED)))
        .singleElement()
        .satisfies(t -> assertThat(t.isDone()).isTrue());
  }

  @Test
  @DisplayName(
      "the new series copies the todo's template fields and the audit records both changes")
  void copiesTemplateAndAudits() {
    User user = newUser();
    Todo todo = oneOff(user, WED);

    SeriesDto dto =
        seriesService.convertTodo(user.getId(), todo.getId(), weeklyOn(WED, DayOfWeek.WEDNESDAY));

    assertThat(dto.name()).isEqualTo("Standup");
    assertThat(dto.description()).isEqualTo("daily sync");
    assertThat(dto.dueTime()).isEqualTo(LocalTime.of(9, 30));
    assertThat(dto.priority()).isEqualTo(Priority.HIGH);
    assertThat(dto.status()).isEqualTo(SeriesStatus.ACTIVE);
    assertThat(seriesRepository.findById(dto.id())).isPresent();

    List<AuditLogEntry> created = auditEntries(user.getId(), "SERIES_CREATED");
    assertThat(created).hasSize(1);
    assertThat(created.get(0).getEntityId()).isEqualTo(dto.id().toString());
    List<AuditLogEntry> updated = auditEntries(user.getId(), "TODO_UPDATED");
    assertThat(updated).hasSize(1);
    assertThat(updated.get(0).getEntityId()).isEqualTo(todo.getId().toString());
  }

  @Test
  @DisplayName(
      "converting a todo that is already recurring gives 400 BAD_REQUEST and changes nothing")
  void convertingTwiceIsBadRequest() {
    User user = newUser();
    Todo todo = oneOff(user, WED);
    SeriesDto first =
        seriesService.convertTodo(user.getId(), todo.getId(), weeklyOn(WED, DayOfWeek.WEDNESDAY));
    int seriesCountBefore = seriesRepository.findByUserIdOrderByNameAsc(user.getId()).size();

    assertThatThrownBy(
            () ->
                seriesService.convertTodo(
                    user.getId(), todo.getId(), weeklyOn(WED, DayOfWeek.WEDNESDAY)))
        .isInstanceOf(ApiException.class)
        .hasMessage("This todo is already recurring")
        .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.BAD_REQUEST));
    assertThat(seriesRepository.findByUserIdOrderByNameAsc(user.getId()))
        .hasSize(seriesCountBefore);
    assertThat(todoRepository.findById(todo.getId()).orElseThrow().getSeriesId())
        .isEqualTo(first.id());
  }

  @Test
  @DisplayName("a rule that starts before the todo's date gives 400 VALIDATION_FAILED")
  void startBeforeTodoDateIsRejected() {
    User user = newUser();
    Todo todo = oneOff(user, WED);

    assertThatThrownBy(
            () ->
                seriesService.convertTodo(
                    user.getId(), todo.getId(), weeklyOn(TUE, DayOfWeek.MONDAY)))
        .isInstanceOf(ApiException.class)
        .hasMessage("Start date cannot be before the todo's date")
        .satisfies(
            e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    assertThat(todoRepository.findById(todo.getId()).orElseThrow().getSeriesId()).isNull();
  }

  @Test
  @DisplayName("an invalid rule gives 400 VALIDATION_FAILED and the todo stays one-off")
  void invalidRuleIsRejected() {
    User user = newUser();
    Todo todo = oneOff(user, WED);
    RuleRequest noWeekdays =
        new RuleRequest(Frequency.WEEKLY, null, null, null, WED, EndType.NEVER, null, null);

    assertThatThrownBy(() -> seriesService.convertTodo(user.getId(), todo.getId(), noWeekdays))
        .isInstanceOf(ApiException.class)
        .satisfies(
            e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    assertThat(todoRepository.findById(todo.getId()).orElseThrow().getSeriesId()).isNull();
  }

  @Test
  @DisplayName("another user's todo gives 404 and an unknown id gives the same 404")
  void otherUserGetsNotFound() {
    User owner = newUser();
    User other = newUser();
    Todo todo = oneOff(owner, WED);
    RuleRequest rule = weeklyOn(WED, DayOfWeek.WEDNESDAY);

    assertThatThrownBy(() -> seriesService.convertTodo(other.getId(), todo.getId(), rule))
        .isInstanceOf(ApiException.class)
        .hasMessage("Todo not found")
        .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.NOT_FOUND));
    assertThatThrownBy(() -> seriesService.convertTodo(other.getId(), UUID.randomUUID(), rule))
        .isInstanceOf(ApiException.class)
        .hasMessage("Todo not found");
    assertThat(todoRepository.findById(todo.getId()).orElseThrow().getSeriesId()).isNull();
  }
}
