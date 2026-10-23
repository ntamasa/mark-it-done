package com.ntamasa.markitdone.common;

/**
 * The one unchecked exception that business code throws. It carries an ErrorCode and a user-facing
 * message.
 */
public class ApiException extends RuntimeException {
  private final ErrorCode code;

  /**
   * Create an ApiException with the given error code and message.
   *
   * @param code the error code
   * @param message the user-facing message
   */
  public ApiException(ErrorCode code, String message) {
    super(message);
    this.code = code;
  }

  /**
   * Create an ApiException with the given error code, message, and cause.
   *
   * @param code the error code
   * @param message the user-facing message
   * @param cause the underlying cause
   */
  public ApiException(ErrorCode code, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
  }

  /**
   * Get the error code.
   *
   * @return the error code
   */
  public ErrorCode getCode() {
    return code;
  }

  /**
   * Factory method for NOT_FOUND errors.
   *
   * @param what a description of what was not found
   * @return an ApiException with NOT_FOUND code and message "{what} not found"
   */
  public static ApiException notFound(String what) {
    return new ApiException(ErrorCode.NOT_FOUND, what + " not found");
  }
}
