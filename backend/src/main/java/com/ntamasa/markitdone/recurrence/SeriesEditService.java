package com.ntamasa.markitdone.recurrence;

import com.ntamasa.markitdone.audit.AuditAction;
import com.ntamasa.markitdone.audit.AuditService;
import com.ntamasa.markitdone.category.CategoryService;
import com.ntamasa.markitdone.common.ApiException;
import com.ntamasa.markitdone.common.ErrorCode;
import com.ntamasa.markitdone.config.MarkitdoneProperties;
import com.ntamasa.markitdone.todo.EditScope;
import com.ntamasa.markitdone.todo.Todo;
import com.ntamasa.markitdone.todo.TodoMapper;
import com.ntamasa.markitdone.todo.TodoRepository;
import com.ntamasa.markitdone.todo.TodoService;
import com.ntamasa.markitdone.todo.dto.TodoDto;
import com.ntamasa.markitdone.todo.dto.TodoRequest;
import com.ntamasa.markitdone.user.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Edits of the occurrences of recurring todos: this occurrence only, or this and all later ones.
 */
@Service
public class SeriesEditService {

  private static final String SERIES_TYPE = "RecurrenceSeries";
  private static final int MAX_NAME_LENGTH = 200;
  private static final int MAX_DESCRIPTION_LENGTH = 2000;

  private final TodoService todoService;
  private final TodoRepository todoRepository;
  private final RecurrenceSeriesRepository seriesRepository;
  private final SeriesExclusionRepository exclusionRepository;
  private final MaterializationService materializationService;
  private final CategoryService categoryService;
  private final UserRepository userRepository;
  private final AuditService auditService;
  private final MarkitdoneProperties properties;
  private final Clock clock;

  /**
   * Create a SeriesEditService.
   *
   * @param todoService the todo service, used for one-off edits and THIS edits
   * @param todoRepository the todo repository
   * @param seriesRepository the series repository
   * @param exclusionRepository the series exclusion repository
   * @param materializationService the materialisation service
   * @param categoryService the category service, used to check category ownership
   * @param userRepository the user repository, used to find the user's time zone
   * @param auditService the audit service
   * @param properties the application properties
   * @param clock the clock that decides what "today" is
   */
  public SeriesEditService(
      TodoService todoService,
      TodoRepository todoRepository,
      RecurrenceSeriesRepository seriesRepository,
      SeriesExclusionRepository exclusionRepository,
      MaterializationService materializationService,
      CategoryService categoryService,
      UserRepository userRepository,
      AuditService auditService,
      MarkitdoneProperties properties,
      Clock clock) {
    this.todoService = todoService;
    this.todoRepository = todoRepository;
    this.seriesRepository = seriesRepository;
    this.exclusionRepository = exclusionRepository;
    this.materializationService = materializationService;
    this.categoryService = categoryService;
    this.userRepository = userRepository;
    this.auditService = auditService;
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * Edits a todo. A one-off todo is edited as before and the scope is ignored. For an occurrence
   * the scope is required: THIS changes only that row and detaches it; FUTURE updates the series
   * template (and, when the request carries a different rule, the rule) from that occurrence on.
   *
   * @param userId the owner
   * @param todoId the todo id
   * @param scope THIS or FUTURE, required for an occurrence
   * @param req the new fields and an optional rule (only used with FUTURE)
   * @return the edited row, or null when a rule change leaves no occurrence on that date
   * @throws ApiException NOT_FOUND for a todo that is not the user's, BAD_REQUEST for a missing
   *     scope or a date change with FUTURE, VALIDATION_FAILED for invalid input
   */
  @Transactional
  public TodoDto update(UUID userId, UUID todoId, EditScope scope, TodoRequest req) {
    Todo todo = todoService.requireOwned(userId, todoId);
    if (todo.getSeriesId() == null) {
      return todoService.update(userId, todoId, req);
    }
    if (scope == null) {
      throw new ApiException(ErrorCode.BAD_REQUEST, "scope is required for recurring todos");
    }
    if (scope == EditScope.THIS) {
      todo.setDetached(true);
      return todoService.update(userId, todoId, req);
    }
    return editFuture(userId, todo, req);
  }

  private TodoDto editFuture(UUID userId, Todo target, TodoRequest req) {
    String name = validName(req.name());
    String description = cleanDescription(req.description());
    checkCategory(userId, req.categoryId());
    if (!Objects.equals(req.dueDate(), target.getDueDate())) {
      throw new ApiException(ErrorCode.BAD_REQUEST, "Change the date with 'Only this occurrence'");
    }
    UUID seriesId = target.getSeriesId();
    LocalDate occurrence = target.getOccurrenceDate();
    RecurrenceSeries series =
        seriesRepository
            .findByIdAndUserId(seriesId, userId)
            .orElseThrow(() -> ApiException.notFound("Todo"));
    RecurrenceRule oldRule = series.rule();
    RecurrenceRule newRule = null;
    if (req.rule() != null) {
      RecurrenceRule candidate = req.rule().toRule();
      RecurrenceRules.validate(candidate);
      if (!sameMeaning(oldRule, candidate, occurrence)) {
        if (candidate.startDate().isBefore(occurrence)) {
          throw new ApiException(
              ErrorCode.BAD_REQUEST, "The new rule must not start before this occurrence");
        }
        newRule = candidate;
      }
    }
    ZoneId zone =
        userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User")).zone();
    Map<String, Object> before = snapshot(series);
    if (newRule == null) {
      return editTemplate(userId, series, target, occurrence, req, name, description, zone, before);
    }
    LocalDate horizon = horizon(zone);
    if (RecurrenceCalculator.countBefore(oldRule, occurrence) == 0) {
      return replaceInPlace(
          userId, seriesId, occurrence, newRule, req, name, description, zone, horizon, before);
    }
    return split(
        userId,
        seriesId,
        occurrence,
        oldRule,
        newRule,
        req,
        name,
        description,
        zone,
        horizon,
        before);
  }

  private TodoDto editTemplate(
      UUID userId,
      RecurrenceSeries series,
      Todo target,
      LocalDate occurrence,
      TodoRequest req,
      String name,
      String description,
      ZoneId zone,
      Map<String, Object> before) {
    series.updateTemplate(name, description, req.dueTime(), req.priority(), req.categoryId());
    seriesRepository.save(series);
    for (Todo row :
        todoRepository.findBySeriesIdAndOccurrenceDateGreaterThanEqual(
            series.getId(), occurrence)) {
      if (!row.isDetached() || row.getId().equals(target.getId())) {
        row.update(
            name, description, row.getDueDate(), req.dueTime(), req.priority(), req.categoryId());
      }
    }
    todoRepository.flush();
    auditService.record(
        AuditAction.SERIES_UPDATED, userId, SERIES_TYPE, series.getId(), before, snapshot(series));
    return dtoOf(target.getId(), series, zone);
  }

  private TodoDto replaceInPlace(
      UUID userId,
      UUID seriesId,
      LocalDate occurrence,
      RecurrenceRule newRule,
      TodoRequest req,
      String name,
      String description,
      ZoneId zone,
      LocalDate horizon,
      Map<String, Object> before) {
    todoRepository.deleteFutureNonDetached(seriesId, occurrence);
    exclusionRepository.deleteFromDate(seriesId, occurrence);
    RecurrenceSeries series = seriesRepository.findById(seriesId).orElseThrow();
    series.updateTemplate(name, description, req.dueTime(), req.priority(), req.categoryId());
    series.applyRule(newRule);
    series.setMaterializedUntil(null);
    seriesRepository.saveAndFlush(series);
    materializationService.materialize(series, horizon);
    seriesRepository.flush();
    auditService.record(
        AuditAction.SERIES_UPDATED, userId, SERIES_TYPE, seriesId, before, snapshot(series));
    return dtoAt(series, occurrence, zone);
  }

  private TodoDto split(
      UUID userId,
      UUID seriesId,
      LocalDate occurrence,
      RecurrenceRule oldRule,
      RecurrenceRule newRule,
      TodoRequest req,
      String name,
      String description,
      ZoneId zone,
      LocalDate horizon,
      Map<String, Object> before) {
    todoRepository.deleteFutureNonDetached(seriesId, occurrence);
    RecurrenceSeries old = seriesRepository.findById(seriesId).orElseThrow();
    old.applyRule(
        new RecurrenceRule(
            oldRule.frequency(),
            oldRule.intervalDays(),
            oldRule.weekdays(),
            oldRule.monthDays(),
            oldRule.startDate(),
            EndType.ON_DATE,
            null,
            occurrence.minusDays(1)));
    seriesRepository.saveAndFlush(old);
    RecurrenceSeries created =
        seriesRepository.saveAndFlush(
            new RecurrenceSeries(
                userId,
                name,
                description,
                req.dueTime(),
                req.priority(),
                req.categoryId(),
                newRule));
    materializationService.materialize(created, horizon);
    seriesRepository.flush();
    Map<String, Object> after = new HashMap<>();
    after.put("old", snapshot(old));
    after.put("new", snapshot(created));
    auditService.record(AuditAction.SERIES_UPDATED, userId, SERIES_TYPE, seriesId, before, after);
    return dtoAt(created, occurrence, zone);
  }

  private TodoDto dtoAt(RecurrenceSeries series, LocalDate occurrence, ZoneId zone) {
    return todoRepository
        .findBySeriesIdAndOccurrenceDateGreaterThanEqual(series.getId(), occurrence)
        .stream()
        .filter(t -> occurrence.equals(t.getOccurrenceDate()))
        .findFirst()
        .map(t -> TodoMapper.toDto(t, zone, Map.of(series.getId(), series)))
        .orElse(null);
  }

  private TodoDto dtoOf(UUID todoId, RecurrenceSeries series, ZoneId zone) {
    Todo todo = todoRepository.findById(todoId).orElseThrow();
    return TodoMapper.toDto(todo, zone, Map.of(series.getId(), series));
  }

  private LocalDate horizon(ZoneId zone) {
    return LocalDate.now(clock.withZone(zone)).plusMonths(properties.recurrence().horizonMonths());
  }

  /**
   * Compares two rules by meaning: fields that do not apply to the frequency are ignored, and a
   * start date equal to the occurrence date counts as the old start.
   */
  private static boolean sameMeaning(
      RecurrenceRule old, RecurrenceRule next, LocalDate occurrence) {
    if (old.frequency() != next.frequency() || old.endType() != next.endType()) {
      return false;
    }
    if (!next.startDate().equals(old.startDate()) && !next.startDate().equals(occurrence)) {
      return false;
    }
    boolean daysSame =
        switch (old.frequency()) {
          case EVERY_N_DAYS -> Objects.equals(old.intervalDays(), next.intervalDays());
          case WEEKLY -> old.weekdays().equals(next.weekdays());
          case MONTHLY -> old.monthDays().equals(next.monthDays());
        };
    boolean endSame =
        switch (old.endType()) {
          case NEVER -> true;
          case AFTER_COUNT -> Objects.equals(old.endCount(), next.endCount());
          case ON_DATE -> Objects.equals(old.endDate(), next.endDate());
        };
    return daysSame && endSame;
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
    map.put("startDate", series.rule().startDate().toString());
    return map;
  }
}
