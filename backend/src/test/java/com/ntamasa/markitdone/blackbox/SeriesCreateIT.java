package com.ntamasa.markitdone.blackbox;

import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static com.ntamasa.markitdone.blackbox.ApiClient.assertProblem;
import static com.ntamasa.markitdone.blackbox.ApiClient.json;
import static com.ntamasa.markitdone.blackbox.TestAccounts.Session;
import static com.ntamasa.markitdone.blackbox.TestAccounts.jsonBody;
import static com.ntamasa.markitdone.blackbox.TestAccounts.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Black-box tests for the recurring-series API (MAR-78): POST /api/series and the occurrences it
 * materialises as todo rows, read back through GET /api/todos.
 * Every test registers its own user in the time zone Europe/Budapest, so "today" is computed in that zone.
 * The horizon is three months after today, inclusive. Only HTTP requests, status codes and JSON bodies are used.
 */
@DisplayName("POST /api/series")
class SeriesCreateIT extends AbstractIntegrationTest {

	private static final String SERIES = "/api/series";
	private static final String TODOS = "/api/todos";
	private static final ZoneId ZONE = ZoneId.of("Europe/Budapest");
	/** Largest span GET /api/todos accepts (inclusive ends, 62 days apart). */
	private static final int MAX_RANGE_DAYS = 62;
	private static final String WEEKLY_SUMMARY = "Weekly: Mon, Wed, Fri";
	private static final String VALIDATION_MESSAGE_WEEKDAY = "pick at least one weekday";
	private static final String VALIDATION_MESSAGE_INTERVAL = "intervalDays must be 1-365";
	private static final String VALIDATION_MESSAGE_MONTH_DAYS = "monthDays must be 1-31 and not empty";
	private static final String VALIDATION_MESSAGE_END_COUNT = "endCount must be 1-999";
	private static final String VALIDATION_MESSAGE_END_DATE = "endDate must be on or after startDate";
	private static final String VALIDATION_MESSAGE_FREQUENCY = "frequency is required";
	private static final DateTimeFormatter UNTIL_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
	private static final ObjectMapper JSON = new ObjectMapper();

	/** Every field of the SeriesDto contract. */
	private static final List<String> SERIES_FIELDS = List.of(
			"id", "name", "description", "dueTime", "priority", "categoryId", "rule", "ruleSummary", "status",
			"nextOccurrence", "nextOccurrenceTodoId", "endsSummary", "cancelledOn");

	/** Every field of the rule shape, echoed back by the series. */
	private static final List<String> RULE_FIELDS = List.of(
			"frequency", "intervalDays", "weekdays", "monthDays", "startDate", "endType", "endCount", "endDate");

	private ApiClient api() {
		return new ApiClient(baseUrl());
	}

	/** Registers a fresh user whose time zone is Europe/Budapest. */
	private Session registerUser() throws Exception {
		return TestAccounts.register(baseUrl(), uniqueEmail());
	}

	/** Today's date in Europe/Budapest. */
	private static LocalDate today() {
		return LocalDate.now(ZONE);
	}

	/** The rolling horizon: three months after today, inclusive. */
	private static LocalDate horizon() {
		return today().plusMonths(3);
	}

	// ---------------------------------------------------------------- request builders

	/** A rule object with every rule field present (null where unused), starting today and never ending. */
	private static Map<String, Object> rule(String frequency) {
		Map<String, Object> rule = new LinkedHashMap<>();
		rule.put("frequency", frequency);
		rule.put("intervalDays", null);
		rule.put("weekdays", null);
		rule.put("monthDays", null);
		rule.put("startDate", today().toString());
		rule.put("endType", "NEVER");
		rule.put("endCount", null);
		rule.put("endDate", null);
		return rule;
	}

	/** A series body with the given rule, a name, a due time of 18:00 and an optional category. */
	private static String seriesBody(Map<String, Object> rule, String categoryId) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("name", "Gym");
		body.put("description", null);
		body.put("dueTime", "18:00");
		body.put("priority", "HIGH");
		body.put("categoryId", categoryId);
		body.put("rule", rule);
		return JSON.writeValueAsString(body);
	}

	private static String seriesBody(Map<String, Object> rule) {
		return seriesBody(rule, null);
	}

	/** The example weekly rule from the brief: Mon, Wed and Fri, never ending, starting today. */
	private static Map<String, Object> weeklyMonWedFri() {
		Map<String, Object> rule = rule("WEEKLY");
		rule.put("weekdays", List.of("MONDAY", "WEDNESDAY", "FRIDAY"));
		return rule;
	}

	private static Map<String, Object> dailyAfterCount(int count) {
		Map<String, Object> rule = rule("EVERY_N_DAYS");
		rule.put("intervalDays", 1);
		rule.put("endType", "AFTER_COUNT");
		rule.put("endCount", count);
		return rule;
	}

	private static Map<String, Object> dailyOnDate(LocalDate endDate) {
		Map<String, Object> rule = rule("EVERY_N_DAYS");
		rule.put("intervalDays", 1);
		rule.put("endType", "ON_DATE");
		rule.put("endDate", endDate.toString());
		return rule;
	}

	// ---------------------------------------------------------------- HTTP helpers

	private HttpResponse<String> createRaw(Session session, String body) throws Exception {
		return api().postJsonWithCsrf(SERIES, body, session.access(), session.refresh());
	}

	/** POST /api/series that must succeed with 201. Returns the SeriesDto. */
	private JsonNode createOk(Session session, String body) throws Exception {
		HttpResponse<String> response = createRaw(session, body);
		assertThat(response.statusCode()).as("POST " + SERIES + " status, body: " + response.body()).isEqualTo(201);
		return json(response);
	}

	/** GET /api/todos for [from, to], asserting 200. */
	private JsonNode todosInRange(Session session, LocalDate from, LocalDate to) throws Exception {
		return api().getJsonOk(TODOS + "?from=" + from + "&to=" + to, session.access(), session.refresh());
	}

	/** The todo rows of GET /api/todos for [from, to] as a list. */
	private List<JsonNode> rowsOf(Session session, LocalDate from, LocalDate to) throws Exception {
		List<JsonNode> rows = new ArrayList<>();
		for (JsonNode row : todosInRange(session, from, to)) {
			rows.add(row);
		}
		return rows;
	}

	/**
	 * Every todo row from today up to and including the horizon, read through GET /api/todos.
	 * The range is split into windows of at most 62 days, the largest span the API accepts.
	 */
	private List<JsonNode> allRowsUpToHorizon(Session session) throws Exception {
		List<JsonNode> rows = new ArrayList<>();
		LocalDate end = horizon();
		for (LocalDate from = today(); !from.isAfter(end); from = from.plusDays(MAX_RANGE_DAYS + 1)) {
			LocalDate to = from.plusDays(MAX_RANGE_DAYS);
			if (to.isAfter(end)) {
				to = end;
			}
			rows.addAll(rowsOf(session, from, to));
		}
		return rows;
	}

	/** Due dates of the rows, sorted as strings (YYYY-MM-DD sorts chronologically). */
	private static List<String> dueDates(List<JsonNode> rows) {
		List<String> dates = new ArrayList<>();
		for (JsonNode row : rows) {
			dates.add(row.path("dueDate").asString());
		}
		dates.sort(String::compareTo);
		return dates;
	}

	private static List<String> strings(List<LocalDate> dates) {
		List<String> result = new ArrayList<>();
		for (LocalDate date : dates) {
			result.add(date.toString());
		}
		result.sort(String::compareTo);
		return result;
	}

	/** Every date in [from, to] (both inclusive) that satisfies the predicate. */
	private static List<LocalDate> datesWhere(LocalDate from, LocalDate to, Predicate<LocalDate> predicate) {
		List<LocalDate> dates = new ArrayList<>();
		for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
			if (predicate.test(d)) {
				dates.add(d);
			}
		}
		return dates;
	}

	/**
	 * Asserts that the rows the user has, from today to the horizon, are exactly the expected due dates,
	 * that every row belongs to the series, and that nothing is materialised after the horizon.
	 */
	private void assertExactRows(Session session, String seriesId, List<LocalDate> expected) throws Exception {
		List<JsonNode> rows = allRowsUpToHorizon(session);
		assertThat(dueDates(rows)).as("due dates of the materialised rows").containsExactlyElementsOf(strings(expected));
		for (JsonNode row : rows) {
			assertThat(row.path("seriesId").asString()).as("seriesId of " + row).isEqualTo(seriesId);
		}
		JsonNode afterHorizon = todosInRange(session, horizon().plusDays(1), horizon().plusDays(MAX_RANGE_DAYS));
		assertThat(afterHorizon).as("rows after the horizon").isEmpty();
	}

	/** Creates a series with the given body, and returns its id. */
	private String createSeries(Session session, String body) throws Exception {
		return createOk(session, body).path("id").asString();
	}

	private static void assertContractFields(JsonNode series) {
		assertThat(series.propertyNames()).as("SeriesDto fields").containsAll(SERIES_FIELDS);
	}

	private static List<String> textsOf(JsonNode array) {
		List<String> values = new ArrayList<>();
		for (JsonNode value : array) {
			values.add(value.asString());
		}
		return values;
	}

	/** The Mon/Wed/Fri dates from [from, to]. */
	private static List<LocalDate> monWedFriBetween(LocalDate from, LocalDate to) {
		Set<DayOfWeek> days = Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
		return datesWhere(from, to, d -> days.contains(d.getDayOfWeek()));
	}

	/** Asserts that a validation failure creates nothing: no todo row in the next 62 days. */
	private void assertNothingCreated(Session session) throws Exception {
		JsonNode rows = todosInRange(session, today(), today().plusDays(MAX_RANGE_DAYS));
		assertThat(rows).as("rows after a rejected series").isEmpty();
	}

	// ---------------------------------------------------------------- create response

	@Test
	@DisplayName("POST a weekly Mon/Wed/Fri series that never ends returns 201 with the SeriesDto")
	void createWeeklyNeverReturns201SeriesDto() throws Exception {
		Session session = registerUser();

		HttpResponse<String> response = createRaw(session, seriesBody(weeklyMonWedFri()));

		assertThat(response.statusCode()).as("POST status, body: " + response.body()).isEqualTo(201);
		JsonNode series = json(response);
		assertContractFields(series);
		assertThat(series.path("id").asString()).isNotBlank();
		UUID.fromString(series.path("id").asString());
		assertThat(series.path("name").asString()).isEqualTo("Gym");
		assertThat(series.path("priority").asString()).isEqualTo("HIGH");
		assertThat(series.path("dueTime").asString()).startsWith("18:00");
		assertThat(series.path("categoryId").isNull()).as("categoryId is null").isTrue();

		JsonNode rule = series.path("rule");
		assertThat(rule.propertyNames()).as("rule fields").containsAll(RULE_FIELDS);
		assertThat(rule.path("frequency").asString()).isEqualTo("WEEKLY");
		assertThat(textsOf(rule.path("weekdays"))).containsExactly("MONDAY", "WEDNESDAY", "FRIDAY");
		assertThat(rule.path("startDate").asString()).isEqualTo(today().toString());
		assertThat(rule.path("endType").asString()).isEqualTo("NEVER");

		assertThat(series.path("ruleSummary").asString()).isEqualTo(WEEKLY_SUMMARY);
		assertThat(series.path("status").asString()).isEqualTo("ACTIVE");
		// The computed fields: the first Mon/Wed/Fri slot on or after today, and the row that holds it.
		LocalDate firstSlot = monWedFriBetween(today(), today().plusDays(14)).get(0);
		assertThat(series.path("nextOccurrence").asString()).as("nextOccurrence").isEqualTo(firstSlot.toString());
		JsonNode firstRow = null;
		for (JsonNode row : rowsOf(session, today(), today().plusDays(MAX_RANGE_DAYS))) {
			if (series.path("id").asString().equals(row.path("seriesId").asString())
					&& row.path("dueDate").asString().equals(firstSlot.toString())) {
				firstRow = row;
			}
		}
		assertThat(firstRow).as("row of the first slot " + firstSlot).isNotNull();
		assertThat(series.path("nextOccurrenceTodoId").asString())
				.as("nextOccurrenceTodoId").isEqualTo(firstRow.path("id").asString());
		assertThat(series.path("endsSummary").asString()).as("endsSummary").isEqualTo("Never");
		assertThat(series.path("cancelledOn").isNull()).as("cancelledOn is null for an active series").isTrue();
	}

	// ---------------------------------------------------------------- materialisation

	@Test
	@DisplayName("A weekly Mon/Wed/Fri series from today has one row per slot up to today + 3 months")
	void weeklyMaterialisesOneRowPerSlotUpToHorizon() throws Exception {
		Session session = registerUser();
		String seriesId = createSeries(session, seriesBody(weeklyMonWedFri()));

		List<LocalDate> expected = monWedFriBetween(today(), horizon());
		assertThat(expected).isNotEmpty();
		assertExactRows(session, seriesId, expected);
	}

	@Test
	@DisplayName("Weekly rows carry seriesId, occurrenceDate = dueDate, dueTime 18:00:00 and the weekly summary")
	void weeklyRowsCarrySeriesFields() throws Exception {
		Session session = registerUser();
		String seriesId = createSeries(session, seriesBody(weeklyMonWedFri()));

		List<JsonNode> rows = rowsOf(session, today(), today().plusDays(MAX_RANGE_DAYS));
		assertThat(rows).isNotEmpty();
		for (JsonNode row : rows) {
			assertThat(row.path("seriesId").asString()).as("seriesId").isEqualTo(seriesId);
			assertThat(row.path("occurrenceDate").asString()).as("occurrenceDate").isEqualTo(row.path("dueDate").asString());
			assertThat(row.path("dueTime").asString()).as("dueTime").isEqualTo("18:00:00");
			assertThat(row.path("ruleSummary").asString()).as("ruleSummary").isEqualTo(WEEKLY_SUMMARY);
			LocalDate due = LocalDate.parse(row.path("dueDate").asString());
			assertThat(due.getDayOfWeek()).as("weekday of " + due)
					.isIn(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
		}
	}

	@Test
	@DisplayName("Weekly rows cover the whole horizon through two windows and nothing after it")
	void weeklyRowsCoverHorizonInTwoWindows() throws Exception {
		Session session = registerUser();
		String seriesId = createSeries(session, seriesBody(weeklyMonWedFri()));

		List<JsonNode> first = rowsOf(session, today(), today().plusDays(MAX_RANGE_DAYS));
		List<JsonNode> second = rowsOf(session, today().plusDays(MAX_RANGE_DAYS + 1), horizon());

		List<String> expectedFirst = strings(monWedFriBetween(today(), today().plusDays(MAX_RANGE_DAYS)));
		List<String> expectedSecond = strings(monWedFriBetween(today().plusDays(MAX_RANGE_DAYS + 1), horizon()));
		assertThat(dueDates(first)).containsExactlyElementsOf(expectedFirst);
		assertThat(dueDates(second)).containsExactlyElementsOf(expectedSecond);
		for (JsonNode row : second) {
			assertThat(row.path("seriesId").asString()).isEqualTo(seriesId);
		}
		assertThat(rowsOf(session, horizon().plusDays(1), horizon().plusDays(MAX_RANGE_DAYS)))
				.as("rows after the horizon").isEmpty();
	}

	@Test
	@DisplayName("A daily series ending after 5 occurrences has exactly 5 rows and the 'times' summary")
	void dailyAfterFiveCreatesExactlyFiveRows() throws Exception {
		Session session = registerUser();
		String seriesId = createSeries(session, seriesBody(dailyAfterCount(5)));

		List<LocalDate> expected = List.of(today(), today().plusDays(1), today().plusDays(2),
				today().plusDays(3), today().plusDays(4));
		assertExactRows(session, seriesId, expected);
		for (JsonNode row : rowsOf(session, today(), today().plusDays(MAX_RANGE_DAYS))) {
			assertThat(row.path("ruleSummary").asString()).isEqualTo("Every day · 5 times");
		}
	}

	@Test
	@DisplayName("A daily series ending after 1 occurrence has exactly 1 row with the singular 'time' summary")
	void dailyAfterOneCreatesOneRow() throws Exception {
		Session session = registerUser();
		String seriesId = createSeries(session, seriesBody(dailyAfterCount(1)));

		assertExactRows(session, seriesId, List.of(today()));
		List<JsonNode> rows = rowsOf(session, today(), today());
		assertThat(rows).hasSize(1);
		assertThat(rows.get(0).path("ruleSummary").asString()).isEqualTo("Every day · 1 time");
	}

	@Test
	@DisplayName("A daily series ending on today + 10 has 11 rows and the 'until' summary")
	void dailyUntilTenDaysAheadCreatesElevenRows() throws Exception {
		Session session = registerUser();
		LocalDate end = today().plusDays(10);
		String seriesId = createSeries(session, seriesBody(dailyOnDate(end)));

		assertExactRows(session, seriesId, datesWhere(today(), end, d -> true));
		String until = "Every day · until " + end.format(UNTIL_FORMAT);
		for (JsonNode row : rowsOf(session, today(), end)) {
			assertThat(row.path("ruleSummary").asString()).isEqualTo(until);
		}
	}

	@Test
	@DisplayName("An every-3-days series has rows every 3 days from the start, summary 'Every 3 days'")
	void everyThreeDaysCreatesRowsEveryThreeDays() throws Exception {
		Session session = registerUser();
		Map<String, Object> rule = rule("EVERY_N_DAYS");
		rule.put("intervalDays", 3);
		String seriesId = createSeries(session, seriesBody(rule));

		List<LocalDate> expected = new ArrayList<>();
		for (LocalDate d = today(); !d.isAfter(horizon()); d = d.plusDays(3)) {
			expected.add(d);
		}
		assertExactRows(session, seriesId, expected);
		for (JsonNode row : rowsOf(session, today(), today().plusDays(MAX_RANGE_DAYS))) {
			assertThat(row.path("ruleSummary").asString()).isEqualTo("Every 3 days");
		}
	}

	@Test
	@DisplayName("A monthly [31] series starting on the 1st falls back to the last day in shorter months")
	void monthlyDay31FallsBackToLastDayInShorterMonths() throws Exception {
		Session session = registerUser();
		LocalDate start = today().withDayOfMonth(1).plusMonths(1);
		Map<String, Object> rule = rule("MONTHLY");
		rule.put("monthDays", List.of(31));
		rule.put("startDate", start.toString());
		String seriesId = createSeries(session, seriesBody(rule));

		List<LocalDate> expected = new ArrayList<>();
		for (YearMonth month = YearMonth.from(start); !month.atDay(1).isAfter(horizon()); month = month.plusMonths(1)) {
			LocalDate occurrence = month.atDay(Math.min(31, month.lengthOfMonth()));
			if (!occurrence.isBefore(start) && !occurrence.isAfter(horizon())) {
				expected.add(occurrence);
			}
		}
		assertThat(expected).isNotEmpty();
		assertExactRows(session, seriesId, expected);
	}

	@Test
	@DisplayName("A monthly [30, 31] series gives one row in February when February is inside the window")
	void monthlyDays30And31GiveOneRowInFebruary() throws Exception {
		Session session = registerUser();
		LocalDate start = today().withDayOfMonth(1).plusMonths(1);
		Map<String, Object> rule = rule("MONTHLY");
		rule.put("monthDays", List.of(30, 31));
		rule.put("startDate", start.toString());
		String seriesId = createSeries(session, seriesBody(rule));

		// The February occurrence is the last day of February (28 or 29), so it must fall inside the window.
		List<LocalDate> febOccurrences = new ArrayList<>();
		for (YearMonth month = YearMonth.from(start); !month.atDay(1).isAfter(horizon()); month = month.plusMonths(1)) {
			if (month.getMonthValue() == 2) {
				LocalDate lastDay = month.atEndOfMonth();
				if (!lastDay.isBefore(start) && !lastDay.isAfter(horizon())) {
					febOccurrences.add(lastDay);
				}
			}
		}
		Assumptions.assumeTrue(!febOccurrences.isEmpty(),
				"the February occurrence (last day of February) is not inside the materialisation window, so this case is skipped");

		List<JsonNode> rows = rowsOf(session, today(), horizon());
		List<String> febRows = new ArrayList<>();
		for (JsonNode row : rows) {
			LocalDate due = LocalDate.parse(row.path("dueDate").asString());
			if (due.getMonthValue() == 2) {
				febRows.add(due.toString());
			}
		}
		assertThat(febRows).as("February rows").hasSize(1);
		assertThat(rows).allSatisfy(row -> assertThat(row.path("seriesId").asString()).isEqualTo(seriesId));
	}

	@Test
	@DisplayName("A series starting in the future has no rows before its start date")
	void futureStartDateHasNoRowsBeforeIt() throws Exception {
		Session session = registerUser();
		LocalDate start = today().plusDays(14);
		Map<String, Object> rule = weeklyMonWedFri();
		rule.put("startDate", start.toString());
		String seriesId = createSeries(session, seriesBody(rule));

		assertThat(rowsOf(session, today(), start.minusDays(1))).as("rows before the start date").isEmpty();
		assertExactRows(session, seriesId, monWedFriBetween(start, horizon()));
	}

	// ---------------------------------------------------------------- validation

	@Test
	@DisplayName("Weekly with no weekdays returns 400 'pick at least one weekday' and creates no rows")
	void emptyWeekdaysReturns400() throws Exception {
		Session session = registerUser();
		Map<String, Object> rule = rule("WEEKLY");
		rule.put("weekdays", List.of());

		HttpResponse<String> response = createRaw(session, seriesBody(rule));

		assertProblem(response, 400, "VALIDATION_FAILED");
		assertThat(response.body()).contains(VALIDATION_MESSAGE_WEEKDAY);
		assertNothingCreated(session);
	}

	@Test
	@DisplayName("Every N days with intervalDays 0 returns 400 and creates no rows")
	void intervalDaysZeroReturns400() throws Exception {
		Session session = registerUser();
		Map<String, Object> rule = rule("EVERY_N_DAYS");
		rule.put("intervalDays", 0);

		HttpResponse<String> response = createRaw(session, seriesBody(rule));

		assertProblem(response, 400, "VALIDATION_FAILED");
		assertThat(response.body()).contains(VALIDATION_MESSAGE_INTERVAL);
		assertNothingCreated(session);
	}

	@Test
	@DisplayName("Monthly with a month day of 32 returns 400 and creates no rows")
	void monthDay32Returns400() throws Exception {
		Session session = registerUser();
		Map<String, Object> rule = rule("MONTHLY");
		rule.put("monthDays", List.of(32));

		HttpResponse<String> response = createRaw(session, seriesBody(rule));

		assertProblem(response, 400, "VALIDATION_FAILED");
		assertThat(response.body()).contains(VALIDATION_MESSAGE_MONTH_DAYS);
		assertNothingCreated(session);
	}

	@Test
	@DisplayName("A series ending after 0 occurrences returns 400 and creates no rows")
	void endCountZeroReturns400() throws Exception {
		Session session = registerUser();
		Map<String, Object> rule = dailyAfterCount(0);

		HttpResponse<String> response = createRaw(session, seriesBody(rule));

		assertProblem(response, 400, "VALIDATION_FAILED");
		assertThat(response.body()).contains(VALIDATION_MESSAGE_END_COUNT);
		assertNothingCreated(session);
	}

	@Test
	@DisplayName("A series whose end date is before its start date returns 400 and creates no rows")
	void endDateBeforeStartReturns400() throws Exception {
		Session session = registerUser();
		Map<String, Object> rule = dailyOnDate(today().minusDays(1));

		HttpResponse<String> response = createRaw(session, seriesBody(rule));

		assertProblem(response, 400, "VALIDATION_FAILED");
		assertThat(response.body()).contains(VALIDATION_MESSAGE_END_DATE);
		assertNothingCreated(session);
	}

	@Test
	@DisplayName("A rule without a frequency returns 400 'frequency is required' and creates no rows")
	void missingFrequencyReturns400() throws Exception {
		Session session = registerUser();
		Map<String, Object> rule = rule("WEEKLY");
		rule.remove("frequency");
		rule.put("weekdays", List.of("MONDAY"));

		HttpResponse<String> response = createRaw(session, seriesBody(rule));

		assertProblem(response, 400, "VALIDATION_FAILED");
		assertThat(response.body()).contains(VALIDATION_MESSAGE_FREQUENCY);
		assertNothingCreated(session);
	}

	@Test
	@DisplayName("Several rule problems are reported together, joined by '; ', and create no rows")
	void severalProblemsAreJoinedBySemicolon() throws Exception {
		Session session = registerUser();
		Map<String, Object> rule = rule("EVERY_N_DAYS");
		rule.put("intervalDays", 0);
		rule.put("endType", "ON_DATE");
		rule.put("endDate", today().minusDays(2).toString());

		HttpResponse<String> response = createRaw(session, seriesBody(rule));

		assertProblem(response, 400, "VALIDATION_FAILED");
		String body = response.body();
		assertThat(body).contains(VALIDATION_MESSAGE_INTERVAL).contains(VALIDATION_MESSAGE_END_DATE);
		assertThat(body.contains(VALIDATION_MESSAGE_INTERVAL + "; " + VALIDATION_MESSAGE_END_DATE)
				|| body.contains(VALIDATION_MESSAGE_END_DATE + "; " + VALIDATION_MESSAGE_INTERVAL))
				.as("messages joined by '; ' in " + body)
				.isTrue();
		assertNothingCreated(session);
	}

	@Test
	@DisplayName("Weekly with no weekdays and a zero end count reports both messages joined by '; '")
	void weeklyEmptyWeekdaysAndZeroEndCountAreJoined() throws Exception {
		Session session = registerUser();
		Map<String, Object> rule = rule("WEEKLY");
		rule.put("weekdays", List.of());
		rule.put("endType", "AFTER_COUNT");
		rule.put("endCount", 0);

		HttpResponse<String> response = createRaw(session, seriesBody(rule));

		assertProblem(response, 400, "VALIDATION_FAILED");
		String body = response.body();
		assertThat(body).contains(VALIDATION_MESSAGE_WEEKDAY).contains(VALIDATION_MESSAGE_END_COUNT);
		assertThat(body.contains(VALIDATION_MESSAGE_WEEKDAY + "; " + VALIDATION_MESSAGE_END_COUNT)
				|| body.contains(VALIDATION_MESSAGE_END_COUNT + "; " + VALIDATION_MESSAGE_WEEKDAY))
				.as("messages joined by '; ' in " + body)
				.isTrue();
		assertNothingCreated(session);
	}

	// ---------------------------------------------------------------- category

	@Test
	@DisplayName("A series with another user's categoryId returns 400 and creates no rows")
	void otherUsersCategoryReturns400() throws Exception {
		Session owner = registerUser();
		Session other = registerUser();
		String foreignCategory = createCategory(owner, "Owner only");

		HttpResponse<String> response = createRaw(other, seriesBody(weeklyMonWedFri(), foreignCategory));

		assertThat(response.statusCode()).as("POST status, body: " + response.body()).isEqualTo(400);
		assertNothingCreated(other);
	}

	@Test
	@DisplayName("A series with the user's own categoryId returns 201 and every occurrence carries it")
	void ownCategoryIsCarriedByEveryOccurrence() throws Exception {
		Session session = registerUser();
		String category = createCategory(session, "Health");

		JsonNode series = createOk(session, seriesBody(weeklyMonWedFri(), category));
		assertThat(series.path("categoryId").asString()).isEqualTo(category);

		List<JsonNode> rows = rowsOf(session, today(), today().plusDays(MAX_RANGE_DAYS));
		assertThat(rows).isNotEmpty();
		for (JsonNode row : rows) {
			assertThat(row.path("categoryId").asString()).as("categoryId of " + row).isEqualTo(category);
		}
	}

	/** Creates a category through POST /api/categories and returns its id. */
	private String createCategory(Session session, String name) throws Exception {
		HttpResponse<String> response = api().postJsonWithCsrf("/api/categories",
				jsonBody("name", name, "swatch", "TEAL"), session.access(), session.refresh());
		assertThat(response.statusCode()).as("POST /api/categories status, body: " + response.body()).isEqualTo(201);
		return json(response).path("id").asString();
	}

	// ---------------------------------------------------------------- one-off todos

	@Test
	@DisplayName("A one-off todo keeps seriesId, occurrenceDate and ruleSummary null next to a series")
	void oneOffTodoHasNullSeriesFields() throws Exception {
		Session session = registerUser();
		createSeries(session, seriesBody(weeklyMonWedFri()));

		HttpResponse<String> created = api().postJsonWithCsrf("/api/todos",
				jsonBody("name", "Call the bank", "dueDate", today().plusDays(2).toString(), "priority", "LOW"),
				session.access(), session.refresh());
		assertThat(created.statusCode()).as("POST /api/todos status, body: " + created.body()).isEqualTo(201);
		JsonNode todo = json(created);
		assertThat(todo.path("seriesId").isNull()).as("seriesId is null").isTrue();
		assertThat(todo.path("occurrenceDate").isNull()).as("occurrenceDate is null").isTrue();
		assertThat(todo.path("ruleSummary").isNull()).as("ruleSummary is null").isTrue();

		JsonNode listed = todosInRange(session, today().plusDays(2), today().plusDays(2));
		assertThat(listed).hasSize(1);
		assertThat(listed.get(0).path("name").asString()).isEqualTo("Call the bank");
		assertThat(listed.get(0).path("seriesId").isNull()).as("listed seriesId is null").isTrue();
		assertThat(listed.get(0).path("occurrenceDate").isNull()).as("listed occurrenceDate is null").isTrue();
		assertThat(listed.get(0).path("ruleSummary").isNull()).as("listed ruleSummary is null").isTrue();
	}

	// ---------------------------------------------------------------- access control

	@Test
	@DisplayName("POST /api/series without a session returns 401")
	void unauthenticatedCreateReturns401() throws Exception {
		HttpResponse<String> response = api().postJsonWithCsrf(SERIES, seriesBody(weeklyMonWedFri()));

		assertThat(response.statusCode()).as("POST status without session").isEqualTo(401);
	}

	@Test
	@DisplayName("POST /api/series without a CSRF token returns 403 and creates nothing")
	void createWithoutCsrfReturns403() throws Exception {
		Session session = registerUser();

		HttpResponse<String> response = api().postJson(SERIES, seriesBody(weeklyMonWedFri()),
				session.access(), session.refresh());

		assertThat(response.statusCode()).as("POST status without CSRF token").isEqualTo(403);
		assertNothingCreated(session);
	}
}
