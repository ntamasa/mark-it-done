package com.ntamasa.markitdone.recurrence.dto;

/**
 * The number of occurrences of a series after today.
 *
 * @param count the number of occurrences dated after today in the owner's time zone
 */
public record FutureCountDto(long count) {}
