package com.ntamasa.markitdone.blackbox;

import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static com.ntamasa.markitdone.blackbox.ApiClient.assertProblem;
import static com.ntamasa.markitdone.blackbox.ApiClient.json;
import static com.ntamasa.markitdone.blackbox.SeriesTestSupport.everyNDays;
import static com.ntamasa.markitdone.blackbox.SeriesTestSupport.everyNthDay;
import static com.ntamasa.markitdone.blackbox.SeriesTestSupport.expectedNames;
import static com.ntamasa.markitdone.blackbox.SeriesTestSupport.datesBetween;
import static com.ntamasa.markitdone.blackbox.SeriesTestSupport.endingAfter;
import static com.ntamasa.markitdone.blackbox.SeriesTestSupport.horizon;
import static com.ntamasa.markitdone.blackbox.SeriesTestSupport.mondays;
import static com.ntamasa.markitdone.blackbox.SeriesTestSupport.nextMonday;
import static com.ntamasa.markitdone.blackbox.SeriesTestSupport.seriesBody;
import static com.ntamasa.markitdone.blackbox.SeriesTestSupport.todoBody;
import static com.ntamasa.markitdone.blackbox.SeriesTestSupport.weekly;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Black-box tests for editing recurring occurrences (MAR-80): PUT /api/todos/{id}?scope=THIS|FUTURE.
 * Each test registers its own user in Europe/Budapest and creates a daily, never-ending series that starts
 * on the next Monday after today (D). The brief's Nov 2 .. Nov 10 map to D .. D+8.
 * Only HTTP requests, status codes and JSON bodies are used.
 */
@DisplayName("PUT /api/todos/{id}?scope=THIS|FUTURE")
class SeriesEditIT extends AbstractIntegrationTest {

	private static final String ORIGINAL = "Daily walk";

	/** A daily series created for one test user. Day n is the occurrence on D + n. */
	private record Daily(SeriesTestSupport support, TestAccounts.Session session, String seriesId, LocalDate first) {

		LocalDate day(int offset) {
			return first.plusDays(offset);
		}

		String idOn(int offset) throws Exception {
			return support.rowOn(session, day(offset)).path("id").asString();
		}
	}

	/** Registers a user and creates the daily series starting on D = the next Monday after today. */
	private Daily createDaily() throws Exception {
		SeriesTestSupport support = new SeriesTestSupport(baseUrl());
		TestAccounts.Session session = support.registerUser();
		LocalDate first = nextMonday();
		JsonNode series = support.createSeries(session, seriesBody(ORIGINAL, everyNDays(1, first)));
		return new Daily(support, session, series.path("id").asString(), first);
	}

	/** Every daily occurrence from D to the horizon, named ORIGINAL. */
	private static Map<LocalDate, String> allOriginal(Daily d) {
		return expectedNames(d.first(), horizon(), ORIGINAL, Map.of());
	}

	// ---------------------------------------------------------------- THIS

	@Test
	@DisplayName("THIS on D+2 renamed 'X' changes only that row and the response is its TodoDto")
	void thisRenameChangesOnlyThatRow() throws Exception {
		Daily d = createDaily();

		HttpResponse<String> response = d.support().edit(d.session(), d.idOn(2), "THIS",
				todoBody("X", d.day(2)));

		assertThat(response.statusCode()).as("PUT status, body: " + response.body()).isEqualTo(200);
		JsonNode updated = json(response);
		assertThat(updated.path("name").asString()).isEqualTo("X");
		assertThat(updated.path("dueDate").asString()).isEqualTo(d.day(2).toString());
		assertThat(updated.path("seriesId").asString()).isEqualTo(d.seriesId());

		Map<LocalDate, String> expected = expectedNames(d.first(), horizon(), ORIGINAL, Map.of(d.day(2), "X"));
		assertThat(d.support().namesByDate(d.session(), d.first(), horizon()))
				.as("names of every occurrence")
				.containsExactlyEntriesOf(expected);
	}

	@Test
	@DisplayName("THIS on D+6 'Z', then FUTURE on D+4 'Y': D+4, D+5, D+7.. are Y, D+6 stays Z, earlier rows unchanged")
	void futureAfterDetachedRowKeepsDetachedRow() throws Exception {
		Daily d = createDaily();
		assertThat(d.support().edit(d.session(), d.idOn(2), "THIS", todoBody("X", d.day(2))).statusCode())
				.isEqualTo(200);
		assertThat(d.support().edit(d.session(), d.idOn(6), "THIS", todoBody("Z", d.day(6))).statusCode())
				.isEqualTo(200);

		HttpResponse<String> response = d.support().edit(d.session(), d.idOn(4), "FUTURE",
				todoBody("Y", d.day(4)));

		assertThat(response.statusCode()).as("PUT status, body: " + response.body()).isEqualTo(200);
		assertThat(json(response).path("name").asString()).isEqualTo("Y");

		Map<LocalDate, String> expected = new TreeMap<>();
		for (LocalDate date : datesBetween(d.first(), horizon())) {
			String name;
			if (date.equals(d.day(2))) {
				name = "X";
			} else if (date.equals(d.day(6))) {
				name = "Z";
			} else if (!date.isBefore(d.day(4))) {
				name = "Y";
			} else {
				name = ORIGINAL;
			}
			expected.put(date, name);
		}
		assertThat(d.support().namesByDate(d.session(), d.first(), horizon()))
				.as("names of every occurrence")
				.containsExactlyEntriesOf(expected);
	}

	@Test
	@DisplayName("THIS can change the date of one occurrence: only that row moves")
	void thisChangesDateOfOneOccurrence() throws Exception {
		Daily d = createDaily();
		LocalDate newDate = d.day(150);

		HttpResponse<String> response = d.support().edit(d.session(), d.idOn(3), "THIS",
				todoBody(ORIGINAL, newDate));

		assertThat(response.statusCode()).as("PUT status, body: " + response.body()).isEqualTo(200);
		assertThat(json(response).path("dueDate").asString()).isEqualTo(newDate.toString());
		assertThat(d.support().rows(d.session(), d.day(3), d.day(3))).as("rows left on the old date").isEmpty();
		assertThat(d.support().rowOn(d.session(), newDate).path("name").asString()).isEqualTo(ORIGINAL);

		Map<LocalDate, String> expected = new TreeMap<>(allOriginal(d));
		expected.remove(d.day(3));
		assertThat(d.support().namesByDate(d.session(), d.first(), horizon()))
				.as("occurrences other than the moved one")
				.containsExactlyEntriesOf(expected);
	}

	// ---------------------------------------------------------------- FUTURE (same rule)

	@Test
	@DisplayName("FUTURE with a changed due date returns 400 and changes nothing")
	void futureWithChangedDateReturns400() throws Exception {
		Daily d = createDaily();

		HttpResponse<String> response = d.support().edit(d.session(), d.idOn(4), "FUTURE",
				todoBody("Moved", d.day(5)));

		assertProblem(response, 400, "BAD_REQUEST");
		assertThat(response.body()).contains("Change the date with 'Only this occurrence'");
		assertThat(d.support().namesByDate(d.session(), d.first(), horizon()))
				.containsExactlyEntriesOf(allOriginal(d));
	}

	// ---------------------------------------------------------------- FUTURE with a rule change

	@Test
	@DisplayName("FUTURE rule change on D+8 to weekly Mondays: 204, old future rows gone, detached row kept, new series from D+14")
	void futureRuleChangeSplitsSeries() throws Exception {
		Daily d = createDaily();
		assertThat(d.support().edit(d.session(), d.idOn(10), "THIS", todoBody("Detached", d.day(10))).statusCode())
				.isEqualTo(200);

		HttpResponse<String> response = d.support().edit(d.session(), d.idOn(8), "FUTURE",
				todoBody(ORIGINAL, d.day(8), weekly(d.day(8), List.of("MONDAY"))));

		assertThat(response.statusCode()).as("PUT status, body: " + response.body()).isEqualTo(204);
		assertThat(response.body()).isEmpty();

		// Occurrences before D+8 keep the old series.
		for (LocalDate date : datesBetween(d.day(0), d.day(7))) {
			JsonNode row = d.support().rowOn(d.session(), date);
			assertThat(row.path("seriesId").asString()).as("seriesId of " + date).isEqualTo(d.seriesId());
			assertThat(row.path("name").asString()).isEqualTo(ORIGINAL);
		}

		// The detached row stays with the old series and keeps its edit.
		JsonNode detached = d.support().rowOn(d.session(), d.day(10));
		assertThat(detached.path("name").asString()).isEqualTo("Detached");
		assertThat(detached.path("seriesId").asString()).isEqualTo(d.seriesId());

		// From D+8 on: the detached row, plus Mondays from D+14 that belong to one new series.
		Set<LocalDate> expectedDates = new TreeSet<>(mondays(d.day(14), horizon()));
		expectedDates.add(d.day(10));
		Set<LocalDate> actualDates = new TreeSet<>();
		String newSeriesId = null;
		for (JsonNode row : d.support().rows(d.session(), d.day(8), horizon())) {
			LocalDate date = LocalDate.parse(row.path("dueDate").asString());
			actualDates.add(date);
			if (date.equals(d.day(10))) {
				continue;
			}
			String seriesId = row.path("seriesId").asString();
			assertThat(seriesId).as("seriesId of " + date).isNotEqualTo(d.seriesId());
			if (newSeriesId == null) {
				newSeriesId = seriesId;
			}
			assertThat(seriesId).as("seriesId of " + date).isEqualTo(newSeriesId);
			assertThat(row.path("ruleSummary").asString()).as("ruleSummary of " + date).isEqualTo("Weekly: Mon");
		}
		assertThat(newSeriesId).as("a new series generated Mondays from D+14").isNotNull();
		assertThat(actualDates).as("dates from D+8 on").isEqualTo(expectedDates);
	}

	@Test
	@DisplayName("FUTURE rule change on the first occurrence (D) edits the same series in place, no new series")
	void futureRuleChangeOnFirstOccurrenceKeepsSeries() throws Exception {
		Daily d = createDaily();

		HttpResponse<String> response = d.support().edit(d.session(), d.idOn(0), "FUTURE",
				todoBody("Every other day", d.day(0), everyNDays(2, d.day(0))));

		assertThat(response.statusCode()).as("PUT status, body: " + response.body()).isEqualTo(200);
		JsonNode updated = json(response);
		assertThat(updated.path("seriesId").asString()).isEqualTo(d.seriesId());
		assertThat(updated.path("dueDate").asString()).isEqualTo(d.day(0).toString());
		assertThat(updated.path("name").asString()).isEqualTo("Every other day");

		List<LocalDate> expectedDates = everyNthDay(d.day(0), horizon(), 2);
		List<JsonNode> rows = d.support().rows(d.session(), d.first(), horizon());
		List<LocalDate> actualDates = new ArrayList<>();
		for (JsonNode row : rows) {
			LocalDate date = LocalDate.parse(row.path("dueDate").asString());
			actualDates.add(date);
			assertThat(row.path("seriesId").asString()).as("seriesId of " + date).isEqualTo(d.seriesId());
			assertThat(row.path("name").asString()).as("name of " + date).isEqualTo("Every other day");
			assertThat(row.path("ruleSummary").asString()).as("ruleSummary of " + date).isEqualTo("Every 2 days");
		}
		assertThat(actualDates).as("rows every 2 days from D, none in between").containsExactlyElementsOf(expectedDates);
	}

	@Test
	@DisplayName("FUTURE rule change whose start is before the occurrence date returns 400 and changes nothing")
	void futureRuleStartBeforeOccurrenceReturns400() throws Exception {
		Daily d = createDaily();

		HttpResponse<String> response = d.support().edit(d.session(), d.idOn(4), "FUTURE",
				todoBody(ORIGINAL, d.day(4), everyNDays(1, d.day(2))));

		assertThat(response.statusCode()).as("PUT status, body: " + response.body()).isEqualTo(400);
		assertThat(d.support().namesByDate(d.session(), d.first(), horizon()))
				.containsExactlyEntriesOf(allOriginal(d));
	}

	@Test
	@DisplayName("FUTURE with an invalid new rule returns 400 and changes nothing")
	void futureInvalidRuleReturns400() throws Exception {
		Daily d = createDaily();

		HttpResponse<String> noWeekdays = d.support().edit(d.session(), d.idOn(4), "FUTURE",
				todoBody(ORIGINAL, d.day(4), weekly(d.day(4), List.of())));
		HttpResponse<String> zeroCount = d.support().edit(d.session(), d.idOn(4), "FUTURE",
				todoBody(ORIGINAL, d.day(4), endingAfter(everyNDays(1, d.day(4)), 0)));

		assertThat(noWeekdays.statusCode()).as("weekly with no weekdays, body: " + noWeekdays.body()).isEqualTo(400);
		assertThat(zeroCount.statusCode()).as("end count 0, body: " + zeroCount.body()).isEqualTo(400);
		assertThat(d.support().namesByDate(d.session(), d.first(), horizon()))
				.containsExactlyEntriesOf(allOriginal(d));
	}

	// ---------------------------------------------------------------- scope rules

	@Test
	@DisplayName("An occurrence without a scope returns 400 'scope is required for recurring todos' and changes nothing")
	void occurrenceWithoutScopeReturns400() throws Exception {
		Daily d = createDaily();

		HttpResponse<String> response = d.support().edit(d.session(), d.idOn(2), null,
				todoBody("X", d.day(2)));

		assertProblem(response, 400, "BAD_REQUEST");
		assertThat(response.body()).contains("scope is required for recurring todos");
		assertThat(d.support().namesByDate(d.session(), d.first(), horizon()))
				.containsExactlyEntriesOf(allOriginal(d));
	}

	@Test
	@DisplayName("A one-off todo without a scope is updated normally")
	void oneOffWithoutScopeUpdatesNormally() throws Exception {
		SeriesTestSupport support = new SeriesTestSupport(baseUrl());
		TestAccounts.Session session = support.registerUser();
		LocalDate due = nextMonday().plusDays(2);
		String id = support.createOneOff(session, "Dentist", due).path("id").asString();

		HttpResponse<String> response = support.edit(session, id, null, todoBody("Dentist, moved", due));

		assertThat(response.statusCode()).as("PUT status, body: " + response.body()).isEqualTo(200);
		assertThat(json(response).path("name").asString()).isEqualTo("Dentist, moved");
		assertThat(support.rowOn(session, due).path("name").asString()).isEqualTo("Dentist, moved");
	}

	@Test
	@DisplayName("A one-off todo with scope THIS or FUTURE ignores the scope and is updated normally")
	void oneOffWithScopeUpdatesNormally() throws Exception {
		SeriesTestSupport support = new SeriesTestSupport(baseUrl());
		TestAccounts.Session session = support.registerUser();
		LocalDate due = nextMonday().plusDays(2);
		String id = support.createOneOff(session, "Dentist", due).path("id").asString();

		for (String scope : List.of("THIS", "FUTURE")) {
			String name = "Dentist " + scope.toLowerCase();
			HttpResponse<String> response = support.edit(session, id, scope, todoBody(name, due));

			assertThat(response.statusCode()).as(scope + " PUT status, body: " + response.body()).isEqualTo(200);
			assertThat(json(response).path("name").asString()).isEqualTo(name);
			assertThat(support.rowOn(session, due).path("name").asString()).isEqualTo(name);
		}
	}

	// ---------------------------------------------------------------- access control

	@Test
	@DisplayName("Another user's occurrence returns 404 for THIS and FUTURE and the owner's row is unchanged")
	void otherUsersOccurrenceReturns404() throws Exception {
		Daily d = createDaily();
		TestAccounts.Session other = d.support().registerUser();
		String id = d.idOn(2);

		HttpResponse<String> thisScope = d.support().edit(other, id, "THIS", todoBody("Hijacked", d.day(2)));
		HttpResponse<String> futureScope = d.support().edit(other, id, "FUTURE", todoBody("Hijacked", d.day(2)));

		assertThat(thisScope.statusCode()).as("THIS status, body: " + thisScope.body()).isEqualTo(404);
		assertThat(futureScope.statusCode()).as("FUTURE status, body: " + futureScope.body()).isEqualTo(404);
		assertThat(d.support().namesByDate(d.session(), d.first(), horizon()))
				.containsExactlyEntriesOf(allOriginal(d));
	}

	@Test
	@DisplayName("PUT with scope but without a CSRF token returns 403 and changes nothing")
	void updateWithoutCsrfReturns403() throws Exception {
		Daily d = createDaily();

		HttpResponse<String> response = d.support().editWithoutCsrf(d.session(), d.idOn(2), "THIS",
				todoBody("X", d.day(2)));

		assertThat(response.statusCode()).as("PUT status without CSRF, body: " + response.body()).isEqualTo(403);
		assertThat(d.support().namesByDate(d.session(), d.first(), horizon()))
				.containsExactlyEntriesOf(allOriginal(d));
	}
}
