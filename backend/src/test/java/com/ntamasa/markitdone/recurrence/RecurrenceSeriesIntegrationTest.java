package com.ntamasa.markitdone.recurrence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ntamasa.markitdone.category.Category;
import com.ntamasa.markitdone.category.CategoryRepository;
import com.ntamasa.markitdone.category.Swatch;
import com.ntamasa.markitdone.support.AbstractPostgresIntegrationTest;
import com.ntamasa.markitdone.support.TestUsers;
import com.ntamasa.markitdone.todo.Priority;
import com.ntamasa.markitdone.todo.Todo;
import com.ntamasa.markitdone.todo.TodoRepository;
import com.ntamasa.markitdone.user.User;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class RecurrenceSeriesIntegrationTest extends AbstractPostgresIntegrationTest {

  private static final LocalDate DAY = LocalDate.of(2026, 6, 1);

  @Autowired private RecurrenceSeriesRepository seriesRepository;
  @Autowired private SeriesExclusionRepository exclusionRepository;
  @Autowired private TodoRepository todoRepository;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private TestUsers testUsers;

  private User newUser() {
    return testUsers.create("series-" + UUID.randomUUID() + "@example.com");
  }

  private RecurrenceSeries newSeries(User user, UUID categoryId) {
    RecurrenceRule rule =
        new RecurrenceRule(
            Frequency.WEEKLY,
            null,
            Set.of(DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY),
            null,
            DAY,
            EndType.AFTER_COUNT,
            10,
            null);
    return seriesRepository.saveAndFlush(
        new RecurrenceSeries(
            user.getId(), "Gym", "Legs", LocalTime.of(7, 0), Priority.HIGH, categoryId, rule));
  }

  @Test
  @DisplayName("a series round-trips with its rule, defaults and queries")
  void saveAndFind() {
    User user = newUser();
    RecurrenceSeries saved = newSeries(user, null);

    RecurrenceSeries found =
        seriesRepository.findByIdAndUserId(saved.getId(), user.getId()).orElseThrow();

    assertThat(found.getStatus()).isEqualTo(SeriesStatus.ACTIVE);
    assertThat(found.weekdays()).containsExactly(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);
    assertThat(found.rule().frequency()).isEqualTo(Frequency.WEEKLY);
    assertThat(found.rule().monthDays()).isEmpty();
    assertThat(found.rule().endCount()).isEqualTo(10);
    assertThat(found.getCreatedAt()).isNotNull();
    assertThat(seriesRepository.findByIdAndUserId(saved.getId(), UUID.randomUUID())).isEmpty();
    assertThat(seriesRepository.findByUserIdOrderByNameAsc(user.getId())).hasSize(1);
  }

  @Test
  @DisplayName("two todos with the same series and occurrence date violate the unique index")
  void duplicateSlotRejected() {
    User user = newUser();
    RecurrenceSeries series = newSeries(user, null);
    todoRepository.saveAndFlush(Todo.occurrenceOf(series, DAY));

    assertThatThrownBy(() -> todoRepository.saveAndFlush(Todo.occurrenceOf(series, DAY)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("two one-off todos with the same date are allowed")
  void oneOffDuplicatesAllowed() {
    User user = newUser();
    todoRepository.saveAndFlush(new Todo(user.getId(), "A", null, DAY, null, Priority.LOW, null));
    todoRepository.saveAndFlush(new Todo(user.getId(), "A", null, DAY, null, Priority.LOW, null));

    assertThat(todoRepository.findByUserIdAndDueDateBetween(user.getId(), DAY, DAY)).hasSize(2);
  }

  @Test
  @DisplayName("occurrenceOf copies the template and is not detached")
  void occurrenceOfCopiesTemplate() {
    User user = newUser();
    RecurrenceSeries series = newSeries(user, null);

    Todo todo = Todo.occurrenceOf(series, DAY.plusDays(2));

    assertThat(todo.getSeriesId()).isEqualTo(series.getId());
    assertThat(todo.getOccurrenceDate()).isEqualTo(DAY.plusDays(2));
    assertThat(todo.getDueDate()).isEqualTo(DAY.plusDays(2));
    assertThat(todo.getName()).isEqualTo("Gym");
    assertThat(todo.getDueTime()).isEqualTo(LocalTime.of(7, 0));
    assertThat(todo.getPriority()).isEqualTo(Priority.HIGH);
    assertThat(todo.isDetached()).isFalse();
  }

  @Test
  @DisplayName("deleting a series deletes its occurrences and exclusions")
  void deletingSeriesCascades() {
    User user = newUser();
    RecurrenceSeries series = newSeries(user, null);
    Todo occurrence = todoRepository.saveAndFlush(Todo.occurrenceOf(series, DAY));
    exclusionRepository.saveAndFlush(new SeriesExclusion(series.getId(), DAY.plusDays(7)));

    seriesRepository.deleteById(series.getId());
    seriesRepository.flush();

    assertThat(todoRepository.findById(occurrence.getId())).isEmpty();
    assertThat(exclusionRepository.findExcludedDates(series.getId())).isEmpty();
  }

  @Test
  @DisplayName("deleting the category sets the series category to null")
  void deletingCategoryClearsSeriesCategory() {
    User user = newUser();
    Category category =
        categoryRepository.saveAndFlush(new Category(user.getId(), "Health", Swatch.TEAL));
    RecurrenceSeries series = newSeries(user, category.getId());
    assertThat(series.getCategoryId()).isEqualTo(category.getId());

    categoryRepository.delete(category);
    categoryRepository.flush();

    assertThat(seriesRepository.findById(series.getId()).orElseThrow().getCategoryId()).isNull();
  }

  @Test
  @DisplayName("series-scoped todo and exclusion queries work")
  void repositoryQueries() {
    User user = newUser();
    RecurrenceSeries series = newSeries(user, null);
    todoRepository.saveAndFlush(Todo.occurrenceOf(series, DAY));
    Todo detached = Todo.occurrenceOf(series, DAY.plusDays(7));
    detached.setDetached(true);
    todoRepository.saveAndFlush(detached);
    todoRepository.saveAndFlush(Todo.occurrenceOf(series, DAY.plusDays(14)));
    todoRepository.saveAndFlush(Todo.occurrenceOf(series, DAY.plusDays(21)));
    UUID id = series.getId();

    assertThat(todoRepository.findOccurrenceDates(id, DAY, DAY.plusDays(7)))
        .containsExactlyInAnyOrder(DAY, DAY.plusDays(7));
    assertThat(todoRepository.findBySeriesIdAndOccurrenceDateGreaterThanEqual(id, DAY.plusDays(14)))
        .hasSize(2);
    assertThat(todoRepository.findNextDueDate(id, DAY.plusDays(1))).contains(DAY.plusDays(7));

    assertThat(todoRepository.deleteAfter(id, DAY.plusDays(14))).isEqualTo(1);
    assertThat(todoRepository.deleteFutureNonDetached(id, DAY.plusDays(1))).isEqualTo(1);
    assertThat(todoRepository.findOccurrenceDates(id, DAY, DAY.plusDays(30)))
        .containsExactlyInAnyOrder(DAY, DAY.plusDays(7));

    exclusionRepository.saveAndFlush(new SeriesExclusion(id, DAY));
    exclusionRepository.saveAndFlush(new SeriesExclusion(id, DAY.plusDays(7)));
    assertThat(exclusionRepository.findExcludedDates(id)).hasSize(2);
    assertThat(exclusionRepository.deleteFromDate(id, DAY.plusDays(1))).isEqualTo(1);
    assertThat(exclusionRepository.findExcludedDates(id)).containsExactly(DAY);
  }
}
