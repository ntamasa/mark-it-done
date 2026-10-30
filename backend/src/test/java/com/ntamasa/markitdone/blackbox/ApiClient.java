package com.ntamasa.markitdone.blackbox;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Black-box HTTP helper shared by the API tests.
 * It only sends requests and reads status codes, headers, cookies and JSON bodies.
 * The CSRF helpers follow the public contract: GET /api/auth/csrf sets the XSRF-TOKEN cookie,
 * and every non-GET request must repeat that value in the X-XSRF-TOKEN header.
 */
final class ApiClient {

	static final String CSRF_COOKIE = "XSRF-TOKEN";
	static final String CSRF_HEADER = "X-XSRF-TOKEN";
	static final String ACCESS_COOKIE = "md_access";
	static final String REFRESH_COOKIE = "md_refresh";

	private static final ObjectMapper JSON = new ObjectMapper();

	private final String baseUrl;
	private final HttpClient http = HttpClient.newHttpClient();

	ApiClient(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	/** A cookie as it is sent back in a Cookie request header. */
	record Cookie(String name, String value) {
		String asPair() {
			return name + "=" + value;
		}
	}

	/** A Set-Cookie response header, split into its name, value and lower-cased attributes. */
	record SetCookie(String name, String value, List<String> attributes) {

		boolean hasAttribute(String attribute) {
			return attributes.contains(attribute.toLowerCase());
		}

		/** True when the server clears the cookie with Max-Age=0. */
		boolean isCleared() {
			return attributes.contains("max-age=0");
		}
	}

	/** GET without a body, sending the given cookies. */
	HttpResponse<String> get(String path, Cookie... cookies) throws Exception {
		return send(builder(path, cookies).GET().build());
	}

	/** POST with a JSON body, without any CSRF header. Useful for asserting CSRF rejection. */
	HttpResponse<String> postJson(String path, String json, Cookie... cookies) throws Exception {
		return send(builder(path, cookies)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json))
				.build());
	}

	/** POST with a JSON body, with a freshly fetched CSRF cookie and matching X-XSRF-TOKEN header. */
	HttpResponse<String> postJsonWithCsrf(String path, String json, Cookie... extraCookies) throws Exception {
		Cookie csrf = fetchCsrfCookie();
		Cookie[] all = new Cookie[extraCookies.length + 1];
		all[0] = csrf;
		System.arraycopy(extraCookies, 0, all, 1, extraCookies.length);
		return send(builder(path, all)
				.header("Content-Type", "application/json")
				.header(CSRF_HEADER, csrf.value())
				.POST(HttpRequest.BodyPublishers.ofString(json))
				.build());
	}

	/** PATCH with a JSON body, without any CSRF header. Useful for asserting CSRF rejection. */
	HttpResponse<String> patchJson(String path, String json, Cookie... cookies) throws Exception {
		return send(builder(path, cookies)
				.header("Content-Type", "application/json")
				.method("PATCH", HttpRequest.BodyPublishers.ofString(json))
				.build());
	}

	/** PATCH with a JSON body, with a freshly fetched CSRF cookie and matching X-XSRF-TOKEN header. */
	HttpResponse<String> patchJsonWithCsrf(String path, String json, Cookie... extraCookies) throws Exception {
		Cookie csrf = fetchCsrfCookie();
		Cookie[] all = new Cookie[extraCookies.length + 1];
		all[0] = csrf;
		System.arraycopy(extraCookies, 0, all, 1, extraCookies.length);
		return send(builder(path, all)
				.header("Content-Type", "application/json")
				.header(CSRF_HEADER, csrf.value())
				.method("PATCH", HttpRequest.BodyPublishers.ofString(json))
				.build());
	}

	/** PUT with a JSON body, without any CSRF header. Useful for asserting CSRF rejection. */
	HttpResponse<String> putJson(String path, String json, Cookie... cookies) throws Exception {
		return send(builder(path, cookies)
				.header("Content-Type", "application/json")
				.PUT(HttpRequest.BodyPublishers.ofString(json))
				.build());
	}

	/** PUT with a JSON body, with a freshly fetched CSRF cookie and matching X-XSRF-TOKEN header. */
	HttpResponse<String> putJsonWithCsrf(String path, String json, Cookie... extraCookies) throws Exception {
		Cookie csrf = fetchCsrfCookie();
		Cookie[] all = new Cookie[extraCookies.length + 1];
		all[0] = csrf;
		System.arraycopy(extraCookies, 0, all, 1, extraCookies.length);
		return send(builder(path, all)
				.header("Content-Type", "application/json")
				.header(CSRF_HEADER, csrf.value())
				.PUT(HttpRequest.BodyPublishers.ofString(json))
				.build());
	}

	/** GET with the given cookies, asserting the response is 200 with a JSON body. Returns the parsed body. */
	JsonNode getJsonOk(String path, Cookie... cookies) throws Exception {
		HttpResponse<String> response = get(path, cookies);
		assertThat(response.statusCode()).as("GET " + path + " status, body: " + response.body()).isEqualTo(200);
		return json(response);
	}

	/** POST without a body, with a freshly fetched CSRF cookie and matching X-XSRF-TOKEN header. */
	HttpResponse<String> postWithCsrf(String path, Cookie... extraCookies) throws Exception {
		Cookie csrf = fetchCsrfCookie();
		Cookie[] all = new Cookie[extraCookies.length + 1];
		all[0] = csrf;
		System.arraycopy(extraCookies, 0, all, 1, extraCookies.length);
		return send(builder(path, all)
				.header(CSRF_HEADER, csrf.value())
				.POST(HttpRequest.BodyPublishers.noBody())
				.build());
	}

	/** Calls GET /api/auth/csrf and returns the XSRF-TOKEN cookie it sets. */
	Cookie fetchCsrfCookie() throws Exception {
		HttpResponse<String> response = get("/api/auth/csrf");
		assertThat(response.statusCode()).as("GET /api/auth/csrf status").isEqualTo(204);
		SetCookie xsrf = setCookie(response, CSRF_COOKIE)
				.orElseThrow(() -> new AssertionError("GET /api/auth/csrf did not set XSRF-TOKEN"));
		assertThat(xsrf.value()).as("XSRF-TOKEN value").isNotBlank();
		return new Cookie(CSRF_COOKIE, xsrf.value());
	}

	static Cookie cookie(String name, String value) {
		return new Cookie(name, value);
	}

	static Optional<SetCookie> setCookie(HttpResponse<?> response, String name) {
		return setCookies(response).stream()
				.filter(cookie -> cookie.name().equals(name))
				.findFirst();
	}

	static List<SetCookie> setCookies(HttpResponse<?> response) {
		return response.headers().allValues("Set-Cookie").stream()
				.map(ApiClient::parseSetCookie)
				.collect(Collectors.toList());
	}

	/** Asserts a problem+json response with the given status and error code. */
	static void assertProblem(HttpResponse<String> response, int status, String code) {
		assertThat(response.statusCode()).as("status of " + response.body()).isEqualTo(status);
		assertThat(response.headers().firstValue("Content-Type").orElse(""))
				.startsWith("application/problem+json");
		JsonNode body = json(response);
		assertThat(body.path("status").asInt()).isEqualTo(status);
		assertThat(body.path("code").asString()).isEqualTo(code);
	}

	static JsonNode json(HttpResponse<String> response) {
		return JSON.readTree(response.body());
	}

	private HttpRequest.Builder builder(String path, Cookie... cookies) {
		HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(baseUrl + path));
		if (cookies.length > 0) {
			builder.header("Cookie", Arrays.stream(cookies)
					.map(Cookie::asPair)
					.collect(Collectors.joining("; ")));
		}
		return builder;
	}

	private HttpResponse<String> send(HttpRequest request) throws Exception {
		return http.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private static SetCookie parseSetCookie(String header) {
		String[] parts = header.split(";");
		int eq = parts[0].indexOf('=');
		String name = parts[0].substring(0, eq).trim();
		String value = parts[0].substring(eq + 1).trim();
		List<String> attributes = Arrays.stream(parts, 1, parts.length)
				.map(part -> part.trim().toLowerCase())
				.collect(Collectors.toList());
		return new SetCookie(name, value, attributes);
	}
}
