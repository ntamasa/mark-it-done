package com.ntamasa.markitdone.blackbox;

import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import tools.jackson.databind.JsonNode;

import static com.ntamasa.markitdone.blackbox.ApiClient.json;
import static com.ntamasa.markitdone.blackbox.TestAccounts.Session;
import static com.ntamasa.markitdone.blackbox.TestAccounts.jsonBody;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shared helpers for the recurring-series tests that need dates relative to "today" in Europe/Budapest.
 * The clock cannot be fixed over HTTP, so every scenario is built from the next Monday after today,
 * which keeps all generated dates in the future. Only HTTP requests, status codes and JSON bodies are used.
 */
final class SeriesTestSupport {

	static final String TIMEZONE = "Europe/Budapest";
	static final ZoneId ZONE = ZoneId.of(TIMEZONE);
	static final String SERIES = "/api/series";
	static final String TODOS = "/api/todos";
	/** Largest span GET /api/todos accepts: "to" may be at most 62 days after "from". */
	static final int MAX_WINDOW_SPAN_DAYS = 62;

	private final String baseUrl;
	private final ApiClient api;

	SeriesTestSupport(String baseUrl) {
		this.baseUrl = baseUrl;
		this.api = new ApiClient(baseUrl);
	}

	ApiClient api() {
		return api;
	}

	/** Registers a fresh user in Europe/Budapest. */
	Session registerUser() throws Exception {
		return TestAccounts.register(baseUrl, TestAccounts.uniqueEmail());
	}

	/** Today's date in Europe/Budapest. */
	static LocalDate today() {
		return LocalDate.now(ZONE);
	}

	/** The first Monday strictly after today (Europe/Budapest). */
	static LocalDate nextMonday() {
		return today().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
	}

	/** The rolling horizon: three months after today, inclusive. */
	static LocalDate horizon() {
		return today().plusMonths(3);
	}

	// ---------------------------------------------------------------- rule builders

	/** A rule that repeats every n days from the given start, never ending. */
	static Map<String, Object> everyNDays(int n, LocalDate start) {
		Map<String, Object> rule = baseRule("EVERY_N_DAYS", start);
		rule.put("intervalDays", n);
		return rule;
	}

	/** A weekly rule on the given weekdays from the given start, never ending. */
	static Map<String, Object> weekly(LocalDate start, List<String> weekdays) {
		Map<String, Object> rule = baseRule("WEEKLY", start);
		rule.put("weekdays", weekdays);
		return rule;
	}

	/** Turns a rule into one that ends after the given number of occurrences. */
	static Map<String, Object> endingAfter(Map<String, Object> rule, int count) {
		rule.put("endType", "AFTER_COUNT");
		rule.put("endCount", count);
		return rule;
	}

	private static Map<String, Object> baseRule(String frequency, LocalDate start) {
		Map<String, Object> rule = new LinkedHashMap<>();
		rule.put("frequency", frequency);
		rule.put("intervalDays", null);
		rule.put("weekdays", null);
		rule.put("monthDays", null);
		rule.put("startDate", start.toString());
		rule.put("endType", "NEVER");
		rule.put("endCount", null);
		rule.put("endDate", null);
		return rule;
	}

	// ---------------------------------------------------------------- body builders

	/** POST /api/series body: a template with the given name, due time 18:00, priority MEDIUM and the rule. */
	static String seriesBody(String name, Map<String, Object> rule) {
		return jsonBody("name", name, "dueTime", "18:00", "priority", "MEDIUM", "rule", rule);
	}

	/** PUT /api/todos/{id} body without a rule: the normal todo fields with the template's time and priority. */
	static String todoBody(String name, LocalDate dueDate) {
		return jsonBody("name", name, "dueDate", dueDate.toString(), "dueTime", "18:00", "priority", "MEDIUM");
	}

	/** PUT /api/todos/{id} body with a rule (used for "this and future" rule changes). */
	static String todoBody(String name, LocalDate dueDate, Map<String, Object> rule) {
		return jsonBody("name", name, "dueDate", dueDate.toString(), "dueTime", "18:00", "priority", "MEDIUM",
				"rule", rule);
	}

	// ---------------------------------------------------------------- HTTP helpers

	/** POST /api/series that must succeed with 201. Returns the SeriesDto. */
	JsonNode createSeries(Session session, String body) throws Exception {
		HttpResponse<String> response = api.postJsonWithCsrf(SERIES, body, session.access(), session.refresh());
		assertThat(response.statusCode()).as("POST " + SERIES + " status, body: " + response.body()).isEqualTo(201);
		return json(response);
	}

	/** POST /api/todos for a one-off todo that must succeed with 201. Returns the TodoDto. */
	JsonNode createOneOff(Session session, String name, LocalDate dueDate) throws Exception {
		HttpResponse<String> response = api.postJsonWithCsrf("/api/todos",
				jsonBody("name", name, "dueDate", dueDate.toString(), "priority", "LOW"),
				session.access(), session.refresh());
		assertThat(response.statusCode()).as("POST /api/todos status, body: " + response.body()).isEqualTo(201);
		return json(response);
	}

	/** PUT /api/todos/{id}, with the scope query parameter when scope is not null, with a CSRF token. */
	HttpResponse<String> edit(Session session, String id, String scope, String body) throws Exception {
		return api.putJsonWithCsrf(todoPath(id, scope), body, session.access(), session.refresh());
	}

	/** PUT /api/todos/{id} without any CSRF header. */
	HttpResponse<String> editWithoutCsrf(Session session, String id, String scope, String body) throws Exception {
		return api.putJson(todoPath(id, scope), body, session.access(), session.refresh());
	}

	static String todoPath(String id, String scope) {
		return TODOS + "/" + id + (scope == null ? "" : "?scope=" + scope);
	}

	/**
	 * The todo rows of GET /api/todos from "from" to "to" (both inclusive), in the order the API returns them.
	 * The range is split into windows of at most 62 days.
	 */
	List<JsonNode> rows(Session session, LocalDate from, LocalDate to) throws Exception {
		List<JsonNode> rows = new ArrayList<>();
		LocalDate start = from;
		while (!start.isAfter(to)) {
			LocalDate end = start.plusDays(MAX_WINDOW_SPAN_DAYS);
			if (end.isAfter(to)) {
				end = to;
			}
			JsonNode page = api.getJsonOk(TODOS + "?from=" + start + "&to=" + end,
					session.access(), session.refresh());
			for (JsonNode row : page) {
				rows.add(row);
			}
			start = end.plusDays(1);
		}
		return rows;
	}

	/** The name of every row from "from" to "to", keyed by due date. Asserts there is at most one row per date. */
	Map<LocalDate, String> namesByDate(Session session, LocalDate from, LocalDate to) throws Exception {
		Map<LocalDate, String> names = new TreeMap<>();
		for (JsonNode row : rows(session, from, to)) {
			LocalDate date = LocalDate.parse(row.path("dueDate").asString());
			assertThat(names).as("rows on " + date).doesNotContainKey(date);
			names.put(date, row.path("name").asString());
		}
		return names;
	}

	/** The single row due on the given date. Asserts that exactly one row exists. */
	JsonNode rowOn(Session session, LocalDate date) throws Exception {
		List<JsonNode> rows = rows(session, date, date);
		assertThat(rows).as("rows due on " + date).hasSize(1);
		return rows.get(0);
	}

	// ---------------------------------------------------------------- date helpers

	/** Every date from "from" to "to", both inclusive. */
	static List<LocalDate> datesBetween(LocalDate from, LocalDate to) {
		List<LocalDate> dates = new ArrayList<>();
		for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
			dates.add(d);
		}
		return dates;
	}

	/** Every n-th day from "from" to "to", both inclusive. */
	static List<LocalDate> everyNthDay(LocalDate from, LocalDate to, int n) {
		List<LocalDate> dates = new ArrayList<>();
		for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(n)) {
			dates.add(d);
		}
		return dates;
	}

	/** The Mondays from "from" to "to", both inclusive. */
	static List<LocalDate> mondays(LocalDate from, LocalDate to) {
		List<LocalDate> dates = new ArrayList<>();
		for (LocalDate d : datesBetween(from, to)) {
			if (d.getDayOfWeek() == DayOfWeek.MONDAY) {
				dates.add(d);
			}
		}
		return dates;
	}

	/** The expected name of each date from "from" to "to": the overrides, or the default name elsewhere. */
	static Map<LocalDate, String> expectedNames(LocalDate from, LocalDate to, String defaultName,
			Map<LocalDate, String> overrides) {
		Map<LocalDate, String> names = new TreeMap<>();
		for (LocalDate date : datesBetween(from, to)) {
			names.put(date, overrides.getOrDefault(date, defaultName));
		}
		return names;
	}
}
