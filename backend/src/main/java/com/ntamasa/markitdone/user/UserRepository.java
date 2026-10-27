package com.ntamasa.markitdone.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository for accessing user entities. */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

  /**
   * Finds a user by email.
   *
   * @param email the user's email
   * @return an Optional containing the user if found
   */
  Optional<User> findByEmail(String email);

  /**
   * Checks if a user with the given email exists.
   *
   * @param email the user's email
   * @return true if a user with the email exists, false otherwise
   */
  boolean existsByEmail(String email);
}
