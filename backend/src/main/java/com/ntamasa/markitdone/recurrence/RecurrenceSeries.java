package com.ntamasa.markitdone.recurrence;

import com.ntamasa.markitdone.todo.Priority;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A recurring todo: a todo template plus a recurrence rule. Occurrences are real todo rows linked
 * to the series. "Ended" is derived from the rule and is not stored.
 */
@Entity
@Table(name = "recurrence_series")
public class RecurrenceSeries {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "name", nullable = false, length = 200)
  private String name;

  @Column(name = "description", length = 2000)
  private String description;

  @Column(name = "due_time")
  private LocalTime dueTime;

  @Column(name = "priority", nullable = false, length = 10)
  @Enumerated(EnumType.STRING)
  private Priority priority;

  @Column(name = "category_id")
  private UUID categoryId;

  @Column(name = "frequency", nullable = false, length = 16)
  @Enumerated(EnumType.STRING)
  private Frequency frequency;

  @Column(name = "interval_days")
  private Integer intervalDays;

  @Column(name = "weekdays", length = 32)
  @Convert(converter = IntegerSetConverter.class)
  private Set<Integer> weekdayNumbers;

  @Column(name = "month_days", length = 100)
  @Convert(converter = IntegerSetConverter.class)
  private Set<Integer> monthDays;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @Column(name = "end_type", nullable = false, length = 16)
  @Enumerated(EnumType.STRING)
  private EndType endType;

  @Column(name = "end_count")
  private Integer endCount;

  @Column(name = "end_date")
  private LocalDate endDate;

  @Column(name = "status", nullable = false, length = 16)
  @Enumerated(EnumType.STRING)
  private SeriesStatus status = SeriesStatus.ACTIVE;

  @Column(name = "cancelled_on")
  private LocalDate cancelledOn;

  @Column(name = "materialized_until")
  private LocalDate materializedUntil;

  @Column(name = "created_at", nullable = false)
  @CreationTimestamp
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  @UpdateTimestamp
  private Instant updatedAt;

  public RecurrenceSeries() {}

  /**
   * Creates an active series.
   *
   * @param userId the owner
   * @param name the template name
   * @param description the template description, or null
   * @param dueTime the template time, or null for all-day
   * @param priority the template priority
   * @param categoryId the template category id, or null
   * @param rule the recurrence rule
   */
  public RecurrenceSeries(
      UUID userId,
      String name,
      String description,
      LocalTime dueTime,
      Priority priority,
      UUID categoryId,
      RecurrenceRule rule) {
    this.userId = userId;
    this.name = name;
    this.description = description;
    this.dueTime = dueTime;
    this.priority = priority;
    this.categoryId = categoryId;
    this.status = SeriesStatus.ACTIVE;
    applyRule(rule);
  }

  /**
   * Builds an active series from a template and a rule. The series is not saved.
   *
   * @param userId the owner
   * @param name the template name
   * @param description the template description, or null
   * @param dueTime the template time, or null for all-day
   * @param priority the template priority
   * @param categoryId the template category id, or null
   * @param rule the recurrence rule
   * @return the new series
   */
  public static RecurrenceSeries fromTemplate(
      UUID userId,
      String name,
      String description,
      LocalTime dueTime,
      Priority priority,
      UUID categoryId,
      RecurrenceRule rule) {
    return new RecurrenceSeries(userId, name, description, dueTime, priority, categoryId, rule);
  }

  /**
   * Replaces the rule of the series.
   *
   * @param rule the new rule
   */
  public void applyRule(RecurrenceRule rule) {
    this.frequency = rule.frequency();
    this.intervalDays = rule.intervalDays();
    this.weekdayNumbers =
        rule.weekdays() == null
            ? null
            : rule.weekdays().stream()
                .map(DayOfWeek::getValue)
                .collect(Collectors.toCollection(TreeSet::new));
    this.monthDays = rule.monthDays() == null ? null : new TreeSet<>(rule.monthDays());
    this.startDate = rule.startDate();
    this.endType = rule.endType();
    this.endCount = rule.endCount();
    this.endDate = rule.endDate();
  }

  /**
   * Replaces the template fields.
   *
   * @param name the name
   * @param description the description, or null
   * @param dueTime the time, or null for all-day
   * @param priority the priority
   * @param categoryId the category id, or null
   */
  public void updateTemplate(
      String name, String description, LocalTime dueTime, Priority priority, UUID categoryId) {
    this.name = name;
    this.description = description;
    this.dueTime = dueTime;
    this.priority = priority;
    this.categoryId = categoryId;
  }

  /**
   * Cancels the series.
   *
   * @param day the cancellation day
   */
  public void cancel(LocalDate day) {
    this.status = SeriesStatus.CANCELLED;
    this.cancelledOn = day;
  }

  /**
   * Returns the rule of the series.
   *
   * @return the rule, with empty sets instead of nulls
   */
  public RecurrenceRule rule() {
    return new RecurrenceRule(
        frequency,
        intervalDays,
        weekdays(),
        monthDays == null ? Collections.emptySet() : Collections.unmodifiableSet(monthDays),
        startDate,
        endType,
        endCount,
        endDate);
  }

  /**
   * Returns the weekdays of the rule.
   *
   * @return the weekdays in ISO order, empty if none
   */
  public Set<DayOfWeek> weekdays() {
    Set<DayOfWeek> result = new TreeSet<>();
    if (weekdayNumbers != null) {
      weekdayNumbers.forEach(n -> result.add(DayOfWeek.of(n)));
    }
    return result;
  }

  public void setMaterializedUntil(LocalDate materializedUntil) {
    this.materializedUntil = materializedUntil;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public LocalTime getDueTime() {
    return dueTime;
  }

  public Priority getPriority() {
    return priority;
  }

  public UUID getCategoryId() {
    return categoryId;
  }

  public SeriesStatus getStatus() {
    return status;
  }

  public LocalDate getCancelledOn() {
    return cancelledOn;
  }

  public LocalDate getMaterializedUntil() {
    return materializedUntil;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
