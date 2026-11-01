package com.ntamasa.markitdone.recurrence;

import com.ntamasa.markitdone.common.ApiException;
import com.ntamasa.markitdone.common.ErrorCode;
import com.ntamasa.markitdone.recurrence.dto.RuleRequest;
import com.ntamasa.markitdone.recurrence.dto.SeriesDto;
import com.ntamasa.markitdone.todo.NextDueRow;
import com.ntamasa.markitdone.todo.TodoRepository;
import com.ntamasa.markitdone.user.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of recurring series: the API view with derived status, ends summary and next
 * occurrence, and the list and detail queries. "Today" is the owner's day in the owner's time zone.
 */
@Service
public class SeriesQueries {

  private static final DateTimeFormatter MONTH_DAY =
      DateTimeFormatter.ofPattern("MMM d", Locale.US);
  private static final DateTimeFormatter MONTH_DAY_YEAR =
      DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);

  private final RecurrenceSeriesRepository seriesRepository;
  private final TodoRepository todoRepository;
  private final UserRepository userRepository;
  private final Clock clock;

  /**
   * Create a SeriesQueries.
   *
   * @param seriesRepository the series repository
   * @param todoRepository the todo repository, used to find the next occurrence
   * @param userRepository the user repository, used to find the time zone of the owner
   * @param clock the clock that decides what "today" is
   */
  public SeriesQueries(
      RecurrenceSeriesRepository seriesRepository,
      TodoRepository todoRepository,
      UserRepository userRepository,
      Clock clock) {
    this.seriesRepository = seriesRepository;
    this.todoRepository = todoRepository;
    this.userRepository = userRepository;
    this.clock = clock;
  }

  /**
   * Maps one series to its API shape, with the derived status, the ends summary and the next
   * occurrence as of today in the owner's time zone.
   *
   * @param series the series
   * @return the API representation
   */
  @Transactional(readOnly = true)
  public SeriesDto toDto(RecurrenceSeries series) {
    ZoneId zone = zone(series.getUserId());
    return toDtos(List.of(series), zone, today(zone)).get(0);
  }

  /**
   * Returns one series of the user.
   *
   * @param userId the owner
   * @param id the series id
   * @return the series
   * @throws ApiException NOT_FOUND when the series is not the user's
   */
  @Transactional(readOnly = true)
  public SeriesDto get(UUID userId, UUID id) {
    RecurrenceSeries series =
        seriesRepository
            .findByIdAndUserId(id, userId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Series not found"));
    ZoneId zone = zone(userId);
    return toDtos(List.of(series), zone, today(zone)).get(0);
  }

  /**
   * Lists the series of the user, filtered by derived status and by name, sorted by next occurrence
   * (none last) and then by name.
   *
   * @param userId the owner
   * @param status ACTIVE, ENDED, CANCELLED or ALL; null means ACTIVE
   * @param q a case-insensitive part of the name, trimmed; blank or null means no name filter
   * @return the matching series
   * @throws ApiException BAD_REQUEST for any other status value
   */
  @Transactional(readOnly = true)
  public List<SeriesDto> list(UUID userId, String status, String q) {
    SeriesStatus wanted = parseStatus(status);
    ZoneId zone = zone(userId);
    LocalDate today = today(zone);
    String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
    List<RecurrenceSeries> all = seriesRepository.findByUserIdOrderByNameAsc(userId);
    return toDtos(all, zone, today).stream()
        .filter(dto -> wanted == null || dto.status() == wanted)
        .filter(dto -> needle.isEmpty() || dto.name().toLowerCase(Locale.ROOT).contains(needle))
        .sorted(
            Comparator.comparing(
                    SeriesDto::nextOccurrence,
                    Comparator.nullsLast(Comparator.<LocalDate>naturalOrder()))
                .thenComparing(SeriesDto::name)
                .thenComparing(SeriesDto::id))
        .toList();
  }

  /**
   * Maps several series with one query for their next occurrences. Derived status and next
   * occurrence are computed as of the given day.
   */
  private List<SeriesDto> toDtos(List<RecurrenceSeries> series, ZoneId zone, LocalDate today) {
    List<UUID> open =
        series.stream()
            .filter(s -> s.getStatus() != SeriesStatus.CANCELLED)
            .map(RecurrenceSeries::getId)
            .toList();
    Map<UUID, NextDueRow> next = new HashMap<>();
    if (!open.isEmpty()) {
      for (NextDueRow row : todoRepository.findNextRows(open, today)) {
        next.putIfAbsent(row.getSeriesId(), row);
      }
    }
    return series.stream().map(s -> toDto(s, today, next.get(s.getId()))).toList();
  }

  private static SeriesDto toDto(RecurrenceSeries series, LocalDate today, NextDueRow nextRow) {
    RecurrenceRule rule = series.rule();
    boolean cancelled = series.getStatus() == SeriesStatus.CANCELLED;
    SeriesStatus status;
    String endsSummary;
    LocalDate nextOccurrence = null;
    UUID nextTodoId = null;
    if (cancelled) {
      status = SeriesStatus.CANCELLED;
      endsSummary = "Cancelled " + series.getCancelledOn().format(MONTH_DAY);
    } else {
      boolean ended = hasEnded(rule, today);
      status = ended ? SeriesStatus.ENDED : SeriesStatus.ACTIVE;
      endsSummary = endsSummary(rule, today);
      if (!ended && nextRow != null) {
        nextOccurrence = nextRow.getDueDate();
        nextTodoId = nextRow.getId();
      }
    }
    return new SeriesDto(
        series.getId(),
        series.getName(),
        series.getDescription(),
        series.getDueTime(),
        series.getPriority(),
        series.getCategoryId(),
        RuleRequest.of(rule),
        RecurrenceRules.summary(rule),
        status,
        nextOccurrence,
        nextTodoId,
        endsSummary,
        series.getCancelledOn());
  }

  /** A series has ended when its rule has no occurrence on or after today. */
  private static boolean hasEnded(RecurrenceRule rule, LocalDate today) {
    if (rule.endType() == EndType.NEVER) {
      return false;
    }
    return RecurrenceCalculator.lastSlot(rule).map(last -> last.isBefore(today)).orElse(true);
  }

  private static String endsSummary(RecurrenceRule rule, LocalDate today) {
    return switch (rule.endType()) {
      case NEVER -> "Never";
      case ON_DATE -> "On " + rule.endDate().format(MONTH_DAY_YEAR);
      case AFTER_COUNT -> {
        int total = rule.endCount();
        int left = Math.max(0, total - RecurrenceCalculator.countBefore(rule, today));
        yield left == 0 ? "After " + total + " (ended)" : "After " + total + " (" + left + " left)";
      }
    };
  }

  private static SeriesStatus parseStatus(String raw) {
    if (raw == null) {
      return SeriesStatus.ACTIVE;
    }
    return switch (raw) {
      case "ACTIVE", "ENDED", "CANCELLED" -> SeriesStatus.valueOf(raw);
      case "ALL" -> null;
      default -> throw new ApiException(ErrorCode.BAD_REQUEST, "Invalid status");
    };
  }

  private ZoneId zone(UUID userId) {
    return userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User")).zone();
  }

  private LocalDate today(ZoneId zone) {
    return LocalDate.now(clock.withZone(zone));
  }
}
