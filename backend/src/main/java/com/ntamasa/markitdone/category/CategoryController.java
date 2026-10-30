package com.ntamasa.markitdone.category;

import com.ntamasa.markitdone.auth.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints for the categories of the current user. */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

  private final CategoryService categoryService;

  /**
   * Create a CategoryController.
   *
   * @param categoryService the category service
   */
  public CategoryController(CategoryService categoryService) {
    this.categoryService = categoryService;
  }

  /**
   * Lists the current user's categories sorted by name.
   *
   * @return the categories
   */
  @GetMapping
  public List<CategoryDto> list() {
    return categoryService.list(CurrentUser.id());
  }

  /**
   * Creates a category for the current user.
   *
   * @param req the name and swatch
   * @return the created category
   */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CategoryDto create(@Valid @RequestBody CategoryRequest req) {
    return categoryService.create(CurrentUser.id(), req);
  }

  /**
   * Updates a category of the current user.
   *
   * @param id the category id
   * @param req the new name and swatch
   * @return the updated category
   */
  @PutMapping("/{id}")
  public CategoryDto update(@PathVariable UUID id, @Valid @RequestBody CategoryRequest req) {
    return categoryService.update(CurrentUser.id(), id, req);
  }
}
