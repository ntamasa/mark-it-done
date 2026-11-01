package com.ntamasa.markitdone.recurrence;

import com.ntamasa.markitdone.auth.CurrentUser;
import com.ntamasa.markitdone.recurrence.dto.FutureCountDto;
import com.ntamasa.markitdone.recurrence.dto.SeriesDto;
import com.ntamasa.markitdone.recurrence.dto.SeriesRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints for the recurring series of the current user. */
@RestController
@RequestMapping("/api/series")
public class SeriesController {

  private final SeriesService seriesService;
  private final SeriesQueries seriesQueries;

  /**
   * Create a SeriesController.
   *
   * @param seriesService the series service
   * @param seriesQueries the read side of the series
   */
  public SeriesController(SeriesService seriesService, SeriesQueries seriesQueries) {
    this.seriesService = seriesService;
    this.seriesQueries = seriesQueries;
  }

  /**
   * Lists the series of the current user.
   *
   * @param status ACTIVE (default), ENDED, CANCELLED or ALL
   * @param q a case-insensitive part of the name
   * @return the matching series, sorted by next occurrence and then by name
   */
  @GetMapping
  public List<SeriesDto> list(
      @RequestParam(required = false) String status, @RequestParam(required = false) String q) {
    return seriesQueries.list(CurrentUser.id(), status, q);
  }

  /**
   * Returns one series of the current user.
   *
   * @param id the series id
   * @return the series
   */
  @GetMapping("/{id}")
  public SeriesDto get(@PathVariable UUID id) {
    return seriesQueries.get(CurrentUser.id(), id);
  }

  /**
   * Creates a series for the current user and materialises its occurrences.
   *
   * @param req the template and rule
   * @return the created series
   */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public SeriesDto create(@Valid @RequestBody SeriesRequest req) {
    return seriesService.create(CurrentUser.id(), req);
  }

  /**
   * Cancels a series of the current user: later occurrences are deleted and it is never
   * materialised again. Cancelling again returns the same series.
   *
   * @param id the series id
   * @return the cancelled series
   */
  @PostMapping("/{id}/cancel")
  public SeriesDto cancel(@PathVariable UUID id) {
    return seriesService.cancel(CurrentUser.id(), id);
  }

  /**
   * Counts the occurrences of a series of the current user after today.
   *
   * @param id the series id
   * @return the count
   */
  @GetMapping("/{id}/future-count")
  public FutureCountDto futureCount(@PathVariable UUID id) {
    return new FutureCountDto(seriesService.futureCount(CurrentUser.id(), id));
  }
}
