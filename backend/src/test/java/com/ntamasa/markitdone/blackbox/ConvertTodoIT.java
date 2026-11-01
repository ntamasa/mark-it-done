package com.ntamasa.markitdone.blackbox;

import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static com.ntamasa.markitdone.blackbox.ApiClient.assertProblem;
import static com.ntamasa.markitdone.blackbox.ApiClient.json;
import static com.ntamasa.markitdone.blackbox.SeriesTestSupport.datesBetween;
import static com.ntamasa.markitdone.blackbox.SeriesTestSupport.horizon;
import static com.ntamasa.markitdone.blackbox.SeriesTestSupport.seriesBody;
import static com.ntamasa.markitdone.blackbox.SeriesTestSupport.weekly;
import static com.ntamasa.markitdone.blackbox.TestAccounts.Session;
import static com.ntamasa.markitdone.blackbox.TestAccounts.jsonBody;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Black-box tests for converting a one-off todo into a recurring series (MAR-85):
 * POST /api/todos/{id}/recurrence with a rule object (the same shape as the "rule" of POST /api/series).
 * The horizon is three months after today (Europe/Budapest), inclusive.
 * Only HTTP requests, status codes and JSON bodies are used; audit rows are not checked and the
 * detached flag is not exposed, so it is not asserted either.
 */
@DisplayName("POST /api/todos/{id}/recurrence (MAR-85)")
class ConvertTodoIT extends AbstractIntegrationTest {

	private static final String TODOS = "/api/todos";
	private static final String SERIES = "/api/series";
	private static final String ALREADY_RECURRING = "This todo is already recurring";
	private static final String START_BEFORE_TODO = "Start date cannot be before the todo's date";
	private static final String WEEKLY_MON_WED = "Weekly: Mon, Wed";
	private static final ObjectMapper JSON = new ObjectMapper();

	/** Every field of the SeriesDto contract. */
	private static final List<String> SERIES_FIELDS = List.of(
			"id", "name", "description", "dueTime", "priority", "categoryId", "rule", "ruleSummary", "status",
			"nextOccurrence", "nextOccurrenceTodoId", "endsSummary", "cancelledOn");

	// ---------------------------------------------------------------- helpers

	/** The next Wednesday/Tuesday/... strictly after today, in Europe/Budapest. */
	private static LocalDate nextWeekday(DayOfWeek day) {
		return SeriesTestSupport.today().with(TemporalAdjusters.next(day));
	}

	/** The dates from "from" to "to" (both inclusive) that fall on one of the given weekdays. */
	private static List<LocalDate> weekdaysBetween(LocalDate from, LocalDate to, DayOfWeek... days) {
		Set<DayOfWeek> wanted = Set.of(days);
		return datesBetween(from, to).stream().filter(d -> wanted.contains(d.getDayOfWeek())).toList();
	}

	private static List<LocalDate> dueDates(List<JsonNode> rows) {
		return rows.stream().map(row -> LocalDate.parse(row.path("dueDate").asString())).toList();
	}

	private static String ruleBody(Map<String, Object> rule) {
		return JSON.writeValueAsString(rule);
	}

	private static String todoPath(String id, String suffix) {
		return TODOS + "/" + id + suffix;
	}

	/** POST /api/todos for a one-off todo that must succeed with 201. Returns the TodoDto. */
	private static JsonNode createTodo(SeriesTestSupport support, Session session, String body) throws Exception {
		HttpResponse<String> response = support.api().postJsonWithCsrf(TODOS, body,
				session.access(), session.refresh());
		assertThat(response.statusCode()).as("POST " + TODOS + " status, body: " + response.body()).isEqualTo(201);
		return json(response);
	}

	/** Creates a category through POST /api/categories and returns its id. */
	private static String createCategory(SeriesTestSupport support, Session session) throws Exception {
		String name = "Cat-" + UUID.randomUUID().toString().substring(0, 8);
		HttpResponse<String> response = support.api().postJsonWithCsrf("/api/categories",
				jsonBody("name", name, "swatch", "TEAL"), session.access(), session.refresh());
		assertThat(response.statusCode()).as("POST /api/categories status, body: " + response.body()).isEqualTo(201);
		return json(response).path("id").asString();
	}

	/** POST /api/todos/{id}/recurrence with a rule object, with a CSRF token, as the given session. */
	private static HttpResponse<String> convert(SeriesTestSupport support, Session session, String todoId,
			String body) throws Exception {
		return support.api().postJsonWithCsrf(todoPath(todoId, "/recurrence"), body,
				session.access(), session.refresh());
	}

	/** GET /api/todos/{id} as the given session, asserting 200. */
	private static JsonNode getTodo(SeriesTestSupport support, Session session, String todoId) throws Exception {
		return support.api().getJsonOk(todoPath(todoId, ""), session.access(), session.refresh());
	}

	/** Creates a series with POST /api/series (must succeed with 201) and returns its id. */
	private static String createSeries(SeriesTestSupport support, Session session, String name,
			Map<String, Object> rule) throws Exception {
		return support.createSeries(session, seriesBody(name, rule)).path("id").asString();
	}

	// ---------------------------------------------------------------- conversion: fields and rows

	@Test
	@DisplayName("Converting a Wednesday one-off with weekly Mon/Wed returns 201 with the todo's fields and ruleSummary 'Weekly: Mon, Wed'")
	void convertWednesdayTodoReturnsSeriesFromItsFields() throws Exception {
		SeriesTestSupport support = new SeriesTestSupport(baseUrl());
		Session session = support.registerUser();
		LocalDate wednesday = nextWeekday(DayOfWeek.WEDNESDAY);
		String categoryId = createCategory(support, session);
		JsonNode todo = createTodo(support, session, jsonBody("name", "Standup", "description", "Bring notes",
				"dueDate", wednesday.toString(), "dueTime", "18:30", "priority", "HIGH", "categoryId", categoryId));
		String todoId = todo.path("id").asString();

		HttpResponse<String> response = convert(support, session, todoId,
				ruleBody(weekly(wednesday, List.of("MONDAY", "WEDNESDAY"))));

		assertThat(response.statusCode()).as("POST recurrence status, body: " + response.body()).isEqualTo(201);
		JsonNode series = json(response);
		assertThat(series.propertyNames()).as("SeriesDto fields").containsAll(SERIES_FIELDS);
		assertThat(series.path("id").asString()).isNotBlank();
		assertThat(series.path("name").asString()).isEqualTo("Standup");
		assertThat(series.path("description").asString()).isEqualTo("Bring notes");
		assertThat(series.path("dueTime").asString()).startsWith("18:30");
		assertThat(series.path("priority").asString()).isEqualTo("HIGH");
		assertThat(series.path("categoryId").asString()).isEqualTo(categoryId);
		assertThat(series.path("ruleSummary").asString()).isEqualTo(WEEKLY_MON_WED);
		assertThat(series.path("rule").path("frequency").asString()).isEqualTo("WEEKLY");
		assertThat(series.path("rule").path("startDate").asString()).isEqualTo(wednesday.toString());
		String seriesId = series.path("id").asString();

		JsonNode converted = getTodo(support, session, todoId);
		assertThat(converted.path("id").asString()).as("todo keeps its id").isEqualTo(todoId);
		assertThat(converted.path("seriesId").asString()).as("todo seriesId").isEqualTo(seriesId);
		assertThat(converted.path("occurrenceDate").asString()).as("occurrenceDate").isEqualTo(wednesday.toString());
		assertThat(converted.path("dueDate").asString()).isEqualTo(wednesday.toString());
		assertThat(converted.path("name").asString()).isEqualTo("Standup");
		assertThat(converted.path("ruleSummary").asString()).isEqualTo(WEEKLY_MON_WED);

		// Exactly one row on the todo's date, and it is the converted todo itself.
		assertThat(support.rowOn(session, wednesday).path("id").asString()).isEqualTo(todoId);

		// The rest of the series is generated: Mon W+5, Wed W+7, ... up to the horizon.
		List<JsonNode> later = support.rows(session, wednesday.plusDays(1), horizon());
		List<LocalDate> expected = weekdaysBetween(wednesday.plusDays(1), horizon(),
				DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);
		assertThat(dueDates(later)).as("generated due dates after the todo").containsExactlyElementsOf(expected);
		assertThat(expected.get(0)).isEqualTo(wednesday.plusDays(5));
		assertThat(expected.get(1)).isEqualTo(wednesday.plusDays(7));
		for (JsonNode row : later) {
			assertThat(row.path("seriesId").asString()).as("seriesId of " + row).isEqualTo(seriesId);
		}
	}

	@Test
	@DisplayName("Converting a Tuesday one-off with weekly Monday keeps the todo on Tuesday with the seriesId and generates the Mondays after it")
	void convertTuesdayTodoStaysOnItsDateAndMondaysAreGenerated() throws Exception {
		SeriesTestSupport support = new SeriesTestSupport(baseUrl());
		Session session = support.registerUser();
		LocalDate tuesday = nextWeekday(DayOfWeek.TUESDAY);
		JsonNode todo = createTodo(support, session, jsonBody("name", "Dentist",
				"dueDate", tuesday.toString(), "priority", "LOW"));
		String todoId = todo.path("id").asString();

		HttpResponse<String> response = convert(support, session, todoId,
				ruleBody(weekly(tuesday, List.of("MONDAY"))));

		assertThat(response.statusCode()).as("POST recurrence status, body: " + response.body()).isEqualTo(201);
		String seriesId = json(response).path("id").asString();

		JsonNode converted = getTodo(support, session, todoId);
		assertThat(converted.path("id").asString()).isEqualTo(todoId);
		assertThat(converted.path("dueDate").asString()).as("todo keeps its Tuesday date").isEqualTo(tuesday.toString());
		assertThat(converted.path("seriesId").asString()).as("todo seriesId").isEqualTo(seriesId);
		assertThat(converted.path("occurrenceDate").asString()).as("occurrenceDate").isEqualTo(tuesday.toString());

		// No extra row on Tuesday: the converted todo is the only row on that date.
		assertThat(support.rowOn(session, tuesday).path("id").asString()).isEqualTo(todoId);

		// Only Mondays are generated after the Tuesday; the first one is the day after the next Sunday.
		List<LocalDate> mondays = weekdaysBetween(tuesday.plusDays(1), horizon(), DayOfWeek.MONDAY);
		List<JsonNode> later = support.rows(session, tuesday.plusDays(1), horizon());
		assertThat(dueDates(later)).as("rows after the Tuesday").containsExactlyElementsOf(mondays);
		assertThat(mondays.get(0)).isEqualTo(tuesday.plusDays(6));
		for (JsonNode row : later) {
			assertThat(row.path("seriesId").asString()).as("seriesId of " + row).isEqualTo(seriesId);
		}
	}

	@Test
	@DisplayName("A done todo stays done after it is converted")
	void doneTodoStaysDoneAfterConversion() throws Exception {
		SeriesTestSupport support = new SeriesTestSupport(baseUrl());
		Session session = support.registerUser();
		LocalDate wednesday = nextWeekday(DayOfWeek.WEDNESDAY);
		String todoId = createTodo(support, session, jsonBody("name", "Report",
				"dueDate", wednesday.toString(), "dueTime", "09:00", "priority", "MEDIUM")).path("id").asString();
		assertThat(support.api().patchJsonWithCsrf(todoPath(todoId, "/done"), "{\"done\":true}",
				session.access(), session.refresh()).statusCode()).isEqualTo(200);

		HttpResponse<String> response = convert(support, session, todoId,
				ruleBody(weekly(wednesday, List.of("MONDAY", "WEDNESDAY"))));

		assertThat(response.statusCode()).as("POST recurrence status, body: " + response.body()).isEqualTo(201);
		String seriesId = json(response).path("id").asString();
		JsonNode converted = getTodo(support, session, todoId);
		assertThat(converted.path("done").asBoolean()).as("done after conversion").isTrue();
		assertThat(converted.path("seriesId").asString()).isEqualTo(seriesId);
		assertThat(support.rowOn(session, wednesday).path("done").asBoolean()).as("done in the list").isTrue();
	}

	// ---------------------------------------------------------------- errors

	@Test
	@DisplayName("Converting a todo that is already recurring returns 400 'This todo is already recurring' and changes nothing")
	void convertingTwiceReturns400() throws Exception {
		SeriesTestSupport support = new SeriesTestSupport(baseUrl());
		Session session = support.registerUser();
		LocalDate wednesday = nextWeekday(DayOfWeek.WEDNESDAY);
		String todoId = createTodo(support, session, jsonBody("name", "Yoga",
				"dueDate", wednesday.toString(), "priority", "LOW")).path("id").asString();
		String firstSeriesId = json(convert(support, session, todoId,
				ruleBody(weekly(wednesday, List.of("MONDAY", "WEDNESDAY"))))).path("id").asString();

		HttpResponse<String> second = convert(support, session, todoId,
				ruleBody(weekly(wednesday, List.of("FRIDAY"))));

		assertProblem(second, 400, "BAD_REQUEST");
		assertThat(second.body()).contains(ALREADY_RECURRING);
		assertThat(getTodo(support, session, todoId).path("seriesId").asString())
				.as("seriesId after the rejected second conversion").isEqualTo(firstSeriesId);
	}

	@Test
	@DisplayName("Converting an occurrence of an existing series returns 400 'This todo is already recurring'")
	void convertingAnOccurrenceReturns400() throws Exception {
		SeriesTestSupport support = new SeriesTestSupport(baseUrl());
		Session session = support.registerUser();
		LocalDate monday = SeriesTestSupport.nextMonday();
		String seriesId = createSeries(support, session, "Gym", weekly(monday, List.of("MONDAY")));
		JsonNode occurrence = support.rowOn(session, monday);
		String occurrenceId = occurrence.path("id").asString();
		assertThat(occurrence.path("seriesId").asString()).isEqualTo(seriesId);

		HttpResponse<String> response = convert(support, session, occurrenceId,
				ruleBody(weekly(monday, List.of("MONDAY"))));

		assertProblem(response, 400, "BAD_REQUEST");
		assertThat(response.body()).contains(ALREADY_RECURRING);
		assertThat(getTodo(support, session, occurrenceId).path("seriesId").asString())
				.as("occurrence keeps its series").isEqualTo(seriesId);
	}

	@Test
	@DisplayName("A rule that starts before the todo's date returns 400 'Start date cannot be before the todo's date' and creates nothing")
	void startBeforeTodoDateReturns400AndCreatesNothing() throws Exception {
		SeriesTestSupport support = new SeriesTestSupport(baseUrl());
		Session session = support.registerUser();
		LocalDate wednesday = nextWeekday(DayOfWeek.WEDNESDAY);
		String todoId = createTodo(support, session, jsonBody("name", "Early",
				"dueDate", wednesday.toString(), "priority", "LOW")).path("id").asString();

		HttpResponse<String> response = convert(support, session, todoId,
				ruleBody(weekly(wednesday.minusDays(1), List.of("MONDAY", "WEDNESDAY"))));

		assertProblem(response, 400, "VALIDATION_FAILED");
		assertThat(response.body()).contains(START_BEFORE_TODO);
		assertThat(getTodo(support, session, todoId).path("seriesId").isNull())
				.as("todo stays one-off").isTrue();
		assertThat(support.rowOn(session, wednesday).path("id").asString()).isEqualTo(todoId);
		assertThat(support.rows(session, wednesday.plusDays(1), horizon()))
				.as("no series rows after the todo").isEmpty();
	}

	@Test
	@DisplayName("A rule that is invalid (weekly with no weekdays) returns 400 and the todo stays one-off")
	void invalidRuleReturns400() throws Exception {
		SeriesTestSupport support = new SeriesTestSupport(baseUrl());
		Session session = support.registerUser();
		LocalDate wednesday = nextWeekday(DayOfWeek.WEDNESDAY);
		String todoId = createTodo(support, session, jsonBody("name", "Broken rule",
				"dueDate", wednesday.toString(), "priority", "LOW")).path("id").asString();
		Map<String, Object> rule = weekly(wednesday, List.of());

		HttpResponse<String> response = convert(support, session, todoId, ruleBody(rule));

		assertThat(response.statusCode()).as("POST recurrence status, body: " + response.body()).isEqualTo(400);
		assertThat(getTodo(support, session, todoId).path("seriesId").isNull())
				.as("todo stays one-off").isTrue();
		assertThat(support.rows(session, wednesday.plusDays(1), horizon()))
				.as("no series rows after the todo").isEmpty();
	}

	// ---------------------------------------------------------------- access control

	@Test
	@DisplayName("Converting another user's todo returns 404 and leaves the owner's todo one-off")
	void otherUsersTodoReturns404() throws Exception {
		SeriesTestSupport support = new SeriesTestSupport(baseUrl());
		Session owner = support.registerUser();
		Session intruder = support.registerUser();
		LocalDate wednesday = nextWeekday(DayOfWeek.WEDNESDAY);
		String todoId = createTodo(support, owner, jsonBody("name", "Private",
				"dueDate", wednesday.toString(), "priority", "LOW")).path("id").asString();

		HttpResponse<String> response = support.api().postJsonWithCsrf(todoPath(todoId, "/recurrence"),
				ruleBody(weekly(wednesday, List.of("MONDAY", "WEDNESDAY"))), intruder.access(), intruder.refresh());

		assertThat(response.statusCode()).as("POST recurrence status, body: " + response.body()).isEqualTo(404);
		assertThat(getTodo(support, owner, todoId).path("seriesId").isNull())
				.as("owner's todo stays one-off").isTrue();
	}

	@Test
	@DisplayName("Converting a random UUID returns 404")
	void unknownTodoReturns404() throws Exception {
		SeriesTestSupport support = new SeriesTestSupport(baseUrl());
		Session session = support.registerUser();
		LocalDate wednesday = nextWeekday(DayOfWeek.WEDNESDAY);

		HttpResponse<String> response = convert(support, session, UUID.randomUUID().toString(),
				ruleBody(weekly(wednesday, List.of("MONDAY", "WEDNESDAY"))));

		assertThat(response.statusCode()).as("POST recurrence status, body: " + response.body()).isEqualTo(404);
	}

	@Test
	@DisplayName("POST /api/todos/{id}/recurrence without a session returns 401 and the todo stays one-off")
	void unauthenticatedConvertReturns401() throws Exception {
		SeriesTestSupport support = new SeriesTestSupport(baseUrl());
		Session session = support.registerUser();
		LocalDate wednesday = nextWeekday(DayOfWeek.WEDNESDAY);
		String todoId = createTodo(support, session, jsonBody("name", "Anonymous",
				"dueDate", wednesday.toString(), "priority", "LOW")).path("id").asString();

		HttpResponse<String> response = support.api().postJsonWithCsrf(todoPath(todoId, "/recurrence"),
				ruleBody(weekly(wednesday, List.of("MONDAY", "WEDNESDAY"))));

		assertThat(response.statusCode()).as("POST recurrence status without session").isEqualTo(401);
		assertThat(getTodo(support, session, todoId).path("seriesId").isNull())
				.as("todo stays one-off").isTrue();
	}

	@Test
	@DisplayName("POST /api/todos/{id}/recurrence without a CSRF token returns 403 and the todo stays one-off")
	void convertWithoutCsrfReturns403() throws Exception {
		SeriesTestSupport support = new SeriesTestSupport(baseUrl());
		Session session = support.registerUser();
		LocalDate wednesday = nextWeekday(DayOfWeek.WEDNESDAY);
		String todoId = createTodo(support, session, jsonBody("name", "No token",
				"dueDate", wednesday.toString(), "priority", "LOW")).path("id").asString();

		HttpResponse<String> response = support.api().postJson(todoPath(todoId, "/recurrence"),
				ruleBody(weekly(wednesday, List.of("MONDAY", "WEDNESDAY"))), session.access(), session.refresh());

		assertThat(response.statusCode()).as("POST recurrence status without CSRF token").isEqualTo(403);
		assertThat(getTodo(support, session, todoId).path("seriesId").isNull())
				.as("todo stays one-off").isTrue();
	}
}
