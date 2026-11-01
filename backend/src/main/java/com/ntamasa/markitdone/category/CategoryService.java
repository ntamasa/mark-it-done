package com.ntamasa.markitdone.category;

import com.ntamasa.markitdone.audit.AuditAction;
import com.ntamasa.markitdone.audit.AuditService;
import com.ntamasa.markitdone.common.ApiException;
import com.ntamasa.markitdone.common.ErrorCode;
import com.ntamasa.markitdone.recurrence.RecurrenceRules;
import com.ntamasa.markitdone.recurrence.RecurrenceSeries;
import com.ntamasa.markitdone.recurrence.RecurrenceSeriesRepository;
import com.ntamasa.markitdone.todo.Todo;
import com.ntamasa.markitdone.todo.TodoRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Rules for listing, creating and editing the categories of a user. */
@Service
public class CategoryService {

  private static final String ENTITY_TYPE = "Category";
  private static final int MAX_NAME_LENGTH = 40;
  private static final String NAME_TAKEN = "You already have a category with this name.";

  private final CategoryRepository categoryRepository;
  private final TodoRepository todoRepository;
  private final RecurrenceSeriesRepository seriesRepository;
  private final AuditService auditService;

  /**
   * Create a CategoryService.
   *
   * @param categoryRepository the category repository
   * @param todoRepository the todo repository
   * @param seriesRepository the recurrence series repository
   * @param auditService the audit service
   */
  public CategoryService(
      CategoryRepository categoryRepository,
      TodoRepository todoRepository,
      RecurrenceSeriesRepository seriesRepository,
      AuditService auditService) {
    this.categoryRepository = categoryRepository;
    this.todoRepository = todoRepository;
    this.seriesRepository = seriesRepository;
    this.auditService = auditService;
  }

  /**
   * Lists the categories of a user sorted by name.
   *
   * @param userId the owner
   * @return the user's categories
   */
  @Transactional(readOnly = true)
  public List<CategoryDto> list(UUID userId) {
    Map<UUID, Integer> counts = itemCounts(userId);
    return categoryRepository.findByUserIdOrderByNameKeyAsc(userId).stream()
        .map(c -> CategoryDto.of(c, counts.getOrDefault(c.getId(), 0)))
        .toList();
  }

  /**
   * Creates a category for a user. Records a CATEGORY_CREATED audit entry.
   *
   * @param userId the owner
   * @param req the name and swatch
   * @return the created category
   * @throws ApiException VALIDATION_FAILED for a blank or too long name, CATEGORY_NAME_TAKEN when
   *     the user already has the name
   */
  @Transactional
  public CategoryDto create(UUID userId, CategoryRequest req) {
    String name = validName(req.name());
    if (categoryRepository.existsByUserIdAndNameKey(userId, Category.key(name))) {
      throw nameTaken();
    }
    Category category = new Category(userId, name, req.swatch());
    try {
      category = categoryRepository.saveAndFlush(category);
    } catch (DataIntegrityViolationException ex) {
      throw nameTaken(ex);
    }
    auditService.record(
        AuditAction.CATEGORY_CREATED,
        userId,
        ENTITY_TYPE,
        category.getId(),
        null,
        snapshot(category));
    return CategoryDto.of(category, 0);
  }

  /**
   * Updates the name and swatch of a category of a user. Records a CATEGORY_UPDATED audit entry
   * with the state before and after the change.
   *
   * @param userId the owner
   * @param categoryId the category to update
   * @param req the new name and swatch
   * @return the updated category
   * @throws ApiException NOT_FOUND when the category is not the user's, VALIDATION_FAILED for a
   *     blank or too long name, CATEGORY_NAME_TAKEN when another category has the name
   */
  @Transactional
  public CategoryDto update(UUID userId, UUID categoryId, CategoryRequest req) {
    Category category = requireOwned(userId, categoryId);
    String name = validName(req.name());
    if (categoryRepository.existsByUserIdAndNameKeyAndIdNot(
        userId, Category.key(name), categoryId)) {
      throw nameTaken();
    }
    Map<String, Object> before = snapshot(category);
    category.rename(name);
    category.changeSwatch(req.swatch());
    try {
      categoryRepository.saveAndFlush(category);
    } catch (DataIntegrityViolationException ex) {
      throw nameTaken(ex);
    }
    auditService.record(
        AuditAction.CATEGORY_UPDATED, userId, ENTITY_TYPE, categoryId, before, snapshot(category));
    return CategoryDto.of(category, itemCounts(userId).getOrDefault(categoryId, 0));
  }

  /**
   * Lists the one-off todos and recurring series in a category.
   *
   * @param userId the owner
   * @param id the category
   * @return the usage
   * @throws ApiException NOT_FOUND when the category is not the user's
   */
  @Transactional(readOnly = true)
  public CategoryUsageDto usage(UUID userId, UUID id) {
    requireOwned(userId, id);
    List<CategoryUsageDto.TodoItem> todos =
        todoRepository.findOneOffsInCategory(userId, id).stream()
            .map(t -> new CategoryUsageDto.TodoItem(t.getId(), t.getName(), t.getDueDate()))
            .toList();
    List<CategoryUsageDto.SeriesItem> series =
        seriesRepository.findByUserIdAndCategoryIdOrderByNameAscIdAsc(userId, id).stream()
            .map(
                s ->
                    new CategoryUsageDto.SeriesItem(
                        s.getId(), s.getName(), RecurrenceRules.summary(s.rule())))
            .toList();
    return new CategoryUsageDto(todos, series);
  }

  /**
   * Deletes a category after moving the assigned items. Items that are not assigned end up without
   * a category. All assignments are validated first, so one invalid assignment changes nothing.
   * Records a CATEGORY_DELETED audit entry with the assignments.
   *
   * @param userId the owner
   * @param id the category to delete
   * @param req where the items go
   * @throws ApiException NOT_FOUND when the category is not the user's, VALIDATION_FAILED when an
   *     assignment is invalid
   */
  @Transactional
  public void delete(UUID userId, UUID id, DeleteCategoryRequest req) {
    Category category = requireOwned(userId, id);
    List<Assignment> assignments =
        req == null || req.assignments() == null ? List.of() : req.assignments();
    Set<UUID> todoIds =
        todoRepository.findOneOffsInCategory(userId, id).stream()
            .map(Todo::getId)
            .collect(Collectors.toSet());
    Set<UUID> seriesIds =
        seriesRepository.findByUserIdAndCategoryIdOrderByNameAscIdAsc(userId, id).stream()
            .map(RecurrenceSeries::getId)
            .collect(Collectors.toSet());
    Set<UUID> validTargets = new HashSet<>();
    for (Assignment a : assignments) {
      if (a == null || a.kind() == null || a.id() == null) {
        throw invalid("Every assignment needs a kind and an id.");
      }
      boolean inCategory =
          a.kind() == Kind.TODO ? todoIds.contains(a.id()) : seriesIds.contains(a.id());
      if (!inCategory) {
        throw invalid("An assigned item is not in this category.");
      }
      UUID target = a.categoryId();
      if (target == null || validTargets.contains(target)) {
        continue;
      }
      if (target.equals(id)) {
        throw invalid("An item cannot be moved to the category being deleted.");
      }
      if (categoryRepository.findByIdAndUserId(target, userId).isEmpty()) {
        throw invalid("An assignment points to an unknown category.");
      }
      validTargets.add(target);
    }

    Map<String, Object> before = snapshot(category);
    for (Assignment a : assignments) {
      if (a.kind() == Kind.TODO) {
        todoRepository.updateCategory(a.id(), a.categoryId());
      } else {
        seriesRepository.updateCategory(a.id(), a.categoryId());
        todoRepository.updateCategoryOfSeries(a.id(), a.categoryId());
      }
    }
    categoryRepository.deleteById(id);
    categoryRepository.flush();
    auditService.record(
        AuditAction.CATEGORY_DELETED,
        userId,
        ENTITY_TYPE,
        id,
        before,
        Map.of("assignments", assignments));
  }

  private Map<UUID, Integer> itemCounts(UUID userId) {
    Map<UUID, Integer> counts = new HashMap<>();
    for (CategoryCount row : todoRepository.countOneOffsByCategory(userId)) {
      counts.merge(row.getCategoryId(), (int) row.getItemCount(), Integer::sum);
    }
    for (CategoryCount row : seriesRepository.countByCategory(userId)) {
      counts.merge(row.getCategoryId(), (int) row.getItemCount(), Integer::sum);
    }
    return counts;
  }

  private static ApiException invalid(String message) {
    return new ApiException(ErrorCode.VALIDATION_FAILED, message);
  }

  /**
   * Returns a category only when it belongs to the given user. Used by other features to validate a
   * category id.
   *
   * @param userId the owner
   * @param categoryId the category id
   * @return the category
   * @throws ApiException NOT_FOUND when the category does not exist or belongs to another user
   */
  @Transactional(readOnly = true)
  public Category requireOwned(UUID userId, UUID categoryId) {
    return categoryRepository
        .findByIdAndUserId(categoryId, userId)
        .orElseThrow(() -> ApiException.notFound("Category"));
  }

  private static String validName(String raw) {
    String name = raw == null ? "" : raw.trim();
    if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED, "Name must be between 1 and 40 characters.");
    }
    return name;
  }

  private static Map<String, Object> snapshot(Category category) {
    return Map.of("name", category.getName(), "swatch", category.getSwatch().name());
  }

  private static ApiException nameTaken() {
    return new ApiException(ErrorCode.CATEGORY_NAME_TAKEN, NAME_TAKEN);
  }

  private static ApiException nameTaken(Throwable cause) {
    return new ApiException(ErrorCode.CATEGORY_NAME_TAKEN, NAME_TAKEN, cause);
  }
}
