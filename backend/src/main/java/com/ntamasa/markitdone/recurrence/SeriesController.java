package com.ntamasa.markitdone.recurrence;

import com.ntamasa.markitdone.auth.CurrentUser;
import com.ntamasa.markitdone.recurrence.dto.SeriesDto;
import com.ntamasa.markitdone.recurrence.dto.SeriesRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints for the recurring series of the current user. */
@RestController
@RequestMapping("/api/series")
public class SeriesController {

  private final SeriesService seriesService;

  /**
   * Create a SeriesController.
   *
   * @param seriesService the series service
   */
  public SeriesController(SeriesService seriesService) {
    this.seriesService = seriesService;
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
}
