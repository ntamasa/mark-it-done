package com.ntamasa.markitdone.recurrence;

import com.ntamasa.markitdone.audit.AuditAction;
import com.ntamasa.markitdone.audit.AuditService;
import com.ntamasa.markitdone.user.UserRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Keeps every active series materialised up to its owner's horizon. Runs every night at 02:15 UTC
 * and once at application startup. Each series is processed in its own transaction, so one failing
 * series does not stop the others.
 *
 * <p>Assumes a single application instance: there is no distributed lock in v1, so running several
 * instances would run the job concurrently on each of them.
 *
 * <p>Switched off with {@code markitdone.jobs.enabled=false}.
 */
@Component
@ConditionalOnProperty(
    name = "markitdone.jobs.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class MaterializationJob {

  private static final Logger log = LoggerFactory.getLogger(MaterializationJob.class);
  private static final String ENTITY_TYPE = "RecurrenceSeries";

  private final RecurrenceSeriesRepository seriesRepository;
  private final UserRepository userRepository;
  private final MaterializationService materializationService;
  private final AuditService auditService;
  private final TransactionTemplate transactionTemplate;

  /**
   * Create a MaterializationJob.
   *
   * @param seriesRepository the series repository
   * @param userRepository the user repository
   * @param materializationService the materialisation service
   * @param auditService the audit service
   * @param transactionManager the transaction manager
   */
  public MaterializationJob(
      RecurrenceSeriesRepository seriesRepository,
      UserRepository userRepository,
      MaterializationService materializationService,
      AuditService auditService,
      PlatformTransactionManager transactionManager) {
    this.seriesRepository = seriesRepository;
    this.userRepository = userRepository;
    this.materializationService = materializationService;
    this.auditService = auditService;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  /** Runs the job at startup. */
  @EventListener(ApplicationReadyEvent.class)
  public void onStartup() {
    runOnce();
  }

  /** Runs the job every night at 02:15 UTC. */
  @Scheduled(cron = "0 15 2 * * *", zone = "UTC")
  public void nightly() {
    runOnce();
  }

  /**
   * Materialises every active series up to its owner's horizon.
   *
   * @return the total number of rows created
   */
  public int runOnce() {
    List<UUID> ids = seriesRepository.findIdsByStatus(SeriesStatus.ACTIVE);
    int total = 0;
    for (UUID id : ids) {
      try {
        Integer created = transactionTemplate.execute(status -> processSeries(id));
        total += created == null ? 0 : created;
      } catch (RuntimeException e) {
        log.error("Materialization failed for series {}", id, e);
      }
    }
    log.info("Materialization run finished: {} rows created for {} series", total, ids.size());
    return total;
  }

  private int processSeries(UUID id) {
    RecurrenceSeries series = seriesRepository.findById(id).orElse(null);
    if (series == null || series.getStatus() != SeriesStatus.ACTIVE) {
      return 0;
    }
    ZoneId zone = userRepository.findById(series.getUserId()).orElseThrow().zone();
    LocalDate until = materializationService.horizon(zone);
    LocalDate previous = series.getMaterializedUntil();
    LocalDate from = previous == null ? series.rule().startDate() : previous.plusDays(1);
    int created = materializationService.materialize(series, until);
    if (created > 0) {
      Map<String, Object> after = new LinkedHashMap<>();
      after.put("from", from);
      after.put("to", until);
      after.put("created", created);
      auditService.record(
          AuditAction.SERIES_MATERIALIZED, series.getUserId(), ENTITY_TYPE, id, null, after);
    }
    return created;
  }
}
