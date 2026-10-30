package com.ntamasa.markitdone.category;

import com.ntamasa.markitdone.audit.AuditAction;
import com.ntamasa.markitdone.audit.AuditService;
import com.ntamasa.markitdone.common.ApiException;
import com.ntamasa.markitdone.common.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
  private final AuditService auditService;

  /**
   * Create a CategoryService.
   *
   * @param categoryRepository the category repository
   * @param auditService the audit service
   */
  public CategoryService(CategoryRepository categoryRepository, AuditService auditService) {
    this.categoryRepository = categoryRepository;
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
    return categoryRepository.findByUserIdOrderByNameKeyAsc(userId).stream()
        .map(CategoryDto::of)
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
    return CategoryDto.of(category);
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
    return CategoryDto.of(category);
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
