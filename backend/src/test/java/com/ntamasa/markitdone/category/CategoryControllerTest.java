package com.ntamasa.markitdone.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class CategoryControllerTest {

  @Mock private CategoryService categoryService;

  @InjectMocks private CategoryController categoryController;

  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void authenticate() {
    Jwt jwt =
        Jwt.withTokenValue("t")
            .header("alg", "HS256")
            .subject(userId.toString())
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void listUsesCurrentUser() {
    List<CategoryDto> expected = List.of();
    when(categoryService.list(userId)).thenReturn(expected);

    assertThat(categoryController.list()).isSameAs(expected);
  }

  @Test
  void createUsesCurrentUser() {
    CategoryRequest req = new CategoryRequest("Work", Swatch.TEAL);
    CategoryDto expected = new CategoryDto(UUID.randomUUID(), "Work", Swatch.TEAL, 0);
    when(categoryService.create(eq(userId), any(CategoryRequest.class))).thenReturn(expected);

    assertThat(categoryController.create(req)).isSameAs(expected);
    verify(categoryService).create(userId, req);
  }

  @Test
  void updateUsesCurrentUserAndPathId() {
    UUID categoryId = UUID.randomUUID();
    CategoryRequest req = new CategoryRequest("Home", Swatch.SKY);
    CategoryDto expected = new CategoryDto(categoryId, "Home", Swatch.SKY, 0);
    when(categoryService.update(userId, categoryId, req)).thenReturn(expected);

    assertThat(categoryController.update(categoryId, req)).isSameAs(expected);
  }
}
