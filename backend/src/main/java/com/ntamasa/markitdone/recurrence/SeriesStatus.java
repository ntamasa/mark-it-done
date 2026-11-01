package com.ntamasa.markitdone.recurrence;

/**
 * Status of a series. ACTIVE and CANCELLED are stored; ENDED is derived from the rule (no
 * occurrence on or after today) and is only returned by the API.
 */
public enum SeriesStatus {
  ACTIVE,
  ENDED,
  CANCELLED
}
