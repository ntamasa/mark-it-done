package com.ntamasa.markitdone.todo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Repository for accessing todo entities. */
@Repository
public interface TodoRepository extends JpaRepository<Todo, UUID> {

  /**
   * Lists a user's todos whose due date falls within a date range, both ends inclusive.
   *
   * @param userId the owner
   * @param from the first due date (inclusive)
   * @param to the last due date (inclusive)
   * @return the matching todos, in no particular order
   */
  List<Todo> findByUserIdAndDueDateBetween(UUID userId, LocalDate from, LocalDate to);

  /**
   * Finds a todo by id, only if it belongs to the given user.
   *
   * @param id the todo id
   * @param userId the owner
   * @return the todo if it exists and belongs to the user
   */
  Optional<Todo> findByIdAndUserId(UUID id, UUID userId);

  /**
   * Lists the occurrence dates of a series within a range, both ends inclusive.
   *
   * @param seriesId the series
   * @param from the first date
   * @param to the last date
   * @return the occurrence dates
   */
  @Query(
      "select t.occurrenceDate from Todo t where t.seriesId = :seriesId"
          + " and t.occurrenceDate between :from and :to")
  List<LocalDate> findOccurrenceDates(
      @Param("seriesId") UUID seriesId, @Param("from") LocalDate from, @Param("to") LocalDate to);

  /**
   * Lists the rows of a series whose occurrence date is on or after a date.
   *
   * @param seriesId the series
   * @param from the first occurrence date
   * @return the rows
   */
  List<Todo> findBySeriesIdAndOccurrenceDateGreaterThanEqual(UUID seriesId, LocalDate from);

  /**
   * Deletes the non-detached rows of a series whose occurrence date is on or after a date.
   *
   * @param seriesId the series
   * @param from the first occurrence date
   * @return the number of deleted rows
   */
  @Transactional
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "delete from Todo t where t.seriesId = :seriesId and t.detached = false"
          + " and t.occurrenceDate >= :from")
  int deleteFutureNonDetached(@Param("seriesId") UUID seriesId, @Param("from") LocalDate from);

  /**
   * Deletes all rows of a series whose occurrence date is after a date.
   *
   * @param seriesId the series
   * @param after the date after which rows are deleted (exclusive)
   * @return the number of deleted rows
   */
  @Transactional
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("delete from Todo t where t.seriesId = :seriesId and t.occurrenceDate > :after")
  int deleteAfter(@Param("seriesId") UUID seriesId, @Param("after") LocalDate after);

  /**
   * Finds the earliest due date of a series' rows on or after a date.
   *
   * @param seriesId the series
   * @param from the first due date to consider
   * @return the next due date, if any
   */
  @Query("select min(t.dueDate) from Todo t where t.seriesId = :seriesId and t.dueDate >= :from")
  Optional<LocalDate> findNextDueDate(
      @Param("seriesId") UUID seriesId, @Param("from") LocalDate from);
}
