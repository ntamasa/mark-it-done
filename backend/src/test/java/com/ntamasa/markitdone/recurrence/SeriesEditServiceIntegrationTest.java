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
import com.ntamasa.markitdone.todo.EditScope;
import com.ntamasa.markitdone.todo.Priority;
import com.ntamasa.markitdone.todo.Todo;
import com.ntamasa.markitdone.todo.TodoRepository;
import com.ntamasa.markitdone.todo.dto.TodoDto;
import com.ntamasa.markitdone.todo.dto.TodoRequest;
import com.ntamasa.markitdone.user.User;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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

@Import(SeriesEditServiceIntegrationTest.FixedClockConfig.class)
class SeriesEditServiceIntegrationTest extends AbstractPostgresIntegrationTest {

  private static final LocalDate START = LocalDate.of(2026, 11, 2);

  @TestConfiguration
  static class FixedClockConfig {
    @Bean
    @Primary
    Clock fixedClock() {
      return Clock.fixed(Instant.parse("2026-11-02T08:00:00Z"), ZoneOffset.UTC);
    }
  }

  @Autowired private SeriesEditService editService;
  @Autowired private SeriesService seriesService;
  @Autowired private TodoRepository todoRepository;
  @Autowired private RecurrenceSeriesRepository seriesRepository;
  @Autowired private AuditLogRepository auditLogRepository;
  @Autowired private TestUsers testUsers;

  private User newUser() {
    return testUsers.create("edit-" + UUID.randomUUID() + "@example.com");
  }

  private SeriesDto dailySeries(User user) {
    return seriesService.create(
        user.getId(),
        new SeriesRequest(
            "Daily",
            null,
            null,
            Priority.LOW,
            null,
            new RuleRequest(
                Frequency.EVERY_N_DAYS, 1, null, null, START, EndType.NEVER, null, null)));
  }

  private Todo row(UUID seriesId, LocalDate date) {
    return todoRepository.findBySeriesIdAndOccurrenceDateGreaterThanEqual(seriesId, date).stream()
        .filter(t -> date.equals(t.getOccurrenceDate()))
        .findFirst()
        .orElseThrow();
  }

  private static TodoRequest body(String name, LocalDate due, RuleRequest rule) {
    return new TodoRequest(name, null, due, null, Priority.LOW, null, rule);
  }

  private static RuleRequest weeklyMonday(LocalDate start) {
    return new RuleRequest(
        Frequency.WEEKLY, null, Set.of(DayOfWeek.MONDAY), null, start, EndType.NEVER, null, null);
  }

  @Test
  @DisplayName("THIS changes only that row and detaches it")
  void thisDetachesOneRow() {
    User user = newUser();
    SeriesDto series = dailySeries(user);
    Todo nov4 = row(series.id(), LocalDate.of(2026, 11, 4));

    TodoDto dto =
        editService.update(
            user.getId(), nov4.getId(), EditScope.THIS, body("X", nov4.getDueDate(), null));

    assertThat(dto.name()).isEqualTo("X");
    Todo changed = row(series.id(), LocalDate.of(2026, 11, 4));
    assertThat(changed.getName()).isEqualTo("X");
    assertThat(changed.isDetached()).isTrue();
    assertThat(row(series.id(), LocalDate.of(2026, 11, 5)).getName()).isEqualTo("Daily");
    assertThat(row(series.id(), LocalDate.of(2026, 11, 3)).isDetached()).isFalse();
  }

  @Test
  @DisplayName("THIS with a new date moves only that row and keeps its slot")
  void thisMovesDate() {
    User user = newUser();
    SeriesDto series = dailySeries(user);
    Todo nov4 = row(series.id(), LocalDate.of(2026, 11, 4));

    editService.update(
        user.getId(),
        nov4.getId(),
        EditScope.THIS,
        body("Moved", LocalDate.of(2026, 11, 20), null));

    Todo moved = row(series.id(), LocalDate.of(2026, 11, 4));
    assertThat(moved.getDueDate()).isEqualTo(LocalDate.of(2026, 11, 20));
    assertThat(moved.isDetached()).isTrue();
    assertThat(
            todoRepository.findOccurrenceDates(
                series.id(), LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 4)))
        .hasSize(1);
  }

  @Test
  @DisplayName("FUTURE template change spares detached rows and past rows")
  void futureTemplate() {
    User user = newUser();
    SeriesDto series = dailySeries(user);
    editService.update(
        user.getId(),
        row(series.id(), LocalDate.of(2026, 11, 8)).getId(),
        EditScope.THIS,
        body("Z", LocalDate.of(2026, 11, 8), null));
    editService.update(
        user.getId(),
        row(series.id(), LocalDate.of(2026, 11, 4)).getId(),
        EditScope.THIS,
        body("X", LocalDate.of(2026, 11, 4), null));

    Todo nov6 = row(series.id(), LocalDate.of(2026, 11, 6));
    TodoDto dto =
        editService.update(
            user.getId(), nov6.getId(), EditScope.FUTURE, body("Y", nov6.getDueDate(), null));

    assertThat(dto.name()).isEqualTo("Y");
    for (int day : List.of(6, 7, 9, 10, 11)) {
      assertThat(row(series.id(), LocalDate.of(2026, 11, day)).getName()).isEqualTo("Y");
    }
    assertThat(row(series.id(), LocalDate.of(2026, 11, 8)).getName()).isEqualTo("Z");
    assertThat(row(series.id(), LocalDate.of(2026, 11, 4)).getName()).isEqualTo("X");
    assertThat(row(series.id(), LocalDate.of(2026, 11, 2)).getName()).isEqualTo("Daily");
    assertThat(row(series.id(), LocalDate.of(2026, 11, 5)).getName()).isEqualTo("Daily");
    assertThat(seriesRepository.findById(series.id()).orElseThrow().getName()).isEqualTo("Y");
    assertThat(auditLogRepository.findAll())
        .extracting(AuditLogEntry::getAction)
        .contains("SERIES_UPDATED", "TODO_UPDATED");
  }

  @Test
  @DisplayName("FUTURE rule change splits the series")
  void futureRuleSplit() {
    User user = newUser();
    SeriesDto series = dailySeries(user);
    Todo nov10 = row(series.id(), LocalDate.of(2026, 11, 10));

    TodoDto dto =
        editService.update(
            user.getId(),
            nov10.getId(),
            EditScope.FUTURE,
            body("Daily", nov10.getDueDate(), weeklyMonday(LocalDate.of(2026, 11, 10))));

    assertThat(dto).isNull();
    RecurrenceRule old = seriesRepository.findById(series.id()).orElseThrow().rule();
    assertThat(old.endType()).isEqualTo(EndType.ON_DATE);
    assertThat(old.endDate()).isEqualTo(LocalDate.of(2026, 11, 9));
    assertThat(
            todoRepository.findBySeriesIdAndOccurrenceDateGreaterThanEqual(
                series.id(), LocalDate.of(2026, 11, 10)))
        .isEmpty();
    assertThat(row(series.id(), LocalDate.of(2026, 11, 9))).isNotNull();
    List<RecurrenceSeries> all =
        seriesRepository.findByUserIdOrderByNameAsc(user.getId()).stream()
            .filter(s -> !s.getId().equals(series.id()))
            .toList();
    assertThat(all).hasSize(1);
    UUID newId = all.getFirst().getId();
    List<Todo> rows = todoRepository.findBySeriesIdAndOccurrenceDateGreaterThanEqual(newId, START);
    assertThat(rows).isNotEmpty();
    assertThat(rows)
        .extracting(Todo::getOccurrenceDate)
        .allMatch(d -> d.getDayOfWeek() == DayOfWeek.MONDAY)
        .first()
        .isEqualTo(LocalDate.of(2026, 11, 16));
  }

  @Test
  @DisplayName("FUTURE rule change on the first occurrence replaces the series in place")
  void futureRuleInPlace() {
    User user = newUser();
    SeriesDto series = dailySeries(user);
    Todo nov2 = row(series.id(), START);

    TodoDto dto =
        editService.update(
            user.getId(),
            nov2.getId(),
            EditScope.FUTURE,
            body("Daily", nov2.getDueDate(), weeklyMonday(START)));

    assertThat(dto).isNotNull();
    assertThat(dto.seriesId()).isEqualTo(series.id());
    assertThat(seriesRepository.findByUserIdOrderByNameAsc(user.getId())).hasSize(1);
    assertThat(seriesRepository.findById(series.id()).orElseThrow().rule().frequency())
        .isEqualTo(Frequency.WEEKLY);
    assertThat(todoRepository.findBySeriesIdAndOccurrenceDateGreaterThanEqual(series.id(), START))
        .extracting(Todo::getOccurrenceDate)
        .allMatch(d -> d.getDayOfWeek() == DayOfWeek.MONDAY);
  }

  @Test
  @DisplayName("FUTURE with an unchanged rule only updates the template")
  void futureSameRuleIsTemplateOnly() {
    User user = newUser();
    SeriesDto series = dailySeries(user);
    Todo nov6 = row(series.id(), LocalDate.of(2026, 11, 6));
    RuleRequest same =
        new RuleRequest(
            Frequency.EVERY_N_DAYS,
            1,
            Set.of(DayOfWeek.FRIDAY),
            null,
            nov6.getDueDate(),
            EndType.NEVER,
            5,
            null);

    editService.update(
        user.getId(), nov6.getId(), EditScope.FUTURE, body("Y", nov6.getDueDate(), same));

    assertThat(seriesRepository.findByUserIdOrderByNameAsc(user.getId())).hasSize(1);
    assertThat(row(series.id(), LocalDate.of(2026, 11, 7)).getName()).isEqualTo("Y");
  }

  @Test
  @DisplayName("FUTURE with a changed date is rejected")
  void futureWithDateChange() {
    User user = newUser();
    SeriesDto series = dailySeries(user);
    Todo nov6 = row(series.id(), LocalDate.of(2026, 11, 6));

    assertThatThrownBy(
            () ->
                editService.update(
                    user.getId(),
                    nov6.getId(),
                    EditScope.FUTURE,
                    body("Y", LocalDate.of(2026, 11, 7), null)))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getCode()).isEqualTo(ErrorCode.BAD_REQUEST);
              assertThat(e.getMessage()).isEqualTo("Change the date with 'Only this occurrence'");
            });
  }

  @Test
  @DisplayName("a new rule that starts before the occurrence is rejected")
  void ruleStartsBeforeOccurrence() {
    User user = newUser();
    SeriesDto series = dailySeries(user);
    Todo nov6 = row(series.id(), LocalDate.of(2026, 11, 6));

    assertThatThrownBy(
            () ->
                editService.update(
                    user.getId(),
                    nov6.getId(),
                    EditScope.FUTURE,
                    body("Y", nov6.getDueDate(), weeklyMonday(LocalDate.of(2026, 11, 3)))))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.BAD_REQUEST));
  }

  @Test
  @DisplayName("an occurrence without a scope is rejected")
  void missingScope() {
    User user = newUser();
    SeriesDto series = dailySeries(user);
    Todo nov6 = row(series.id(), LocalDate.of(2026, 11, 6));

    assertThatThrownBy(
            () ->
                editService.update(
                    user.getId(), nov6.getId(), null, body("Y", nov6.getDueDate(), null)))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getCode()).isEqualTo(ErrorCode.BAD_REQUEST);
              assertThat(e.getMessage()).isEqualTo("scope is required for recurring todos");
            });
  }

  @Test
  @DisplayName("another user gets not found")
  void otherUser() {
    User owner = newUser();
    User other = newUser();
    SeriesDto series = dailySeries(owner);
    Todo nov6 = row(series.id(), LocalDate.of(2026, 11, 6));

    assertThatThrownBy(
            () ->
                editService.update(
                    other.getId(),
                    nov6.getId(),
                    EditScope.THIS,
                    body("Y", nov6.getDueDate(), null)))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
  }

  @Test
  @DisplayName("a one-off todo ignores the scope")
  void oneOffIgnoresScope() {
    User user = newUser();
    Todo todo =
        todoRepository.save(new Todo(user.getId(), "One", null, START, null, Priority.LOW, null));

    TodoDto dto = editService.update(user.getId(), todo.getId(), null, body("Two", START, null));

    assertThat(dto.name()).isEqualTo("Two");
    assertThat(dto.seriesId()).isNull();
  }
}
