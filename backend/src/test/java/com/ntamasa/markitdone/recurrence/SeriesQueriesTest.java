package com.ntamasa.markitdone.recurrence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.ntamasa.markitdone.common.ApiException;
import com.ntamasa.markitdone.common.ErrorCode;
import com.ntamasa.markitdone.recurrence.dto.SeriesDto;
import com.ntamasa.markitdone.todo.NextDueRow;
import com.ntamasa.markitdone.todo.Priority;
import com.ntamasa.markitdone.todo.TodoRepository;
import com.ntamasa.markitdone.user.ThemePreference;
import com.ntamasa.markitdone.user.User;
import com.ntamasa.markitdone.user.UserRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests of the read side of series. "Today" is 2026-11-02 in Pacific/Auckland, while UTC still
 * says 2026-11-01.
 */
@ExtendWith(MockitoExtension.class)
class SeriesQueriesTest {

  private static final String ZONE = "Pacific/Auckland";
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);
  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID OTHER_ID = UUID.randomUUID();

  @Mock private RecurrenceSeriesRepository seriesRepository;
  @Mock private TodoRepository todoRepository;
  @Mock private UserRepository userRepository;

  private SeriesQueries queries;

  private RecurrenceSeries gym;
  private RecurrenceSeries read;
  private RecurrenceSeries oldCourse;
  private RecurrenceSeries yoga;

  private final UUID gymNextId = UUID.randomUUID();
  private final UUID readNextId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(Instant.parse("2026-11-01T20:00:00Z"), ZoneOffset.UTC);
    queries = new SeriesQueries(seriesRepository, todoRepository, userRepository, clock);

    gym =
        stored(
            "Gym",
            new RecurrenceRule(
                Frequency.WEEKLY,
                null,
                Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                null,
                LocalDate.of(2026, 10, 21),
                EndType.AFTER_COUNT,
                20,
                null));
    read =
        stored(
            "Read",
            new RecurrenceRule(
                Frequency.EVERY_N_DAYS,
                1,
                null,
                null,
                LocalDate.of(2026, 11, 1),
                EndType.NEVER,
                null,
                null));
    oldCourse =
        stored(
            "Old course",
            new RecurrenceRule(
                Frequency.MONTHLY,
                null,
                null,
                Set.of(2),
                LocalDate.of(2026, 5, 2),
                EndType.AFTER_COUNT,
                6,
                null));
    yoga =
        stored(
            "Yoga",
            new RecurrenceRule(
                Frequency.EVERY_N_DAYS,
                7,
                null,
                null,
                LocalDate.of(2026, 9, 1),
                EndType.NEVER,
                null,
                null));
    yoga.cancel(LocalDate.of(2026, 10, 6));

    lenient().when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
    lenient().when(userRepository.findById(OTHER_ID)).thenReturn(Optional.of(user(OTHER_ID)));
    lenient()
        .when(seriesRepository.findByUserIdOrderByNameAsc(USER_ID))
        .thenReturn(List.of(oldCourse, gym, read, yoga));
    lenient()
        .when(todoRepository.findNextRows(anyCollection(), eq(TODAY)))
        .thenReturn(
            List.of(
                row(read.getId(), readNextId, LocalDate.of(2026, 11, 3)),
                row(gym.getId(), gymNextId, TODAY)));
  }

  private static User user(UUID id) {
    User user = new User("u@example.com", "User", "hash", ZONE, ThemePreference.SYSTEM);
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }

  private static RecurrenceSeries stored(String name, RecurrenceRule rule) {
    RecurrenceSeries series =
        new RecurrenceSeries(USER_ID, name, null, null, Priority.LOW, null, rule);
    ReflectionTestUtils.setField(series, "id", UUID.randomUUID());
    return series;
  }

  private static NextDueRow row(UUID seriesId, UUID id, LocalDate due) {
    return new NextDueRow() {
      @Override
      public UUID getSeriesId() {
        return seriesId;
      }

      @Override
      public UUID getId() {
        return id;
      }

      @Override
      public LocalDate getDueDate() {
        return due;
      }
    };
  }

  private static List<String> names(List<SeriesDto> dtos) {
    return dtos.stream().map(SeriesDto::name).toList();
  }

  @Test
  @DisplayName("the default ACTIVE filter returns the two active series sorted by next occurrence")
  void defaultReturnsActiveSortedByNextOccurrence() {
    List<SeriesDto> result = queries.list(USER_ID, null, null);

    assertThat(names(result)).containsExactly("Gym", "Read");
    assertThat(result.get(0).nextOccurrence()).isEqualTo(TODAY);
    assertThat(result.get(0).nextOccurrenceTodoId()).isEqualTo(gymNextId);
    assertThat(result.get(1).nextOccurrence()).isEqualTo(LocalDate.of(2026, 11, 3));
    assertThat(result.get(1).nextOccurrenceTodoId()).isEqualTo(readNextId);
  }

  @Test
  @DisplayName("ACTIVE with an explicit status value gives the same result as the default")
  void explicitActiveSameAsDefault() {
    assertThat(names(queries.list(USER_ID, "ACTIVE", null))).containsExactly("Gym", "Read");
  }

  @Test
  @DisplayName("ENDED returns the monthly series with the summary After 6 (ended)")
  void endedReturnsEndedSeries() {
    List<SeriesDto> result = queries.list(USER_ID, "ENDED", null);

    assertThat(names(result)).containsExactly("Old course");
    SeriesDto ended = result.get(0);
    assertThat(ended.status()).isEqualTo(SeriesStatus.ENDED);
    assertThat(ended.endsSummary()).isEqualTo("After 6 (ended)");
    assertThat(ended.nextOccurrence()).isNull();
    assertThat(ended.nextOccurrenceTodoId()).isNull();
  }

  @Test
  @DisplayName("CANCELLED returns the series with the summary Cancelled Oct 6")
  void cancelledReturnsCancelledSeries() {
    List<SeriesDto> result = queries.list(USER_ID, "CANCELLED", null);

    assertThat(names(result)).containsExactly("Yoga");
    SeriesDto cancelled = result.get(0);
    assertThat(cancelled.status()).isEqualTo(SeriesStatus.CANCELLED);
    assertThat(cancelled.endsSummary()).isEqualTo("Cancelled Oct 6");
    assertThat(cancelled.cancelledOn()).isEqualTo(LocalDate.of(2026, 10, 6));
    assertThat(cancelled.nextOccurrence()).isNull();
  }

  @Test
  @DisplayName("ALL returns every series, the ones without next occurrence last, then by name")
  void allReturnsEverythingSorted() {
    List<SeriesDto> result = queries.list(USER_ID, "ALL", null);

    assertThat(names(result)).containsExactly("Gym", "Read", "Old course", "Yoga");
  }

  @Test
  @DisplayName("the search is a case-insensitive contains on the trimmed name")
  void searchMatchesCaseInsensitiveContains() {
    assertThat(names(queries.list(USER_ID, null, "gy"))).containsExactly("Gym");
    assertThat(names(queries.list(USER_ID, null, "  GY  "))).containsExactly("Gym");
    assertThat(names(queries.list(USER_ID, null, "   "))).containsExactly("Gym", "Read");
  }

  @Test
  @DisplayName("an unknown status value gives 400 BAD_REQUEST")
  void invalidStatusIsBadRequest() {
    assertThatThrownBy(() -> queries.list(USER_ID, "PAUSED", null))
        .isInstanceOf(ApiException.class)
        .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.BAD_REQUEST));
    assertThatThrownBy(() -> queries.list(USER_ID, "active", null))
        .isInstanceOf(ApiException.class)
        .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.BAD_REQUEST));
  }

  @Test
  @DisplayName("another user sees none of the series")
  void otherUserSeesNothing() {
    when(seriesRepository.findByUserIdOrderByNameAsc(OTHER_ID)).thenReturn(List.of());

    assertThat(queries.list(OTHER_ID, "ALL", null)).isEmpty();
  }

  @Test
  @DisplayName("get returns the owner's series")
  void getReturnsOwnSeries() {
    when(seriesRepository.findByIdAndUserId(gym.getId(), USER_ID)).thenReturn(Optional.of(gym));

    SeriesDto dto = queries.get(USER_ID, gym.getId());

    assertThat(dto.name()).isEqualTo("Gym");
    assertThat(dto.endsSummary()).isEqualTo("After 20 (15 left)");
    assertThat(dto.nextOccurrenceTodoId()).isEqualTo(gymNextId);
  }

  @Test
  @DisplayName("get of another user's or an unknown series is 404 Series not found")
  void getOtherOrUnknownIsNotFound() {
    when(seriesRepository.findByIdAndUserId(gym.getId(), OTHER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> queries.get(OTHER_ID, gym.getId()))
        .isInstanceOf(ApiException.class)
        .hasMessage("Series not found")
        .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.NOT_FOUND));
  }

  @Test
  @DisplayName("the ends summary is Never for a series without end")
  void endsSummaryNever() {
    assertThat(queries.toDto(read).endsSummary()).isEqualTo("Never");
  }

  @Test
  @DisplayName("the ends summary is On {date} for a series ending on a date still to come")
  void endsSummaryOnDate() {
    RecurrenceSeries until =
        stored(
            "Until",
            new RecurrenceRule(
                Frequency.EVERY_N_DAYS,
                1,
                null,
                null,
                LocalDate.of(2026, 10, 1),
                EndType.ON_DATE,
                null,
                LocalDate.of(2026, 12, 31)));

    SeriesDto dto = queries.toDto(until);

    assertThat(dto.status()).isEqualTo(SeriesStatus.ACTIVE);
    assertThat(dto.endsSummary()).isEqualTo("On Dec 31, 2026");
  }

  @Test
  @DisplayName("a series whose end date is before today is ENDED with no next occurrence")
  void endDateInPastIsEnded() {
    RecurrenceSeries until =
        stored(
            "Until",
            new RecurrenceRule(
                Frequency.EVERY_N_DAYS,
                1,
                null,
                null,
                LocalDate.of(2026, 10, 1),
                EndType.ON_DATE,
                null,
                LocalDate.of(2026, 10, 31)));

    SeriesDto dto = queries.toDto(until);

    assertThat(dto.status()).isEqualTo(SeriesStatus.ENDED);
    assertThat(dto.endsSummary()).isEqualTo("On Oct 31, 2026");
    assertThat(dto.nextOccurrence()).isNull();
  }

  @Test
  @DisplayName("a series whose last occurrence is today is still ACTIVE with one left")
  void lastOccurrenceTodayIsActive() {
    RecurrenceSeries twice =
        stored(
            "Twice",
            new RecurrenceRule(
                Frequency.MONTHLY,
                null,
                null,
                Set.of(2),
                LocalDate.of(2026, 10, 2),
                EndType.AFTER_COUNT,
                2,
                null));

    SeriesDto dto = queries.toDto(twice);

    assertThat(dto.status()).isEqualTo(SeriesStatus.ACTIVE);
    assertThat(dto.endsSummary()).isEqualTo("After 2 (1 left)");
  }
}
