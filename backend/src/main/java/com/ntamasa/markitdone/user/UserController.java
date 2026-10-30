package com.ntamasa.markitdone.user;

import com.ntamasa.markitdone.auth.CurrentUser;
import com.ntamasa.markitdone.user.dto.UserDto;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints for the current user's account and preferences. */
@RestController
@RequestMapping("/api/users")
public class UserController {

  private final UserService userService;

  /**
   * Create a UserController.
   *
   * @param userService the user service
   */
  public UserController(UserService userService) {
    this.userService = userService;
  }

  /**
   * Updates the current user's time zone and theme preference. An empty body changes nothing.
   *
   * @param req the fields to change, may be null
   * @return the updated user
   */
  @PatchMapping("/me")
  public UserDto updatePreferences(
      @Valid @RequestBody(required = false) UpdatePreferencesRequest req) {
    return userService.updatePreferences(CurrentUser.id(), req);
  }
}
