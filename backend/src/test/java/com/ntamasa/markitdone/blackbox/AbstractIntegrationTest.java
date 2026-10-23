package com.ntamasa.markitdone.blackbox;

import java.net.http.HttpClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * Base class for all backend integration tests.
 * Provides shared PostgreSQL 18 and Mailpit containers for integration testing.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
abstract class AbstractIntegrationTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

	@Container
	static GenericContainer<?> mailpit = new GenericContainer<>("axllent/mailpit")
		.withExposedPorts(1025, 8025);

	@LocalServerPort
	protected int port;

	protected HttpClient httpClient = HttpClient.newHttpClient();

	@DynamicPropertySource
	static void configureMailpit(DynamicPropertyRegistry registry) {
		registry.add("spring.mail.host", () -> mailpit.getHost());
		registry.add("spring.mail.port", () -> mailpit.getMappedPort(1025));
	}

	protected String baseUrl() {
		return "http://localhost:" + port;
	}

	protected String mailpitHttpApiUrl() {
		return "http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025);
	}
}
