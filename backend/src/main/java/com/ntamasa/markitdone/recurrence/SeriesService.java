package com.ntamasa.markitdone.recurrence;

import com.ntamasa.markitdone.audit.AuditAction;
import com.ntamasa.markitdone.audit.AuditService;
import com.ntamasa.markitdone.category.CategoryService;
import com.ntamasa.markitdone.common.ApiException;
import com.ntamasa.markitdone.common.ErrorCode;
import com.ntamasa.markitdone.recurrence.dto.RuleRequest;
import com.ntamasa.markitdone.recurrence.dto.SeriesDto;
import com.ntamasa.markitdone.recurrence.dto.SeriesRequest;
import com.ntamasa.markitdone.user.UserRepository;
import java.time.ZoneId;
import java.util.HashMap;
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

  /**
   * Create a SeriesService.
   *
   * @param seriesRepository the series repository
   * @param materializationService the materialisation service
   * @param categoryService the category service, used to check category ownership
   * @param userRepository the user repository, used to find the user's time zone
   * @param auditService the audit service
   */
  public SeriesService(
      RecurrenceSeriesRepository seriesRepository,
      MaterializationService materializationService,
      CategoryService categoryService,
      UserRepository userRepository,
      AuditService auditService) {
    this.seriesRepository = seriesRepository;
    this.materializationService = materializationService;
    this.categoryService = categoryService;
    this.userRepository = userRepository;
    this.auditService = auditService;
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
    return toDto(series);
  }

  private static SeriesDto toDto(RecurrenceSeries series) {
    RecurrenceRule rule = series.rule();
    return new SeriesDto(
        series.getId(),
        series.getName(),
        series.getDescription(),
        series.getDueTime(),
        series.getPriority(),
        series.getCategoryId(),
        RuleRequest.of(rule),
        RecurrenceRules.summary(rule),
        series.getStatus(),
        null,
        null,
        null,
        series.getCancelledOn());
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
