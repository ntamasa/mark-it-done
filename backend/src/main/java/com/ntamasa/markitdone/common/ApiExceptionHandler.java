package com.ntamasa.markitdone.common;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.databind.exc.InvalidFormatException;

/**
 * Global exception handler for API errors. Handles ApiException, bean-validation failures, unknown
 * routes, access denied, and anything else.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
  private static final Logger logger = LoggerFactory.getLogger(ApiExceptionHandler.class);

  /** Handle ApiException. */
  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ProblemDetail> handleApiException(ApiException ex) {
    ErrorCode code = ex.getCode();
    ProblemDetail detail = ProblemDetail.forStatusAndDetail(code.getStatus(), ex.getMessage());
    detail.setProperty("code", code.name());
    return ResponseEntity.status(code.getStatus()).body(detail);
  }

  /** Handle unexpected exceptions by returning a generic 500 error and logging the stack trace. */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleGenericException(Exception ex) {
    logger.error("Unexpected error", ex);
    ProblemDetail detail =
        ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
    detail.setProperty("code", ErrorCode.INTERNAL_ERROR.name());
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(detail);
  }

  /**
   * Handle bean validation failures (request bodies and parameters) by returning a 400
   * VALIDATION_FAILED with an errors list.
   */
  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<FieldError> errors = new ArrayList<>();
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(
            fieldError ->
                errors.add(new FieldError(fieldError.getField(), fieldError.getDefaultMessage())));

    ProblemDetail detail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
    detail.setProperty("code", ErrorCode.VALIDATION_FAILED.name());
    detail.setProperty("errors", errors);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(detail);
  }

  /**
   * Handle unreadable request bodies, such as an unknown enum value, by returning a 400
   * VALIDATION_FAILED. The field is named when Jackson can tell which property failed.
   */
  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<FieldError> errors = new ArrayList<>();
    if (ex.getCause() instanceof InvalidFormatException invalid
        && !invalid.getPath().isEmpty()
        && invalid.getPath().get(0).getPropertyName() != null) {
      errors.add(new FieldError(invalid.getPath().get(0).getPropertyName(), "Invalid value"));
    }

    ProblemDetail detail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
    detail.setProperty("code", ErrorCode.VALIDATION_FAILED.name());
    detail.setProperty("errors", errors);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(detail);
  }

  /** DTO for field validation errors. */
  public record FieldError(String field, String message) {}
}
