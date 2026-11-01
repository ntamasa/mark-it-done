package com.ntamasa.markitdone.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ntamasa.markitdone.audit.AuditLogRepository;
import com.ntamasa.markitdone.common.ApiException;
import com.ntamasa.markitdone.common.ErrorCode;
import com.ntamasa.markitdone.recurrence.EndType;
import com.ntamasa.markitdone.recurrence.Frequency;
import com.ntamasa.markitdone.recurrence.RecurrenceSeriesRepository;
import com.ntamasa.markitdone.recurrence.SeriesService;
import com.ntamasa.markitdone.recurrence.dto.RuleRequest;
import com.ntamasa.markitdone.recurrence.dto.SeriesRequest;
import com.ntamasa.markitdone.support.AbstractPostgresIntegrationTest;
import com.ntamasa.markitdone.support.TestUsers;
import com.ntamasa.markitdone.todo.Priority;
import com.ntamasa.markitdone.todo.Todo;
import com.ntamasa.markitdone.todo.TodoRepository;
import com.ntamasa.markitdone.todo.TodoService;
import com.ntamasa.markitdone.todo.dto.TodoRequest;
import com.ntamasa.markitdone.user.User;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Category usage, item counts and deletion with reassignments, against PostgreSQL. */
class CategoryDeleteIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private CategoryService categoryService;
  @Autowired private TodoService todoService;
  @Autowired private SeriesService seriesService;
  @Autowired private TodoRepository todoRepository;
  @Autowired private RecurrenceSeriesRepository seriesRepository;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private AuditLogRepository auditLogRepository;
  @Autowired private TestUsers testUsers;

  /** A user with Work (2 one-offs, 1 series of 3 occurrences) and an empty Home. */
  private record Fixture(User user, UUID work, UUID home, UUID todo1, UUID todo2, UUID series) {}

  private Fixture fixture() {
    User user = testUsers.create("cat-delete-" + UUID.randomUUID() + "@example.com");
    UUID work = categoryService.create(user.getId(), new CategoryRequest("Work", Swatch.TEAL)).id();
    UUID home = categoryService.create(user.getId(), new CategoryRequest("Home", Swatch.PINK)).id();
    LocalDate day = LocalDate.now().plusDays(1);
    UUID todo1 = newTodo(user, "One", day, work);
    UUID todo2 = newTodo(user, "Two", day.plusDays(1), work);
    RuleRequest rule =
        new RuleRequest(Frequency.EVERY_N_DAYS, 1, null, null, day, EndType.AFTER_COUNT, 3, null);
    UUID series =
        seriesService
            .create(user.getId(), new SeriesRequest("Gym", null, null, Priority.MEDIUM, work, rule))
            .id();
    return new Fixture(user, work, home, todo1, todo2, series);
  }

  private UUID newTodo(User user, String name, LocalDate due, UUID categoryId) {
    return todoService
        .create(user.getId(), new TodoRequest(name, null, due, null, Priority.MEDIUM, categoryId))
        .id();
  }

  private List<Todo> occurrences(UUID seriesId) {
    return todoRepository.findBySeriesIdAndOccurrenceDateGreaterThanEqual(
        seriesId, LocalDate.of(2000, 1, 1));
  }

  private UUID categoryOf(UUID todoId) {
    return todoRepository.findById(todoId).orElseThrow().getCategoryId();
  }

  private static Assignment todo(UUID id, UUID target) {
    return new Assignment(Kind.TODO, id, target);
  }

  private static Assignment series(UUID id, UUID target) {
    return new Assignment(Kind.SERIES, id, target);
  }

  @Test
  @DisplayName("usage lists the one-offs and the series, and itemCount is one-offs plus series")
  void usageAndItemCount() {
    Fixture f = fixture();

    CategoryUsageDto usage = categoryService.usage(f.user().getId(), f.work());

    assertThat(usage.todos())
        .extracting(CategoryUsageDto.TodoItem::id)
        .containsExactly(f.todo1(), f.todo2());
    assertThat(usage.todos().getFirst().name()).isEqualTo("One");
    assertThat(usage.todos().getFirst().dueDate()).isEqualTo(LocalDate.now().plusDays(1));
    assertThat(usage.series()).hasSize(1);
    assertThat(usage.series().getFirst().id()).isEqualTo(f.series());
    assertThat(usage.series().getFirst().name()).isEqualTo("Gym");
    assertThat(usage.series().getFirst().ruleSummary()).isNotBlank();
    assertThat(occurrences(f.series())).hasSize(3);

    List<CategoryDto> list = categoryService.list(f.user().getId());
    assertThat(list)
        .filteredOn(c -> c.id().equals(f.work()))
        .singleElement()
        .satisfies(c -> assertThat(c.itemCount()).isEqualTo(3));
    assertThat(list)
        .filteredOn(c -> c.id().equals(f.home()))
        .singleElement()
        .satisfies(c -> assertThat(c.itemCount()).isZero());
  }

  @Test
  @DisplayName("update returns the real itemCount")
  void updateReturnsItemCount() {
    Fixture f = fixture();

    CategoryDto dto =
        categoryService.update(
            f.user().getId(), f.work(), new CategoryRequest("Work 2", Swatch.TEAL));

    assertThat(dto.itemCount()).isEqualTo(3);
  }

  @Test
  @DisplayName("delete moves assigned items, leaves the others uncategorised and audits")
  void deleteWithAssignments() {
    Fixture f = fixture();
    UUID userId = f.user().getId();

    categoryService.delete(
        userId,
        f.work(),
        new DeleteCategoryRequest(
            List.of(todo(f.todo1(), f.home()), series(f.series(), f.home()))));

    assertThat(categoryOf(f.todo1())).isEqualTo(f.home());
    assertThat(categoryOf(f.todo2())).isNull();
    assertThat(seriesRepository.findById(f.series()).orElseThrow().getCategoryId())
        .isEqualTo(f.home());
    assertThat(occurrences(f.series()))
        .hasSize(3)
        .allSatisfy(t -> assertThat(t.getCategoryId()).isEqualTo(f.home()));
    assertThat(categoryRepository.findById(f.work())).isEmpty();
    assertThat(auditLogRepository.findByUserIdOrderByOccurredAtDesc(userId))
        .anySatisfy(
            e -> {
              assertThat(e.getAction()).isEqualTo("CATEGORY_DELETED");
              assertThat(e.getEntityId()).isEqualTo(f.work().toString());
              assertThat(e.getAfterState()).contains(f.todo1().toString());
            });
  }

  @Test
  @DisplayName("a null target moves the item to no category and an empty list leaves all items")
  void nullTargetAndEmptyList() {
    Fixture f = fixture();

    categoryService.delete(
        f.user().getId(), f.work(), new DeleteCategoryRequest(List.of(series(f.series(), null))));

    assertThat(seriesRepository.findById(f.series()).orElseThrow().getCategoryId()).isNull();
    assertThat(occurrences(f.series())).allSatisfy(t -> assertThat(t.getCategoryId()).isNull());
    assertThat(categoryRepository.findById(f.work())).isEmpty();
  }

  @Test
  @DisplayName("an assignment targeting the deleted category is a 400 and nothing changes")
  void targetIsDeletedCategory() {
    Fixture f = fixture();

    assertThatThrownBy(
            () ->
                categoryService.delete(
                    f.user().getId(),
                    f.work(),
                    new DeleteCategoryRequest(List.of(todo(f.todo1(), f.work())))))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));

    assertUnchanged(f);
  }

  @Test
  @DisplayName("one invalid assignment in a mixed list leaves the valid ones unapplied too")
  void mixedListRollsBack() {
    Fixture f = fixture();

    assertThatThrownBy(
            () ->
                categoryService.delete(
                    f.user().getId(),
                    f.work(),
                    new DeleteCategoryRequest(
                        List.of(
                            todo(f.todo1(), f.home()),
                            series(f.series(), f.home()),
                            todo(f.todo2(), UUID.randomUUID())))))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));

    assertUnchanged(f);
  }

  @Test
  @DisplayName("an item that is not in this category or not the user's is a 400")
  void itemNotInCategory() {
    Fixture f = fixture();
    User other = testUsers.create("cat-other-" + UUID.randomUUID() + "@example.com");
    UUID foreignTodo = newTodo(other, "Foreign", LocalDate.of(2030, 5, 1), null);
    UUID elsewhere = newTodo(f.user(), "Elsewhere", LocalDate.of(2030, 5, 1), f.home());

    for (UUID item : List.of(foreignTodo, elsewhere)) {
      assertThatThrownBy(
              () ->
                  categoryService.delete(
                      f.user().getId(),
                      f.work(),
                      new DeleteCategoryRequest(List.of(todo(item, f.home())))))
          .isInstanceOfSatisfying(
              ApiException.class,
              e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }
    // a todo id used as a series, and a series id used as a todo
    assertThatThrownBy(
            () ->
                categoryService.delete(
                    f.user().getId(),
                    f.work(),
                    new DeleteCategoryRequest(List.of(series(f.todo1(), f.home())))))
        .isInstanceOf(ApiException.class);
    assertUnchanged(f);
  }

  @Test
  @DisplayName("a target category of another user is a 400")
  void foreignTarget() {
    Fixture f = fixture();
    User other = testUsers.create("cat-other-" + UUID.randomUUID() + "@example.com");
    UUID foreign =
        categoryService.create(other.getId(), new CategoryRequest("Mine", Swatch.SKY)).id();

    assertThatThrownBy(
            () ->
                categoryService.delete(
                    f.user().getId(),
                    f.work(),
                    new DeleteCategoryRequest(List.of(todo(f.todo1(), foreign)))))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));

    assertUnchanged(f);
  }

  @Test
  @DisplayName("another user's category is a 404 for usage and delete")
  void foreignCategoryIsNotFound() {
    Fixture f = fixture();
    User other = testUsers.create("cat-other-" + UUID.randomUUID() + "@example.com");

    assertThatThrownBy(() -> categoryService.delete(other.getId(), f.work(), emptyRequest()))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
    assertThatThrownBy(() -> categoryService.usage(other.getId(), f.work()))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
    assertUnchanged(f);
  }

  private static DeleteCategoryRequest emptyRequest() {
    return new DeleteCategoryRequest(List.of());
  }

  private void assertUnchanged(Fixture f) {
    assertThat(categoryRepository.findById(f.work())).isPresent();
    assertThat(categoryOf(f.todo1())).isEqualTo(f.work());
    assertThat(categoryOf(f.todo2())).isEqualTo(f.work());
    assertThat(seriesRepository.findById(f.series()).orElseThrow().getCategoryId())
        .isEqualTo(f.work());
    assertThat(occurrences(f.series()))
        .allSatisfy(t -> assertThat(t.getCategoryId()).isEqualTo(f.work()));
  }
}
