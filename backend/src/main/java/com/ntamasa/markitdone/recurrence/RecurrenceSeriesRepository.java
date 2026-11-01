package com.ntamasa.markitdone.recurrence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
