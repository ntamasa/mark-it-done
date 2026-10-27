package com.ntamasa.markitdone.blackbox;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for X-Request-Id header handling.
 * Tests that the request ID filter correctly generates, validates, and echoes request IDs.
 */
@DisplayName("X-Request-Id Header Handling")
class RequestIdFilterIT extends AbstractIntegrationTest {

	private static final String REQUEST_ID_HEADER = "X-Request-Id";

	@Test
	@DisplayName("Response carries X-Request-Id header when not provided in request")
	void responseShouldCarryGeneratedRequestId() throws Exception {
		HttpRequest request = HttpRequest.newBuilder()
			.uri(java.net.URI.create(baseUrl() + "/actuator/health"))
			.GET()
			.build();

		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

		assertThat(response.headers().firstValue(REQUEST_ID_HEADER))
			.isPresent()
			.get()
			.asString()
			.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
	}

	@Test
	@DisplayName("Valid request ID (8 chars) is echoed unchanged")
	void validRequestIdEightCharsEchoedUnchanged() throws Exception {
		String clientId = "abcd1234";

		HttpRequest request = HttpRequest.newBuilder()
			.uri(java.net.URI.create(baseUrl() + "/actuator/health"))
			.header(REQUEST_ID_HEADER, clientId)
			.GET()
			.build();

		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

		assertThat(response.headers().firstValue(REQUEST_ID_HEADER))
			.isPresent()
			.get()
			.asString()
			.isEqualTo(clientId);
	}

	@Test
	@DisplayName("Valid request ID (64 chars) is echoed unchanged")
	void validRequestIdSixtyFourCharsEchoedUnchanged() throws Exception {
		String clientId = "a".repeat(64);

		HttpRequest request = HttpRequest.newBuilder()
			.uri(java.net.URI.create(baseUrl() + "/actuator/health"))
			.header(REQUEST_ID_HEADER, clientId)
			.GET()
			.build();

		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

		assertThat(response.headers().firstValue(REQUEST_ID_HEADER))
			.isPresent()
			.get()
			.asString()
			.isEqualTo(clientId);
	}

	@Test
	@DisplayName("Valid request ID with dashes is echoed unchanged")
	void validRequestIdWithDashesEchoedUnchanged() throws Exception {
		String clientId = "abcd1234-ef";

		HttpRequest request = HttpRequest.newBuilder()
			.uri(java.net.URI.create(baseUrl() + "/actuator/health"))
			.header(REQUEST_ID_HEADER, clientId)
			.GET()
			.build();

		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

		assertThat(response.headers().firstValue(REQUEST_ID_HEADER))
			.isPresent()
			.get()
			.asString()
			.isEqualTo(clientId);
	}

	@Test
	@DisplayName("Invalid request ID (7 chars, too short) is replaced with UUID")
	void invalidRequestIdTooShortReplacedWithUuid() throws Exception {
		String invalidId = "short";

		HttpRequest request = HttpRequest.newBuilder()
			.uri(java.net.URI.create(baseUrl() + "/actuator/health"))
			.header(REQUEST_ID_HEADER, invalidId)
			.GET()
			.build();

		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

		assertThat(response.headers().firstValue(REQUEST_ID_HEADER))
			.isPresent()
			.get()
			.asString()
			.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
			.isNotEqualTo(invalidId);
	}

	@Test
	@DisplayName("Invalid request ID (65 chars, too long) is replaced with UUID")
	void invalidRequestIdTooLongReplacedWithUuid() throws Exception {
		String invalidId = "a".repeat(65);

		HttpRequest request = HttpRequest.newBuilder()
			.uri(java.net.URI.create(baseUrl() + "/actuator/health"))
			.header(REQUEST_ID_HEADER, invalidId)
			.GET()
			.build();

		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

		assertThat(response.headers().firstValue(REQUEST_ID_HEADER))
			.isPresent()
			.get()
			.asString()
			.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
			.isNotEqualTo(invalidId);
	}

	@Test
	@DisplayName("Invalid request ID with special chars (script tag) is replaced with UUID")
	void invalidRequestIdWithSpecialCharsReplacedWithUuid() throws Exception {
		String invalidId = "<script>";

		HttpRequest request = HttpRequest.newBuilder()
			.uri(java.net.URI.create(baseUrl() + "/actuator/health"))
			.header(REQUEST_ID_HEADER, invalidId)
			.GET()
			.build();

		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

		assertThat(response.headers().firstValue(REQUEST_ID_HEADER))
			.isPresent()
			.get()
			.asString()
			.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
			.isNotEqualTo(invalidId);
	}

	@Test
	@DisplayName("Invalid request ID with spaces is replaced with UUID")
	void invalidRequestIdWithSpacesReplacedWithUuid() throws Exception {
		String invalidId = "id with spaces";

		HttpRequest request = HttpRequest.newBuilder()
			.uri(java.net.URI.create(baseUrl() + "/actuator/health"))
			.header(REQUEST_ID_HEADER, invalidId)
			.GET()
			.build();

		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

		assertThat(response.headers().firstValue(REQUEST_ID_HEADER))
			.isPresent()
			.get()
			.asString()
			.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
			.isNotEqualTo(invalidId);
	}

	@Test
	@DisplayName("Invalid request ID with underscores is replaced with UUID")
	void invalidRequestIdWithUnderscoresReplacedWithUuid() throws Exception {
		String invalidId = "invalid_id_123";

		HttpRequest request = HttpRequest.newBuilder()
			.uri(java.net.URI.create(baseUrl() + "/actuator/health"))
			.header(REQUEST_ID_HEADER, invalidId)
			.GET()
			.build();

		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

		assertThat(response.headers().firstValue(REQUEST_ID_HEADER))
			.isPresent()
			.get()
			.asString()
			.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
			.isNotEqualTo(invalidId);
	}

	@Test
	@DisplayName("Two requests without header get different IDs")
	void twoRequestsWithoutHeaderGetDifferentIds() throws Exception {
		HttpRequest request1 = HttpRequest.newBuilder()
			.uri(java.net.URI.create(baseUrl() + "/actuator/health"))
			.GET()
			.build();

		HttpResponse<String> response1 = httpClient.send(request1, HttpResponse.BodyHandlers.ofString());

		HttpRequest request2 = HttpRequest.newBuilder()
			.uri(java.net.URI.create(baseUrl() + "/actuator/health"))
			.GET()
			.build();

		HttpResponse<String> response2 = httpClient.send(request2, HttpResponse.BodyHandlers.ofString());

		String id1 = response1.headers().firstValue(REQUEST_ID_HEADER).orElseThrow();
		String id2 = response2.headers().firstValue(REQUEST_ID_HEADER).orElseThrow();

		assertThat(id1).isNotEqualTo(id2);
	}

	@Test
	@DisplayName("Request ID header is present on /api endpoint")
	void requestIdHeaderOnApiEndpoint() throws Exception {
		HttpRequest request = HttpRequest.newBuilder()
			.uri(java.net.URI.create(baseUrl() + "/api/tasks"))
			.GET()
			.build();

		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

		assertThat(response.headers().firstValue(REQUEST_ID_HEADER))
			.isPresent()
			.get()
			.asString()
			.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
	}
}
