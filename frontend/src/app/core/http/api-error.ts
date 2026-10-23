export interface FieldError {
  field: string;
  message: string;
}

export interface ApiProblem {
  status: number;
  code: string;
  detail: string;
  errors: FieldError[];
}

const NETWORK_ERROR_CODE = 'NETWORK_ERROR';
const NETWORK_ERROR_MESSAGE = 'Cannot reach the server. Check your connection.';
const UNKNOWN_ERROR_CODE = 'UNKNOWN';
const UNKNOWN_ERROR_MESSAGE = 'Something went wrong.';

export function toApiProblem(err: unknown): ApiProblem {
  // Handle HttpErrorResponse
  if (typeof err === 'object' && err !== null && 'error' in err && 'status' in err) {
    const httpError = err as { error: unknown; status: number };

    // Network error (status 0)
    if (httpError.status === 0) {
      return {
        status: 0,
        code: NETWORK_ERROR_CODE,
        detail: NETWORK_ERROR_MESSAGE,
        errors: [],
      };
    }

    // Parse the error response body
    const errorBody = httpError.error;
    if (typeof errorBody === 'object' && errorBody !== null) {
      const problem = errorBody as Partial<ApiProblem>;
      return {
        status: httpError.status,
        code: problem.code || UNKNOWN_ERROR_CODE,
        detail: problem.detail || UNKNOWN_ERROR_MESSAGE,
        errors: problem.errors || [],
      };
    }

    // Fallback for non-object error
    return {
      status: httpError.status,
      code: UNKNOWN_ERROR_CODE,
      detail: UNKNOWN_ERROR_MESSAGE,
      errors: [],
    };
  }

  // Non-HTTP error
  return {
    status: 0,
    code: UNKNOWN_ERROR_CODE,
    detail: UNKNOWN_ERROR_MESSAGE,
    errors: [],
  };
}
