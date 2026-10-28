package com.ntamasa.markitdone.auth;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test-only controller exposing the current user id. */
@RestController
@RequestMapping("/api/test")
public class TestCurrentUserController {

  @GetMapping("/me")
  public String me() {
    return CurrentUser.id().toString();
  }

  @PostMapping("/echo")
  public String echo() {
    return UUID.randomUUID().toString();
  }
}
