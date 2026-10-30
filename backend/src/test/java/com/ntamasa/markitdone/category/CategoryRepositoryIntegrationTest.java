package com.ntamasa.markitdone.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ntamasa.markitdone.support.AbstractPostgresIntegrationTest;
import com.ntamasa.markitdone.support.TestUsers;
import com.ntamasa.markitdone.user.User;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class CategoryRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private CategoryRepository categoryRepository;
  @Autowired private TestUsers testUsers;

  private User newUser() {
    return testUsers.create("categories-" + UUID.randomUUID() + "@example.com");
  }

  @Test
  @DisplayName("two categories of one user are listed sorted by name")
  void listsCategoriesSortedByName() {
    User user = newUser();
    categoryRepository.saveAndFlush(new Category(user.getId(), "Work", Swatch.TEAL));
    categoryRepository.saveAndFlush(new Category(user.getId(), "Home", Swatch.SKY));

    List<Category> result = categoryRepository.findByUserIdOrderByNameKeyAsc(user.getId());

    assertThat(result).extracting(Category::getName).containsExactly("Home", "Work");
  }

  @Test
  @DisplayName("the same name in a different case for the same user violates the database")
  void duplicateNameKeyForSameUserIsRejected() {
    User user = newUser();
    categoryRepository.saveAndFlush(new Category(user.getId(), "Work", Swatch.TEAL));

    assertThatThrownBy(
            () ->
                categoryRepository.saveAndFlush(new Category(user.getId(), " work ", Swatch.PINK)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("the same name for another user is accepted")
  void sameNameForAnotherUserIsAccepted() {
    User first = newUser();
    User second = newUser();
    categoryRepository.saveAndFlush(new Category(first.getId(), "Work", Swatch.TEAL));

    Category saved =
        categoryRepository.saveAndFlush(new Category(second.getId(), "Work", Swatch.TEAL));

    assertThat(saved.getId()).isNotNull();
    assertThat(categoryRepository.existsByUserIdAndNameKey(second.getId(), "work")).isTrue();
  }

  @Test
  @DisplayName("findByIdAndUserId returns the category only for its owner")
  void findByIdAndUserIdChecksOwnership() {
    User owner = newUser();
    User other = newUser();
    Category saved =
        categoryRepository.saveAndFlush(new Category(owner.getId(), "Work", Swatch.INDIGO));

    assertThat(categoryRepository.findByIdAndUserId(saved.getId(), owner.getId())).isPresent();
    assertThat(categoryRepository.findByIdAndUserId(saved.getId(), other.getId())).isEmpty();
  }

  @Test
  @DisplayName("existsByUserIdAndNameKeyAndIdNot ignores the category itself")
  void existsExcludingSelf() {
    User user = newUser();
    Category work =
        categoryRepository.saveAndFlush(new Category(user.getId(), "Work", Swatch.TEAL));
    categoryRepository.saveAndFlush(new Category(user.getId(), "Home", Swatch.SKY));

    assertThat(
            categoryRepository.existsByUserIdAndNameKeyAndIdNot(user.getId(), "work", work.getId()))
        .isFalse();
    assertThat(
            categoryRepository.existsByUserIdAndNameKeyAndIdNot(user.getId(), "home", work.getId()))
        .isTrue();
  }
}
