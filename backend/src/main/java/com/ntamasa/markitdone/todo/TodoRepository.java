package com.ntamasa.markitdone.todo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository for accessing todo entities. */
@Repository
public interface TodoRepository extends JpaRepository<Todo, UUID> {

  /**
   * Lists a user's todos whose due date falls within a date range, both ends inclusive.
   *
   * @param userId the owner
   * @param from the first due date (inclusive)
   * @param to the last due date (inclusive)
   * @return the matching todos, in no particular order
   */
  List<Todo> findByUserIdAndDueDateBetween(UUID userId, LocalDate from, LocalDate to);

  /**
   * Finds a todo by id, only if it belongs to the given user.
   *
   * @param id the todo id
   * @param userId the owner
   * @return the todo if it exists and belongs to the user
   */
  Optional<Todo> findByIdAndUserId(UUID id, UUID userId);
}
