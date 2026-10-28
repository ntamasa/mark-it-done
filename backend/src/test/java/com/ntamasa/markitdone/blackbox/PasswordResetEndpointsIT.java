package com.ntamasa.markitdone.blackbox;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.ntamasa.markitdone.blackbox.ApiClient.REFRESH_COOKIE;
import static com.ntamasa.markitdone.blackbox.ApiClient.assertProblem;
import static com.ntamasa.markitdone.blackbox.ApiClient.json;
import static com.ntamasa.markitdone.blackbox.TestAccounts.DEFAULT_TIMEZONE;
import static com.ntamasa.markitdone.blackbox.TestAccounts.PASSWORD;
import static com.ntamasa.markitdone.blackbox.TestAccounts.assertCleared;
import static com.ntamasa.markitdone.blackbox.TestAccounts.errorFields;
import static com.ntamasa.markitdone.blackbox.TestAccounts.jsonBody;
import static com.ntamasa.markitdone.blackbox.TestAccounts.login;
import static com.ntamasa.markitdone.blackbox.TestAccounts.loginRaw;
import static com.ntamasa.markitdone.blackbox.TestAccounts.registerExpectingCreated;
import static com.ntamasa.markitdone.blackbox.TestAccounts.registerBody;
import static com.ntamasa.markitdone.blackbox.TestAccounts.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Black-box tests for POST /api/auth/forgot-password and POST /api/auth/reset-password (MAR-61).
 * The reset token is taken from the reset e-mail that Mailpit received, exactly as a user would use it.
 * Every test uses its own e-mail address and its own Mailpit recipient.
 */
@DisplayName("Forgot and reset password endpoints")
class PasswordResetEndpointsIT extends AbstractIntegrationTest {

	private static final String FORGOT_PATH = "/api/auth/forgot-password";
	private static final String RESET_PATH = "/api/auth/reset-password";
	private static final String ACCEPTED_MESSAGE = "If an account exists for this email, a reset link has been sent.";
	private static final String INVALID_TOKEN_MESSAGE = "This reset link is invalid or has expired.";
	private static final String NEW_PASSWORD = "Winter-2027!z";
	private static final String WEAK_PASSWORD = "weak";
	private static final String DISPLAY_NAME = "Ada Reset";
	private static final Duration MAIL_TIMEOUT = Duration.ofSeconds(10);
	private static final Duration QUIET_PERIOD = Duration.ofSeconds(3);

	private ApiClient api() {
		return new ApiClient(baseUrl());
	}

	private MailpitClient mailpit() {
		return new MailpitClient(mailpitHttpApiUrl());
	}

	/** POST forgot-password with CSRF and asserts 202. */
	private void requestReset(String email) throws Exception {
		HttpResponse<String> response = forgotRaw(email);
		assertThat(response.statusCode()).as("forgot-password status, body: " + response.body()).isEqualTo(202);
	}

	private HttpResponse<String> forgotRaw(String email) throws Exception {
		return api().postJsonWithCsrf(FORGOT_PATH, jsonBody("email", email));
	}

	private HttpResponse<String> resetRaw(String token, String password) throws Exception {
		return api().postJsonWithCsrf(RESET_PATH, jsonBody("token", token, "password", password));
	}

	/** The token of the single reset mail that the address has received so far. */
	private String awaitSingleToken(String email) throws Exception {
		MailpitClient mailpit = mailpit();
		List<MailpitClient.Message> messages = mailpit.awaitMessagesTo(email, 1, MAIL_TIMEOUT);
		String text = mailpit.text(messages.get(0));
		Optional<String> token = MailpitClient.resetToken(text);
		assertThat(token).as("reset link with token in mail body: " + text).isPresent();
		return token.get();
	}

	// ---------------------------------------------------------------- POST /api/auth/forgot-password

	@Test
	@DisplayName("Forgot-password returns the same 202 body for a registered and an unregistered email")
	void forgotPasswordAnswersIdenticallyForKnownAndUnknownEmail() throws Exception {
		String registered = uniqueEmail();
		String unknown = uniqueEmail();
		registerExpectingCreated(baseUrl(), registerBody(DISPLAY_NAME, registered, PASSWORD, DEFAULT_TIMEZONE));

		HttpResponse<String> knownResponse = forgotRaw(registered);
		HttpResponse<String> unknownResponse = forgotRaw(unknown);

		assertThat(knownResponse.statusCode()).as("known email status").isEqualTo(202);
		assertThat(unknownResponse.statusCode()).as("unknown email status").isEqualTo(202);
		assertThat(json(knownResponse).path("status").asString()).isEqualTo(ACCEPTED_MESSAGE);
		assertThat(knownResponse.body()).as("body for a known email").isEqualTo(unknownResponse.body());
		assertThat(json(knownResponse).propertyNames()).as("body fields").containsExactly("status");
	}

	@Test
	@DisplayName("A registered email gets exactly one reset mail with the subject, greeting, link and footer")
	void registeredEmailGetsOneResetMail() throws Exception {
		String email = uniqueEmail();
		registerExpectingCreated(baseUrl(), registerBody(DISPLAY_NAME, email, PASSWORD, DEFAULT_TIMEZONE));

		requestReset(email);
		MailpitClient mailpit = mailpit();
		List<MailpitClient.Message> messages = mailpit.awaitMessagesTo(email, 1, MAIL_TIMEOUT);
		Thread.sleep(1000);
		messages = mailpit.messagesTo(email);

		assertThat(messages).as("mails to " + email).hasSize(1);
		MailpitClient.Message message = messages.get(0);
		assertThat(message.subject()).isEqualTo(MailpitClient.RESET_SUBJECT);

		String text = mailpit.text(message);
		assertThat(text).as("greeting with display name").contains(DISPLAY_NAME);
		assertThat(text).as("reset link").contains("/reset-password?token=");
		assertThat(MailpitClient.resetToken(text)).as("token in link").isPresent();
		assertThat(text).as("ignore note").contains("If you didn't ask for this, you can ignore this email.");
		assertThat(text).as("signature").contains("— markitDONE");
	}

	@Test
	@DisplayName("An unregistered email gets no reset mail")
	void unregisteredEmailGetsNoMail() throws Exception {
		String unknown = uniqueEmail();

		requestReset(unknown);
		Thread.sleep(QUIET_PERIOD.toMillis());

		assertThat(mailpit().messagesTo(unknown)).as("mails to an unknown address").isEmpty();
	}

	@Test
	@DisplayName("A second forgot-password request invalidates the link from the first one")
	void secondRequestInvalidatesFirstLink() throws Exception {
		String email = uniqueEmail();
		registerExpectingCreated(baseUrl(), registerBody(DISPLAY_NAME, email, PASSWORD, DEFAULT_TIMEZONE));
		MailpitClient mailpit = mailpit();

		requestReset(email);
		String firstToken = awaitSingleToken(email);

		requestReset(email);
		List<MailpitClient.Message> messages = mailpit.awaitMessagesTo(email, 2, MAIL_TIMEOUT);
		String secondToken = null;
		for (MailpitClient.Message message : messages) {
			Optional<String> token = MailpitClient.resetToken(mailpit.text(message));
			if (token.isPresent() && !token.get().equals(firstToken)) {
				secondToken = token.get();
			}
		}
		assertThat(secondToken).as("token of the second reset mail").isNotNull();

		HttpResponse<String> oldLink = resetRaw(firstToken, NEW_PASSWORD);
		assertProblem(oldLink, 400, "RESET_TOKEN_INVALID");

		HttpResponse<String> newLink = resetRaw(secondToken, NEW_PASSWORD);
		assertThat(newLink.statusCode()).as("reset with the second link, body: " + newLink.body()).isEqualTo(204);
	}

	@Test
	@DisplayName("Forgot-password without a CSRF token returns 403 and sends no mail")
	void forgotPasswordWithoutCsrfReturns403() throws Exception {
		String email = uniqueEmail();
		registerExpectingCreated(baseUrl(), registerBody(DISPLAY_NAME, email, PASSWORD, DEFAULT_TIMEZONE));

		HttpResponse<String> response = api().postJson(FORGOT_PATH, jsonBody("email", email));
		assertThat(response.statusCode()).as("status without CSRF token").isEqualTo(403);

		Thread.sleep(QUIET_PERIOD.toMillis());
		assertThat(mailpit().messagesTo(email)).as("mails after a rejected request").isEmpty();
	}

	// ---------------------------------------------------------------- POST /api/auth/reset-password

	@Test
	@DisplayName("Reset with the mailed token returns 204, clears the session cookies and changes the password")
	void resetWithMailedTokenChangesPasswordAndClearsCookies() throws Exception {
		String email = uniqueEmail();
		registerExpectingCreated(baseUrl(), registerBody(DISPLAY_NAME, email, PASSWORD, DEFAULT_TIMEZONE));
		requestReset(email);
		String token = awaitSingleToken(email);

		HttpResponse<String> reset = resetRaw(token, NEW_PASSWORD);

		assertThat(reset.statusCode()).as("reset status, body: " + reset.body()).isEqualTo(204);
		assertCleared(reset, ApiClient.ACCESS_COOKIE);
		assertCleared(reset, REFRESH_COOKIE);

		HttpResponse<String> withNew = loginRaw(baseUrl(), email, NEW_PASSWORD, false, DEFAULT_TIMEZONE);
		assertThat(withNew.statusCode()).as("login with the new password, body: " + withNew.body()).isEqualTo(200);

		HttpResponse<String> withOld = loginRaw(baseUrl(), email, PASSWORD, false, DEFAULT_TIMEZONE);
		assertProblem(withOld, 401, "INVALID_CREDENTIALS");
	}

	@Test
	@DisplayName("A refresh cookie issued before the reset is rejected with 401 afterwards")
	void refreshCookieFromBeforeResetIsRejected() throws Exception {
		String email = uniqueEmail();
		registerExpectingCreated(baseUrl(), registerBody(DISPLAY_NAME, email, PASSWORD, DEFAULT_TIMEZONE));
		TestAccounts.Session before = login(baseUrl(), email);

		requestReset(email);
		String token = awaitSingleToken(email);
		HttpResponse<String> reset = resetRaw(token, NEW_PASSWORD);
		assertThat(reset.statusCode()).as("reset status, body: " + reset.body()).isEqualTo(204);

		HttpResponse<String> refresh = api().postWithCsrf("/api/auth/refresh", before.refresh());
		assertThat(refresh.statusCode()).as("refresh with a pre-reset cookie, body: " + refresh.body()).isEqualTo(401);
	}

	@Test
	@DisplayName("A weak new password returns 400 VALIDATION_FAILED on password and leaves the link usable")
	void weakPasswordReturns400AndTokenStillWorks() throws Exception {
		String email = uniqueEmail();
		registerExpectingCreated(baseUrl(), registerBody(DISPLAY_NAME, email, PASSWORD, DEFAULT_TIMEZONE));
		requestReset(email);
		String token = awaitSingleToken(email);

		HttpResponse<String> weak = resetRaw(token, WEAK_PASSWORD);
		assertProblem(weak, 400, "VALIDATION_FAILED");
		assertThat(errorFields(weak)).as("error fields").contains("password");

		HttpResponse<String> retry = resetRaw(token, NEW_PASSWORD);
		assertThat(retry.statusCode()).as("retry with the same token, body: " + retry.body()).isEqualTo(204);
	}

	@Test
	@DisplayName("Reusing a reset token returns 400 RESET_TOKEN_INVALID with the standard message")
	void reusedTokenReturns400() throws Exception {
		String email = uniqueEmail();
		registerExpectingCreated(baseUrl(), registerBody(DISPLAY_NAME, email, PASSWORD, DEFAULT_TIMEZONE));
		requestReset(email);
		String token = awaitSingleToken(email);
		assertThat(resetRaw(token, NEW_PASSWORD).statusCode()).as("first use").isEqualTo(204);

		HttpResponse<String> second = resetRaw(token, "Spring-2028!q");

		assertProblem(second, 400, "RESET_TOKEN_INVALID");
		assertThat(second.body()).contains(INVALID_TOKEN_MESSAGE);
	}

	@Test
	@DisplayName("A garbage reset token returns 400 RESET_TOKEN_INVALID with the standard message")
	void garbageTokenReturns400() throws Exception {
		HttpResponse<String> response = resetRaw("this-is-not-a-real-reset-token", NEW_PASSWORD);

		assertProblem(response, 400, "RESET_TOKEN_INVALID");
		assertThat(response.body()).contains(INVALID_TOKEN_MESSAGE);
	}

	@Test
	@DisplayName("Reset-password without a CSRF token returns 403 and does not consume the link")
	void resetPasswordWithoutCsrfReturns403() throws Exception {
		String email = uniqueEmail();
		registerExpectingCreated(baseUrl(), registerBody(DISPLAY_NAME, email, PASSWORD, DEFAULT_TIMEZONE));
		requestReset(email);
		String token = awaitSingleToken(email);

		HttpResponse<String> response = api().postJson(RESET_PATH, jsonBody("token", token, "password", NEW_PASSWORD));
		assertThat(response.statusCode()).as("status without CSRF token").isEqualTo(403);

		HttpResponse<String> retry = resetRaw(token, NEW_PASSWORD);
		assertThat(retry.statusCode()).as("the link is still usable, body: " + retry.body()).isEqualTo(204);
	}
}
