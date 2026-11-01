package com.ntamasa.markitdone.recurrence;

import com.ntamasa.markitdone.audit.AuditAction;
import com.ntamasa.markitdone.audit.AuditService;
import com.ntamasa.markitdone.category.CategoryService;
import com.ntamasa.markitdone.common.ApiException;
import com.ntamasa.markitdone.common.ErrorCode;
import com.ntamasa.markitdone.recurrence.dto.SeriesDto;
import com.ntamasa.markitdone.recurrence.dto.SeriesRequest;
import com.ntamasa.markitdone.todo.TodoRepository;
import com.ntamasa.markitdone.user.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Rules for creating recurring series. */
@Service
public class SeriesService {

  private static final String ENTITY_TYPE = "RecurrenceSeries";
  private static final int MAX_NAME_LENGTH = 200;
  private static final int MAX_DESCRIPTION_LENGTH = 2000;

  private final RecurrenceSeriesRepository seriesRepository;
  private final MaterializationService materializationService;
  private final CategoryService categoryService;
  private final UserRepository userRepository;
  private final AuditService auditService;
  private final TodoRepository todoRepository;
  private final Clock clock;

  /**
   * Create a SeriesService.
   *
   * @param seriesRepository the series repository
   * @param materializationService the materialisation service
   * @param categoryService the category service, used to check category ownership
   * @param userRepository the user repository, used to find the user's time zone
   * @param auditService the audit service
   * @param todoRepository the todo repository, used to delete and count occurrences
   * @param clock the clock that decides what "today" is
   */
  public SeriesService(
      RecurrenceSeriesRepository seriesRepository,
      MaterializationService materializationService,
      CategoryService categoryService,
      UserRepository userRepository,
      AuditService auditService,
      TodoRepository todoRepository,
      Clock clock) {
    this.seriesRepository = seriesRepository;
    this.materializationService = materializationService;
    this.categoryService = categoryService;
    this.userRepository = userRepository;
    this.auditService = auditService;
    this.todoRepository = todoRepository;
    this.clock = clock;
  }

  /**
   * Creates a series and materialises its occurrences up to the horizon, in one transaction.
   * Records a SERIES_CREATED audit entry.
   *
   * @param userId the owner
   * @param req the template and rule
   * @return the created series
   * @throws ApiException VALIDATION_FAILED for an invalid name, description, rule or unknown
   *     category
   */
  @Transactional
  public SeriesDto create(UUID userId, SeriesRequest req) {
    String name = validName(req.name());
    String description = cleanDescription(req.description());
    if (req.rule() == null) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "rule is required");
    }
    RecurrenceRule rule = req.rule().toRule();
    RecurrenceRules.validate(rule);
    checkCategory(userId, req.categoryId());
    ZoneId zone =
        userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User")).zone();
    RecurrenceSeries series =
        seriesRepository.saveAndFlush(
            new RecurrenceSeries(
                userId, name, description, req.dueTime(), req.priority(), req.categoryId(), rule));
    materializationService.materialize(series, materializationService.horizon(zone));
    seriesRepository.flush();
    auditService.record(
        AuditAction.SERIES_CREATED, userId, ENTITY_TYPE, series.getId(), null, snapshot(series));
    return SeriesQueries.toDto(series);
  }

  /**
   * Cancels a series: its occurrences after today (in the owner's time zone) are deleted, today's
   * and earlier occurrences are kept, and the series is never materialised again. Cancelling an
   * already cancelled series changes nothing and returns it as it is. Records a SERIES_CANCELLED
   * audit entry.
   *
   * @param userId the owner
   * @param seriesId the series
   * @return the cancelled series
   * @throws ApiException NOT_FOUND when the series is not the user's
   */
  @Transactional
  public SeriesDto cancel(UUID userId, UUID seriesId) {
    RecurrenceSeries series = requireOwned(userId, seriesId);
    if (series.getStatus() == SeriesStatus.CANCELLED) {
      return SeriesQueries.toDto(series);
    }
    LocalDate today = today(userId);
    Map<String, Object> before = snapshot(series);
    int removed = todoRepository.deleteAfter(seriesId, today);
    RecurrenceSeries current = requireOwned(userId, seriesId);
    current.cancel(today);
    seriesRepository.saveAndFlush(current);
    Map<String, Object> after = new LinkedHashMap<>();
    after.put("removedOccurrences", removed);
    after.put("cancelledOn", today.toString());
    auditService.record(AuditAction.SERIES_CANCELLED, userId, ENTITY_TYPE, seriesId, before, after);
    return SeriesQueries.toDto(current);
  }

  /**
   * Counts the occurrences of a series dated after today in the owner's time zone. The confirm
   * dialog of a cancel shows this number.
   *
   * @param userId the owner
   * @param seriesId the series
   * @return the number of occurrences after today
   * @throws ApiException NOT_FOUND when the series is not the user's
   */
  @Transactional(readOnly = true)
  public long futureCount(UUID userId, UUID seriesId) {
    requireOwned(userId, seriesId);
    return todoRepository.countBySeriesIdAndOccurrenceDateAfter(seriesId, today(userId));
  }

  private RecurrenceSeries requireOwned(UUID userId, UUID seriesId) {
    return seriesRepository
        .findByIdAndUserId(seriesId, userId)
        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Series not found"));
  }

  private LocalDate today(UUID userId) {
    ZoneId zone =
        userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User")).zone();
    return LocalDate.now(clock.withZone(zone));
  }

  private void checkCategory(UUID userId, UUID categoryId) {
    if (categoryId == null) {
      return;
    }
    try {
      categoryService.requireOwned(userId, categoryId);
    } catch (ApiException ex) {
      if (ex.getCode() == ErrorCode.NOT_FOUND) {
        throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown category", ex);
      }
      throw ex;
    }
  }

  private static String validName(String raw) {
    String name = raw == null ? "" : raw.trim();
    if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED, "Name must be between 1 and 200 characters.");
    }
    return name;
  }

  private static String cleanDescription(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    if (raw.length() > MAX_DESCRIPTION_LENGTH) {
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED, "Description must be at most 2000 characters.");
    }
    return raw;
  }

  private static Map<String, Object> snapshot(RecurrenceSeries series) {
    Map<String, Object> map = new HashMap<>();
    map.put("name", series.getName());
    map.put("description", series.getDescription());
    map.put("dueTime", series.getDueTime() == null ? null : series.getDueTime().toString());
    map.put("priority", series.getPriority().name());
    map.put(
        "categoryId", series.getCategoryId() == null ? null : series.getCategoryId().toString());
    map.put("rule", RecurrenceRules.summary(series.rule()));
    return map;
  }
}
