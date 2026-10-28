package com.ntamasa.markitdone.blackbox;

import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.ntamasa.markitdone.blackbox.ApiClient.ACCESS_COOKIE;
import static com.ntamasa.markitdone.blackbox.ApiClient.REFRESH_COOKIE;
import static com.ntamasa.markitdone.blackbox.ApiClient.assertProblem;
import static com.ntamasa.markitdone.blackbox.ApiClient.cookie;
import static com.ntamasa.markitdone.blackbox.ApiClient.json;
import static com.ntamasa.markitdone.blackbox.ApiClient.setCookie;
import static com.ntamasa.markitdone.blackbox.ApiClient.setCookies;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Black-box tests for the auth endpoints (MAR-57) that can be exercised without an existing user:
 * CSRF bootstrap, login rejection, refresh and logout without a valid session, and /me without a session.
 * Login success, rotation, reuse detection and audit checks need a registered user and are covered after MAR-51.
 */
@DisplayName("Auth endpoints")
class AuthEndpointsIT extends AbstractIntegrationTest {

	private static final String INVALID_CREDENTIALS_MESSAGE = "Email or password is incorrect.";
	private static final String SESSION_EXPIRED_MESSAGE = "Session expired. Please log in again.";
	private static final String GARBAGE_TOKEN = "this.is.not-a-valid-jwt";

	private ApiClient api() {
		return new ApiClient(baseUrl());
	}

	/** Email that no test ever registers, unique per call. */
	private static String unknownEmail() {
		return "unknown-" + UUID.randomUUID() + "@example.test";
	}

	private static String loginBody(String email) {
		return "{\"email\":\"" + email + "\","
				+ "\"password\":\"Summer-2026!x\","
				+ "\"rememberMe\":false,"
				+ "\"timezone\":\"Europe/Budapest\"}";
	}

	// ---------------------------------------------------------------- GET /api/auth/csrf

	@Test
	@DisplayName("GET /api/auth/csrf returns 204 and sets a non-empty XSRF-TOKEN cookie")
	void csrfReturns204AndSetsXsrfCookie() throws Exception {
		HttpResponse<String> response = api().get("/api/auth/csrf");

		assertThat(response.statusCode()).isEqualTo(204);
		assertThat(setCookie(response, "XSRF-TOKEN"))
				.as("Set-Cookie for XSRF-TOKEN")
				.isPresent()
				.hasValueSatisfying(cookie -> assertThat(cookie.value()).isNotBlank());
	}

	// ---------------------------------------------------------------- POST /api/auth/login

	@Test
	@DisplayName("POST /api/auth/login with an unknown email returns 401 INVALID_CREDENTIALS")
	void loginWithUnknownEmailReturns401InvalidCredentials() throws Exception {
		HttpResponse<String> response = api().postJsonWithCsrf("/api/auth/login", loginBody(unknownEmail()));

		assertProblem(response, 401, "INVALID_CREDENTIALS");
		assertThat(response.body()).contains(INVALID_CREDENTIALS_MESSAGE);
	}

	@Test
	@DisplayName("Failed login with an unknown email returns no token and sets no session cookies")
	void failedLoginSetsNoSessionCookiesAndLeaksNoToken() throws Exception {
		HttpResponse<String> response = api().postJsonWithCsrf("/api/auth/login", loginBody(unknownEmail()));

		assertThat(response.statusCode()).isEqualTo(401);
		assertThat(setCookie(response, ACCESS_COOKIE)).as("md_access Set-Cookie").isEmpty();
		assertThat(setCookie(response, REFRESH_COOKIE)).as("md_refresh Set-Cookie").isEmpty();
		assertThat(json(response).propertyNames())
				.as("top-level body fields")
				.noneMatch(name -> name.toLowerCase().contains("token"));
		assertThat(response.body()).doesNotContain(GARBAGE_TOKEN).doesNotContain("eyJ");
	}

	@Test
	@DisplayName("POST /api/auth/login with a missing email returns 400 VALIDATION_FAILED")
	void loginWithMissingEmailReturns400ValidationFailed() throws Exception {
		String body = "{\"password\":\"Summer-2026!x\",\"rememberMe\":false,\"timezone\":\"Europe/Budapest\"}";

		HttpResponse<String> response = api().postJsonWithCsrf("/api/auth/login", body);

		assertProblem(response, 400, "VALIDATION_FAILED");
	}

	// ---------------------------------------------------------------- POST /api/auth/refresh

	@Test
	@DisplayName("POST /api/auth/refresh without a refresh cookie returns 401 and clears both cookies")
	void refreshWithoutCookieReturns401AndClearsCookies() throws Exception {
		HttpResponse<String> response = api().postWithCsrf("/api/auth/refresh");

		assertProblem(response, 401, "UNAUTHORIZED");
		assertThat(response.body()).contains(SESSION_EXPIRED_MESSAGE);
		assertClearedCookie(response, ACCESS_COOKIE);
		assertClearedCookie(response, REFRESH_COOKIE);
	}

	@Test
	@DisplayName("POST /api/auth/refresh with a garbage refresh cookie returns 401 and clears both cookies")
	void refreshWithGarbageCookieReturns401AndClearsCookies() throws Exception {
		HttpResponse<String> response = api().postWithCsrf("/api/auth/refresh",
				cookie(REFRESH_COOKIE, GARBAGE_TOKEN));

		assertProblem(response, 401, "UNAUTHORIZED");
		assertThat(response.body()).contains(SESSION_EXPIRED_MESSAGE);
		assertClearedCookie(response, ACCESS_COOKIE);
		assertClearedCookie(response, REFRESH_COOKIE);
	}

	// ---------------------------------------------------------------- POST /api/auth/logout

	@Test
	@DisplayName("POST /api/auth/logout without any cookie returns 204 and clears the cookies")
	void logoutWithoutCookieReturns204AndClearsCookies() throws Exception {
		HttpResponse<String> response = api().postWithCsrf("/api/auth/logout");

		assertThat(response.statusCode()).isEqualTo(204);
		assertClearedCookie(response, ACCESS_COOKIE);
		assertClearedCookie(response, REFRESH_COOKIE);
	}

	@Test
	@DisplayName("POST /api/auth/logout with an unknown refresh cookie returns 204 and clears the cookies")
	void logoutWithUnknownRefreshCookieReturns204AndClearsCookies() throws Exception {
		HttpResponse<String> response = api().postWithCsrf("/api/auth/logout",
				cookie(REFRESH_COOKIE, GARBAGE_TOKEN));

		assertThat(response.statusCode()).isEqualTo(204);
		assertClearedCookie(response, ACCESS_COOKIE);
		assertClearedCookie(response, REFRESH_COOKIE);
	}

	// ---------------------------------------------------------------- GET /api/auth/me

	@Test
	@DisplayName("GET /api/auth/me without a session returns 401 problem+json")
	void meWithoutSessionReturns401() throws Exception {
		HttpResponse<String> response = api().get("/api/auth/me");

		assertProblem(response, 401, "UNAUTHORIZED");
	}

	// ---------------------------------------------------------------- helpers

	/** The server clears a cookie by sending it again with Max-Age=0. */
	private static void assertClearedCookie(HttpResponse<String> response, String name) {
		assertThat(setCookies(response))
				.as("Set-Cookie headers should clear " + name)
				.anySatisfy(cookie -> {
					assertThat(cookie.name()).isEqualTo(name);
					assertThat(cookie.isCleared()).as(name + " is cleared with Max-Age=0").isTrue();
				});
	}
}
