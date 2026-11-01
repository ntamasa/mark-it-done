package com.ntamasa.markitdone.recurrence;

import com.ntamasa.markitdone.category.CategoryCount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository for recurrence series. */
@Repository
public interface RecurrenceSeriesRepository extends JpaRepository<RecurrenceSeries, UUID> {

  /**
   * Finds a series by id, only if it belongs to the given user.
   *
   * @param id the series id
   * @param userId the owner
   * @return the series if found
   */
  Optional<RecurrenceSeries> findByIdAndUserId(UUID id, UUID userId);

  /**
   * Lists a user's series sorted by name.
   *
   * @param userId the owner
   * @return the series
   */
  List<RecurrenceSeries> findByUserIdOrderByNameAsc(UUID userId);

  /**
   * Lists the ids of all series with the given status.
   *
   * @param status the status
   * @return the ids
   */
  @Query("select s.id from RecurrenceSeries s where s.status = :status")
  List<UUID> findIdsByStatus(SeriesStatus status);

  /**
   * Counts a user's series per category.
   *
   * @param userId the owner
   * @return one row per category that has at least one series
   */
  @Query(
      "select s.categoryId as categoryId, count(s) as itemCount from RecurrenceSeries s"
          + " where s.userId = :userId and s.categoryId is not null group by s.categoryId")
  List<CategoryCount> countByCategory(@Param("userId") UUID userId);

  /**
   * Lists a user's series in a category, sorted by name.
   *
   * @param userId the owner
   * @param categoryId the category
   * @return the series
   */
  List<RecurrenceSeries> findByUserIdAndCategoryIdOrderByNameAscIdAsc(UUID userId, UUID categoryId);

  /**
   * Sets the category of one series.
   *
   * @param id the series
   * @param categoryId the new category, or null
   * @return the number of updated rows
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("update RecurrenceSeries s set s.categoryId = :categoryId where s.id = :id")
  int updateCategory(@Param("id") UUID id, @Param("categoryId") UUID categoryId);
}
