package com.ntamasa.markitdone.todo;

import com.ntamasa.markitdone.audit.AuditAction;
import com.ntamasa.markitdone.audit.AuditService;
import com.ntamasa.markitdone.category.CategoryService;
import com.ntamasa.markitdone.common.ApiException;
import com.ntamasa.markitdone.common.ErrorCode;
import com.ntamasa.markitdone.todo.dto.TodoDto;
import com.ntamasa.markitdone.todo.dto.TodoRequest;
import com.ntamasa.markitdone.user.UserRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Rules for listing, creating, editing, completing and deleting the one-off todos of a user. */
@Service
public class TodoService {

  private static final String ENTITY_TYPE = "Todo";
  private static final int MAX_RANGE_DAYS = 62;
  private static final int MAX_NAME_LENGTH = 200;
  private static final int MAX_DESCRIPTION_LENGTH = 2000;

  private final TodoRepository todoRepository;
  private final CategoryService categoryService;
  private final UserRepository userRepository;
  private final AuditService auditService;

  /**
   * Create a TodoService.
   *
   * @param todoRepository the todo repository
   * @param categoryService the category service, used to check category ownership
   * @param userRepository the user repository, used to find the user's time zone
   * @param auditService the audit service
   */
  public TodoService(
      TodoRepository todoRepository,
      CategoryService categoryService,
      UserRepository userRepository,
      AuditService auditService) {
    this.todoRepository = todoRepository;
    this.categoryService = categoryService;
    this.userRepository = userRepository;
    this.auditService = auditService;
  }

  /**
   * Lists the user's todos due between two dates, both inclusive, sorted by date and then by the
   * day ordering.
   *
   * @param userId the owner
   * @param from the first date
   * @param to the last date
   * @return the todos
   * @throws ApiException BAD_REQUEST when from is after to or the dates are more than 62 days apart
   */
  @Transactional(readOnly = true)
  public List<TodoDto> range(UUID userId, LocalDate from, LocalDate to) {
    if (from == null
        || to == null
        || from.isAfter(to)
        || ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
      throw new ApiException(ErrorCode.BAD_REQUEST, "Invalid date range");
    }
    ZoneId zone = zoneOf(userId);
    return todoRepository.findByUserIdAndDueDateBetween(userId, from, to).stream()
        .sorted(Comparator.comparing(Todo::getDueDate).thenComparing(TodoOrdering.DAY))
        .map(todo -> TodoMapper.toDto(todo, zone))
        .toList();
  }

  /**
   * Returns one todo of the user.
   *
   * @param userId the owner
   * @param id the todo id
   * @return the todo
   * @throws ApiException NOT_FOUND when the todo is not the user's
   */
  @Transactional(readOnly = true)
  public TodoDto get(UUID userId, UUID id) {
    return TodoMapper.toDto(requireOwned(userId, id), zoneOf(userId));
  }

  /**
   * Creates a todo. Records a TODO_CREATED audit entry.
   *
   * @param userId the owner
   * @param req the todo fields
   * @return the created todo
   * @throws ApiException VALIDATION_FAILED for an invalid name or description or an unknown
   *     category
   */
  @Transactional
  public TodoDto create(UUID userId, TodoRequest req) {
    String name = validName(req.name());
    String description = cleanDescription(req.description());
    checkCategory(userId, req.categoryId());
    Todo todo =
        todoRepository.saveAndFlush(
            new Todo(
                userId,
                name,
                description,
                req.dueDate(),
                req.dueTime(),
                req.priority(),
                req.categoryId()));
    auditService.record(
        AuditAction.TODO_CREATED, userId, ENTITY_TYPE, todo.getId(), null, snapshot(todo));
    return TodoMapper.toDto(todo, zoneOf(userId));
  }

  /**
   * Updates a todo. Past and expired todos can be edited. Records a TODO_UPDATED audit entry.
   *
   * @param userId the owner
   * @param id the todo id
   * @param req the new todo fields
   * @return the updated todo
   * @throws ApiException NOT_FOUND when the todo is not the user's, VALIDATION_FAILED for invalid
   *     input or an unknown category
   */
  @Transactional
  public TodoDto update(UUID userId, UUID id, TodoRequest req) {
    Todo todo = requireOwned(userId, id);
    String name = validName(req.name());
    String description = cleanDescription(req.description());
    checkCategory(userId, req.categoryId());
    Map<String, Object> before = snapshot(todo);
    todo.update(name, description, req.dueDate(), req.dueTime(), req.priority(), req.categoryId());
    todo = todoRepository.saveAndFlush(todo);
    auditService.record(AuditAction.TODO_UPDATED, userId, ENTITY_TYPE, id, before, snapshot(todo));
    return TodoMapper.toDto(todo, zoneOf(userId));
  }

  /**
   * Marks a todo done or not done. Records a TODO_DONE_CHANGED audit entry.
   *
   * @param userId the owner
   * @param id the todo id
   * @param done the new done state
   * @return the updated todo
   * @throws ApiException NOT_FOUND when the todo is not the user's
   */
  @Transactional
  public TodoDto setDone(UUID userId, UUID id, boolean done) {
    Todo todo = requireOwned(userId, id);
    boolean old = todo.isDone();
    todo.setDone(done);
    todo = todoRepository.saveAndFlush(todo);
    auditService.record(
        AuditAction.TODO_DONE_CHANGED,
        userId,
        ENTITY_TYPE,
        id,
        Map.of("done", old),
        Map.of("done", done));
    return TodoMapper.toDto(todo, zoneOf(userId));
  }

  /**
   * Deletes a todo. Records a TODO_DELETED audit entry.
   *
   * @param userId the owner
   * @param id the todo id
   * @throws ApiException NOT_FOUND when the todo is not the user's
   */
  @Transactional
  public void delete(UUID userId, UUID id) {
    Todo todo = requireOwned(userId, id);
    Map<String, Object> before = snapshot(todo);
    todoRepository.delete(todo);
    todoRepository.flush();
    auditService.record(AuditAction.TODO_DELETED, userId, ENTITY_TYPE, id, before, null);
  }

  /**
   * Returns a todo only when it belongs to the given user.
   *
   * @param userId the owner
   * @param id the todo id
   * @return the todo
   * @throws ApiException NOT_FOUND "Todo not found" when it does not exist or is another user's
   */
  @Transactional(readOnly = true)
  public Todo requireOwned(UUID userId, UUID id) {
    return todoRepository
        .findByIdAndUserId(id, userId)
        .orElseThrow(() -> ApiException.notFound("Todo"));
  }

  private ZoneId zoneOf(UUID userId) {
    return userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User")).zone();
  }

  private void checkCategory(UUID userId, UUID categoryId) {
    if (categoryId == null) {
      return;
    }
    try {
      categoryService.requireOwned(userId, categoryId);
    } catch (ApiException ex) {
      if (ex.getCode() == ErrorCode.NOT_FOUND) {
        throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown category", ex);
      }
      throw ex;
    }
  }

  private static String validName(String raw) {
    String name = raw == null ? "" : raw.trim();
    if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED, "Name must be between 1 and 200 characters.");
    }
    return name;
  }

  private static String cleanDescription(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    if (raw.length() > MAX_DESCRIPTION_LENGTH) {
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED, "Description must be at most 2000 characters.");
    }
    return raw;
  }

  private static Map<String, Object> snapshot(Todo todo) {
    Map<String, Object> map = new HashMap<>();
    map.put("name", todo.getName());
    map.put("description", todo.getDescription());
    map.put("dueDate", todo.getDueDate().toString());
    map.put("dueTime", todo.getDueTime() == null ? null : todo.getDueTime().toString());
    map.put("priority", todo.getPriority().name());
    map.put("categoryId", todo.getCategoryId() == null ? null : todo.getCategoryId().toString());
    map.put("done", todo.isDone());
    return map;
  }
}
