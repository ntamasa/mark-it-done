package com.ntamasa.markitdone.todo;

import static org.assertj.core.api.Assertions.assertThat;

import com.ntamasa.markitdone.support.AbstractPostgresIntegrationTest;
import com.ntamasa.markitdone.todo.dto.TodoDto;
import com.ntamasa.markitdone.todo.dto.TodoRequest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class TodoDtoJsonIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private JsonMapper jsonMapper;

  @Test
  @DisplayName("dates and times serialise as ISO strings")
  void serialisesIso() {
    Todo todo =
        new Todo(
            UUID.randomUUID(),
            "x",
            null,
            LocalDate.of(2026, 10, 21),
            LocalTime.of(14, 30),
            Priority.URGENT,
            null);
    TodoDto dto = TodoMapper.toDto(todo, ZoneId.of("UTC"));

    JsonNode json = jsonMapper.valueToTree(dto);

    assertThat(json.get("dueDate").asString()).isEqualTo("2026-10-21");
    assertThat(json.get("dueTime").asString()).isEqualTo("14:30:00");
    assertThat(json.get("priority").asString()).isEqualTo("URGENT");
    assertThat(json.get("done").isBoolean()).isTrue();
    assertThat(json.has("seriesId")).isTrue();
    assertThat(json.has("ruleSummary")).isTrue();
  }

  @Test
  @DisplayName("a request accepts 14:30 and 14:30:00 as the due time")
  void acceptsShortTime() {
    String body =
        "{\"name\":\"a\",\"dueDate\":\"2026-10-21\",\"dueTime\":\"%s\",\"priority\":\"LOW\"}";

    TodoRequest shortForm = jsonMapper.readValue(body.formatted("14:30"), TodoRequest.class);
    TodoRequest longForm = jsonMapper.readValue(body.formatted("14:30:00"), TodoRequest.class);

    assertThat(shortForm.dueTime()).isEqualTo(LocalTime.of(14, 30));
    assertThat(longForm.dueTime()).isEqualTo(LocalTime.of(14, 30));
    assertThat(shortForm.dueDate()).isEqualTo(LocalDate.of(2026, 10, 21));
  }
}
