package com.ntamasa.markitdone.blackbox;

import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static com.ntamasa.markitdone.blackbox.ApiClient.assertProblem;
import static com.ntamasa.markitdone.blackbox.ApiClient.json;
import static com.ntamasa.markitdone.blackbox.SeriesTestSupport.endingAfter;
import static com.ntamasa.markitdone.blackbox.SeriesTestSupport.everyNDays;
import static com.ntamasa.markitdone.blackbox.SeriesTestSupport.nextMonday;
import static com.ntamasa.markitdone.blackbox.SeriesTestSupport.today;
import static com.ntamasa.markitdone.blackbox.TestAccounts.Session;
import static com.ntamasa.markitdone.blackbox.TestAccounts.jsonBody;
import static com.ntamasa.markitdone.blackbox.TestAccounts.register;
import static com.ntamasa.markitdone.blackbox.TestAccounts.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Black-box tests for category usage, itemCount and the delete-with-reassignment endpoint (MAR-68).
 * Endpoints: GET /api/categories (itemCount), GET /api/categories/{id}/usage,
 * POST /api/categories/{id}/delete with {assignments: [{kind: TODO | SERIES, id, categoryId | null}]}.
 * Every test registers its own users and builds its own categories, one-off todos and a series.
 * The usage body shape is not fixed by the brief, so usage checks look for objects by their "id" field.
 */
@DisplayName("/api/categories/{id}/usage and /api/categories/{id}/delete")
class CategoryDeleteIT extends AbstractIntegrationTest {

	private static final String CATEGORIES = "/api/categories";
	private static final String SERIES = "/api/series";
	private static final String TODOS = "/api/todos";
	private static final String RULE_SUMMARY = "Every day · 3 times";

	private static final ObjectMapper JSON = new ObjectMapper();

	/** A user with Work (holding 2 one-offs and 1 series of 3 occurrences) and an empty Home. */
	private record Seeded(Session user, String workId, String homeId, String todo1, String todo2,
			String seriesId, List<String> occurrenceIds, LocalDate todo1Date, LocalDate todo2Date) {
	}

	private ApiClient api() {
		return new ApiClient(baseUrl());
	}

	private SeriesTestSupport support() {
		return new SeriesTestSupport(baseUrl());
	}

	private Session registerUser() throws Exception {
		return register(baseUrl(), uniqueEmail());
	}

	// ---------------------------------------------------------------- setup helpers

	private String createCategory(Session session, String name, String swatch) throws Exception {
		HttpResponse<String> response = api().postJsonWithCsrf(CATEGORIES,
				jsonBody("name", name, "swatch", swatch), session.access(), session.refresh());
		assertThat(response.statusCode()).as("POST /api/categories status, body: " + response.body()).isEqualTo(201);
		return json(response).path("id").asString();
	}

	/** A one-off todo, optionally in a category (null leaves it without one). */
	private String createTodo(Session session, String name, LocalDate dueDate, String categoryId) throws Exception {
		HttpResponse<String> response = api().postJsonWithCsrf(TODOS,
				jsonBody("name", name, "dueDate", dueDate.toString(), "priority", "LOW", "categoryId", categoryId),
				session.access(), session.refresh());
		assertThat(response.statusCode()).as("POST /api/todos status, body: " + response.body()).isEqualTo(201);
		return json(response).path("id").asString();
	}

	/** A daily series that ends after 3 occurrences, starting on the given date, in the given category. */
	private String createSeries(Session session, String name, String categoryId, LocalDate start) throws Exception {
		HttpResponse<String> response = api().postJsonWithCsrf(SERIES,
				jsonBody("name", name, "dueTime", "18:00", "priority", "MEDIUM", "categoryId", categoryId,
						"rule", endingAfter(everyNDays(1, start), 3)),
				session.access(), session.refresh());
		assertThat(response.statusCode()).as("POST /api/series status, body: " + response.body()).isEqualTo(201);
		return json(response).path("id").asString();
	}

	/**
	 * Work with two one-offs and a daily series of 3 occurrences starting on the given date, plus an empty Home.
	 * The one-offs fall on the days after the next Monday, so they are in the future.
	 */
	private Seeded seed(LocalDate seriesStart) throws Exception {
		Session user = registerUser();
		String work = createCategory(user, "Work", "TEAL");
		String home = createCategory(user, "Home", "ROSE");
		LocalDate todo1Date = nextMonday().plusDays(4);
		LocalDate todo2Date = nextMonday().plusDays(5);
		String todo1 = createTodo(user, "Buy milk", todo1Date, work);
		String todo2 = createTodo(user, "Send invoice", todo2Date, work);
		String series = createSeries(user, "Stand-up", work, seriesStart);
		List<String> occurrences = occurrencesOf(user, series);
		assertThat(occurrences).as("occurrences of the seeded series").hasSize(3);
		return new Seeded(user, work, home, todo1, todo2, series, occurrences, todo1Date, todo2Date);
	}

	private Seeded seed() throws Exception {
		return seed(nextMonday());
	}

	// ---------------------------------------------------------------- read helpers

	private JsonNode categoryList(Session session) throws Exception {
		return api().getJsonOk(CATEGORIES, session.access(), session.refresh());
	}

	private Optional<JsonNode> categoryNode(Session session, String id) throws Exception {
		for (JsonNode category : categoryList(session)) {
			if (id.equals(category.path("id").asString())) {
				return Optional.of(category);
			}
		}
		return Optional.empty();
	}

	private int itemCount(Session session, String id) throws Exception {
		JsonNode category = categoryNode(session, id)
				.orElseThrow(() -> new AssertionError("category " + id + " is not in the list"));
		return category.path("itemCount").asInt();
	}

	private HttpResponse<String> usage(Session session, String categoryId) throws Exception {
		return api().get(CATEGORIES + "/" + categoryId + "/usage", session.access(), session.refresh());
	}

	private JsonNode usageOk(Session session, String categoryId) throws Exception {
		HttpResponse<String> response = usage(session, categoryId);
		assertThat(response.statusCode()).as("GET usage status, body: " + response.body()).isEqualTo(200);
		return json(response);
	}

	private JsonNode getSeries(Session session, String seriesId) throws Exception {
		return api().getJsonOk(SERIES + "/" + seriesId, session.access(), session.refresh());
	}

	/** Every todo row (one-offs and occurrences) in a window that covers all seeded dates, keyed by id. */
	private Map<String, JsonNode> rowsById(Session session) throws Exception {
		Map<String, JsonNode> rows = new TreeMap<>();
		for (JsonNode row : support().rows(session, today().minusDays(10), nextMonday().plusDays(10))) {
			rows.put(row.path("id").asString(), row);
		}
		return rows;
	}

	/** The ids of the occurrence rows that belong to the series. */
	private List<String> occurrencesOf(Session session, String seriesId) throws Exception {
		List<String> ids = new ArrayList<>();
		for (JsonNode row : support().rows(session, today().minusDays(10), nextMonday().plusDays(10))) {
			if (seriesId.equals(row.path("seriesId").asString())) {
				ids.add(row.path("id").asString());
			}
		}
		return ids;
	}

	/** The categoryId of a todo, series or category node; null when it is JSON null. */
	private static String categoryIdOf(JsonNode node) {
		JsonNode categoryId = node.path("categoryId");
		return categoryId.isNull() || categoryId.isMissingNode() ? null : categoryId.asString();
	}

	// ---------------------------------------------------------------- delete helpers

	/** One assignment; a null categoryId is sent as JSON null, which means "no category". */
	private static Map<String, Object> assignment(String kind, String id, String categoryId) {
		Map<String, Object> assignment = new LinkedHashMap<>();
		assignment.put("kind", kind);
		assignment.put("id", id);
		assignment.put("categoryId", categoryId);
		return assignment;
	}

	private static String deleteBody(List<Map<String, Object>> assignments) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("assignments", assignments);
		return JSON.writeValueAsString(body);
	}

	private HttpResponse<String> deleteCategory(Session session, String categoryId,
			List<Map<String, Object>> assignments) throws Exception {
		return api().postJsonWithCsrf(CATEGORIES + "/" + categoryId + "/delete", deleteBody(assignments),
				session.access(), session.refresh());
	}

	/** Recursively collects every JSON object in the tree. */
	private static void collectObjects(JsonNode node, List<JsonNode> out) {
		if (node.isObject()) {
			out.add(node);
			for (String name : node.propertyNames()) {
				collectObjects(node.path(name), out);
			}
		} else if (node.isArray()) {
			for (JsonNode child : node) {
				collectObjects(child, out);
			}
		}
	}

	/** The first object anywhere in the body whose "id" equals the given id. */
	private static Optional<JsonNode> objectWithId(JsonNode root, String id) {
		List<JsonNode> objects = new ArrayList<>();
		collectObjects(root, objects);
		return objects.stream().filter(object -> id.equals(object.path("id").asString())).findFirst();
	}

	/** Asserts that Work still holds everything it held before the rejected request. */
	private void assertNothingChanged(Seeded s) throws Exception {
		assertThat(categoryNode(s.user(), s.workId())).as("Work still exists").isPresent();
		assertThat(itemCount(s.user(), s.workId())).as("Work itemCount").isEqualTo(3);
		assertThat(itemCount(s.user(), s.homeId())).as("Home itemCount").isZero();
		Map<String, JsonNode> rows = rowsById(s.user());
		assertThat(categoryIdOf(rows.get(s.todo1()))).as("todo1 category").isEqualTo(s.workId());
		assertThat(categoryIdOf(rows.get(s.todo2()))).as("todo2 category").isEqualTo(s.workId());
		for (String occurrence : s.occurrenceIds()) {
			assertThat(categoryIdOf(rows.get(occurrence))).as("occurrence " + occurrence + " category")
					.isEqualTo(s.workId());
		}
		assertThat(categoryIdOf(getSeries(s.user(), s.seriesId()))).as("series category").isEqualTo(s.workId());
	}

	// ---------------------------------------------------------------- itemCount

	@Test
	@DisplayName("GET /api/categories: Work itemCount is 3 (2 one-offs + 1 series, occurrences not counted) and Home is 0")
	void itemCountCountsOneOffsAndSeriesOnly() throws Exception {
		Seeded s = seed();

		assertThat(itemCount(s.user(), s.workId())).isEqualTo(3);
		assertThat(itemCount(s.user(), s.homeId())).isZero();
	}

	// ---------------------------------------------------------------- usage

	@Test
	@DisplayName("GET /api/categories/{id}/usage lists the 2 one-offs (id, name, dueDate) and the series (id, name, ruleSummary)")
	void usageListsOneOffsAndSeries() throws Exception {
		Seeded s = seed();

		JsonNode usage = usageOk(s.user(), s.workId());

		JsonNode todo1 = objectWithId(usage, s.todo1())
				.orElseThrow(() -> new AssertionError("todo1 is not in usage: " + usage));
		assertThat(todo1.path("name").asString()).isEqualTo("Buy milk");
		assertThat(todo1.path("dueDate").asString()).isEqualTo(s.todo1Date().toString());

		JsonNode todo2 = objectWithId(usage, s.todo2())
				.orElseThrow(() -> new AssertionError("todo2 is not in usage: " + usage));
		assertThat(todo2.path("name").asString()).isEqualTo("Send invoice");
		assertThat(todo2.path("dueDate").asString()).isEqualTo(s.todo2Date().toString());

		JsonNode series = objectWithId(usage, s.seriesId())
				.orElseThrow(() -> new AssertionError("series is not in usage: " + usage));
		assertThat(series.path("name").asString()).isEqualTo("Stand-up");
		assertThat(series.path("ruleSummary").asString()).isEqualTo(RULE_SUMMARY);
	}

	@Test
	@DisplayName("GET /api/categories/{id}/usage does not list the series' occurrences as separate items")
	void usageDoesNotListOccurrences() throws Exception {
		Seeded s = seed();

		JsonNode usage = usageOk(s.user(), s.workId());

		for (String occurrence : s.occurrenceIds()) {
			assertThat(objectWithId(usage, occurrence))
					.as("occurrence " + occurrence + " must not appear in usage")
					.isEmpty();
		}
	}

	@Test
	@DisplayName("GET /api/categories/{id}/usage for an empty category returns 200")
	void usageOfEmptyCategoryReturnsOk() throws Exception {
		Seeded s = seed();

		HttpResponse<String> response = usage(s.user(), s.homeId());

		assertThat(response.statusCode()).as("usage of Home, body: " + response.body()).isEqualTo(200);
		assertThat(objectWithId(json(response), s.todo1())).isEmpty();
		assertThat(objectWithId(json(response), s.seriesId())).isEmpty();
	}

	// ---------------------------------------------------------------- delete: success

	@Test
	@DisplayName("POST /api/categories/{id}/delete moves todo1 and the series (with all occurrences) to Home, leaves todo2 uncategorised, deletes Work")
	void deleteReassignsChosenItemsAndLeavesUnlistedOnesUncategorised() throws Exception {
		Seeded s = seed();

		HttpResponse<String> response = deleteCategory(s.user(), s.workId(), List.of(
				assignment("TODO", s.todo1(), s.homeId()),
				assignment("SERIES", s.seriesId(), s.homeId())));

		assertThat(response.statusCode()).as("delete status, body: " + response.body()).isEqualTo(204);

		Map<String, JsonNode> rows = rowsById(s.user());
		assertThat(categoryIdOf(rows.get(s.todo1()))).as("todo1 moved to Home").isEqualTo(s.homeId());
		assertThat(categoryIdOf(rows.get(s.todo2()))).as("todo2 left out, no category").isNull();
		for (String occurrence : s.occurrenceIds()) {
			assertThat(categoryIdOf(rows.get(occurrence))).as("occurrence " + occurrence + " moved to Home")
					.isEqualTo(s.homeId());
		}
		assertThat(categoryIdOf(getSeries(s.user(), s.seriesId()))).as("series moved to Home")
				.isEqualTo(s.homeId());

		assertThat(categoryNode(s.user(), s.workId())).as("Work is gone from the list").isEmpty();
		assertThat(itemCount(s.user(), s.homeId())).as("Home itemCount (todo1 + series)").isEqualTo(2);
	}

	@Test
	@DisplayName("A series reassigned to Home also moves its past occurrences")
	void deleteMovesPastOccurrencesWithSeries() throws Exception {
		Seeded s = seed(today().minusDays(5));
		assertThat(s.occurrenceIds()).hasSize(3);

		HttpResponse<String> response = deleteCategory(s.user(), s.workId(), List.of(
				assignment("SERIES", s.seriesId(), s.homeId())));

		assertThat(response.statusCode()).as("delete status, body: " + response.body()).isEqualTo(204);
		Map<String, JsonNode> rows = rowsById(s.user());
		for (String occurrence : s.occurrenceIds()) {
			assertThat(categoryIdOf(rows.get(occurrence))).as("past occurrence " + occurrence + " moved to Home")
					.isEqualTo(s.homeId());
		}
		assertThat(categoryIdOf(getSeries(s.user(), s.seriesId()))).isEqualTo(s.homeId());
	}

	@Test
	@DisplayName("An assignment with categoryId null leaves the todo and the series without a category")
	void nullCategoryIdLeavesItemsUncategorised() throws Exception {
		Seeded s = seed();

		HttpResponse<String> response = deleteCategory(s.user(), s.workId(), List.of(
				assignment("TODO", s.todo1(), null),
				assignment("SERIES", s.seriesId(), null)));

		assertThat(response.statusCode()).as("delete status, body: " + response.body()).isEqualTo(204);
		Map<String, JsonNode> rows = rowsById(s.user());
		assertThat(categoryIdOf(rows.get(s.todo1()))).as("todo1 has no category").isNull();
		assertThat(categoryIdOf(getSeries(s.user(), s.seriesId()))).as("series has no category").isNull();
		for (String occurrence : s.occurrenceIds()) {
			assertThat(categoryIdOf(rows.get(occurrence))).as("occurrence " + occurrence + " has no category")
					.isNull();
		}
		assertThat(categoryNode(s.user(), s.workId())).as("Work is gone from the list").isEmpty();
	}

	@Test
	@DisplayName("POST /api/categories/{id}/delete with empty assignments returns 204 and uncategorises everything in Work")
	void emptyAssignmentsUncategorisesEverythingInWork() throws Exception {
		Seeded s = seed();

		HttpResponse<String> response = deleteCategory(s.user(), s.workId(), List.of());

		assertThat(response.statusCode()).as("delete status, body: " + response.body()).isEqualTo(204);
		Map<String, JsonNode> rows = rowsById(s.user());
		assertThat(categoryIdOf(rows.get(s.todo1()))).isNull();
		assertThat(categoryIdOf(rows.get(s.todo2()))).isNull();
		for (String occurrence : s.occurrenceIds()) {
			assertThat(categoryIdOf(rows.get(occurrence))).isNull();
		}
		assertThat(categoryIdOf(getSeries(s.user(), s.seriesId()))).isNull();
		assertThat(categoryNode(s.user(), s.workId())).as("Work is gone from the list").isEmpty();
		assertThat(itemCount(s.user(), s.homeId())).as("Home itemCount").isZero();
	}

	// ---------------------------------------------------------------- delete: rejected, nothing changes

	@Test
	@DisplayName("An assignment targeting the category being deleted returns 400 VALIDATION_FAILED and changes nothing")
	void assignmentTargetingDeletedCategoryIsRejected() throws Exception {
		Seeded s = seed();

		HttpResponse<String> response = deleteCategory(s.user(), s.workId(), List.of(
				assignment("TODO", s.todo1(), s.workId())));

		assertProblem(response, 400, "VALIDATION_FAILED");
		assertNothingChanged(s);
	}

	@Test
	@DisplayName("An assignment targeting another user's category returns 400 VALIDATION_FAILED and changes nothing")
	void assignmentTargetingForeignCategoryIsRejected() throws Exception {
		Seeded s = seed();
		Session bob = registerUser();
		String bobCategory = createCategory(bob, "Bob's Home", "SKY");

		HttpResponse<String> response = deleteCategory(s.user(), s.workId(), List.of(
				assignment("TODO", s.todo1(), bobCategory)));

		assertProblem(response, 400, "VALIDATION_FAILED");
		assertNothingChanged(s);
	}

	@Test
	@DisplayName("An assignment targeting a random category id returns 400 VALIDATION_FAILED and changes nothing")
	void assignmentTargetingUnknownCategoryIsRejected() throws Exception {
		Seeded s = seed();

		HttpResponse<String> response = deleteCategory(s.user(), s.workId(), List.of(
				assignment("SERIES", s.seriesId(), UUID.randomUUID().toString())));

		assertProblem(response, 400, "VALIDATION_FAILED");
		assertNothingChanged(s);
	}

	@Test
	@DisplayName("An assignment of a one-off that is in Home, not in the deleted category, returns 400 and changes nothing")
	void assignmentOfTodoOutsideCategoryIsRejected() throws Exception {
		Seeded s = seed();
		String homeTodo = createTodo(s.user(), "Water plants", nextMonday().plusDays(6), s.homeId());

		HttpResponse<String> response = deleteCategory(s.user(), s.workId(), List.of(
				assignment("TODO", homeTodo, s.homeId())));

		assertProblem(response, 400, "VALIDATION_FAILED");
		assertNothingChanged(s);
		assertThat(categoryIdOf(rowsById(s.user()).get(homeTodo))).as("Home todo unchanged").isEqualTo(s.homeId());
	}

	@Test
	@DisplayName("An assignment of another user's todo returns 400 and changes nothing")
	void assignmentOfForeignTodoIsRejected() throws Exception {
		Seeded s = seed();
		Session bob = registerUser();
		String bobTodo = createTodo(bob, "Bob's todo", nextMonday().plusDays(6), null);

		HttpResponse<String> response = deleteCategory(s.user(), s.workId(), List.of(
				assignment("TODO", bobTodo, s.homeId())));

		assertProblem(response, 400, "VALIDATION_FAILED");
		assertNothingChanged(s);
	}

	// ---------------------------------------------------------------- ownership and not found

	@Test
	@DisplayName("Another user's usage and delete of a category return 404, and the owner's data is unchanged")
	void otherUserCannotUseOrDeleteCategory() throws Exception {
		Seeded s = seed();
		Session intruder = registerUser();

		HttpResponse<String> usageResponse = usage(intruder, s.workId());
		HttpResponse<String> deleteResponse = deleteCategory(intruder, s.workId(), List.of());

		assertThat(usageResponse.statusCode()).as("usage by another user, body: " + usageResponse.body())
				.isEqualTo(404);
		assertThat(deleteResponse.statusCode()).as("delete by another user, body: " + deleteResponse.body())
				.isEqualTo(404);
		assertNothingChanged(s);
	}

	@Test
	@DisplayName("Usage and delete of a random category id return 404")
	void randomCategoryIdReturnsNotFound() throws Exception {
		Seeded s = seed();
		String randomId = UUID.randomUUID().toString();

		HttpResponse<String> usageResponse = usage(s.user(), randomId);
		HttpResponse<String> deleteResponse = deleteCategory(s.user(), randomId, List.of());

		assertThat(usageResponse.statusCode()).as("usage of random id, body: " + usageResponse.body())
				.isEqualTo(404);
		assertThat(deleteResponse.statusCode()).as("delete of random id, body: " + deleteResponse.body())
				.isEqualTo(404);
		assertNothingChanged(s);
	}

	@Test
	@DisplayName("The 404 for another user's category and for a random id have the same body")
	void notFoundBodyDoesNotRevealOwnership() throws Exception {
		Seeded s = seed();
		Session intruder = registerUser();

		HttpResponse<String> foreign = usage(intruder, s.workId());
		HttpResponse<String> random = usage(intruder, UUID.randomUUID().toString());

		assertThat(foreign.statusCode()).isEqualTo(404);
		assertThat(random.statusCode()).isEqualTo(404);
		assertThat(json(foreign).path("status").asInt()).isEqualTo(json(random).path("status").asInt());
		assertThat(json(foreign).path("code").asString()).isEqualTo(json(random).path("code").asString());
	}

	// ---------------------------------------------------------------- authentication and CSRF

	@Test
	@DisplayName("GET /api/categories/{id}/usage without a session returns 401")
	void unauthenticatedUsageReturns401() throws Exception {
		Seeded s = seed();

		HttpResponse<String> response = api().get(CATEGORIES + "/" + s.workId() + "/usage");

		assertThat(response.statusCode()).as("usage status without session").isEqualTo(401);
	}

	@Test
	@DisplayName("POST /api/categories/{id}/delete without a session returns 401 and changes nothing")
	void unauthenticatedDeleteReturns401() throws Exception {
		Seeded s = seed();

		HttpResponse<String> response = api().postJsonWithCsrf(CATEGORIES + "/" + s.workId() + "/delete",
				deleteBody(List.of(assignment("TODO", s.todo1(), s.homeId()))));

		assertThat(response.statusCode()).as("delete status without session").isEqualTo(401);
		assertNothingChanged(s);
	}

	@Test
	@DisplayName("POST /api/categories/{id}/delete without a CSRF token returns 403 and changes nothing")
	void deleteWithoutCsrfReturns403() throws Exception {
		Seeded s = seed();

		HttpResponse<String> response = api().postJson(CATEGORIES + "/" + s.workId() + "/delete",
				deleteBody(List.of(assignment("TODO", s.todo1(), s.homeId()))),
				s.user().access(), s.user().refresh());

		assertThat(response.statusCode()).as("delete status without CSRF token").isEqualTo(403);
		assertNothingChanged(s);
	}
}
