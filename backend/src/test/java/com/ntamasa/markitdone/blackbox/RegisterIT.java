package com.ntamasa.markitdone.blackbox;

import java.net.http.HttpResponse;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.ntamasa.markitdone.blackbox.ApiClient.assertProblem;
import static com.ntamasa.markitdone.blackbox.ApiClient.json;
import static com.ntamasa.markitdone.blackbox.TestAccounts.DEFAULT_TIMEZONE;
import static com.ntamasa.markitdone.blackbox.TestAccounts.PASSWORD;
import static com.ntamasa.markitdone.blackbox.TestAccounts.assertNoSecretsInBody;
import static com.ntamasa.markitdone.blackbox.TestAccounts.assertRefreshCookieWithoutMaxAge;
import static com.ntamasa.markitdone.blackbox.TestAccounts.assertSessionCookies;
import static com.ntamasa.markitdone.blackbox.TestAccounts.errorFields;
import static com.ntamasa.markitdone.blackbox.TestAccounts.jsonBody;
import static com.ntamasa.markitdone.blackbox.TestAccounts.register;
import static com.ntamasa.markitdone.blackbox.TestAccounts.registerBody;
import static com.ntamasa.markitdone.blackbox.TestAccounts.registerRaw;
import static com.ntamasa.markitdone.blackbox.TestAccounts.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Black-box tests for POST /api/auth/register (MAR-51).
 * Each test registers its own user with a unique e-mail address.
 */
@DisplayName("POST /api/auth/register")
class RegisterIT extends AbstractIntegrationTest {

	private static final String DUPLICATE_MESSAGE = "An account with this email already exists.";

	private ApiClient api() {
		return new ApiClient(baseUrl());
	}

	/** An e-mail address that is unique per call, with the given letter case in its domain part. */
	private static String uniqueMixedCaseEmail() {
		return "Ada.Lovelace-" + UUID.randomUUID() + "@Example.TEST";
	}

	// ---------------------------------------------------------------- happy path

	@Test
	@DisplayName("Valid registration returns 201 with the trimmed display name and lower-cased e-mail")
	void happyPathReturns201WithNormalisedUser() throws Exception {
		String email = uniqueMixedCaseEmail();
		HttpResponse<String> response = registerRaw(baseUrl(),
				registerBody("  Ada Lovelace  ", email, PASSWORD, DEFAULT_TIMEZONE));

		assertThat(response.statusCode()).as("status, body: " + response.body()).isEqualTo(201);
		assertThat(json(response).path("displayName").asString()).isEqualTo("Ada Lovelace");
		assertThat(json(response).path("email").asString()).isEqualTo(email.toLowerCase(Locale.ROOT));
		assertThat(json(response).path("timezone").asString()).isEqualTo(DEFAULT_TIMEZONE);
	}

	@Test
	@DisplayName("Valid registration sets md_access and md_refresh as HttpOnly cookies")
	void happyPathSetsHttpOnlySessionCookies() throws Exception {
		HttpResponse<String> response = registerRaw(baseUrl(),
				registerBody("Grace Hopper", uniqueEmail(), PASSWORD, DEFAULT_TIMEZONE));

		assertThat(response.statusCode()).isEqualTo(201);
		assertSessionCookies(response);
	}

	@Test
	@DisplayName("Registration without remember-me sends the refresh cookie without Max-Age")
	void happyPathRefreshCookieHasNoMaxAge() throws Exception {
		HttpResponse<String> response = registerRaw(baseUrl(),
				registerBody("Grace Hopper", uniqueEmail(), PASSWORD, DEFAULT_TIMEZONE));

		assertThat(response.statusCode()).isEqualTo(201);
		assertRefreshCookieWithoutMaxAge(response);
	}

	@Test
	@DisplayName("Registration response body never contains a password, a password hash or a token")
	void happyPathBodyHasNoSecrets() throws Exception {
		HttpResponse<String> response = registerRaw(baseUrl(),
				registerBody("Grace Hopper", uniqueEmail(), PASSWORD, DEFAULT_TIMEZONE));

		assertThat(response.statusCode()).isEqualTo(201);
		assertNoSecretsInBody(response);
		assertThat(response.body()).doesNotContain("passwordHash");
	}

	@Test
	@DisplayName("The cookies from registration authenticate GET /api/auth/me for the same user")
	void registeredCookiesReturnTheSameUserFromMe() throws Exception {
		String email = uniqueEmail();
		TestAccounts.Session created = register(baseUrl(), email);

		HttpResponse<String> me = api().get("/api/auth/me", created.access(), created.refresh());

		assertThat(me.statusCode()).as("GET /api/auth/me status").isEqualTo(200);
		assertThat(json(me).path("email").asString()).isEqualTo(email);
		assertThat(json(me).path("displayName").asString()).isEqualTo("Test User");
		assertThat(json(me).path("timezone").asString()).isEqualTo(DEFAULT_TIMEZONE);
	}

	// ---------------------------------------------------------------- duplicates

	@Test
	@DisplayName("Registering an e-mail that exists in another letter case returns 409 EMAIL_TAKEN")
	void duplicateEmailInDifferentCaseReturns409() throws Exception {
		String email = uniqueEmail();
		register(baseUrl(), email);

		HttpResponse<String> response = registerRaw(baseUrl(),
				registerBody("Second Person", email.toUpperCase(Locale.ROOT), PASSWORD, DEFAULT_TIMEZONE));

		assertProblem(response, 409, "EMAIL_TAKEN");
		assertThat(response.body()).contains(DUPLICATE_MESSAGE);
	}

	// ---------------------------------------------------------------- validation

	@Test
	@DisplayName("A weak password returns 400 VALIDATION_FAILED with errors[0].field = password")
	void weakPasswordReturns400WithPasswordField() throws Exception {
		HttpResponse<String> response = registerRaw(baseUrl(),
				registerBody("Weak Password", uniqueEmail(), "weakpass", DEFAULT_TIMEZONE));

		assertProblem(response, 400, "VALIDATION_FAILED");
		assertThat(errorFields(response)).isNotEmpty();
		assertThat(errorFields(response).get(0)).isEqualTo("password");
	}

	@Test
	@DisplayName("A missing display name returns 400 VALIDATION_FAILED")
	void missingDisplayNameReturns400() throws Exception {
		HttpResponse<String> response = registerRaw(baseUrl(),
				registerBody(null, uniqueEmail(), PASSWORD, DEFAULT_TIMEZONE));

		assertProblem(response, 400, "VALIDATION_FAILED");
	}

	@Test
	@DisplayName("A display name longer than 80 characters returns 400 VALIDATION_FAILED")
	void tooLongDisplayNameReturns400() throws Exception {
		String displayName = "a".repeat(81);
		HttpResponse<String> response = registerRaw(baseUrl(),
				registerBody(displayName, uniqueEmail(), PASSWORD, DEFAULT_TIMEZONE));

		assertProblem(response, 400, "VALIDATION_FAILED");
	}

	@Test
	@DisplayName("An invalid e-mail address returns 400 VALIDATION_FAILED")
	void invalidEmailReturns400() throws Exception {
		HttpResponse<String> response = registerRaw(baseUrl(),
				registerBody("No Address", "not-an-email", PASSWORD, DEFAULT_TIMEZONE));

		assertProblem(response, 400, "VALIDATION_FAILED");
	}

	@Test
	@DisplayName("A missing password returns 400 VALIDATION_FAILED")
	void missingPasswordReturns400() throws Exception {
		HttpResponse<String> response = registerRaw(baseUrl(),
				jsonBody("displayName", "No Password", "email", uniqueEmail(), "timezone", DEFAULT_TIMEZONE));

		assertProblem(response, 400, "VALIDATION_FAILED");
	}

	// ---------------------------------------------------------------- time zone fallback

	@Test
	@DisplayName("An unknown time zone does not fail registration and the user gets UTC")
	void unknownTimezoneFallsBackToUtc() throws Exception {
		HttpResponse<String> response = registerRaw(baseUrl(),
				registerBody("Mars Visitor", uniqueEmail(), PASSWORD, "Mars/Base"));

		assertThat(response.statusCode()).as("status, body: " + response.body()).isEqualTo(201);
		assertThat(json(response).path("timezone").asString()).isEqualTo("UTC");
	}

	// ---------------------------------------------------------------- CSRF

	@Test
	@DisplayName("Registration without a CSRF token returns 403")
	void registerWithoutCsrfReturns403() throws Exception {
		HttpResponse<String> response = api().postJson("/api/auth/register",
				registerBody("No Csrf", uniqueEmail(), PASSWORD, DEFAULT_TIMEZONE));

		assertThat(response.statusCode()).as("status without CSRF token").isEqualTo(403);
	}
}
