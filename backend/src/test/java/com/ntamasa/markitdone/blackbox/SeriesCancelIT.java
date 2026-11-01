package com.ntamasa.markitdone.blackbox;

import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static com.ntamasa.markitdone.blackbox.ApiClient.json;
import static com.ntamasa.markitdone.blackbox.SeriesTestSupport.SERIES;
import static com.ntamasa.markitdone.blackbox.SeriesTestSupport.everyNDays;
import static com.ntamasa.markitdone.blackbox.SeriesTestSupport.seriesBody;
import static com.ntamasa.markitdone.blackbox.SeriesTestSupport.todoBody;
import static com.ntamasa.markitdone.blackbox.TestAccounts.PASSWORD;
import static com.ntamasa.markitdone.blackbox.TestAccounts.Session;
import static com.ntamasa.markitdone.blackbox.TestAccounts.canonicalErrorBody;
import static com.ntamasa.markitdone.blackbox.TestAccounts.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Black-box tests for cancelling a recurring series (MAR-84):
 * POST /api/series/{id}/cancel and GET /api/series/{id}/future-count.
 * Each test registers its own user in Pacific/Auckland, so "today" there can differ from UTC.
 * The series is a daily, never-ending series that starts yesterday (Auckland), so it has rows for
 * yesterday, today and every later day up to the rolling horizon.
 * Only HTTP requests, status codes and JSON bodies are used; audit rows and the nightly job are not checked.
 */
@DisplayName("POST /api/series/{id}/cancel and GET /api/series/{id}/future-count (MAR-84)")
class SeriesCancelIT extends AbstractIntegrationTest {

	private static final String AUCKLAND = "Pacific/Auckland";
	private static final ZoneId ZONE = ZoneId.of(AUCKLAND);
	private static final String NAME = "Daily stretch";
	private static final String NOT_FOUND_MESSAGE = "Series not found";

	/** A daily series of one test user. Its rows run from yesterday to the horizon. */
	private record Fixture(SeriesTestSupport support, Session session, String seriesId, LocalDate yesterday,
			LocalDate today) {

		LocalDate horizon() {
			return today.plusMonths(3);
		}

		String todayRowId() throws Exception {
			return support.rowOn(session, today).path("id").asString();
		}
	}

	/** Registers an Auckland user and creates the daily series starting yesterday (Auckland). */
	private Fixture createFixture() throws Exception {
		SeriesTestSupport support = new SeriesTestSupport(baseUrl());
		Session session = TestAccounts.registerExpectingCreated(baseUrl(),
				TestAccounts.registerBody("Auckland User", uniqueEmail(), PASSWORD, AUCKLAND));
		LocalDate today = LocalDate.now(ZONE);
		LocalDate yesterday = today.minusDays(1);
		JsonNode series = support.createSeries(session, seriesBody(NAME, everyNDays(1, yesterday)));
		return new Fixture(support, session, series.path("id").asString(), yesterday, today);
	}

	private static String cancelPath(String seriesId) {
		return SERIES + "/" + seriesId + "/cancel";
	}

	private static String futureCountPath(String seriesId) {
		return SERIES + "/" + seriesId + "/future-count";
	}

	/** POST /api/series/{id}/cancel with a CSRF token, as the given session. */
	private HttpResponse<String> cancel(Fixture f, String seriesId) throws Exception {
		return f.support().api().postWithCsrf(cancelPath(seriesId), f.session().access(), f.session().refresh());
	}

	/** GET /api/series/{id}/future-count as the given session, asserting 200. Returns the count. */
	private long futureCount(Fixture f, String seriesId) throws Exception {
		HttpResponse<String> response = f.support().api().get(futureCountPath(seriesId),
				f.session().access(), f.session().refresh());
		assertThat(response.statusCode()).as("GET future-count status, body: " + response.body()).isEqualTo(200);
		JsonNode body = json(response);
		assertThat(body.has("count")).as("future-count body has count: " + response.body()).isTrue();
		return body.path("count").asLong();
	}

	/** Number of rows of the user's todo list dated after today, up to the horizon. */
	private static long rowsAfterToday(Fixture f) throws Exception {
		return f.support().rows(f.session(), f.today().plusDays(1), f.horizon()).size();
	}

	// ---------------------------------------------------------------- cancel: main case

	@Test
	@DisplayName("Cancel keeps yesterday and today (today still done), removes every later row, and returns CANCELLED with cancelledOn = today (Auckland)")
	void cancelKeepsHistoryAndRemovesLaterRows() throws Exception {
		Fixture f = createFixture();
		SeriesTestSupport support = f.support();
		assertThat(support.api().patchJsonWithCsrf("/api/todos/" + f.todayRowId() + "/done", "{\"done\":true}",
				f.session().access(), f.session().refresh()).statusCode()).isEqualTo(200);
		long expectedRemoved = rowsAfterToday(f);
		assertThat(expectedRemoved).as("rows after today before the cancel").isPositive();
		assertThat(futureCount(f, f.seriesId())).as("future-count before the cancel").isEqualTo(expectedRemoved);

		HttpResponse<String> response = cancel(f, f.seriesId());

		assertThat(response.statusCode()).as("POST cancel status, body: " + response.body()).isEqualTo(200);
		JsonNode dto = json(response);
		assertThat(dto.path("id").asString()).isEqualTo(f.seriesId());
		assertThat(dto.path("status").asString()).isEqualTo("CANCELLED");
		assertThat(dto.path("cancelledOn").asString()).isEqualTo(f.today().toString());

		assertThat(support.rows(f.session(), f.yesterday(), f.yesterday())).as("yesterday's row").hasSize(1);
		JsonNode today = support.rowOn(f.session(), f.today());
		assertThat(today.path("done").asBoolean()).as("today's row keeps its done state").isTrue();
		assertThat(support.rows(f.session(), f.today().plusDays(1), f.horizon()))
				.as("rows after today after the cancel").isEmpty();
	}

	@Test
	@DisplayName("future-count after a cancel is 0")
	void futureCountAfterCancelIsZero() throws Exception {
		Fixture f = createFixture();
		assertThat(futureCount(f, f.seriesId())).isPositive();
		assertThat(cancel(f, f.seriesId()).statusCode()).isEqualTo(200);

		assertThat(futureCount(f, f.seriesId())).as("future-count after cancel").isZero();
	}

	@Test
	@DisplayName("A second cancel returns 200 with a body identical to the first one")
	void secondCancelReturnsIdenticalBody() throws Exception {
		Fixture f = createFixture();
		HttpResponse<String> first = cancel(f, f.seriesId());
		assertThat(first.statusCode()).as("first cancel status, body: " + first.body()).isEqualTo(200);

		HttpResponse<String> second = cancel(f, f.seriesId());

		assertThat(second.statusCode()).as("second cancel status, body: " + second.body()).isEqualTo(200);
		assertThat(json(second)).as("second cancel body").isEqualTo(json(first));
		assertThat(futureCount(f, f.seriesId())).isZero();
	}

	@Test
	@DisplayName("After a cancel, today's remaining occurrence can still be edited with scope THIS")
	void todayOccurrenceEditableAfterCancel() throws Exception {
		Fixture f = createFixture();
		String todayId = f.todayRowId();
		assertThat(cancel(f, f.seriesId()).statusCode()).isEqualTo(200);

		HttpResponse<String> response = f.support().edit(f.session(), todayId, "THIS",
				todoBody("Renamed stretch", f.today()));

		assertThat(response.statusCode()).as("PUT THIS status, body: " + response.body()).isEqualTo(200);
		assertThat(json(response).path("name").asString()).isEqualTo("Renamed stretch");
		assertThat(f.support().rowOn(f.session(), f.today()).path("name").asString())
				.isEqualTo("Renamed stretch");
	}

	// ---------------------------------------------------------------- access control

	@Test
	@DisplayName("Another user's series returns 404 'Series not found' for cancel and future-count; the owner's rows are untouched")
	void otherUsersSeriesReturns404() throws Exception {
		Fixture owner = createFixture();
		Session intruder = TestAccounts.register(baseUrl(), uniqueEmail());
		long before = futureCount(owner, owner.seriesId());
		assertThat(before).isPositive();

		HttpResponse<String> cancelResponse = owner.support().api().postWithCsrf(
				cancelPath(owner.seriesId()), intruder.access(), intruder.refresh());
		HttpResponse<String> countResponse = owner.support().api().get(
				futureCountPath(owner.seriesId()), intruder.access(), intruder.refresh());

		assertThat(cancelResponse.statusCode()).as("cancel status, body: " + cancelResponse.body()).isEqualTo(404);
		assertThat(cancelResponse.body()).contains(NOT_FOUND_MESSAGE);
		assertThat(countResponse.statusCode()).as("future-count status, body: " + countResponse.body()).isEqualTo(404);
		assertThat(countResponse.body()).contains(NOT_FOUND_MESSAGE);
		assertThat(futureCount(owner, owner.seriesId())).as("owner's future-count").isEqualTo(before);
	}

	@Test
	@DisplayName("Cancel and future-count on another user's series and on a random UUID return identical 404 bodies")
	void notFoundBodiesDoNotRevealOwnership() throws Exception {
		Fixture owner = createFixture();
		Session intruder = TestAccounts.register(baseUrl(), uniqueEmail());
		String random = UUID.randomUUID().toString();
		ApiClient api = owner.support().api();

		HttpResponse<String> foreignCancel = api.postWithCsrf(cancelPath(owner.seriesId()),
				intruder.access(), intruder.refresh());
		HttpResponse<String> randomCancel = api.postWithCsrf(cancelPath(random), intruder.access(), intruder.refresh());
		HttpResponse<String> foreignCount = api.get(futureCountPath(owner.seriesId()),
				intruder.access(), intruder.refresh());
		HttpResponse<String> randomCount = api.get(futureCountPath(random), intruder.access(), intruder.refresh());

		assertThat(foreignCancel.statusCode()).isEqualTo(404);
		assertThat(randomCancel.statusCode()).isEqualTo(404);
		assertThat(foreignCount.statusCode()).isEqualTo(404);
		assertThat(randomCount.statusCode()).isEqualTo(404);
		assertThat(canonicalErrorBody(foreignCancel)).isEqualTo(canonicalErrorBody(randomCancel));
		assertThat(canonicalErrorBody(foreignCount)).isEqualTo(canonicalErrorBody(randomCount));
	}

	@Test
	@DisplayName("Cancel and future-count without a session return 401")
	void unauthenticatedReturns401() throws Exception {
		Fixture f = createFixture();
		long before = futureCount(f, f.seriesId());

		HttpResponse<String> cancelResponse = f.support().api().postWithCsrf(cancelPath(f.seriesId()));
		HttpResponse<String> countResponse = f.support().api().get(futureCountPath(f.seriesId()));

		assertThat(cancelResponse.statusCode()).as("cancel without session").isEqualTo(401);
		assertThat(countResponse.statusCode()).as("future-count without session").isEqualTo(401);
		assertThat(futureCount(f, f.seriesId())).as("future-count after the rejected cancel").isEqualTo(before);
	}

	@Test
	@DisplayName("Cancel without a CSRF token returns 403 and changes nothing")
	void cancelWithoutCsrfReturns403() throws Exception {
		Fixture f = createFixture();
		long before = futureCount(f, f.seriesId());

		HttpResponse<String> response = f.support().api().postJson(cancelPath(f.seriesId()), "{}",
				f.session().access(), f.session().refresh());

		assertThat(response.statusCode()).as("cancel without CSRF token, body: " + response.body()).isEqualTo(403);
		assertThat(futureCount(f, f.seriesId())).as("future-count after the rejected cancel").isEqualTo(before);
	}
}
