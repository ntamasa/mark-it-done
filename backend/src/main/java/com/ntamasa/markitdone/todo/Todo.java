package com.ntamasa.markitdone.todo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A user's todo: a named item due on a calendar date, optionally at a time of day.
 *
 * <p>A todo without a due time is all-day. Due date and time are calendar values in the user's time
 * zone, not instants. The category is a plain id, so deleting the category leaves the todo without
 * one (the database sets {@code category_id} to null).
 */
@Entity
@Table(name = "todos")
public class Todo {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "name", nullable = false, length = 200)
  private String name;

  @Column(name = "description", length = 2000)
  private String description;

  @Column(name = "due_date", nullable = false)
  private LocalDate dueDate;

  @Column(name = "due_time")
  private LocalTime dueTime;

  @Column(name = "priority", nullable = false, length = 10)
  @Enumerated(EnumType.STRING)
  private Priority priority;

  @Column(name = "category_id")
  private UUID categoryId;

  @Column(name = "done", nullable = false)
  private boolean done;

  @Column(name = "done_at")
  private Instant doneAt;

  @Column(name = "created_at", nullable = false)
  @CreationTimestamp
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  @UpdateTimestamp
  private Instant updatedAt;

  // Constructors
  public Todo() {}

  /**
   * Creates a todo that is not done.
   *
   * @param userId the owner
   * @param name the name
   * @param description the description, or null
   * @param dueDate the due date
   * @param dueTime the due time, or null for an all-day todo
   * @param priority the priority
   * @param categoryId the category id, or null
   */
  public Todo(
      UUID userId,
      String name,
      String description,
      LocalDate dueDate,
      LocalTime dueTime,
      Priority priority,
      UUID categoryId) {
    this.userId = userId;
    this.name = name;
    this.description = description;
    this.dueDate = dueDate;
    this.dueTime = dueTime;
    this.priority = priority;
    this.categoryId = categoryId;
    this.done = false;
  }

  /**
   * Tells whether the todo is expired at the current moment in the given zone.
   *
   * @param zone the user's time zone
   * @return true if the todo is expired now
   */
  public boolean isExpired(ZoneId zone) {
    return isExpired(LocalDateTime.now(zone));
  }

  /**
   * Tells whether the todo is expired at the given moment. A timed todo is expired from its due
   * time onwards. An all-day todo is expired from the start of the next day.
   *
   * @param now the moment to check, in the user's time zone
   * @return true if the todo is expired at {@code now}
   */
  public boolean isExpired(LocalDateTime now) {
    LocalDateTime expiresAt =
        dueTime == null ? dueDate.plusDays(1).atStartOfDay() : LocalDateTime.of(dueDate, dueTime);
    return !now.isBefore(expiresAt);
  }

  /**
   * Marks the todo as done or not done. Marking it done records the current moment; un-marking
   * clears it.
   *
   * @param done true to mark the todo done, false to mark it not done
   */
  public void setDone(boolean done) {
    this.done = done;
    this.doneAt = done ? Instant.now() : null;
  }

  // Getters
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

  public LocalDate getDueDate() {
    return dueDate;
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

  public boolean isDone() {
    return done;
  }

  public Instant getDoneAt() {
    return doneAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
