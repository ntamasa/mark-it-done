package com.ntamasa.markitdone.blackbox;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static com.ntamasa.markitdone.blackbox.ApiClient.assertProblem;
import static com.ntamasa.markitdone.blackbox.ApiClient.json;
import static com.ntamasa.markitdone.blackbox.TestAccounts.PASSWORD;
import static com.ntamasa.markitdone.blackbox.TestAccounts.Session;
import static com.ntamasa.markitdone.blackbox.TestAccounts.canonicalErrorBody;
import static com.ntamasa.markitdone.blackbox.TestAccounts.errorFields;
import static com.ntamasa.markitdone.blackbox.TestAccounts.jsonBody;
import static com.ntamasa.markitdone.blackbox.TestAccounts.registerBody;
import static com.ntamasa.markitdone.blackbox.TestAccounts.registerExpectingCreated;
import static com.ntamasa.markitdone.blackbox.TestAccounts.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Black-box tests for the todo API (MAR-72): /api/todos.
 * Every test registers its own users with the time zone UTC, so "today" and "yesterday" are computed in UTC.
 * Only HTTP requests, status codes, headers and JSON bodies are used.
 */
@DisplayName("/api/todos")
class TodosIT extends AbstractIntegrationTest {

	private static final String TODOS = "/api/todos";
	private static final String TIMEZONE = "UTC";
	private static final ZoneId ZONE = ZoneId.of(TIMEZONE);
	private static final String NOT_FOUND_MESSAGE = "Todo not found";
	private static final String INVALID_RANGE_MESSAGE = "Invalid date range";
	private static final String UNKNOWN_CATEGORY_MESSAGE = "Unknown category";

	/** Every field of the canonical TodoDto contract. */
	private static final List<String> CONTRACT_FIELDS = List.of(
			"id", "name", "description", "dueDate", "dueTime", "priority", "categoryId", "done", "doneAt",
			"createdAt", "updatedAt", "expired", "seriesId", "occurrenceDate", "ruleSummary");

	private ApiClient api() {
		return new ApiClient(baseUrl());
	}

	private static String todosPath(String suffix) {
		return TODOS + suffix;
	}

	/** Registers a user whose time zone is UTC. */
	private Session registerUser() throws Exception {
		return registerExpectingCreated(baseUrl(), registerBody("Todo Tester", uniqueEmail(), PASSWORD, TIMEZONE));
	}

	/** Today's date in the users' time zone (UTC). */
	private static LocalDate today() {
		return LocalDate.now(ZONE);
	}

	private HttpResponse<String> createRaw(Session session, String body) throws Exception {
		return api().postJsonWithCsrf(TODOS, body, session.access(), session.refresh());
	}

	/** POST that must succeed with 201. Returns the created todo. */
	private JsonNode createOk(Session session, String body) throws Exception {
		HttpResponse<String> response = createRaw(session, body);
		assertThat(response.statusCode()).as("POST " + TODOS + " status, body: " + response.body()).isEqualTo(201);
		return json(response);
	}

	/** A POST body for a todo with a name, a due date, an optional time and a priority, and no category. */
	private static String todoBody(String name, LocalDate date, String time, String priority) {
		return jsonBody("name", name, "dueDate", date.toString(), "dueTime", time, "priority", priority);
	}

	/** Creates a todo without a category and returns it. */
	private JsonNode createTodo(Session session, String name, LocalDate date, String time, String priority)
			throws Exception {
		return createOk(session, todoBody(name, date, time, priority));
	}

	/** Creates a todo that is marked done straight away and returns it. */
	private JsonNode createDone(Session session, String name, LocalDate date, String time, String priority)
			throws Exception {
		JsonNode created = createTodo(session, name, date, time, priority);
		return setDone(session, created.path("id").asString(), true);
	}

	private HttpResponse<String> getRaw(Session session, String id) throws Exception {
		return api().get(todosPath("/" + id), session.access(), session.refresh());
	}

	private JsonNode getOk(Session session, String id) throws Exception {
		return api().getJsonOk(todosPath("/" + id), session.access(), session.refresh());
	}

	/** GET /api/todos?from=&to= for the session, asserting 200. */
	private JsonNode range(Session session, LocalDate from, LocalDate to) throws Exception {
		return api().getJsonOk(todosPath("?from=" + from + "&to=" + to), session.access(), session.refresh());
	}

	private HttpResponse<String> rangeRaw(Session session, String query) throws Exception {
		return api().get(todosPath(query), session.access(), session.refresh());
	}

	/** PATCH /api/todos/{id}/done with the given flag, asserting 200. Returns the updated todo. */
	private JsonNode setDone(Session session, String id, boolean done) throws Exception {
		HttpResponse<String> response = api().patchJsonWithCsrf(todosPath("/" + id + "/done"),
				"{\"done\":" + done + "}", session.access(), session.refresh());
		assertThat(response.statusCode()).as("PATCH done status, body: " + response.body()).isEqualTo(200);
		return json(response);
	}

	private static List<String> names(JsonNode todos) {
		List<String> names = new ArrayList<>();
		for (JsonNode todo : todos) {
			names.add(todo.path("name").asString());
		}
		return names;
	}

	/** Creates a category for the session through POST /api/categories and returns its id. */
	private String createCategory(Session session, String name) throws Exception {
		HttpResponse<String> response = api().postJsonWithCsrf("/api/categories",
				jsonBody("name", name, "swatch", "TEAL"), session.access(), session.refresh());
		assertThat(response.statusCode()).as("POST /api/categories status, body: " + response.body()).isEqualTo(201);
		return json(response).path("id").asString();
	}

	/** Waits, if needed, until the clock in UTC is past 00:00:02, so a todo due at 00:00:01 today is already due. */
	private static void waitUntilJustAfterMidnightUtc() throws InterruptedException {
		LocalTime now = LocalTime.now(ZONE);
		LocalTime threshold = LocalTime.of(0, 0, 2);
		if (now.isBefore(threshold)) {
			Thread.sleep(Duration.between(now, threshold).toMillis() + 100);
		}
	}

	private static void assertContractFields(JsonNode todo) {
		assertThat(todo.propertyNames()).as("TodoDto fields").containsAll(CONTRACT_FIELDS);
	}

	private static void assertNullField(JsonNode todo, String field) {
		assertThat(todo.has(field)).as("field " + field + " is present").isTrue();
		assertThat(todo.path(field).isNull()).as("field " + field + " is null").isTrue();
	}

	// ---------------------------------------------------------------- create

	@Test
	@DisplayName("POST a future all-day todo returns 201 with every contract field and the all-day defaults")
	void createFutureAllDayTodoReturnsContractFields() throws Exception {
		Session session = registerUser();
		LocalDate future = today().plusDays(30);

		HttpResponse<String> response = createRaw(session,
				todoBody("Renew passport", future, null, "HIGH"));

		assertThat(response.statusCode()).as("POST status, body: " + response.body()).isEqualTo(201);
		JsonNode todo = json(response);
		assertContractFields(todo);
		UUID.fromString(todo.path("id").asString());
		assertThat(todo.path("name").asString()).isEqualTo("Renew passport");
		assertThat(todo.path("dueDate").asString()).isEqualTo(future.toString());
		assertNullField(todo, "dueTime");
		assertThat(todo.path("priority").asString()).isEqualTo("HIGH");
		assertNullField(todo, "categoryId");
		assertNullField(todo, "description");
		assertThat(todo.path("done").asBoolean()).as("done").isFalse();
		assertNullField(todo, "doneAt");
		assertThat(todo.path("expired").asBoolean()).as("expired of a future all-day todo").isFalse();
		assertNullField(todo, "seriesId");
		assertNullField(todo, "occurrenceDate");
		assertNullField(todo, "ruleSummary");
		Instant.parse(todo.path("createdAt").asString());
		Instant.parse(todo.path("updatedAt").asString());
	}

	@Test
	@DisplayName("POST returns dueDate as YYYY-MM-DD and a timed dueTime as HH:mm:ss, input 14:30 becomes 14:30:00")
	void createTimedTodoFormatsDateAndTime() throws Exception {
		Session session = registerUser();
		LocalDate future = today().plusDays(10);

		JsonNode todo = createTodo(session, "Dentist", future, "14:30", "MEDIUM");

		assertThat(todo.path("dueDate").asString()).matches("\\d{4}-\\d{2}-\\d{2}");
		assertThat(todo.path("dueDate").asString()).isEqualTo(future.toString());
		assertThat(todo.path("dueTime").asString()).isEqualTo("14:30:00");
		assertThat(todo.path("expired").asBoolean()).isFalse();
	}

	@Test
	@DisplayName("POST trims the name and turns a blank description into null")
	void createTrimsNameAndBlankDescriptionBecomesNull() throws Exception {
		Session session = registerUser();

		JsonNode todo = createOk(session, jsonBody("name", "   Buy milk   ", "description", "   ",
				"dueDate", today().plusDays(3).toString(), "priority", "LOW"));

		assertThat(todo.path("name").asString()).isEqualTo("Buy milk");
		assertNullField(todo, "description");
	}

	@Test
	@DisplayName("POST keeps a non-blank description as sent")
	void createKeepsDescription() throws Exception {
		Session session = registerUser();

		JsonNode todo = createOk(session, jsonBody("name", "Report", "description", "Q3 numbers",
				"dueDate", today().plusDays(3).toString(), "priority", "URGENT"));

		assertThat(todo.path("description").asString()).isEqualTo("Q3 numbers");
	}

	@Test
	@DisplayName("POST with a blank name returns 400 with errors[0].field = name and creates nothing")
	void createBlankNameReturns400() throws Exception {
		Session session = registerUser();

		HttpResponse<String> response = createRaw(session, todoBody("   ", today().plusDays(2), null, "LOW"));

		assertThat(response.statusCode()).as("POST status, body: " + response.body()).isEqualTo(400);
		assertThat(errorFields(response)).first().isEqualTo("name");
		HttpResponse<String> listed = rangeRaw(session, "?from=" + today() + "&to=" + today().plusDays(10));
		assertThat(json(listed)).isEmpty();
	}

	@Test
	@DisplayName("POST with a 200-character name returns 201")
	void createNameWith200CharactersIsAccepted() throws Exception {
		Session session = registerUser();
		String name = "n".repeat(200);

		JsonNode todo = createOk(session, todoBody(name, today().plusDays(2), null, "LOW"));

		assertThat(todo.path("name").asString()).isEqualTo(name);
	}

	@Test
	@DisplayName("POST with a 201-character name returns 400 with errors[0].field = name")
	void createNameWith201CharactersReturns400() throws Exception {
		Session session = registerUser();

		HttpResponse<String> response = createRaw(session, todoBody("n".repeat(201), today().plusDays(2), null, "LOW"));

		assertThat(response.statusCode()).as("POST status, body: " + response.body()).isEqualTo(400);
		assertThat(errorFields(response)).first().isEqualTo("name");
	}

	@Test
	@DisplayName("POST without dueDate returns 400")
	void createWithoutDueDateReturns400() throws Exception {
		Session session = registerUser();

		HttpResponse<String> response = createRaw(session, jsonBody("name", "No date", "priority", "LOW"));

		assertThat(response.statusCode()).as("POST status, body: " + response.body()).isEqualTo(400);
	}

	@Test
	@DisplayName("POST without priority returns 400")
	void createWithoutPriorityReturns400() throws Exception {
		Session session = registerUser();

		HttpResponse<String> response = createRaw(session,
				jsonBody("name", "No priority", "dueDate", today().plusDays(2).toString()));

		assertThat(response.statusCode()).as("POST status, body: " + response.body()).isEqualTo(400);
	}

	@Test
	@DisplayName("POST with an unknown priority returns 400")
	void createWithUnknownPriorityReturns400() throws Exception {
		Session session = registerUser();

		HttpResponse<String> response = createRaw(session, todoBody("Bad priority", today().plusDays(2), null, "CRITICAL"));

		assertThat(response.statusCode()).as("POST status, body: " + response.body()).isEqualTo(400);
	}

	@Test
	@DisplayName("POST with another user's categoryId returns 400 VALIDATION_FAILED 'Unknown category'")
	void createWithOtherUsersCategoryReturns400() throws Exception {
		Session owner = registerUser();
		Session other = registerUser();
		String foreignCategory = createCategory(owner, "Owner only");

		HttpResponse<String> response = createRaw(other, jsonBody("name", "Sneaky", "dueDate",
				today().plusDays(2).toString(), "priority", "LOW", "categoryId", foreignCategory));

		assertProblem(response, 400, "VALIDATION_FAILED");
		assertThat(response.body()).contains(UNKNOWN_CATEGORY_MESSAGE);
	}

	@Test
	@DisplayName("POST with the user's own categoryId returns 201 with that categoryId")
	void createWithOwnCategoryReturnsCategoryId() throws Exception {
		Session session = registerUser();
		String category = createCategory(session, "Work");

		JsonNode todo = createOk(session, jsonBody("name", "Team sync", "dueDate",
				today().plusDays(2).toString(), "priority", "HIGH", "categoryId", category));

		assertThat(todo.path("categoryId").asString()).isEqualTo(category);
	}

	// ---------------------------------------------------------------- expiry

	@Test
	@DisplayName("An all-day todo dated yesterday (UTC) is expired")
	void allDayYesterdayIsExpired() throws Exception {
		Session session = registerUser();

		JsonNode todo = createTodo(session, "Yesterday task", today().minusDays(1), null, "MEDIUM");

		assertThat(todo.path("expired").asBoolean()).as("expired").isTrue();
	}

	@Test
	@DisplayName("An all-day todo dated today (UTC) is not expired")
	void allDayTodayIsNotExpired() throws Exception {
		Session session = registerUser();

		JsonNode todo = createTodo(session, "Today task", today(), null, "MEDIUM");

		assertThat(todo.path("expired").asBoolean()).as("expired").isFalse();
	}

	@Test
	@DisplayName("A timed todo dated today at 00:00:01 (UTC) is expired")
	void timedTodayAtOneSecondPastMidnightIsExpired() throws Exception {
		Session session = registerUser();
		waitUntilJustAfterMidnightUtc();

		JsonNode todo = createTodo(session, "Early task", today(), "00:00:01", "MEDIUM");

		assertThat(todo.path("expired").asBoolean()).as("expired").isTrue();
	}

	// ---------------------------------------------------------------- range

	@Test
	@DisplayName("GET range returns only the user's todos inside [from, to] inclusive, in day order")
	void rangeReturnsOnlyOwnTodosInsideWindow() throws Exception {
		Session session = registerUser();
		Session other = registerUser();
		LocalDate base = today().plusDays(5);
		createTodo(session, "Before window", base.minusDays(2), null, "LOW");
		createTodo(session, "Window start", base.minusDays(1), null, "LOW");
		createTodo(session, "Window middle", base, null, "LOW");
		createTodo(session, "Window end", base.plusDays(1), null, "LOW");
		createTodo(session, "After window", base.plusDays(2), null, "LOW");
		createTodo(other, "Other user in window", base, null, "LOW");

		JsonNode todos = range(session, base.minusDays(1), base.plusDays(1));

		assertThat(names(todos)).containsExactly("Window start", "Window middle", "Window end");
	}

	@Test
	@DisplayName("GET range orders a mixed day by every tie-breaker and sorts dates before the day order")
	void rangeSortsMixedDayByDocumentedOrder() throws Exception {
		Session session = registerUser();
		LocalDate day = today().plusDays(5);
		createTodo(session, "Day before", day.minusDays(1), "23:00", "LOW");
		createDone(session, "Day before done", day.minusDays(1), "23:30", "LOW");
		createTodo(session, "Urgent allday", day, null, "URGENT");
		createTodo(session, "High allday", day, null, "HIGH");
		createTodo(session, "Banana", day, null, "MEDIUM");
		createTodo(session, "apple", day, null, "MEDIUM");
		createTodo(session, "low allday", day, null, "LOW");
		createTodo(session, "Early", day, "08:00", "HIGH");
		createTodo(session, "Sync", day, "08:00", "LOW");
		createTodo(session, "Late", day, "17:00", "URGENT");
		createDone(session, "Done timed", day, "07:00", "URGENT");
		createDone(session, "Done allday", day, null, "URGENT");
		createTodo(session, "Day after", day.plusDays(1), null, "URGENT");

		JsonNode todos = range(session, day.minusDays(1), day.plusDays(1));

		assertThat(names(todos)).containsExactly(
				"Day before",
				"Day before done",
				"Urgent allday",
				"High allday",
				"apple",
				"Banana",
				"low allday",
				"Early",
				"Sync",
				"Late",
				"Done allday",
				"Done timed",
				"Day after");
	}

	@Test
	@DisplayName("GET range over a 62-day span returns 200")
	void rangeOf62DaysIsAccepted() throws Exception {
		Session session = registerUser();
		LocalDate from = today();

		HttpResponse<String> response = rangeRaw(session, "?from=" + from + "&to=" + from.plusDays(62));

		assertThat(response.statusCode()).as("GET status, body: " + response.body()).isEqualTo(200);
		assertThat(json(response).isArray()).isTrue();
	}

	@Test
	@DisplayName("GET range over a 63-day span returns 400 BAD_REQUEST 'Invalid date range'")
	void rangeOf63DaysReturns400() throws Exception {
		Session session = registerUser();
		LocalDate from = today();

		HttpResponse<String> response = rangeRaw(session, "?from=" + from + "&to=" + from.plusDays(63));

		assertProblem(response, 400, "BAD_REQUEST");
		assertThat(response.body()).contains(INVALID_RANGE_MESSAGE);
	}

	@Test
	@DisplayName("GET range with from after to returns 400 'Invalid date range'")
	void rangeWithFromAfterToReturns400() throws Exception {
		Session session = registerUser();
		LocalDate day = today();

		HttpResponse<String> response = rangeRaw(session, "?from=" + day + "&to=" + day.minusDays(1));

		assertProblem(response, 400, "BAD_REQUEST");
		assertThat(response.body()).contains(INVALID_RANGE_MESSAGE);
	}

	@Test
	@DisplayName("GET range without from returns 400")
	void rangeWithoutFromReturns400() throws Exception {
		Session session = registerUser();

		HttpResponse<String> response = rangeRaw(session, "?to=" + today());

		assertProblem(response, 400, "BAD_REQUEST");
	}

	@Test
	@DisplayName("GET range without to returns 400")
	void rangeWithoutToReturns400() throws Exception {
		Session session = registerUser();

		HttpResponse<String> response = rangeRaw(session, "?from=" + today());

		assertProblem(response, 400, "BAD_REQUEST");
	}

	@Test
	@DisplayName("GET range without any parameter returns 400")
	void rangeWithoutParametersReturns400() throws Exception {
		Session session = registerUser();

		HttpResponse<String> response = rangeRaw(session, "");

		assertProblem(response, 400, "BAD_REQUEST");
	}

	@Test
	@DisplayName("GET range with a malformed date returns 400")
	void rangeWithMalformedDateReturns400() throws Exception {
		Session session = registerUser();

		HttpResponse<String> response = rangeRaw(session, "?from=2026-13-45&to=" + today());

		assertProblem(response, 400, "BAD_REQUEST");
	}

	// ---------------------------------------------------------------- get one

	@Test
	@DisplayName("GET /{id} returns the todo for its owner")
	void getOwnTodoReturnsTodo() throws Exception {
		Session session = registerUser();
		String id = createTodo(session, "Mine", today().plusDays(2), "09:15", "LOW").path("id").asString();

		JsonNode todo = getOk(session, id);

		assertContractFields(todo);
		assertThat(todo.path("id").asString()).isEqualTo(id);
		assertThat(todo.path("name").asString()).isEqualTo("Mine");
		assertThat(todo.path("dueTime").asString()).isEqualTo("09:15:00");
	}

	@Test
	@DisplayName("GET /{id} of another user's todo and of a random UUID return the same 404 body")
	void getOtherUsersTodoAndRandomUuidReturnIdentical404() throws Exception {
		Session owner = registerUser();
		Session intruder = registerUser();
		String id = createTodo(owner, "Private", today().plusDays(2), null, "LOW").path("id").asString();

		HttpResponse<String> foreign = getRaw(intruder, id);
		HttpResponse<String> random = getRaw(intruder, UUID.randomUUID().toString());

		assertThat(foreign.statusCode()).as("foreign GET status").isEqualTo(404);
		assertThat(random.statusCode()).as("random GET status").isEqualTo(404);
		assertThat(foreign.body()).contains(NOT_FOUND_MESSAGE);
		assertThat(random.body()).contains(NOT_FOUND_MESSAGE);
		Map<String, String> foreignBody = canonicalErrorBody(foreign);
		assertThat(foreignBody).isEqualTo(canonicalErrorBody(random));
	}

	// ---------------------------------------------------------------- update

	@Test
	@DisplayName("PUT changes the name, description, date, time, priority and category")
	void updateChangesFields() throws Exception {
		Session session = registerUser();
		String category = createCategory(session, "Home");
		String id = createTodo(session, "Old name", today().plusDays(2), null, "LOW").path("id").asString();
		LocalDate newDate = today().plusDays(4);

		HttpResponse<String> response = api().putJsonWithCsrf(todosPath("/" + id), jsonBody(
				"name", "New name", "description", "Now with notes", "dueDate", newDate.toString(),
				"dueTime", "09:15", "priority", "URGENT", "categoryId", category),
				session.access(), session.refresh());

		assertThat(response.statusCode()).as("PUT status, body: " + response.body()).isEqualTo(200);
		JsonNode updated = json(response);
		assertContractFields(updated);
		assertThat(updated.path("id").asString()).isEqualTo(id);
		assertThat(updated.path("name").asString()).isEqualTo("New name");
		assertThat(updated.path("description").asString()).isEqualTo("Now with notes");
		assertThat(updated.path("dueDate").asString()).isEqualTo(newDate.toString());
		assertThat(updated.path("dueTime").asString()).isEqualTo("09:15:00");
		assertThat(updated.path("priority").asString()).isEqualTo("URGENT");
		assertThat(updated.path("categoryId").asString()).isEqualTo(category);

		JsonNode reread = getOk(session, id);
		assertThat(reread.path("name").asString()).isEqualTo("New name");
		assertThat(reread.path("dueTime").asString()).isEqualTo("09:15:00");
	}

	@Test
	@DisplayName("PUT can update a todo dated last month")
	void updateTodoDatedLastMonthSucceeds() throws Exception {
		Session session = registerUser();
		LocalDate lastMonth = today().minusMonths(1);
		String id = createTodo(session, "Old task", lastMonth, null, "MEDIUM").path("id").asString();

		HttpResponse<String> response = api().putJsonWithCsrf(todosPath("/" + id),
				todoBody("Old task, edited", lastMonth, null, "LOW"), session.access(), session.refresh());

		assertThat(response.statusCode()).as("PUT status, body: " + response.body()).isEqualTo(200);
		assertThat(json(response).path("name").asString()).isEqualTo("Old task, edited");
		assertThat(json(response).path("dueDate").asString()).isEqualTo(lastMonth.toString());
	}

	@Test
	@DisplayName("PUT of another user's todo returns 404 and changes nothing")
	void updateOtherUsersTodoReturns404() throws Exception {
		Session owner = registerUser();
		Session intruder = registerUser();
		String id = createTodo(owner, "Owner's", today().plusDays(2), null, "LOW").path("id").asString();

		HttpResponse<String> response = api().putJsonWithCsrf(todosPath("/" + id),
				todoBody("Hijacked", today().plusDays(2), null, "URGENT"), intruder.access(), intruder.refresh());

		assertThat(response.statusCode()).as("PUT status, body: " + response.body()).isEqualTo(404);
		assertThat(response.body()).contains(NOT_FOUND_MESSAGE);
		assertThat(getOk(owner, id).path("name").asString()).isEqualTo("Owner's");
	}

	@Test
	@DisplayName("PUT on a random UUID returns 404 'Todo not found'")
	void updateRandomUuidReturns404() throws Exception {
		Session session = registerUser();

		HttpResponse<String> response = api().putJsonWithCsrf(todosPath("/" + UUID.randomUUID()),
				todoBody("Nothing", today().plusDays(2), null, "LOW"), session.access(), session.refresh());

		assertThat(response.statusCode()).as("PUT status, body: " + response.body()).isEqualTo(404);
		assertThat(response.body()).contains(NOT_FOUND_MESSAGE);
	}

	// ---------------------------------------------------------------- done

	@Test
	@DisplayName("PATCH /{id}/done with done true sets done and a doneAt instant")
	void markDoneSetsDoneAt() throws Exception {
		Session session = registerUser();
		String id = createTodo(session, "Finish me", today().plusDays(2), null, "HIGH").path("id").asString();

		HttpResponse<String> response = api().patchJsonWithCsrf(todosPath("/" + id + "/done"), "{\"done\":true}",
				session.access(), session.refresh());

		assertThat(response.statusCode()).as("PATCH status, body: " + response.body()).isEqualTo(200);
		JsonNode todo = json(response);
		assertContractFields(todo);
		assertThat(todo.path("done").asBoolean()).isTrue();
		assertThat(todo.path("doneAt").isNull()).as("doneAt is set").isFalse();
		Instant.parse(todo.path("doneAt").asString());
		assertThat(getOk(session, id).path("done").asBoolean()).isTrue();
	}

	@Test
	@DisplayName("PATCH /{id}/done with done false clears doneAt")
	void unmarkDoneClearsDoneAt() throws Exception {
		Session session = registerUser();
		String id = createTodo(session, "Undo me", today().plusDays(2), null, "HIGH").path("id").asString();
		setDone(session, id, true);

		JsonNode todo = setDone(session, id, false);

		assertThat(todo.path("done").asBoolean()).isFalse();
		assertNullField(todo, "doneAt");
	}

	@Test
	@DisplayName("PATCH /{id}/done of another user's todo returns 404 and leaves it not done")
	void markDoneOtherUsersTodoReturns404() throws Exception {
		Session owner = registerUser();
		Session intruder = registerUser();
		String id = createTodo(owner, "Owner's done", today().plusDays(2), null, "LOW").path("id").asString();

		HttpResponse<String> response = api().patchJsonWithCsrf(todosPath("/" + id + "/done"), "{\"done\":true}",
				intruder.access(), intruder.refresh());

		assertThat(response.statusCode()).as("PATCH status, body: " + response.body()).isEqualTo(404);
		assertThat(response.body()).contains(NOT_FOUND_MESSAGE);
		assertThat(getOk(owner, id).path("done").asBoolean()).isFalse();
	}

	// ---------------------------------------------------------------- delete

	@Test
	@DisplayName("DELETE returns 204 and the todo is then 404")
	void deleteReturns204ThenGetReturns404() throws Exception {
		Session session = registerUser();
		String id = createTodo(session, "Throw away", today().plusDays(2), null, "LOW").path("id").asString();

		HttpResponse<String> response = api().deleteWithCsrf(todosPath("/" + id), session.access(), session.refresh());

		assertThat(response.statusCode()).as("DELETE status, body: " + response.body()).isEqualTo(204);
		assertThat(response.body()).isEmpty();
		HttpResponse<String> after = getRaw(session, id);
		assertThat(after.statusCode()).as("GET after delete").isEqualTo(404);
		assertThat(after.body()).contains(NOT_FOUND_MESSAGE);
	}

	@Test
	@DisplayName("DELETE of another user's todo returns 404 and the owner keeps it")
	void deleteOtherUsersTodoReturns404() throws Exception {
		Session owner = registerUser();
		Session intruder = registerUser();
		String id = createTodo(owner, "Keep me", today().plusDays(2), null, "LOW").path("id").asString();

		HttpResponse<String> response = api().deleteWithCsrf(todosPath("/" + id), intruder.access(), intruder.refresh());

		assertThat(response.statusCode()).as("DELETE status, body: " + response.body()).isEqualTo(404);
		assertThat(response.body()).contains(NOT_FOUND_MESSAGE);
		assertThat(getOk(owner, id).path("name").asString()).isEqualTo("Keep me");
	}

	// ---------------------------------------------------------------- authentication and CSRF

	@Test
	@DisplayName("GET /api/todos range without a session returns 401")
	void unauthenticatedRangeReturns401() throws Exception {
		HttpResponse<String> response = api().get(todosPath("?from=" + today() + "&to=" + today()));

		assertThat(response.statusCode()).as("GET range status without session").isEqualTo(401);
	}

	@Test
	@DisplayName("GET /api/todos/{id} without a session returns 401")
	void unauthenticatedGetOneReturns401() throws Exception {
		HttpResponse<String> response = api().get(todosPath("/" + UUID.randomUUID()));

		assertThat(response.statusCode()).as("GET one status without session").isEqualTo(401);
	}

	@Test
	@DisplayName("POST /api/todos without a session returns 401")
	void unauthenticatedCreateReturns401() throws Exception {
		HttpResponse<String> response = api().postJsonWithCsrf(TODOS,
				todoBody("Anon", today().plusDays(2), null, "LOW"));

		assertThat(response.statusCode()).as("POST status without session").isEqualTo(401);
	}

	@Test
	@DisplayName("PUT /api/todos/{id} without a session returns 401")
	void unauthenticatedUpdateReturns401() throws Exception {
		HttpResponse<String> response = api().putJsonWithCsrf(todosPath("/" + UUID.randomUUID()),
				todoBody("Anon", today().plusDays(2), null, "LOW"));

		assertThat(response.statusCode()).as("PUT status without session").isEqualTo(401);
	}

	@Test
	@DisplayName("PATCH /api/todos/{id}/done without a session returns 401")
	void unauthenticatedDoneReturns401() throws Exception {
		HttpResponse<String> response = api().patchJsonWithCsrf(todosPath("/" + UUID.randomUUID() + "/done"),
				"{\"done\":true}");

		assertThat(response.statusCode()).as("PATCH status without session").isEqualTo(401);
	}

	@Test
	@DisplayName("DELETE /api/todos/{id} without a session returns 401")
	void unauthenticatedDeleteReturns401() throws Exception {
		HttpResponse<String> response = api().deleteWithCsrf(todosPath("/" + UUID.randomUUID()));

		assertThat(response.statusCode()).as("DELETE status without session").isEqualTo(401);
	}

	@Test
	@DisplayName("POST /api/todos without a CSRF token returns 403 and creates nothing")
	void createWithoutCsrfReturns403() throws Exception {
		Session session = registerUser();

		HttpResponse<String> response = api().postJson(TODOS, todoBody("No CSRF", today().plusDays(2), null, "LOW"),
				session.access(), session.refresh());

		assertThat(response.statusCode()).as("POST status without CSRF token").isEqualTo(403);
		assertThat(names(range(session, today(), today().plusDays(3)))).isEmpty();
	}

	@Test
	@DisplayName("PUT /api/todos/{id} without a CSRF token returns 403 and changes nothing")
	void updateWithoutCsrfReturns403() throws Exception {
		Session session = registerUser();
		String id = createTodo(session, "Original", today().plusDays(2), null, "LOW").path("id").asString();

		HttpResponse<String> response = api().putJson(todosPath("/" + id),
				todoBody("Changed", today().plusDays(2), null, "URGENT"), session.access(), session.refresh());

		assertThat(response.statusCode()).as("PUT status without CSRF token").isEqualTo(403);
		assertThat(getOk(session, id).path("name").asString()).isEqualTo("Original");
	}

	@Test
	@DisplayName("PATCH /api/todos/{id}/done without a CSRF token returns 403 and changes nothing")
	void doneWithoutCsrfReturns403() throws Exception {
		Session session = registerUser();
		String id = createTodo(session, "Not done", today().plusDays(2), null, "LOW").path("id").asString();

		HttpResponse<String> response = api().patchJson(todosPath("/" + id + "/done"), "{\"done\":true}",
				session.access(), session.refresh());

		assertThat(response.statusCode()).as("PATCH status without CSRF token").isEqualTo(403);
		assertThat(getOk(session, id).path("done").asBoolean()).isFalse();
	}

	@Test
	@DisplayName("DELETE /api/todos/{id} without a CSRF token returns 403 and keeps the todo")
	void deleteWithoutCsrfReturns403() throws Exception {
		Session session = registerUser();
		String id = createTodo(session, "Still here", today().plusDays(2), null, "LOW").path("id").asString();

		HttpResponse<String> response = api().delete(todosPath("/" + id), session.access(), session.refresh());

		assertThat(response.statusCode()).as("DELETE status without CSRF token").isEqualTo(403);
		assertThat(getOk(session, id).path("name").asString()).isEqualTo("Still here");
	}
}
