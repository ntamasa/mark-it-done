package com.ntamasa.markitdone.todo;

import com.ntamasa.markitdone.category.CategoryCount;
import java.time.LocalDate;
import java.util.Collection;
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
   * Counts the rows of a series whose occurrence date is after a date.
   *
   * @param seriesId the series
   * @param after the date after which rows are counted (exclusive)
   * @return the number of rows
   */
  long countBySeriesIdAndOccurrenceDateAfter(UUID seriesId, LocalDate after);

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

  /**
   * Lists the rows of several series due on or after a date, earliest first. The first row of each
   * series is its next occurrence.
   *
   * @param seriesIds the series, must not be empty
   * @param from the first due date to consider
   * @return the rows ordered by due date and then by id
   */
  @Query(
      "select t.seriesId as seriesId, t.id as id, t.dueDate as dueDate from Todo t"
          + " where t.seriesId in :seriesIds and t.dueDate >= :from"
          + " order by t.dueDate asc, t.id asc")
  List<NextDueRow> findNextRows(
      @Param("seriesIds") Collection<UUID> seriesIds, @Param("from") LocalDate from);

  /**
   * Counts a user's one-off todos (those without a series) per category.
   *
   * @param userId the owner
   * @return one row per category that has at least one one-off todo
   */
  @Query(
      "select t.categoryId as categoryId, count(t) as itemCount from Todo t"
          + " where t.userId = :userId and t.categoryId is not null and t.seriesId is null"
          + " group by t.categoryId")
  List<CategoryCount> countOneOffsByCategory(@Param("userId") UUID userId);

  /**
   * Lists a user's one-off todos in a category, earliest due date first.
   *
   * @param userId the owner
   * @param categoryId the category
   * @return the todos
   */
  @Query(
      "select t from Todo t where t.userId = :userId and t.categoryId = :categoryId"
          + " and t.seriesId is null order by t.dueDate asc, t.name asc, t.id asc")
  List<Todo> findOneOffsInCategory(
      @Param("userId") UUID userId, @Param("categoryId") UUID categoryId);

  /**
   * Sets the category of one todo.
   *
   * @param id the todo
   * @param categoryId the new category, or null
   * @return the number of updated rows
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("update Todo t set t.categoryId = :categoryId where t.id = :id")
  int updateCategory(@Param("id") UUID id, @Param("categoryId") UUID categoryId);

  /**
   * Sets the category of every row of a series, past ones included.
   *
   * @param seriesId the series
   * @param categoryId the new category, or null
   * @return the number of updated rows
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("update Todo t set t.categoryId = :categoryId where t.seriesId = :seriesId")
  int updateCategoryOfSeries(
      @Param("seriesId") UUID seriesId, @Param("categoryId") UUID categoryId);
}
