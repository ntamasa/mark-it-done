package com.ntamasa.markitdone.recurrence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Composite key of a series exclusion.
 *
 * @param seriesId the series
 * @param occurrenceDate the excluded slot date
 */
@Embeddable
public record SeriesExclusionId(
    @Column(name = "series_id") UUID seriesId,
    @Column(name = "occurrence_date") LocalDate occurrenceDate)
    implements Serializable {}
