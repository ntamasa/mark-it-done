package com.ntamasa.markitdone.blackbox;

import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.ntamasa.markitdone.blackbox.ApiClient.ACCESS_COOKIE;
import static com.ntamasa.markitdone.blackbox.ApiClient.REFRESH_COOKIE;
import static com.ntamasa.markitdone.blackbox.ApiClient.assertProblem;
import static com.ntamasa.markitdone.blackbox.ApiClient.cookie;
import static com.ntamasa.markitdone.blackbox.ApiClient.json;
import static com.ntamasa.markitdone.blackbox.ApiClient.setCookie;
import static com.ntamasa.markitdone.blackbox.TestAccounts.DEFAULT_TIMEZONE;
import static com.ntamasa.markitdone.blackbox.TestAccounts.PASSWORD;
import static com.ntamasa.markitdone.blackbox.TestAccounts.assertCleared;
import static com.ntamasa.markitdone.blackbox.TestAccounts.assertNoSecretsInBody;
import static com.ntamasa.markitdone.blackbox.TestAccounts.assertRefreshCookieRemembered;
import static com.ntamasa.markitdone.blackbox.TestAccounts.assertRefreshCookieWithoutMaxAge;
import static com.ntamasa.markitdone.blackbox.TestAccounts.assertSessionCookies;
import static com.ntamasa.markitdone.blackbox.TestAccounts.canonicalErrorBody;
import static com.ntamasa.markitdone.blackbox.TestAccounts.login;
import static com.ntamasa.markitdone.blackbox.TestAccounts.loginRaw;
import static com.ntamasa.markitdone.blackbox.TestAccounts.register;
import static com.ntamasa.markitdone.blackbox.TestAccounts.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Black-box tests for the login-dependent auth flows (MAR-57 checks that need a registered user).
 * Every test registers its own user through POST /api/auth/register and then drives the other endpoints over HTTP.
 * Login always uses a fresh client without cookies, so the session under test is the one the login issued.
 */
@DisplayName("Login, refresh, logout and me flows")
class LoginFlowIT extends AbstractIntegrationTest {

	private static final String INVALID_CREDENTIALS_MESSAGE = "Email or password is incorrect.";
	private static final String SESSION_EXPIRED_MESSAGE = "Session expired. Please log in again.";
	private static final String OTHER_TIMEZONE = "America/New_York";

	private ApiClient api() {
		return new ApiClient(baseUrl());
	}

	// ---------------------------------------------------------------- POST /api/auth/login

	@Test
	@DisplayName("Login with the right password returns 200, the user and both session cookies")
	void loginWithRightPasswordReturnsUserAndCookies() throws Exception {
		String email = uniqueEmail();
		register(baseUrl(), email);

		var response = loginRaw(baseUrl(), email, PASSWORD, false, DEFAULT_TIMEZONE);

		assertThat(response.statusCode()).as("status, body: " + response.body()).isEqualTo(200);
		assertThat(json(response).path("email").asString()).isEqualTo(email);
		assertThat(json(response).path("displayName").asString()).isEqualTo("Test User");
		assertSessionCookies(response);
		assertNoSecretsInBody(response);
	}

	@Test
	@DisplayName("Login with remember-me sets the refresh cookie Max-Age to 1209600 seconds")
	void loginWithRememberMeSetsRefreshMaxAge() throws Exception {
		String email = uniqueEmail();
		register(baseUrl(), email);

		var response = loginRaw(baseUrl(), email, PASSWORD, true, DEFAULT_TIMEZONE);

		assertThat(response.statusCode()).as("status, body: " + response.body()).isEqualTo(200);
		assertRefreshCookieRemembered(response);
	}

	@Test
	@DisplayName("Login without remember-me sends the refresh cookie without Max-Age")
	void loginWithoutRememberMeHasNoRefreshMaxAge() throws Exception {
		String email = uniqueEmail();
		register(baseUrl(), email);

		var response = loginRaw(baseUrl(), email, PASSWORD, false, DEFAULT_TIMEZONE);

		assertThat(response.statusCode()).isEqualTo(200);
		assertRefreshCookieWithoutMaxAge(response);
	}

	@Test
	@DisplayName("Login with a wrong password returns 401 INVALID_CREDENTIALS")
	void wrongPasswordReturns401InvalidCredentials() throws Exception {
		String email = uniqueEmail();
		register(baseUrl(), email);

		var response = loginRaw(baseUrl(), email, "Wrong-Password-2026!", false, DEFAULT_TIMEZONE);

		assertProblem(response, 401, "INVALID_CREDENTIALS");
		assertThat(response.body()).contains(INVALID_CREDENTIALS_MESSAGE);
		assertThat(setCookie(response, ACCESS_COOKIE)).as("md_access Set-Cookie on failure").isEmpty();
		assertThat(setCookie(response, REFRESH_COOKIE)).as("md_refresh Set-Cookie on failure").isEmpty();
	}

	@Test
	@DisplayName("A wrong password and an unknown email give the same 401 body")
	void wrongPasswordBodyIsIdenticalToUnknownEmailBody() throws Exception {
		String registeredEmail = uniqueEmail();
		register(baseUrl(), registeredEmail);
		String unknownEmail = uniqueEmail();

		var wrongPassword = loginRaw(baseUrl(), registeredEmail, "Wrong-Password-2026!", false, DEFAULT_TIMEZONE);
		var unknownUser = loginRaw(baseUrl(), unknownEmail, PASSWORD, false, DEFAULT_TIMEZONE);

		assertProblem(wrongPassword, 401, "INVALID_CREDENTIALS");
		assertProblem(unknownUser, 401, "INVALID_CREDENTIALS");
		assertThat(canonicalErrorBody(wrongPassword))
				.as("body of a wrong password compared with an unknown email")
				.isEqualTo(canonicalErrorBody(unknownUser));
	}

	@Test
	@DisplayName("A login with a different valid time zone updates the time zone seen by GET /api/auth/me")
	void loginWithDifferentTimezoneUpdatesMe() throws Exception {
		String email = uniqueEmail();
		register(baseUrl(), email);

		TestAccounts.Session session = login(baseUrl(), email, PASSWORD, false, OTHER_TIMEZONE);

		var me = api().get("/api/auth/me", session.access());

		assertThat(me.statusCode()).isEqualTo(200);
		assertThat(json(me).path("timezone").asString()).isEqualTo(OTHER_TIMEZONE);
	}

	// ---------------------------------------------------------------- POST /api/auth/refresh

	@Test
	@DisplayName("Refresh with a valid refresh cookie returns 204 and a different refresh cookie value")
	void refreshRotatesRefreshCookie() throws Exception {
		TestAccounts.Session session = register(baseUrl(), uniqueEmail());

		var response = api().postWithCsrf("/api/auth/refresh", session.refresh());

		assertThat(response.statusCode()).as("status, body: " + response.body()).isEqualTo(204);
		var rotated = setCookie(response, REFRESH_COOKIE).orElseThrow(
				() -> new AssertionError("refresh did not set md_refresh"));
		assertThat(rotated.value()).isNotBlank().isNotEqualTo(session.refresh().value());
	}

	@Test
	@DisplayName("Reusing an old refresh cookie returns 401 and clears both cookies")
	void reusingOldRefreshCookieReturns401AndClearsCookies() throws Exception {
		TestAccounts.Session session = register(baseUrl(), uniqueEmail());
		var rotate = api().postWithCsrf("/api/auth/refresh", session.refresh());
		assertThat(rotate.statusCode()).isEqualTo(204);

		var reuse = api().postWithCsrf("/api/auth/refresh", session.refresh());

		assertProblem(reuse, 401, "UNAUTHORIZED");
		assertThat(reuse.body()).contains(SESSION_EXPIRED_MESSAGE);
		assertCleared(reuse, ACCESS_COOKIE);
		assertCleared(reuse, REFRESH_COOKIE);
	}

	@Test
	@DisplayName("Reusing an old refresh cookie revokes the whole family, so the newest one is rejected too")
	void reuseRevokesWholeTokenFamily() throws Exception {
		TestAccounts.Session session = register(baseUrl(), uniqueEmail());
		var rotate = api().postWithCsrf("/api/auth/refresh", session.refresh());
		assertThat(rotate.statusCode()).isEqualTo(204);
		var newest = cookie(REFRESH_COOKIE, setCookie(rotate, REFRESH_COOKIE).orElseThrow().value());

		var reuse = api().postWithCsrf("/api/auth/refresh", session.refresh());
		assertProblem(reuse, 401, "UNAUTHORIZED");

		var afterReuse = api().postWithCsrf("/api/auth/refresh", newest);
		assertProblem(afterReuse, 401, "UNAUTHORIZED");
		assertThat(afterReuse.body()).contains(SESSION_EXPIRED_MESSAGE);
	}

	// ---------------------------------------------------------------- POST /api/auth/logout

	@Test
	@DisplayName("Logout returns 204, clears both cookies and the refresh cookie no longer works")
	void logoutClearsCookiesAndEndsSession() throws Exception {
		TestAccounts.Session session = register(baseUrl(), uniqueEmail());

		var logout = api().postWithCsrf("/api/auth/logout", session.access(), session.refresh());

		assertThat(logout.statusCode()).as("logout status").isEqualTo(204);
		assertCleared(logout, ACCESS_COOKIE);
		assertCleared(logout, REFRESH_COOKIE);

		var refreshAfterLogout = api().postWithCsrf("/api/auth/refresh", session.refresh());
		assertProblem(refreshAfterLogout, 401, "UNAUTHORIZED");
		assertThat(refreshAfterLogout.body()).contains(SESSION_EXPIRED_MESSAGE);
	}

	// ---------------------------------------------------------------- GET /api/auth/me

	@Test
	@DisplayName("GET /api/auth/me with a valid access cookie returns 200 and the signed-in user")
	void meWithValidAccessCookieReturnsUser() throws Exception {
		String email = uniqueEmail();
		TestAccounts.Session session = register(baseUrl(), email);

		var me = api().get("/api/auth/me", session.access());

		assertThat(me.statusCode()).as("status, body: " + me.body()).isEqualTo(200);
		assertThat(json(me).path("email").asString()).isEqualTo(email);
		assertNoSecretsInBody(me);
	}

	@Test
	@DisplayName("Login as the same user again returns 200 with a fresh session")
	void secondLoginForSameUserSucceeds() throws Exception {
		String email = uniqueEmail();
		register(baseUrl(), email);

		TestAccounts.Session second = login(baseUrl(), email);

		assertThat(second.user().path("email").asString()).isEqualTo(email);
		assertThat(api().get("/api/auth/me", second.access()).statusCode()).isEqualTo(200);
	}
}
