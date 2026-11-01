package com.ntamasa.markitdone.recurrence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/** A deleted occurrence of a series, remembered so it is never regenerated. */
@Entity
@Table(name = "series_exclusions")
public class SeriesExclusion {

  @EmbeddedId private SeriesExclusionId id;

  @Column(name = "created_at", nullable = false)
  @CreationTimestamp
  private Instant createdAt;

  public SeriesExclusion() {}

  /**
   * Creates an exclusion.
   *
   * @param seriesId the series
   * @param occurrenceDate the excluded slot date
   */
  public SeriesExclusion(UUID seriesId, LocalDate occurrenceDate) {
    this.id = new SeriesExclusionId(seriesId, occurrenceDate);
  }

  public UUID getSeriesId() {
    return id.seriesId();
  }

  public LocalDate getOccurrenceDate() {
    return id.occurrenceDate();
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
