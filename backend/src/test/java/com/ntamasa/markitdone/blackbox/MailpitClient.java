package com.ntamasa.markitdone.blackbox;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Black-box client for the Mailpit HTTP API (the mail catcher used in the dev stack and in the tests).
 * It lists and searches messages by recipient and reads their plain-text body. Only HTTP and JSON are used.
 * Mail is sent after the database commit, so the await methods poll for a few seconds.
 */
final class MailpitClient {

	/** The subject the reset e-mail must carry. */
	static final String RESET_SUBJECT = "Reset your markitDONE password";

	private static final Pattern RESET_LINK = Pattern.compile("/reset-password\\?token=([^\\s\"'<>]+)");
	private static final ObjectMapper JSON = new ObjectMapper();

	private final String apiUrl;
	private final HttpClient http = HttpClient.newHttpClient();

	MailpitClient(String apiUrl) {
		this.apiUrl = apiUrl;
	}

	/** A message summary as returned by the search endpoint. */
	record Message(String id, String subject, List<String> toAddresses) {
	}

	/** Returns the messages whose To header contains exactly this address (case-insensitive). */
	List<Message> messagesTo(String email) throws Exception {
		String query = "to:\"" + email + "\"";
		HttpResponse<String> response = http.send(
				HttpRequest.newBuilder(URI.create(apiUrl + "/api/v1/search?limit=100&query="
						+ URLEncoder.encode(query, StandardCharsets.UTF_8))).GET().build(),
				HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) {
			throw new AssertionError("Mailpit search failed with " + response.statusCode() + ": " + response.body());
		}
		List<Message> matches = new ArrayList<>();
		for (JsonNode node : JSON.readTree(response.body()).path("messages")) {
			List<String> recipients = new ArrayList<>();
			for (JsonNode to : node.path("To")) {
				recipients.add(to.path("Address").asString());
			}
			boolean exact = recipients.stream().anyMatch(address -> address.equalsIgnoreCase(email));
			if (exact) {
				matches.add(new Message(node.path("ID").asString(), node.path("Subject").asString(), recipients));
			}
		}
		return matches;
	}

	/** Polls until at least {@code minCount} messages to the address exist, or fails after the timeout. */
	List<Message> awaitMessagesTo(String email, int minCount, Duration timeout) throws Exception {
		long deadline = System.nanoTime() + timeout.toNanos();
		List<Message> messages = messagesTo(email);
		while (messages.size() < minCount && System.nanoTime() < deadline) {
			Thread.sleep(200);
			messages = messagesTo(email);
		}
		if (messages.size() < minCount) {
			throw new AssertionError("expected at least " + minCount + " mail(s) to " + email + " but found "
					+ messages.size() + " within " + timeout);
		}
		return messages;
	}

	/** The plain-text body of one message. */
	String text(Message message) throws Exception {
		HttpResponse<String> response = http.send(
				HttpRequest.newBuilder(URI.create(apiUrl + "/api/v1/message/" + message.id())).GET().build(),
				HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) {
			throw new AssertionError("Mailpit message fetch failed with " + response.statusCode());
		}
		return JSON.readTree(response.body()).path("Text").asString();
	}

	/** The reset token from the link {@code /reset-password?token=<url-encoded>} in a plain-text body, URL-decoded. */
	static Optional<String> resetToken(String text) {
		Matcher matcher = RESET_LINK.matcher(text);
		if (!matcher.find()) {
			return Optional.empty();
		}
		return Optional.of(URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8));
	}
}
