package com.ntamasa.markitdone.common;

import org.springframework.http.HttpStatus;

/**
 * Error codes for all API errors. Each code carries its HTTP status.
 */
public enum ErrorCode {
	// 400 Bad Request
	VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
	BAD_REQUEST(HttpStatus.BAD_REQUEST),
	RESET_TOKEN_INVALID(HttpStatus.BAD_REQUEST),

	// 401 Unauthorized
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),

	// 403 Forbidden
	FORBIDDEN(HttpStatus.FORBIDDEN),

	// 404 Not Found
	NOT_FOUND(HttpStatus.NOT_FOUND),

	// 409 Conflict
	EMAIL_TAKEN(HttpStatus.CONFLICT),
	CATEGORY_NAME_TAKEN(HttpStatus.CONFLICT),

	// 500 Internal Server Error
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

	private final HttpStatus status;

	ErrorCode(HttpStatus status) {
		this.status = status;
	}

	public HttpStatus getStatus() {
		return status;
	}
}
