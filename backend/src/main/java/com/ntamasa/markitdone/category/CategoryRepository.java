package com.ntamasa.markitdone.category;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository for accessing category entities. */
@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

  /**
   * Lists a user's categories sorted by their name key.
   *
   * @param userId the owner
   * @return the user's categories in name order
   */
  List<Category> findByUserIdOrderByNameKeyAsc(UUID userId);

  /**
   * Finds a category by id, only if it belongs to the given user.
   *
   * @param id the category id
   * @param userId the owner
   * @return the category if it exists and belongs to the user
   */
  Optional<Category> findByIdAndUserId(UUID id, UUID userId);

  /**
   * Checks whether the user already has a category with the given name key.
   *
   * @param userId the owner
   * @param nameKey the normalised name
   * @return true if the name key is taken
   */
  boolean existsByUserIdAndNameKey(UUID userId, String nameKey);

  /**
   * Checks whether the name key is taken by a category other than the given one.
   *
   * @param userId the owner
   * @param nameKey the normalised name
   * @param id the category to exclude
   * @return true if another category of the user has the name key
   */
  boolean existsByUserIdAndNameKeyAndIdNot(UUID userId, String nameKey, UUID id);
}
