package com.ntamasa.markitdone.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ntamasa.markitdone.audit.AuditAction;
import com.ntamasa.markitdone.audit.AuditService;
import com.ntamasa.markitdone.common.ApiException;
import com.ntamasa.markitdone.common.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

  @Mock private CategoryRepository categoryRepository;
  @Mock private AuditService auditService;

  @InjectMocks private CategoryService categoryService;

  private static final UUID USER_ID = UUID.randomUUID();

  private static Category stored(UUID userId, String name, Swatch swatch) {
    Category category = new Category(userId, name, swatch);
    ReflectionTestUtils.setField(category, "id", UUID.randomUUID());
    return category;
  }

  private void saveReturnsArgumentWithId() {
    when(categoryRepository.saveAndFlush(any(Category.class)))
        .thenAnswer(
            invocation -> {
              Category category = invocation.getArgument(0);
              ReflectionTestUtils.setField(category, "id", UUID.randomUUID());
              return category;
            });
  }

  @Test
  @DisplayName("list maps the user's categories in repository order with item count 0")
  void listMapsCategories() {
    Category work = stored(USER_ID, "Work", Swatch.TEAL);
    when(categoryRepository.findByUserIdOrderByNameKeyAsc(USER_ID)).thenReturn(List.of(work));

    List<CategoryDto> result = categoryService.list(USER_ID);

    assertThat(result).containsExactly(new CategoryDto(work.getId(), "Work", Swatch.TEAL, 0));
  }

  @Test
  @DisplayName("create saves a trimmed name and audits CATEGORY_CREATED with the new state")
  void createSavesAndAudits() {
    when(categoryRepository.existsByUserIdAndNameKey(USER_ID, "work")).thenReturn(false);
    saveReturnsArgumentWithId();

    CategoryDto result =
        categoryService.create(USER_ID, new CategoryRequest("  Work ", Swatch.SKY));

    assertThat(result.name()).isEqualTo("Work");
    assertThat(result.swatch()).isEqualTo(Swatch.SKY);
    assertThat(result.itemCount()).isZero();
    assertThat(result.id()).isNotNull();
    verify(auditService)
        .record(
            eq(AuditAction.CATEGORY_CREATED),
            eq(USER_ID),
            eq("Category"),
            eq(result.id()),
            eq(null),
            eq(Map.of("name", "Work", "swatch", "SKY")));
  }

  @Test
  @DisplayName("create with a name the user already has is rejected with 409 and not saved")
  void createDuplicateNameIsConflict() {
    when(categoryRepository.existsByUserIdAndNameKey(USER_ID, "work")).thenReturn(true);

    assertThatThrownBy(
            () -> categoryService.create(USER_ID, new CategoryRequest("WORK", Swatch.TEAL)))
        .isInstanceOfSatisfying(
            ApiException.class,
            ex -> {
              assertThat(ex.getCode()).isEqualTo(ErrorCode.CATEGORY_NAME_TAKEN);
              assertThat(ex.getMessage()).isEqualTo("You already have a category with this name.");
            });
    verify(categoryRepository, never()).saveAndFlush(any());
    verify(auditService, never()).record(any(), any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("create maps a unique-constraint race to 409 CATEGORY_NAME_TAKEN")
  void createRaceIsConflict() {
    when(categoryRepository.existsByUserIdAndNameKey(USER_ID, "work")).thenReturn(false);
    when(categoryRepository.saveAndFlush(any(Category.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate"));

    assertThatThrownBy(
            () -> categoryService.create(USER_ID, new CategoryRequest("Work", Swatch.TEAL)))
        .isInstanceOfSatisfying(
            ApiException.class,
            ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.CATEGORY_NAME_TAKEN));
    verify(auditService, never()).record(any(), any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("create rejects a name that is blank after trimming")
  void createBlankNameIsValidationFailure() {
    assertThatThrownBy(
            () -> categoryService.create(USER_ID, new CategoryRequest("   ", Swatch.TEAL)))
        .isInstanceOfSatisfying(
            ApiException.class,
            ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    verify(categoryRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("create counts the length after trimming: 40 characters are fine, 41 are not")
  void createLengthIsCheckedAfterTrimming() {
    String forty = "a".repeat(40);
    when(categoryRepository.existsByUserIdAndNameKey(USER_ID, forty)).thenReturn(false);
    saveReturnsArgumentWithId();

    assertThat(
            categoryService
                .create(USER_ID, new CategoryRequest("  " + forty + " ", Swatch.TEAL))
                .name())
        .isEqualTo(forty);

    assertThatThrownBy(
            () -> categoryService.create(USER_ID, new CategoryRequest("b".repeat(41), Swatch.TEAL)))
        .isInstanceOfSatisfying(
            ApiException.class,
            ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
  }

  @Test
  @DisplayName("update changes name and swatch and audits CATEGORY_UPDATED before and after")
  void updateChangesAndAudits() {
    Category category = stored(USER_ID, "Work", Swatch.TEAL);
    when(categoryRepository.findByIdAndUserId(category.getId(), USER_ID))
        .thenReturn(Optional.of(category));
    when(categoryRepository.existsByUserIdAndNameKeyAndIdNot(USER_ID, "home", category.getId()))
        .thenReturn(false);

    CategoryDto result =
        categoryService.update(
            USER_ID, category.getId(), new CategoryRequest("Home", Swatch.INDIGO));

    assertThat(result).isEqualTo(new CategoryDto(category.getId(), "Home", Swatch.INDIGO, 0));
    verify(auditService)
        .record(
            eq(AuditAction.CATEGORY_UPDATED),
            eq(USER_ID),
            eq("Category"),
            eq(category.getId()),
            eq(Map.of("name", "Work", "swatch", "TEAL")),
            eq(Map.of("name", "Home", "swatch", "INDIGO")));
  }

  @Test
  @DisplayName("renaming a category to its own name in another case is allowed")
  void updateToOwnNameInOtherCaseIsAllowed() {
    Category category = stored(USER_ID, "Work", Swatch.TEAL);
    when(categoryRepository.findByIdAndUserId(category.getId(), USER_ID))
        .thenReturn(Optional.of(category));
    when(categoryRepository.existsByUserIdAndNameKeyAndIdNot(USER_ID, "work", category.getId()))
        .thenReturn(false);

    CategoryDto result =
        categoryService.update(USER_ID, category.getId(), new CategoryRequest("WORK", Swatch.TEAL));

    assertThat(result.name()).isEqualTo("WORK");
    verify(categoryRepository).saveAndFlush(category);
  }

  @Test
  @DisplayName("update to a name another category of the user has is rejected with 409")
  void updateToTakenNameIsConflict() {
    Category category = stored(USER_ID, "Work", Swatch.TEAL);
    when(categoryRepository.findByIdAndUserId(category.getId(), USER_ID))
        .thenReturn(Optional.of(category));
    when(categoryRepository.existsByUserIdAndNameKeyAndIdNot(USER_ID, "home", category.getId()))
        .thenReturn(true);

    assertThatThrownBy(
            () ->
                categoryService.update(
                    USER_ID, category.getId(), new CategoryRequest("Home", Swatch.TEAL)))
        .isInstanceOfSatisfying(
            ApiException.class,
            ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.CATEGORY_NAME_TAKEN));
    verify(auditService, never()).record(any(), any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("update of a category that is not the user's is 404 and reveals nothing")
  void updateOtherUsersCategoryIsNotFound() {
    UUID categoryId = UUID.randomUUID();
    when(categoryRepository.findByIdAndUserId(categoryId, USER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                categoryService.update(
                    USER_ID, categoryId, new CategoryRequest("Home", Swatch.TEAL)))
        .isInstanceOfSatisfying(
            ApiException.class,
            ex -> {
              assertThat(ex.getCode()).isEqualTo(ErrorCode.NOT_FOUND);
              assertThat(ex.getMessage()).isEqualTo("Category not found");
            });
    verify(categoryRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("requireOwned returns the user's category")
  void requireOwnedReturnsCategory() {
    Category category = stored(USER_ID, "Work", Swatch.TEAL);
    when(categoryRepository.findByIdAndUserId(category.getId(), USER_ID))
        .thenReturn(Optional.of(category));

    assertThat(categoryService.requireOwned(USER_ID, category.getId())).isSameAs(category);
  }

  @Test
  @DisplayName("requireOwned throws NOT_FOUND for a category of another user")
  void requireOwnedOtherUserIsNotFound() {
    UUID categoryId = UUID.randomUUID();
    when(categoryRepository.findByIdAndUserId(categoryId, USER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> categoryService.requireOwned(USER_ID, categoryId))
        .isInstanceOfSatisfying(
            ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
  }
}
