package com.ntamasa.markitdone.blackbox;

import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static com.ntamasa.markitdone.blackbox.SeriesTestSupport.SERIES;
import static com.ntamasa.markitdone.blackbox.SeriesTestSupport.endingAfter;
import static com.ntamasa.markitdone.blackbox.SeriesTestSupport.everyNDays;
import static com.ntamasa.markitdone.blackbox.SeriesTestSupport.seriesBody;
import static com.ntamasa.markitdone.blackbox.SeriesTestSupport.weekly;
import static com.ntamasa.markitdone.blackbox.TestAccounts.Session;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Black-box tests for the read side of recurring series (MAR-87):
 * GET /api/series?status=ACTIVE|ENDED|CANCELLED|ALL&q=... and GET /api/series/{id}.
 * <p>
 * The main fixture is one user in Europe/Budapest with four series built relative to today:
 * "Gym" (active, weekly, never ends), "Rent" (monthly, 6 occurrences, all before today, so ENDED),
 * "Yoga" (cancelled through POST /api/series/{id}/cancel) and "Water plants" (active, every 3 days, never ends).
 * Expected values are derived from the request data and from GET /api/todos, not from the implementation.
 * Only HTTP requests, status codes and JSON bodies are used.
 */
@DisplayName("GET /api/series and GET /api/series/{id} (MAR-87)")
class SeriesQueriesIT extends AbstractIntegrationTest {

	private static final String GYM = "Gym";
	private static final String RENT = "Rent";
	private static final String YOGA = "Yoga";
	private static final String WATER = "Water plants";
	private static final String NOT_FOUND_MESSAGE = "Series not found";
	private static final DateTimeFormatter MMM_D = DateTimeFormatter.ofPattern("MMM d", Locale.US);
	private static final DateTimeFormatter MMM_D_YYYY = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);

	/** The earliest row of a series dated today or later, as GET /api/todos reports it. */
	private record Upcoming(String name, String seriesId, LocalDate date, String todoId) {
	}

	private SeriesTestSupport support;
	private Session owner;
	private LocalDate today;
	private String gymId;
	private String rentId;
	private String yogaId;
	private String waterId;

	@BeforeEach
	void createFixture() throws Exception {
		support = new SeriesTestSupport(baseUrl());
		owner = support.registerUser();
		today = SeriesTestSupport.today();
		gymId = createSeries(seriesBody(GYM, weekly(today, List.of("MONDAY", "WEDNESDAY", "FRIDAY"))));
		// Monthly on the 1st, six occurrences starting seven months ago: the last one is two months ago.
		LocalDate rentStart = today.withDayOfMonth(1).minusMonths(7);
		rentId = createSeries(seriesBody(RENT, endingAfter(monthly(rentStart, 1), 6)));
		yogaId = createSeries(seriesBody(YOGA, everyNDays(2, today)));
		waterId = createSeries(seriesBody(WATER, everyNDays(3, today)));
		HttpResponse<String> cancelled = support.api().postWithCsrf(SERIES + "/" + yogaId + "/cancel",
				owner.access(), owner.refresh());
		assertThat(cancelled.statusCode()).as("cancel Yoga, body: " + cancelled.body()).isEqualTo(200);
	}

	// ---------------------------------------------------------------- default list and ACTIVE

	@Test
	@DisplayName("Default GET /api/series returns only the two active series, sorted by next occurrence then name")
	void defaultListReturnsActiveSeriesSortedByNextOccurrence() throws Exception {
		JsonNode list = list(null);

		assertThat(names(list)).as("default list names").containsExactlyElementsOf(expectedActiveOrder());
		for (JsonNode series : list) {
			assertThat(series.path("status").asString()).as("status of " + series).isEqualTo("ACTIVE");
		}
	}

	@Test
	@DisplayName("status=ACTIVE returns the same list as the default filter")
	void explicitActiveMatchesDefault() throws Exception {
		assertThat(list("status=ACTIVE")).isEqualTo(list(null));
	}

	@Test
	@DisplayName("nextOccurrence and nextOccurrenceTodoId match the earliest row of the series dated today or later in GET /api/todos")
	void nextOccurrenceMatchesEarliestTodoRow() throws Exception {
		Map<String, JsonNode> byName = byName(list(null));

		for (Upcoming upcoming : List.of(upcoming(GYM, gymId), upcoming(WATER, waterId))) {
			JsonNode series = byName.get(upcoming.name());
			assertThat(series).as("list entry for " + upcoming.name()).isNotNull();
			assertThat(series.path("nextOccurrence").asString())
					.as(upcoming.name() + " nextOccurrence")
					.isEqualTo(upcoming.date().toString());
			assertThat(series.path("nextOccurrenceTodoId").asString())
					.as(upcoming.name() + " nextOccurrenceTodoId")
					.isEqualTo(upcoming.todoId());
			assertThat(LocalDate.parse(series.path("nextOccurrence").asString()))
					.as(upcoming.name() + " next occurrence is not in the past")
					.isAfterOrEqualTo(today);
		}
	}

	@Test
	@DisplayName("A never-ending active series has endsSummary \"Never\"")
	void neverEndingSeriesEndsSummaryIsNever() throws Exception {
		Map<String, JsonNode> byName = byName(list(null));

		assertThat(byName.get(GYM).path("endsSummary").asString()).isEqualTo("Never");
		assertThat(byName.get(WATER).path("endsSummary").asString()).isEqualTo("Never");
	}

	@Test
	@DisplayName("Ordering uses nextOccurrence first and the name as tie-breaker, in a separate user's list")
	void orderingUsesNextOccurrenceThenName() throws Exception {
		Session other = support.registerUser();
		createSeriesFor(other, seriesBody("Zeta", everyNDays(1, today)));
		createSeriesFor(other, seriesBody("Alpha", everyNDays(2, today)));
		createSeriesFor(other, seriesBody("Later", everyNDays(1, today.plusDays(2))));

		JsonNode list = list(other, null);

		assertThat(names(list)).containsExactly("Alpha", "Zeta", "Later");
	}

	@Test
	@DisplayName("An active AFTER_COUNT series shows the occurrences left, excluding those before today")
	void afterCountActiveSeriesShowsLeftCount() throws Exception {
		Session other = support.registerUser();
		// Daily from five days ago with 20 occurrences: five are before today, so 15 are left.
		createSeriesFor(other, seriesBody("Stretch", endingAfter(everyNDays(1, today.minusDays(5)), 20)));

		JsonNode series = byName(list(other, null)).get("Stretch");

		assertThat(series).as("Stretch in the active list").isNotNull();
		assertThat(series.path("status").asString()).isEqualTo("ACTIVE");
		assertThat(series.path("endsSummary").asString()).isEqualTo("After 20 (15 left)");
	}

	@Test
	@DisplayName("An active ON_DATE series shows its end date as \"On <MMM d, yyyy>\"")
	void onDateActiveSeriesShowsEndDate() throws Exception {
		Session other = support.registerUser();
		LocalDate end = today.plusDays(40);
		Map<String, Object> rule = everyNDays(1, today);
		rule.put("endType", "ON_DATE");
		rule.put("endDate", end.toString());
		createSeriesFor(other, seriesBody("Course", rule));

		JsonNode series = byName(list(other, null)).get("Course");

		assertThat(series).as("Course in the active list").isNotNull();
		assertThat(series.path("status").asString()).isEqualTo("ACTIVE");
		assertThat(series.path("endsSummary").asString()).isEqualTo("On " + end.format(MMM_D_YYYY));
	}

	// ---------------------------------------------------------------- ENDED / CANCELLED / ALL

	@Test
	@DisplayName("status=ENDED returns only Rent, with endsSummary \"After 6 (ended)\" and no next occurrence")
	void endedListReturnsOnlyRent() throws Exception {
		JsonNode list = list("status=ENDED");

		assertThat(names(list)).containsExactly(RENT);
		JsonNode rent = list.get(0);
		assertThat(rent.path("status").asString()).isEqualTo("ENDED");
		assertThat(rent.path("endsSummary").asString()).isEqualTo("After 6 (ended)");
		assertJsonNull(rent, "nextOccurrence");
		assertJsonNull(rent, "nextOccurrenceTodoId");
	}

	@Test
	@DisplayName("status=CANCELLED returns only Yoga, with endsSummary \"Cancelled <MMM d>\" for today and no next occurrence")
	void cancelledListReturnsOnlyYoga() throws Exception {
		JsonNode list = list("status=CANCELLED");

		assertThat(names(list)).containsExactly(YOGA);
		JsonNode yoga = list.get(0);
		assertThat(yoga.path("status").asString()).isEqualTo("CANCELLED");
		assertThat(yoga.path("endsSummary").asString()).isEqualTo("Cancelled " + today.format(MMM_D));
		assertThat(yoga.path("cancelledOn").asString()).isEqualTo(today.toString());
		assertJsonNull(yoga, "nextOccurrence");
		assertJsonNull(yoga, "nextOccurrenceTodoId");
	}

	@Test
	@DisplayName("status=ALL returns all four series, with series without a next occurrence after the active ones")
	void allListReturnsEverySeriesWithNullNextLast() throws Exception {
		JsonNode list = list("status=ALL");

		assertThat(names(list)).hasSize(4);
		List<String> expected = new ArrayList<>(expectedActiveOrder());
		expected.add(RENT);
		expected.add(YOGA);
		assertThat(names(list)).containsExactlyElementsOf(expected);
		Map<String, String> statuses = new LinkedHashMap<>();
		for (JsonNode series : list) {
			statuses.put(series.path("name").asString(), series.path("status").asString());
		}
		assertThat(statuses).containsEntry(GYM, "ACTIVE").containsEntry(WATER, "ACTIVE")
				.containsEntry(RENT, "ENDED").containsEntry(YOGA, "CANCELLED");
	}

	@Test
	@DisplayName("An unknown status value is rejected with 400")
	void unknownStatusIsRejected() throws Exception {
		HttpResponse<String> response = support.api().get(SERIES + "?status=BOGUS",
				owner.access(), owner.refresh());

		assertThat(response.statusCode()).as("status of " + response.body()).isEqualTo(400);
	}

	// ---------------------------------------------------------------- search

	@Test
	@DisplayName("q=gy matches only Gym (case-insensitive contains)")
	void searchMatchesCaseInsensitiveContains() throws Exception {
		assertThat(names(list("q=gy"))).containsExactly(GYM);
	}

	@Test
	@DisplayName("q is trimmed before matching: q=\"  GY \" matches only Gym")
	void searchIsTrimmed() throws Exception {
		assertThat(names(list("q=%20%20GY%20"))).containsExactly(GYM);
	}

	@Test
	@DisplayName("Search applies to every status filter: q=gy with status=ALL matches only Gym")
	void searchAppliesToAllStatus() throws Exception {
		assertThat(names(list("status=ALL&q=gy"))).containsExactly(GYM);
	}

	@Test
	@DisplayName("A search with no matching name returns an empty list")
	void searchWithoutMatchReturnsEmptyList() throws Exception {
		assertThat(list("q=zzzz-no-such-series").size()).isZero();
	}

	// ---------------------------------------------------------------- GET /api/series/{id}

	@Test
	@DisplayName("GET /api/series/{id} returns the same DTO as the list entry for every status")
	void detailMatchesListEntry() throws Exception {
		JsonNode all = list("status=ALL");

		for (String id : List.of(gymId, rentId, yogaId, waterId)) {
			JsonNode listEntry = byId(all, id);
			assertThat(listEntry).as("list entry for " + id).isNotNull();
			JsonNode detail = support.api().getJsonOk(SERIES + "/" + id, owner.access(), owner.refresh());
			assertThat(detail).as("detail of " + listEntry.path("name").asString()).isEqualTo(listEntry);
		}
	}

	@Test
	@DisplayName("GET /api/series/{id} for an active series carries its derived fields and template fields")
	void detailOfActiveSeriesCarriesDerivedFields() throws Exception {
		JsonNode gym = support.api().getJsonOk(SERIES + "/" + gymId, owner.access(), owner.refresh());

		assertThat(gym.path("id").asString()).isEqualTo(gymId);
		assertThat(gym.path("name").asString()).isEqualTo(GYM);
		assertThat(gym.path("status").asString()).isEqualTo("ACTIVE");
		assertThat(gym.path("ruleSummary").asString()).isEqualTo("Weekly: Mon, Wed, Fri");
		assertThat(gym.path("endsSummary").asString()).isEqualTo("Never");
		Upcoming next = upcoming(GYM, gymId);
		assertThat(gym.path("nextOccurrence").asString()).isEqualTo(next.date().toString());
		assertThat(gym.path("nextOccurrenceTodoId").asString()).isEqualTo(next.todoId());
	}

	@Test
	@DisplayName("GET /api/series/{id} for an unknown id returns 404 \"Series not found\"")
	void detailOfRandomIdIsNotFound() throws Exception {
		HttpResponse<String> response = support.api().get(SERIES + "/" + UUID.randomUUID(),
				owner.access(), owner.refresh());

		assertNotFound(response);
	}

	@Test
	@DisplayName("GET /api/series/{id} for another user's series returns 404 \"Series not found\"")
	void detailOfAnotherUsersSeriesIsNotFound() throws Exception {
		Session other = support.registerUser();

		HttpResponse<String> response = support.api().get(SERIES + "/" + gymId, other.access(), other.refresh());

		assertNotFound(response);
	}

	// ---------------------------------------------------------------- ownership and authentication

	@Test
	@DisplayName("Another user's list does not contain the owner's series, for every status")
	void anotherUsersListIsEmptyOfTheseSeries() throws Exception {
		Session other = support.registerUser();

		for (String query : List.of(null, "status=ENDED", "status=CANCELLED", "status=ALL", "q=gy")) {
			assertThat(list(other, query).size()).as("list of another user for " + query).isZero();
		}
	}

	@Test
	@DisplayName("GET /api/series without a session returns 401")
	void listWithoutSessionIsUnauthorized() throws Exception {
		HttpResponse<String> response = support.api().get(SERIES);

		assertThat(response.statusCode()).as("status of " + response.body()).isEqualTo(401);
	}

	@Test
	@DisplayName("GET /api/series/{id} without a session returns 401")
	void detailWithoutSessionIsUnauthorized() throws Exception {
		HttpResponse<String> response = support.api().get(SERIES + "/" + gymId);

		assertThat(response.statusCode()).as("status of " + response.body()).isEqualTo(401);
	}

	// ---------------------------------------------------------------- helpers

	/** Monthly rule on the given day of the month, starting at the given date, never ending. */
	private static Map<String, Object> monthly(LocalDate start, int dayOfMonth) {
		Map<String, Object> rule = new LinkedHashMap<>();
		rule.put("frequency", "MONTHLY");
		rule.put("intervalDays", null);
		rule.put("weekdays", null);
		rule.put("monthDays", List.of(dayOfMonth));
		rule.put("startDate", start.toString());
		rule.put("endType", "NEVER");
		rule.put("endCount", null);
		rule.put("endDate", null);
		return rule;
	}

	/** POST /api/series for the owner, asserting 201, and returns the new id. */
	private String createSeries(String body) throws Exception {
		return support.createSeries(owner, body).path("id").asString();
	}

	private void createSeriesFor(Session session, String body) throws Exception {
		support.createSeries(session, body);
	}

	/** GET /api/series with an optional query string, as the owner. Asserts 200 and an array body. */
	private JsonNode list(String query) throws Exception {
		return list(owner, query);
	}

	private JsonNode list(Session session, String query) throws Exception {
		String path = query == null ? SERIES : SERIES + "?" + query;
		JsonNode body = support.api().getJsonOk(path, session.access(), session.refresh());
		assertThat(body.isArray()).as("body of GET " + path + ": " + body).isTrue();
		return body;
	}

	private static List<String> names(JsonNode list) {
		List<String> names = new ArrayList<>();
		for (JsonNode series : list) {
			names.add(series.path("name").asString());
		}
		return names;
	}

	/** The list entries keyed by name. Asserts that every name is unique. */
	private static Map<String, JsonNode> byName(JsonNode list) {
		Map<String, JsonNode> byName = new LinkedHashMap<>();
		for (JsonNode series : list) {
			String name = series.path("name").asString();
			assertThat(byName).as("duplicate name " + name).doesNotContainKey(name);
			byName.put(name, series);
		}
		return byName;
	}

	private static JsonNode byId(JsonNode list, String id) {
		for (JsonNode series : list) {
			if (id.equals(series.path("id").asString())) {
				return series;
			}
		}
		return null;
	}

	/** The expected order of the two active series of the owner: by next occurrence, then by name. */
	private List<String> expectedActiveOrder() throws Exception {
		return Stream.of(upcoming(GYM, gymId), upcoming(WATER, waterId))
				.sorted(Comparator.comparing(Upcoming::date).thenComparing(Upcoming::name))
				.map(Upcoming::name)
				.toList();
	}

	/** The earliest row of the series that is dated today or later, from GET /api/todos. */
	private Upcoming upcoming(String name, String seriesId) throws Exception {
		List<JsonNode> rows = support.rows(owner, today, today.plusDays(30));
		JsonNode earliest = null;
		LocalDate earliestDate = null;
		for (JsonNode row : rows) {
			if (!seriesId.equals(textOrNull(row, "seriesId"))) {
				continue;
			}
			LocalDate date = LocalDate.parse(row.path("dueDate").asString());
			if (date.isBefore(today)) {
				continue;
			}
			if (earliest == null || date.isBefore(earliestDate)) {
				earliest = row;
				earliestDate = date;
			}
		}
		assertThat(earliest).as("a row of " + name + " dated today or later").isNotNull();
		return new Upcoming(name, seriesId, earliestDate, earliest.path("id").asString());
	}

	private static String textOrNull(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return value == null || value.isNull() ? null : value.asString();
	}

	private static void assertJsonNull(JsonNode node, String field) {
		assertThat(node.has(field)).as(field + " is present in " + node).isTrue();
		assertThat(node.get(field).isNull()).as(field + " is null in " + node).isTrue();
	}

	private static void assertNotFound(HttpResponse<String> response) {
		assertThat(response.statusCode()).as("status of " + response.body()).isEqualTo(404);
		assertThat(response.body()).as("404 body").contains(NOT_FOUND_MESSAGE);
	}
}
