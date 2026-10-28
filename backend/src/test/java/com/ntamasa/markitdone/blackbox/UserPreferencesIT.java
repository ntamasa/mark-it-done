package com.ntamasa.markitdone.blackbox;

import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;

import static com.ntamasa.markitdone.blackbox.ApiClient.assertProblem;
import static com.ntamasa.markitdone.blackbox.ApiClient.json;
import static com.ntamasa.markitdone.blackbox.TestAccounts.PASSWORD;
import static com.ntamasa.markitdone.blackbox.TestAccounts.Session;
import static com.ntamasa.markitdone.blackbox.TestAccounts.assertNoSecretsInBody;
import static com.ntamasa.markitdone.blackbox.TestAccounts.register;
import static com.ntamasa.markitdone.blackbox.TestAccounts.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Black-box tests for PATCH /api/users/me (MAR-63): updating the time zone and the theme preference.
 * Every test registers its own user. The current preferences are read back through GET /api/auth/me.
 */
@DisplayName("PATCH /api/users/me")
class UserPreferencesIT extends AbstractIntegrationTest {

	private static final String PREFERENCES_PATH = "/api/users/me";
	private static final String ME_PATH = "/api/auth/me";
	private static final String NEW_YORK = "America/New_York";

	private ApiClient api() {
		return new ApiClient(baseUrl());
	}

	private Session registerUser() throws Exception {
		return register(baseUrl(), uniqueEmail());
	}

	/** GET /api/auth/me for the session, asserting 200. */
	private JsonNode me(Session session) throws Exception {
		HttpResponse<String> response = api().get(ME_PATH, session.access(), session.refresh());
		assertThat(response.statusCode()).as("GET /api/auth/me status").isEqualTo(200);
		return json(response);
	}

	/** PATCH /api/users/me with the session cookies and a fresh CSRF token. */
	private HttpResponse<String> patch(Session session, String body) throws Exception {
		return api().patchJsonWithCsrf(PREFERENCES_PATH, body, session.access(), session.refresh());
	}

	// ---------------------------------------------------------------- theme preference

	@Test
	@DisplayName("PATCH with themePreference DARK returns 200 with DARK, and /me shows DARK afterwards")
	void updatesThemePreferenceToDark() throws Exception {
		Session session = registerUser();

		HttpResponse<String> response = patch(session, "{\"themePreference\":\"DARK\"}");

		assertThat(response.statusCode()).as("PATCH status, body: " + response.body()).isEqualTo(200);
		assertThat(json(response).path("themePreference").asString()).isEqualTo("DARK");
		assertThat(me(session).path("themePreference").asString()).isEqualTo("DARK");
	}

	@ParameterizedTest(name = "themePreference {0} is accepted")
	@ValueSource(strings = {"SYSTEM", "LIGHT", "DARK"})
	@DisplayName("PATCH accepts each of the theme preferences SYSTEM, LIGHT and DARK")
	void acceptsEachThemePreference(String theme) throws Exception {
		Session session = registerUser();

		HttpResponse<String> response = patch(session, "{\"themePreference\":\"" + theme + "\"}");

		assertThat(response.statusCode()).as("PATCH status for " + theme + ", body: " + response.body())
				.isEqualTo(200);
		assertThat(json(response).path("themePreference").asString()).isEqualTo(theme);
		assertThat(me(session).path("themePreference").asString()).isEqualTo(theme);
	}

	@Test
	@DisplayName("PATCH with an unknown themePreference PURPLE returns 400 and /me is unchanged")
	void rejectsUnknownThemePreference() throws Exception {
		Session session = registerUser();
		JsonNode before = me(session);

		HttpResponse<String> response = patch(session, "{\"themePreference\":\"PURPLE\"}");

		assertThat(response.statusCode()).as("PATCH status, body: " + response.body()).isEqualTo(400);
		JsonNode after = me(session);
		assertThat(after.path("themePreference").asString())
				.isEqualTo(before.path("themePreference").asString());
	}

	// ---------------------------------------------------------------- time zone

	@Test
	@DisplayName("PATCH with timezone America/New_York returns 200 and /me shows it, theme unchanged")
	void updatesTimezoneAndKeepsThemePreference() throws Exception {
		Session session = registerUser();
		JsonNode before = me(session);

		HttpResponse<String> response = patch(session, "{\"timezone\":\"" + NEW_YORK + "\"}");

		assertThat(response.statusCode()).as("PATCH status, body: " + response.body()).isEqualTo(200);
		assertThat(json(response).path("timezone").asString()).isEqualTo(NEW_YORK);
		JsonNode after = me(session);
		assertThat(after.path("timezone").asString()).isEqualTo(NEW_YORK);
		assertThat(after.path("themePreference").asString())
				.isEqualTo(before.path("themePreference").asString());
	}

	@Test
	@DisplayName("PATCH with an unknown timezone Mars/Base returns 400 VALIDATION_FAILED and /me is unchanged")
	void rejectsUnknownTimezone() throws Exception {
		Session session = registerUser();
		JsonNode before = me(session);

		HttpResponse<String> response = patch(session, "{\"timezone\":\"Mars/Base\"}");

		assertProblem(response, 400, "VALIDATION_FAILED");
		assertThat(response.body()).contains("Unknown timezone");
		JsonNode after = me(session);
		assertThat(after.path("timezone").asString()).isEqualTo(before.path("timezone").asString());
		assertThat(after.path("themePreference").asString())
				.isEqualTo(before.path("themePreference").asString());
	}

	// ---------------------------------------------------------------- absent and null fields

	@Test
	@DisplayName("PATCH with an empty body returns 200 and changes nothing")
	void emptyBodyIsNoOp() throws Exception {
		Session session = registerUser();
		JsonNode before = me(session);

		HttpResponse<String> response = patch(session, "{}");

		assertThat(response.statusCode()).as("PATCH status, body: " + response.body()).isEqualTo(200);
		JsonNode after = me(session);
		assertThat(after.path("timezone").asString()).isEqualTo(before.path("timezone").asString());
		assertThat(after.path("themePreference").asString())
				.isEqualTo(before.path("themePreference").asString());
	}

	@Test
	@DisplayName("PATCH with timezone null returns 200 and leaves the time zone unchanged")
	void nullTimezoneMeansUnchanged() throws Exception {
		Session session = registerUser();
		JsonNode before = me(session);

		HttpResponse<String> response = patch(session, "{\"timezone\":null}");

		assertThat(response.statusCode()).as("PATCH status, body: " + response.body()).isEqualTo(200);
		JsonNode after = me(session);
		assertThat(after.path("timezone").asString()).isEqualTo(before.path("timezone").asString());
		assertThat(after.path("themePreference").asString())
				.isEqualTo(before.path("themePreference").asString());
	}

	// ---------------------------------------------------------------- authentication and CSRF

	@Test
	@DisplayName("PATCH without a session returns 401")
	void unauthenticatedPatchReturns401() throws Exception {
		HttpResponse<String> response = api().patchJsonWithCsrf(PREFERENCES_PATH, "{\"themePreference\":\"DARK\"}");

		assertThat(response.statusCode()).as("PATCH status without session").isEqualTo(401);
	}

	@Test
	@DisplayName("PATCH without a CSRF token returns 403 and changes nothing")
	void patchWithoutCsrfReturns403() throws Exception {
		Session session = registerUser();
		JsonNode before = me(session);

		HttpResponse<String> response = api().patchJson(PREFERENCES_PATH, "{\"themePreference\":\"DARK\"}",
				session.access(), session.refresh());

		assertThat(response.statusCode()).as("PATCH status without CSRF token").isEqualTo(403);
		JsonNode after = me(session);
		assertThat(after.path("themePreference").asString())
				.isEqualTo(before.path("themePreference").asString());
	}

	// ---------------------------------------------------------------- response content

	@Test
	@DisplayName("The PATCH response never contains a password or password hash")
	void responseNeverContainsPasswordData() throws Exception {
		Session session = registerUser();

		HttpResponse<String> response = patch(session, "{\"themePreference\":\"DARK\",\"timezone\":\"Europe/Paris\"}");

		assertThat(response.statusCode()).as("PATCH status, body: " + response.body()).isEqualTo(200);
		assertNoSecretsInBody(response);
		assertThat(response.body()).doesNotContain(PASSWORD);
		assertThat(json(response).propertyNames())
				.as("top-level body fields")
				.noneMatch(name -> name.toLowerCase().contains("password"));
	}
}
