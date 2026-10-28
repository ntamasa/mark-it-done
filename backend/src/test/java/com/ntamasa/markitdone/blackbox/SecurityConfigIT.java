package com.ntamasa.markitdone.blackbox;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Black-box tests for the API security configuration (MAR-56).
 * Only HTTP requests, headers, cookies, JSON bodies and status codes are used.
 */
@DisplayName("Security configuration")
class SecurityConfigIT extends AbstractIntegrationTest {

	private static final ObjectMapper JSON = new ObjectMapper();

	private static final String ACCESS_COOKIE = "md_access";
	private static final String CSRF_COOKIE = "XSRF-TOKEN";
	private static final String CSRF_HEADER = "X-XSRF-TOKEN";
	private static final String GARBAGE_TOKEN = "this.is.not-a-valid-jwt";
	private static final String LOGIN_BODY = "{\"email\":\"nobody-mar56@example.test\",\"password\":\"irrelevant-Passw0rd!\"}";

	// ---------------------------------------------------------------- public endpoints

	@Test
	@DisplayName("GET /actuator/health answers 200 without any cookie")
	void actuatorHealthIsPublic() throws Exception {
		HttpResponse<String> response = send("GET", "/actuator/health", null);

		assertThat(response.statusCode()).isEqualTo(200);
	}

	@Test
	@DisplayName("GET /actuator/info is public (not 401 or 403) without any cookie")
	void actuatorInfoIsPublic() throws Exception {
		HttpResponse<String> response = send("GET", "/actuator/info", null);

		assertThat(response.statusCode()).isNotIn(401, 403);
	}

	@Test
	@DisplayName("GET /api/auth/csrf is public (not 401 or 403) without any cookie")
	void csrfEndpointIsPublic() throws Exception {
		HttpResponse<String> response = send("GET", "/api/auth/csrf", null);

		assertThat(response.statusCode()).isNotIn(401, 403);
	}

	// ---------------------------------------------------------------- authentication (401)

	@ParameterizedTest
	@ValueSource(strings = {"/api/users/me", "/api/todos"})
	@DisplayName("API request without md_access cookie is rejected with 401 problem+json")
	void apiWithoutCookieReturns401Problem(String path) throws Exception {
		HttpResponse<String> response = send("GET", path, null);

		assertProblem(response, 401, "UNAUTHORIZED", "Authentication required");
	}

	@Test
	@DisplayName("API request with a garbage md_access cookie is rejected with 401 problem+json")
	void apiWithGarbageCookieReturns401Problem() throws Exception {
		HttpResponse<String> response = send("GET", "/api/users/me", null,
				"Cookie", ACCESS_COOKIE + "=" + GARBAGE_TOKEN);

		assertProblem(response, 401, "UNAUTHORIZED", "Authentication required");
	}

	@Test
	@DisplayName("401 response does not use Basic authentication challenge")
	void unauthorizedResponseDoesNotOfferBasic() throws Exception {
		HttpResponse<String> response = send("GET", "/api/users/me", null);

		assertThat(response.statusCode()).isEqualTo(401);
		String challenge = response.headers().firstValue("WWW-Authenticate").orElse("");
		assertThat(challenge.toLowerCase()).doesNotContain("basic");
	}

	@Test
	@DisplayName("401 response creates no HTTP session cookie")
	void unauthorizedResponseCreatesNoSession() throws Exception {
		HttpResponse<String> response = send("GET", "/api/users/me", null);

		assertThat(response.statusCode()).isEqualTo(401);
		assertThat(setCookieHeaders(response))
				.noneMatch(cookie -> cookie.startsWith("JSESSIONID="));
	}

	// ---------------------------------------------------------------- CSRF

	@Test
	@DisplayName("GET /api/auth/csrf sets an XSRF-TOKEN cookie that is not HttpOnly")
	void csrfEndpointSetsReadableXsrfCookie() throws Exception {
		HttpResponse<String> response = send("GET", "/api/auth/csrf", null);

		Optional<String> xsrf = findSetCookie(response, CSRF_COOKIE);
		assertThat(xsrf).as("Set-Cookie for XSRF-TOKEN").isPresent();
		assertThat(xsrf.get().substring(CSRF_COOKIE.length() + 1).split(";", 2)[0])
				.as("XSRF-TOKEN cookie value")
				.isNotBlank();
		assertThat(xsrf.get().toLowerCase()).doesNotContain("httponly");
	}

	@Test
	@DisplayName("POST /api/auth/login without X-XSRF-TOKEN is rejected with 403 problem+json")
	void loginWithoutCsrfHeaderReturns403Problem() throws Exception {
		HttpResponse<String> response = send("POST", "/api/auth/login", LOGIN_BODY,
				"Content-Type", "application/json");

		assertProblem(response, 403, "FORBIDDEN", "Access denied");
	}

	@Test
	@DisplayName("POST /api/auth/login with XSRF cookie but no X-XSRF-TOKEN header is rejected with 403")
	void loginWithCookieButNoHeaderReturns403() throws Exception {
		String token = fetchCsrfToken();

		HttpResponse<String> response = send("POST", "/api/auth/login", LOGIN_BODY,
				"Content-Type", "application/json",
				"Cookie", CSRF_COOKIE + "=" + token);

		assertProblem(response, 403, "FORBIDDEN", "Access denied");
	}

	@Test
	@DisplayName("POST /api/auth/login with an X-XSRF-TOKEN that does not match the cookie is rejected with 403")
	void loginWithMismatchedCsrfHeaderReturns403() throws Exception {
		String token = fetchCsrfToken();

		HttpResponse<String> response = send("POST", "/api/auth/login", LOGIN_BODY,
				"Content-Type", "application/json",
				"Cookie", CSRF_COOKIE + "=" + token,
				CSRF_HEADER, token + "-tampered");

		assertProblem(response, 403, "FORBIDDEN", "Access denied");
	}

	@Test
	@DisplayName("Garbage md_access cookie on POST /api/auth/login is ignored (not 401 or 403)")
	void loginIgnoresGarbageAccessCookie() throws Exception {
		String token = fetchCsrfToken();

		HttpResponse<String> response = send("POST", "/api/auth/login", LOGIN_BODY,
				"Content-Type", "application/json",
				"Cookie", ACCESS_COOKIE + "=" + GARBAGE_TOKEN + "; " + CSRF_COOKIE + "=" + token,
				CSRF_HEADER, token);

		assertThat(response.statusCode())
				.as("public auth endpoint must ignore the access cookie")
				.isNotIn(401, 403);
	}

	// ---------------------------------------------------------------- helpers

	private String fetchCsrfToken() throws Exception {
		HttpResponse<String> response = send("GET", "/api/auth/csrf", null);
		Optional<String> xsrf = findSetCookie(response, CSRF_COOKIE);
		assertThat(xsrf).as("Set-Cookie for XSRF-TOKEN from GET /api/auth/csrf").isPresent();
		return xsrf.get().substring(CSRF_COOKIE.length() + 1).split(";", 2)[0];
	}

	private void assertProblem(HttpResponse<String> response, int status, String code, String title)
			throws Exception {
		assertThat(response.statusCode()).isEqualTo(status);
		assertThat(response.headers().firstValue("Content-Type").orElse(""))
				.startsWith("application/problem+json");
		assertThat(response.body()).contains(code).contains(title);

		JsonNode body = JSON.readTree(response.body());
		assertThat(body.path("status").asInt()).isEqualTo(status);
	}

	private HttpResponse<String> send(String method, String path, String body, String... headerPairs)
			throws Exception {
		HttpRequest.BodyPublisher publisher = body == null
				? HttpRequest.BodyPublishers.noBody()
				: HttpRequest.BodyPublishers.ofString(body);

		HttpRequest.Builder builder = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl() + path))
				.method(method, publisher);

		for (int i = 0; i < headerPairs.length; i += 2) {
			builder.header(headerPairs[i], headerPairs[i + 1]);
		}

		return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
	}

	private static List<String> setCookieHeaders(HttpResponse<String> response) {
		return response.headers().allValues("Set-Cookie");
	}

	private static Optional<String> findSetCookie(HttpResponse<String> response, String name) {
		return setCookieHeaders(response).stream()
				.filter(cookie -> cookie.startsWith(name + "="))
				.findFirst();
	}
}
