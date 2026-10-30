package com.ntamasa.markitdone.blackbox;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;

import static com.ntamasa.markitdone.blackbox.ApiClient.assertProblem;
import static com.ntamasa.markitdone.blackbox.ApiClient.json;
import static com.ntamasa.markitdone.blackbox.TestAccounts.Session;
import static com.ntamasa.markitdone.blackbox.TestAccounts.canonicalErrorBody;
import static com.ntamasa.markitdone.blackbox.TestAccounts.jsonBody;
import static com.ntamasa.markitdone.blackbox.TestAccounts.register;
import static com.ntamasa.markitdone.blackbox.TestAccounts.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Black-box tests for the category API (MAR-66): GET, POST and PUT under /api/categories.
 * Every test registers its own users, so categories never leak between tests.
 * The rules under test: per-user case-insensitive name uniqueness, trimmed names, the 40-character
 * name limit, the required swatch from a fixed list of ten, sorted listing and 404 for other users' categories.
 */
@DisplayName("/api/categories")
class CategoriesIT extends AbstractIntegrationTest {

	private static final String CATEGORIES_PATH = "/api/categories";
	private static final String DUPLICATE_MESSAGE = "You already have a category with this name.";
	private static final String NOT_FOUND_MESSAGE = "Category not found";
	private static final List<String> SWATCHES = List.of(
			"TEAL", "SKY", "INDIGO", "VIOLET", "PINK", "ROSE", "ORANGE", "AMBER", "LIME", "SLATE");

	private ApiClient api() {
		return new ApiClient(baseUrl());
	}

	private Session registerUser() throws Exception {
		return register(baseUrl(), uniqueEmail());
	}

	/** GET /api/categories for the session, asserting 200. Returns the JSON array. */
	private JsonNode list(Session session) throws Exception {
		return api().getJsonOk(CATEGORIES_PATH, session.access(), session.refresh());
	}

	/** POST /api/categories with the session cookies and a fresh CSRF token, returning the raw response. */
	private HttpResponse<String> create(Session session, String name, String swatch) throws Exception {
		return api().postJsonWithCsrf(CATEGORIES_PATH, body(name, swatch), session.access(), session.refresh());
	}

	/** POST that must succeed with 201. Returns the created category. */
	private JsonNode createOk(Session session, String name, String swatch) throws Exception {
		HttpResponse<String> response = create(session, name, swatch);
		assertThat(response.statusCode()).as("POST status for '" + name + "', body: " + response.body())
				.isEqualTo(201);
		return json(response);
	}

	/** PUT /api/categories/{id} with the session cookies and a fresh CSRF token. */
	private HttpResponse<String> update(Session session, String id, String name, String swatch) throws Exception {
		return api().putJsonWithCsrf(CATEGORIES_PATH + "/" + id, body(name, swatch),
				session.access(), session.refresh());
	}

	private static String body(String name, String swatch) {
		return jsonBody("name", name, "swatch", swatch);
	}

	/** The names of a category array, in response order. */
	private static List<String> names(JsonNode categories) {
		List<String> names = new ArrayList<>();
		for (JsonNode category : categories) {
			names.add(category.path("name").asString());
		}
		return names;
	}

	// ---------------------------------------------------------------- create

	@Test
	@DisplayName("POST returns 201 with id, name, swatch and itemCount 0, and GET lists the category")
	void createReturnsCreatedCategoryAndListContainsIt() throws Exception {
		Session session = registerUser();

		HttpResponse<String> response = create(session, "Work", "TEAL");

		assertThat(response.statusCode()).as("POST status, body: " + response.body()).isEqualTo(201);
		JsonNode created = json(response);
		assertThat(created.path("id").asString()).as("id").isNotBlank();
		UUID.fromString(created.path("id").asString());
		assertThat(created.path("name").asString()).isEqualTo("Work");
		assertThat(created.path("swatch").asString()).isEqualTo("TEAL");
		assertThat(created.path("itemCount").asInt()).as("itemCount of a new category").isZero();

		assertThat(list(session))
				.anySatisfy(category -> {
					assertThat(category.path("id").asString()).isEqualTo(created.path("id").asString());
					assertThat(category.path("name").asString()).isEqualTo("Work");
					assertThat(category.path("swatch").asString()).isEqualTo("TEAL");
					assertThat(category.path("itemCount").asInt()).isZero();
				});
	}

	@Test
	@DisplayName("GET returns the user's categories sorted by name, case-insensitively")
	void listIsSortedByNameCaseInsensitively() throws Exception {
		Session session = registerUser();
		createOk(session, "beta", "SKY");
		createOk(session, "Alpha", "PINK");
		createOk(session, "gamma", "LIME");

		JsonNode categories = list(session);

		assertThat(names(categories)).containsExactly("Alpha", "beta", "gamma");
	}

	@Test
	@DisplayName("GET never shows another user's categories")
	void listOnlyContainsOwnCategories() throws Exception {
		Session alice = registerUser();
		Session bob = registerUser();
		createOk(alice, "Alice private", "INDIGO");
		createOk(bob, "Bob private", "AMBER");

		assertThat(names(list(alice))).containsExactly("Alice private");
		assertThat(names(list(bob))).containsExactly("Bob private");
	}

	@Test
	@DisplayName("A surrounding-space name is trimmed: '  Home  ' is stored as 'Home'")
	void nameIsTrimmed() throws Exception {
		Session session = registerUser();

		JsonNode created = createOk(session, "  Home  ", "ROSE");

		assertThat(created.path("name").asString()).isEqualTo("Home");
		assertThat(names(list(session))).containsExactly("Home");
	}

	@ParameterizedTest(name = "duplicate name \"{0}\" of an existing \"Work\" is rejected")
	@ValueSource(strings = {"work", " WORK "})
	@DisplayName("POST with a name already used in another case or with spaces returns 409 CATEGORY_NAME_TAKEN")
	void duplicateNameIsRejected(String duplicate) throws Exception {
		Session session = registerUser();
		createOk(session, "Work", "TEAL");

		HttpResponse<String> response = create(session, duplicate, "VIOLET");

		assertProblem(response, 409, "CATEGORY_NAME_TAKEN");
		assertThat(response.body()).contains(DUPLICATE_MESSAGE);
		assertThat(names(list(session))).containsExactly("Work");
	}

	@Test
	@DisplayName("The same name is allowed for a different user and returns 201")
	void sameNameForAnotherUserIsAllowed() throws Exception {
		Session alice = registerUser();
		Session bob = registerUser();
		createOk(alice, "Work", "TEAL");

		HttpResponse<String> response = create(bob, "Work", "TEAL");

		assertThat(response.statusCode()).as("POST status for the second user, body: " + response.body())
				.isEqualTo(201);
		assertThat(names(list(bob))).containsExactly("Work");
	}

	// ---------------------------------------------------------------- update

	@Test
	@DisplayName("PUT with a new name and swatch returns 200 with the new values, and GET shows them")
	void updateChangesNameAndSwatch() throws Exception {
		Session session = registerUser();
		String id = createOk(session, "Work", "TEAL").path("id").asString();

		HttpResponse<String> response = update(session, id, "Office", "INDIGO");

		assertThat(response.statusCode()).as("PUT status, body: " + response.body()).isEqualTo(200);
		JsonNode updated = json(response);
		assertThat(updated.path("id").asString()).isEqualTo(id);
		assertThat(updated.path("name").asString()).isEqualTo("Office");
		assertThat(updated.path("swatch").asString()).isEqualTo("INDIGO");
		assertThat(updated.path("itemCount").asInt()).isZero();

		assertThat(list(session))
				.anySatisfy(category -> {
					assertThat(category.path("id").asString()).isEqualTo(id);
					assertThat(category.path("name").asString()).isEqualTo("Office");
					assertThat(category.path("swatch").asString()).isEqualTo("INDIGO");
				});
	}

	@Test
	@DisplayName("PUT renaming a category to its own name in a different case returns 200")
	void renameToOwnNameInDifferentCaseIsAllowed() throws Exception {
		Session session = registerUser();
		String id = createOk(session, "Work", "TEAL").path("id").asString();

		HttpResponse<String> response = update(session, id, "WORK", "TEAL");

		assertThat(response.statusCode()).as("PUT status, body: " + response.body()).isEqualTo(200);
		assertThat(json(response).path("name").asString()).isEqualTo("WORK");
		assertThat(names(list(session))).containsExactly("WORK");
	}

	@Test
	@DisplayName("PUT renaming a category to another existing category's name returns 409 CATEGORY_NAME_TAKEN")
	void renameToAnotherCategoriesNameIsRejected() throws Exception {
		Session session = registerUser();
		createOk(session, "Work", "TEAL");
		String homeId = createOk(session, "Home", "ROSE").path("id").asString();

		HttpResponse<String> response = update(session, homeId, "work", "ROSE");

		assertProblem(response, 409, "CATEGORY_NAME_TAKEN");
		assertThat(response.body()).contains(DUPLICATE_MESSAGE);
		assertThat(names(list(session))).containsExactly("Home", "Work");
	}

	@Test
	@DisplayName("PUT by another user on someone else's category returns 404 and changes nothing")
	void otherUserCannotUpdateCategory() throws Exception {
		Session owner = registerUser();
		Session intruder = registerUser();
		String id = createOk(owner, "Work", "TEAL").path("id").asString();

		HttpResponse<String> response = update(intruder, id, "Hijacked", "SLATE");

		assertThat(response.statusCode()).as("PUT status, body: " + response.body()).isEqualTo(404);
		assertThat(response.body()).contains(NOT_FOUND_MESSAGE);
		assertThat(names(list(owner))).containsExactly("Work");
	}

	@Test
	@DisplayName("PUT on another user's category and on a random UUID return the same 404 body")
	void notFoundBodyDoesNotRevealOwnership() throws Exception {
		Session owner = registerUser();
		Session intruder = registerUser();
		String id = createOk(owner, "Work", "TEAL").path("id").asString();

		HttpResponse<String> foreign = update(intruder, id, "Hijacked", "SLATE");
		HttpResponse<String> random = update(intruder, UUID.randomUUID().toString(), "Hijacked", "SLATE");

		assertThat(foreign.statusCode()).isEqualTo(404);
		assertThat(random.statusCode()).isEqualTo(404);
		Map<String, String> foreignBody = canonicalErrorBody(foreign);
		Map<String, String> randomBody = canonicalErrorBody(random);
		assertThat(foreignBody).isEqualTo(randomBody);
	}

	// ---------------------------------------------------------------- validation

	@Test
	@DisplayName("POST with a 41-character name returns 400 VALIDATION_FAILED")
	void createWithTooLongNameIsRejected() throws Exception {
		Session session = registerUser();

		HttpResponse<String> response = create(session, "a".repeat(41), "TEAL");

		assertProblem(response, 400, "VALIDATION_FAILED");
		assertThat(list(session)).isEmpty();
	}

	@Test
	@DisplayName("POST with a 40-character name is accepted")
	void createWithMaximumLengthNameIsAccepted() throws Exception {
		Session session = registerUser();
		String name = "b".repeat(40);

		HttpResponse<String> response = create(session, name, "TEAL");

		assertThat(response.statusCode()).as("POST status, body: " + response.body()).isEqualTo(201);
		assertThat(json(response).path("name").asString()).isEqualTo(name);
	}

	@Test
	@DisplayName("PUT with a 41-character name returns 400 VALIDATION_FAILED and changes nothing")
	void updateWithTooLongNameIsRejected() throws Exception {
		Session session = registerUser();
		String id = createOk(session, "Work", "TEAL").path("id").asString();

		HttpResponse<String> response = update(session, id, "a".repeat(41), "TEAL");

		assertProblem(response, 400, "VALIDATION_FAILED");
		assertThat(names(list(session))).containsExactly("Work");
	}

	@ParameterizedTest(name = "blank name \"{0}\" is rejected")
	@ValueSource(strings = {"", "   "})
	@DisplayName("POST with a blank name returns 400")
	void createWithBlankNameIsRejected(String name) throws Exception {
		Session session = registerUser();

		HttpResponse<String> response = create(session, name, "TEAL");

		assertThat(response.statusCode()).as("POST status, body: " + response.body()).isEqualTo(400);
		assertThat(list(session)).isEmpty();
	}

	@Test
	@DisplayName("POST without a swatch returns 400")
	void createWithoutSwatchIsRejected() throws Exception {
		Session session = registerUser();

		HttpResponse<String> response = api().postJsonWithCsrf(CATEGORIES_PATH, jsonBody("name", "Work"),
				session.access(), session.refresh());

		assertThat(response.statusCode()).as("POST status, body: " + response.body()).isEqualTo(400);
		assertThat(list(session)).isEmpty();
	}

	@Test
	@DisplayName("POST with an unknown swatch NEON returns 400")
	void createWithUnknownSwatchIsRejected() throws Exception {
		Session session = registerUser();

		HttpResponse<String> response = create(session, "Work", "NEON");

		assertThat(response.statusCode()).as("POST status, body: " + response.body()).isEqualTo(400);
		assertThat(list(session)).isEmpty();
	}

	@ParameterizedTest(name = "swatch {0} is accepted")
	@ValueSource(strings = {"TEAL", "SKY", "INDIGO", "VIOLET", "PINK", "ROSE", "ORANGE", "AMBER", "LIME", "SLATE"})
	@DisplayName("POST accepts each of the ten swatches")
	void everySwatchIsAccepted(String swatch) throws Exception {
		Session session = registerUser();

		HttpResponse<String> response = create(session, "Category " + swatch, swatch);

		assertThat(response.statusCode()).as("POST status for " + swatch + ", body: " + response.body())
				.isEqualTo(201);
		assertThat(json(response).path("swatch").asString()).isEqualTo(swatch);
	}

	@Test
	@DisplayName("The fixed swatch list contains exactly the ten documented swatches")
	void allTenSwatchesAreAcceptedTogether() throws Exception {
		Session session = registerUser();
		for (String swatch : SWATCHES) {
			createOk(session, "Cat " + swatch, swatch);
		}

		assertThat(list(session)).hasSize(SWATCHES.size());
	}

	// ---------------------------------------------------------------- authentication and CSRF

	@Test
	@DisplayName("GET /api/categories without a session returns 401")
	void unauthenticatedListReturns401() throws Exception {
		HttpResponse<String> response = api().get(CATEGORIES_PATH);

		assertThat(response.statusCode()).as("GET status without session").isEqualTo(401);
	}

	@Test
	@DisplayName("POST /api/categories without a session returns 401")
	void unauthenticatedCreateReturns401() throws Exception {
		HttpResponse<String> response = api().postJsonWithCsrf(CATEGORIES_PATH, body("Work", "TEAL"));

		assertThat(response.statusCode()).as("POST status without session").isEqualTo(401);
	}

	@Test
	@DisplayName("PUT /api/categories/{id} without a session returns 401")
	void unauthenticatedUpdateReturns401() throws Exception {
		HttpResponse<String> response = api().putJsonWithCsrf(
				CATEGORIES_PATH + "/" + UUID.randomUUID(), body("Work", "TEAL"));

		assertThat(response.statusCode()).as("PUT status without session").isEqualTo(401);
	}

	@Test
	@DisplayName("POST without a CSRF token returns 403 and creates nothing")
	void createWithoutCsrfReturns403() throws Exception {
		Session session = registerUser();

		HttpResponse<String> response = api().postJson(CATEGORIES_PATH, body("Work", "TEAL"),
				session.access(), session.refresh());

		assertThat(response.statusCode()).as("POST status without CSRF token").isEqualTo(403);
		assertThat(list(session)).isEmpty();
	}

	@Test
	@DisplayName("PUT without a CSRF token returns 403 and changes nothing")
	void updateWithoutCsrfReturns403() throws Exception {
		Session session = registerUser();
		String id = createOk(session, "Work", "TEAL").path("id").asString();

		HttpResponse<String> response = api().putJson(CATEGORIES_PATH + "/" + id, body("Office", "SKY"),
				session.access(), session.refresh());

		assertThat(response.statusCode()).as("PUT status without CSRF token").isEqualTo(403);
		assertThat(names(list(session))).containsExactly("Work");
	}
}
