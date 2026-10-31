package com.ntamasa.markitdone.todo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TodoTest {

  private static final ZoneId ZONE = ZoneId.of("Europe/Budapest");
  private static final LocalDate TODAY = LocalDate.of(2026, 3, 10);

  private static Todo timed(LocalDate date, LocalTime time) {
    return new Todo(null, "Task", null, date, time, Priority.MEDIUM, null);
  }

  private static Todo allDay(LocalDate date) {
    return new Todo(null, "Task", null, date, null, Priority.MEDIUM, null);
  }

  @Test
  @DisplayName("timed todo due today at 23:59 is not expired at 12:00")
  void timedLaterTodayIsNotExpired() {
    Todo todo = timed(TODAY, LocalTime.of(23, 59));

    assertThat(todo.isExpired(LocalDateTime.of(TODAY, LocalTime.NOON))).isFalse();
  }

  @Test
  @DisplayName("all-day todo due yesterday is expired today")
  void allDayYesterdayIsExpired() {
    Todo todo = allDay(TODAY.minusDays(1));

    assertThat(todo.isExpired(LocalDateTime.of(TODAY, LocalTime.MIDNIGHT))).isTrue();
  }

  @Test
  @DisplayName("all-day todo due today is not expired until the next day starts")
  void allDayTodayIsNotExpired() {
    Todo todo = allDay(TODAY);

    assertThat(todo.isExpired(LocalDateTime.of(TODAY, LocalTime.of(23, 59, 59)))).isFalse();
    assertThat(todo.isExpired(LocalDateTime.of(TODAY.plusDays(1), LocalTime.MIDNIGHT))).isTrue();
  }

  @Test
  @DisplayName("timed todo due at 08:00 is expired at 09:00")
  void timedPastIsExpired() {
    Todo todo = timed(TODAY, LocalTime.of(8, 0));

    assertThat(todo.isExpired(LocalDateTime.of(TODAY, LocalTime.of(9, 0)))).isTrue();
  }

  @Test
  @DisplayName("timed todo due at 08:00 is expired at exactly 08:00")
  void timedAtDueTimeIsExpired() {
    Todo todo = timed(TODAY, LocalTime.of(8, 0));

    assertThat(todo.isExpired(LocalDateTime.of(TODAY, LocalTime.of(8, 0)))).isTrue();
  }

  @Test
  @DisplayName("isExpired(zone) uses the current time in the zone")
  void expiredInZoneUsesCurrentTime() {
    assertThat(allDay(LocalDate.of(2000, 1, 1)).isExpired(ZONE)).isTrue();
    assertThat(allDay(LocalDate.of(2999, 1, 1)).isExpired(ZONE)).isFalse();
  }

  @Test
  @DisplayName("setDone(true) records the moment and setDone(false) clears it")
  void setDoneRecordsAndClears() {
    Todo todo = allDay(TODAY);
    assertThat(todo.isDone()).isFalse();
    assertThat(todo.getDoneAt()).isNull();

    todo.setDone(true);
    assertThat(todo.isDone()).isTrue();
    assertThat(todo.getDoneAt()).isNotNull();

    todo.setDone(false);
    assertThat(todo.isDone()).isFalse();
    assertThat(todo.getDoneAt()).isNull();
  }
}
