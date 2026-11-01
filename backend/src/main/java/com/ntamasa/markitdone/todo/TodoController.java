package com.ntamasa.markitdone.todo;

import com.ntamasa.markitdone.auth.CurrentUser;
import com.ntamasa.markitdone.todo.dto.DoneRequest;
import com.ntamasa.markitdone.todo.dto.TodoDto;
import com.ntamasa.markitdone.todo.dto.TodoRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints for the one-off todos of the current user. */
@RestController
@RequestMapping("/api/todos")
public class TodoController {

  private final TodoService todoService;

  /**
   * Create a TodoController.
   *
   * @param todoService the todo service
   */
  public TodoController(TodoService todoService) {
    this.todoService = todoService;
  }

  /**
   * Lists the current user's todos due between two dates, both inclusive.
   *
   * @param from the first date, ISO format YYYY-MM-DD
   * @param to the last date, ISO format YYYY-MM-DD
   * @return the todos, sorted by date and then by the day ordering
   */
  @GetMapping
  public List<TodoDto> range(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return todoService.range(CurrentUser.id(), from, to);
  }

  /**
   * Returns one todo of the current user.
   *
   * @param id the todo id
   * @return the todo
   */
  @GetMapping("/{id}")
  public TodoDto get(@PathVariable UUID id) {
    return todoService.get(CurrentUser.id(), id);
  }

  /**
   * Creates a todo for the current user.
   *
   * @param req the todo fields
   * @return the created todo
   */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public TodoDto create(@Valid @RequestBody TodoRequest req) {
    return todoService.create(CurrentUser.id(), req);
  }

  /**
   * Replaces the editable fields of a todo of the current user.
   *
   * @param id the todo id
   * @param req the new todo fields
   * @return the updated todo
   */
  @PutMapping("/{id}")
  public TodoDto update(@PathVariable UUID id, @Valid @RequestBody TodoRequest req) {
    return todoService.update(CurrentUser.id(), id, req);
  }

  /**
   * Marks a todo of the current user done or not done.
   *
   * @param id the todo id
   * @param req the new done state
   * @return the updated todo
   */
  @PatchMapping("/{id}/done")
  public TodoDto setDone(@PathVariable UUID id, @Valid @RequestBody DoneRequest req) {
    return todoService.setDone(CurrentUser.id(), id, req.done());
  }

  /**
   * Deletes a todo of the current user.
   *
   * @param id the todo id
   */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    todoService.delete(CurrentUser.id(), id);
  }
}
