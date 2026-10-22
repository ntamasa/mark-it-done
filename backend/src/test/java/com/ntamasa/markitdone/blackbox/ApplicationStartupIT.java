package com.ntamasa.markitdone.blackbox;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying application startup and health endpoint.
 * Tests that Liquibase runs and the application context loads properly.
 */
class ApplicationStartupIT extends AbstractIntegrationTest {

	@Test
	void contextLoads() {
		// This test implicitly verifies that the context loads by extending
		// the base class which is annotated with @SpringBootTest.
		// The test passes if no exception is thrown during context initialization.
		assertThat(port).isGreaterThan(0);
	}

	@Test
	void healthEndpointReturnsUp() throws Exception {
		HttpRequest request = HttpRequest.newBuilder()
			.uri(java.net.URI.create(baseUrl() + "/actuator/health"))
			.GET()
			.build();

		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("\"status\":\"UP\"");
	}
}
