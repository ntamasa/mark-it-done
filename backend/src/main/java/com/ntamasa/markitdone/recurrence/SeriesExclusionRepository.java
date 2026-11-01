package com.ntamasa.markitdone.recurrence;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Repository for series exclusions. */
@Repository
public interface SeriesExclusionRepository
    extends JpaRepository<SeriesExclusion, SeriesExclusionId> {

  /**
   * Lists the excluded dates of a series.
   *
   * @param seriesId the series
   * @return the excluded dates
   */
  @Query("select e.id.occurrenceDate from SeriesExclusion e where e.id.seriesId = :seriesId")
  List<LocalDate> findExcludedDates(@Param("seriesId") UUID seriesId);

  /**
   * Deletes the exclusions of a series from a date onwards (inclusive).
   *
   * @param seriesId the series
   * @param from the first date to delete
   * @return the number of deleted rows
   */
  @Transactional
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "delete from SeriesExclusion e where e.id.seriesId = :seriesId"
          + " and e.id.occurrenceDate >= :from")
  int deleteFromDate(@Param("seriesId") UUID seriesId, @Param("from") LocalDate from);
}
