package com.ntamasa.markitdone.blackbox;

import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static com.ntamasa.markitdone.blackbox.ApiClient.json;
import static com.ntamasa.markitdone.blackbox.SeriesTestSupport.TODOS;
import static com.ntamasa.markitdone.blackbox.SeriesTestSupport.endingAfter;
import static com.ntamasa.markitdone.blackbox.SeriesTestSupport.everyNDays;
import static com.ntamasa.markitdone.blackbox.SeriesTestSupport.expectedNames;
import static com.ntamasa.markitdone.blackbox.SeriesTestSupport.horizon;
import static com.ntamasa.markitdone.blackbox.SeriesTestSupport.nextMonday;
import static com.ntamasa.markitdone.blackbox.SeriesTestSupport.seriesBody;
import static com.ntamasa.markitdone.blackbox.SeriesTestSupport.todoBody;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Black-box tests for deleting one occurrence of a recurring todo (MAR-83): DELETE /api/todos/{id}.
 * Each test registers its own user in Europe/Budapest. D is the next Monday after today.
 * A deleted occurrence must stay deleted while its series keeps repeating.
 * Only HTTP requests, status codes and JSON bodies are used; audit rows and the series status are not checked.
 */
@DisplayName("DELETE /api/todos/{id} on a recurring occurrence (MAR-83)")
class OccurrenceDeleteIT extends AbstractIntegrationTest {

	private static final String ORIGINAL = "Daily walk";
	private static final String RENAMED = "Renamed walk";

	/** A series created for one test user. Offset n is the occurrence on D + n. */
	private record Series(SeriesTestSupport support, TestAccounts.Session session, String seriesId, LocalDate first) {

		LocalDate day(int offset) {
			return first.plusDays(offset);
		}

		String idOn(int offset) throws Exception {
			return support.rowOn(session, day(offset)).path("id").asString();
		}
	}

	/** Registers a user and creates a series with the given rule, starting on D. */
	private Series createSeries(Map<String, Object> rule) throws Exception {
		SeriesTestSupport support = new SeriesTestSupport(baseUrl());
		TestAccounts.Session session = support.registerUser();
		LocalDate first = nextMonday();
		JsonNode series = support.createSeries(session, seriesBody(ORIGINAL, rule));
		return new Series(support, session, series.path("id").asString(), first);
	}

	/** A daily, never-ending series starting on D. */
	private Series createDaily() throws Exception {
		return createSeries(everyNDays(1, nextMonday()));
	}

	/** The expected names from D to the horizon, with the given dates removed (they were deleted). */
	private static Map<LocalDate, String> withoutDates(Series s, String name, LocalDate... removed) {
		TreeMap<LocalDate, String> expected = new TreeMap<>(expectedNames(s.first(), horizon(), name, Map.of()));
		for (LocalDate date : removed) {
			expected.remove(date);
		}
		return expected;
	}

	private static String todoPath(String id) {
		return TODOS + "/" + id;
	}

	// ---------------------------------------------------------------- basic delete

	@Test
	@DisplayName("DELETE of D+2 in a daily NEVER series returns 204; D+2 is gone and every other day is unchanged")
	void deleteOccurrenceReturns204AndKeepsOthers() throws Exception {
		Series s = createDaily();
		String id = s.idOn(2);

		HttpResponse<String> response = s.support().api().deleteWithCsrf(todoPath(id),
				s.session().access(), s.session().refresh());

		assertThat(response.statusCode()).as("DELETE status, body: " + response.body()).isEqualTo(204);
		assertThat(response.body()).isEmpty();
		assertThat(s.support().rows(s.session(), s.day(2), s.day(2)))
				.as("rows left on D+2")
				.isEmpty();
		assertThat(s.support().namesByDate(s.session(), s.first(), horizon()))
				.as("names of every remaining occurrence")
				.containsExactlyEntriesOf(withoutDates(s, ORIGINAL, s.day(2)));
	}

	@Test
	@DisplayName("GET of the deleted occurrence by id returns 404")
	void getDeletedOccurrenceReturns404() throws Exception {
		Series s = createDaily();
		String id = s.idOn(2);
		assertThat(s.support().api().deleteWithCsrf(todoPath(id), s.session().access(), s.session().refresh())
				.statusCode()).isEqualTo(204);

		HttpResponse<String> after = s.support().api().get(todoPath(id), s.session().access(), s.session().refresh());

		assertThat(after.statusCode()).as("GET after occurrence delete, body: " + after.body()).isEqualTo(404);
	}

	@Test
	@DisplayName("Deleting the same occurrence twice: the second DELETE returns 404")
	void deleteSameOccurrenceTwiceReturns404() throws Exception {
		Series s = createDaily();
		String id = s.idOn(2);
		assertThat(s.support().api().deleteWithCsrf(todoPath(id), s.session().access(), s.session().refresh())
				.statusCode()).isEqualTo(204);

		HttpResponse<String> again = s.support().api().deleteWithCsrf(todoPath(id),
				s.session().access(), s.session().refresh());

		assertThat(again.statusCode()).as("second DELETE status, body: " + again.body()).isEqualTo(404);
		assertThat(s.support().rows(s.session(), s.day(2), s.day(2))).as("rows left on D+2").isEmpty();
	}

	// ---------------------------------------------------------------- re-materialisation guard via later edits

	@Test
	@DisplayName("FUTURE rename on D with the same rule keeps D+2 deleted and renames every other occurrence")
	void futureSameRuleEditDoesNotBringBackDeletedOccurrence() throws Exception {
		Series s = createDaily();
		assertThat(s.support().api().deleteWithCsrf(todoPath(s.idOn(2)), s.session().access(), s.session().refresh())
				.statusCode()).isEqualTo(204);

		HttpResponse<String> response = s.support().edit(s.session(), s.idOn(0), "FUTURE",
				todoBody(RENAMED, s.day(0), everyNDays(1, s.day(0))));

		assertThat(response.statusCode()).as("FUTURE PUT status, body: " + response.body()).isEqualTo(200);
		assertThat(json(response).path("name").asString()).isEqualTo(RENAMED);
		assertThat(s.support().rows(s.session(), s.day(2), s.day(2)))
				.as("rows on D+2 after the FUTURE edit")
				.isEmpty();
		assertThat(s.support().namesByDate(s.session(), s.first(), horizon()))
				.as("names of every remaining occurrence")
				.containsExactlyEntriesOf(withoutDates(s, RENAMED, s.day(2)));
	}

	@Test
	@DisplayName("THIS rename of D+5 does not bring back the deleted D+2")
	void thisEditElsewhereDoesNotBringBackDeletedOccurrence() throws Exception {
		Series s = createDaily();
		assertThat(s.support().api().deleteWithCsrf(todoPath(s.idOn(2)), s.session().access(), s.session().refresh())
				.statusCode()).isEqualTo(204);

		HttpResponse<String> response = s.support().edit(s.session(), s.idOn(5), "THIS",
				todoBody("Edited", s.day(5)));

		assertThat(response.statusCode()).as("THIS PUT status, body: " + response.body()).isEqualTo(200);
		assertThat(s.support().rows(s.session(), s.day(2), s.day(2)))
				.as("rows on D+2 after the THIS edit")
				.isEmpty();
		Map<LocalDate, String> expected = withoutDates(s, ORIGINAL, s.day(2));
		expected.put(s.day(5), "Edited");
		assertThat(s.support().namesByDate(s.session(), s.first(), horizon()))
				.as("names of every remaining occurrence")
				.containsExactlyEntriesOf(expected);
	}

	// ---------------------------------------------------------------- "After N" counting

	@Test
	@DisplayName("Daily AFTER_COUNT 5 from D: deleting D+1 leaves exactly D, D+2, D+3, D+4 and the last is D+4")
	void deleteInAfterCountSeriesKeepsSeriesEndDate() throws Exception {
		Series s = createSeries(endingAfter(everyNDays(1, nextMonday()), 5));
		String id = s.idOn(1);

		HttpResponse<String> response = s.support().api().deleteWithCsrf(todoPath(id),
				s.session().access(), s.session().refresh());

		assertThat(response.statusCode()).as("DELETE status, body: " + response.body()).isEqualTo(204);
		Map<LocalDate, String> names = s.support().namesByDate(s.session(), s.first(), horizon());
		assertThat(names.keySet())
				.as("remaining occurrence dates")
				.containsExactly(s.day(0), s.day(2), s.day(3), s.day(4));
		Map<LocalDate, String> expected = new TreeMap<>();
		for (int offset : List.of(0, 2, 3, 4)) {
			expected.put(s.day(offset), ORIGINAL);
		}
		assertThat(names).as("names of the remaining occurrences").containsExactlyEntriesOf(expected);
		assertThat(new TreeMap<>(names).lastKey()).as("last remaining occurrence").isEqualTo(s.day(4));
	}

	// ---------------------------------------------------------------- isolation

	@Test
	@DisplayName("DELETE of another user's occurrence returns 404 and the owner keeps the row")
	void deleteOtherUsersOccurrenceReturns404() throws Exception {
		Series owner = createDaily();
		String id = owner.idOn(2);
		SeriesTestSupport intruderSupport = new SeriesTestSupport(baseUrl());
		TestAccounts.Session intruder = intruderSupport.registerUser();

		HttpResponse<String> response = intruderSupport.api().deleteWithCsrf(todoPath(id),
				intruder.access(), intruder.refresh());

		assertThat(response.statusCode()).as("DELETE status, body: " + response.body()).isEqualTo(404);
		JsonNode kept = owner.support().rowOn(owner.session(), owner.day(2));
		assertThat(kept.path("id").asString()).isEqualTo(id);
		assertThat(kept.path("name").asString()).isEqualTo(ORIGINAL);
	}

	// ---------------------------------------------------------------- one-off

	@Test
	@DisplayName("DELETE of a one-off todo still returns 204 and the todo is then 404")
	void deleteOneOffStillWorks() throws Exception {
		SeriesTestSupport support = new SeriesTestSupport(baseUrl());
		TestAccounts.Session session = support.registerUser();
		String id = support.createOneOff(session, "One-off task", nextMonday()).path("id").asString();

		HttpResponse<String> response = support.api().deleteWithCsrf(todoPath(id), session.access(), session.refresh());

		assertThat(response.statusCode()).as("DELETE status, body: " + response.body()).isEqualTo(204);
		HttpResponse<String> after = support.api().get(todoPath(id), session.access(), session.refresh());
		assertThat(after.statusCode()).as("GET after one-off delete").isEqualTo(404);
	}

	// ---------------------------------------------------------------- CSRF

	@Test
	@DisplayName("DELETE of an occurrence without a CSRF token returns 403 and keeps the occurrence")
	void deleteOccurrenceWithoutCsrfReturns403() throws Exception {
		Series s = createDaily();
		String id = s.idOn(2);

		HttpResponse<String> response = s.support().api().delete(todoPath(id),
				s.session().access(), s.session().refresh());

		assertThat(response.statusCode()).as("DELETE status without CSRF token").isEqualTo(403);
		JsonNode kept = s.support().rowOn(s.session(), s.day(2));
		assertThat(kept.path("id").asString()).isEqualTo(id);
	}
}
