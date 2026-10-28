package com.ntamasa.markitdone.blackbox;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static com.ntamasa.markitdone.blackbox.ApiClient.ACCESS_COOKIE;
import static com.ntamasa.markitdone.blackbox.ApiClient.REFRESH_COOKIE;
import static com.ntamasa.markitdone.blackbox.ApiClient.SetCookie;
import static com.ntamasa.markitdone.blackbox.ApiClient.cookie;
import static com.ntamasa.markitdone.blackbox.ApiClient.json;
import static com.ntamasa.markitdone.blackbox.ApiClient.setCookie;
import static com.ntamasa.markitdone.blackbox.ApiClient.setCookies;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shared test-data helper for every test that needs a user (MAR-51 onwards).
 * It registers users and logs them in over HTTP only and hands back the session cookies as plain values.
 * Each call uses a fresh e-mail address, so tests never share accounts.
 * It also holds the reusable assertions on Set-Cookie headers and on response bodies.
 */
final class TestAccounts {

	/** Password that satisfies the strong-password policy. */
	static final String PASSWORD = "Summer-2026!x";
	static final String DEFAULT_TIMEZONE = "Europe/Budapest";
	/** Max-Age attribute the server sends on the refresh cookie when "remember me" is on (14 days). */
	static final String REMEMBER_ME_MAX_AGE = "max-age=1209600";

	/** Fields that change from request to request and are ignored when two error bodies are compared. */
	private static final Set<String> REQUEST_SPECIFIC_FIELDS = Set.of("instance", "requestId", "timestamp", "traceId");

	private static final ObjectMapper JSON = new ObjectMapper();

	private TestAccounts() {
	}

	/** The outcome of a successful register or login: the response, the user body and the session cookies. */
	record Session(HttpResponse<String> response, JsonNode user, ApiClient.Cookie access, ApiClient.Cookie refresh) {
	}

	/** An e-mail address that no other test uses. */
	static String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@example.test";
	}

	/** Builds a JSON object from alternating key/value pairs; pairs with a null value are left out. */
	static String jsonBody(Object... keyValues) {
		Map<String, Object> fields = new java.util.LinkedHashMap<>();
		for (int i = 0; i < keyValues.length; i += 2) {
			if (keyValues[i + 1] != null) {
				fields.put((String) keyValues[i], keyValues[i + 1]);
			}
		}
		return JSON.writeValueAsString(fields);
	}

	static String registerBody(String displayName, String email, String password, String timezone) {
		return jsonBody("displayName", displayName, "email", email, "password", password, "timezone", timezone);
	}

	static String loginBody(String email, String password, boolean rememberMe, String timezone) {
		return jsonBody("email", email, "password", password, "rememberMe", rememberMe, "timezone", timezone);
	}

	/** Registers a user with a default display name, the shared password and the default time zone. Asserts 201. */
	static Session register(String baseUrl, String email) throws Exception {
		return registerExpectingCreated(baseUrl, registerBody("Test User", email, PASSWORD, DEFAULT_TIMEZONE));
	}

	/** Sends a register request with a fresh CSRF token and asserts 201. */
	static Session registerExpectingCreated(String baseUrl, String body) throws Exception {
		HttpResponse<String> response = registerRaw(baseUrl, body);
		assertThat(response.statusCode()).as("POST /api/auth/register status, body: " + response.body()).isEqualTo(201);
		return session(response);
	}

	/** Sends a register request with a fresh CSRF token and returns the raw response. */
	static HttpResponse<String> registerRaw(String baseUrl, String body) throws Exception {
		return new ApiClient(baseUrl).postJsonWithCsrf("/api/auth/register", body);
	}

	/** Logs in from a fresh client (no cookies) and asserts 200. */
	static Session login(String baseUrl, String email, String password, boolean rememberMe, String timezone)
			throws Exception {
		HttpResponse<String> response = loginRaw(baseUrl, email, password, rememberMe, timezone);
		assertThat(response.statusCode()).as("POST /api/auth/login status, body: " + response.body()).isEqualTo(200);
		return session(response);
	}

	/** Logs in with the shared password, no remember-me and the default time zone. Asserts 200. */
	static Session login(String baseUrl, String email) throws Exception {
		return login(baseUrl, email, PASSWORD, false, DEFAULT_TIMEZONE);
	}

	/** Sends a login request from a fresh client (no cookies) and returns the raw response. */
	static HttpResponse<String> loginRaw(String baseUrl, String email, String password, boolean rememberMe,
			String timezone) throws Exception {
		return new ApiClient(baseUrl).postJsonWithCsrf("/api/auth/login",
				loginBody(email, password, rememberMe, timezone));
	}

	/** Reads the user body and the md_access / md_refresh values from a response that set them. */
	static Session session(HttpResponse<String> response) {
		SetCookie access = setCookie(response, ACCESS_COOKIE)
				.orElseThrow(() -> new AssertionError("response did not set " + ACCESS_COOKIE));
		SetCookie refresh = setCookie(response, REFRESH_COOKIE)
				.orElseThrow(() -> new AssertionError("response did not set " + REFRESH_COOKIE));
		return new Session(response, json(response), cookie(ACCESS_COOKIE, access.value()),
				cookie(REFRESH_COOKIE, refresh.value()));
	}

	// ---------------------------------------------------------------- assertions

	/** Both session cookies are set with a value and HttpOnly. */
	static void assertSessionCookies(HttpResponse<String> response) {
		for (String name : List.of(ACCESS_COOKIE, REFRESH_COOKIE)) {
			SetCookie cookie = setCookie(response, name)
					.orElseThrow(() -> new AssertionError("Set-Cookie for " + name + " is missing"));
			assertThat(cookie.value()).as(name + " value").isNotBlank();
			assertThat(cookie.hasAttribute("httponly")).as(name + " is HttpOnly").isTrue();
		}
	}

	/** The refresh cookie carries no Max-Age, which is how a session without "remember me" looks. */
	static void assertRefreshCookieWithoutMaxAge(HttpResponse<String> response) {
		SetCookie refresh = setCookie(response, REFRESH_COOKIE)
				.orElseThrow(() -> new AssertionError("Set-Cookie for " + REFRESH_COOKIE + " is missing"));
		assertThat(maxAge(refresh)).as("md_refresh Max-Age without remember-me").isEmpty();
	}

	/** The refresh cookie has exactly the remember-me Max-Age. */
	static void assertRefreshCookieRemembered(HttpResponse<String> response) {
		SetCookie refresh = setCookie(response, REFRESH_COOKIE)
				.orElseThrow(() -> new AssertionError("Set-Cookie for " + REFRESH_COOKIE + " is missing"));
		assertThat(refresh.hasAttribute(REMEMBER_ME_MAX_AGE))
				.as("md_refresh carries " + REMEMBER_ME_MAX_AGE + " in " + refresh.attributes())
				.isTrue();
	}

	/** The server clears the cookie by sending it again with Max-Age=0. */
	static void assertCleared(HttpResponse<String> response, String name) {
		assertThat(setCookies(response))
				.as("Set-Cookie headers should clear " + name)
				.anySatisfy(cookie -> {
					assertThat(cookie.name()).isEqualTo(name);
					assertThat(cookie.isCleared()).as(name + " is cleared with Max-Age=0").isTrue();
				});
	}

	/** A response body that carries no password, no password hash and no token, and echoes no cookie value. */
	static void assertNoSecretsInBody(HttpResponse<String> response) {
		String body = response.body();
		String lower = body.toLowerCase(Locale.ROOT);
		assertThat(lower).as("body must not mention a password").doesNotContain("password");
		assertThat(lower).as("body must not mention a token").doesNotContain("token");
		assertThat(json(response).propertyNames())
				.as("top-level body fields")
				.noneMatch(name -> name.toLowerCase(Locale.ROOT).contains("token"));
		for (SetCookie cookie : setCookies(response)) {
			if (!cookie.value().isBlank()) {
				assertThat(body).as("body must not echo the " + cookie.name() + " value").doesNotContain(cookie.value());
			}
		}
	}

	/**
	 * The body of an error response with the request-specific fields removed, so that two errors
	 * caused by different requests can be compared for equality.
	 */
	static Map<String, String> canonicalErrorBody(HttpResponse<String> response) {
		JsonNode node = json(response);
		Map<String, String> fields = new TreeMap<>();
		for (String name : node.propertyNames()) {
			if (!REQUEST_SPECIFIC_FIELDS.contains(name)) {
				fields.put(name, node.path(name).toString());
			}
		}
		return fields;
	}

	/** Every error field name listed in errors[], in order. */
	static List<String> errorFields(HttpResponse<String> response) {
		JsonNode errors = json(response).path("errors");
		assertThat(errors.isArray()).as("errors[] in " + response.body()).isTrue();
		List<String> fields = new ArrayList<>();
		for (JsonNode error : errors) {
			fields.add(error.path("field").asString());
		}
		return fields;
	}

	private static Optional<String> maxAge(SetCookie cookie) {
		return cookie.attributes().stream().filter(attribute -> attribute.startsWith("max-age=")).findFirst();
	}
}
