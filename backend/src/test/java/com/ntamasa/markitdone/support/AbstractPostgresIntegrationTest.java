package com.ntamasa.markitdone.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base class for integration tests that run the application context against a real PostgreSQL
 * database started by Testcontainers.
 *
 * <p>The container is a singleton shared by all subclasses, because Spring caches the application
 * context across test classes. The web environment is not started. Subclasses get the {@code test}
 * profile.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
public abstract class AbstractPostgresIntegrationTest {

  @ServiceConnection static final PostgreSQLContainer POSTGRES;

  static {
    POSTGRES = new PostgreSQLContainer("postgres:18");
    POSTGRES.start();
  }
}
