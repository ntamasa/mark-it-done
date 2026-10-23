import { toApiProblem } from './api-error';

describe('api-error', () => {
  describe('toApiProblem', () => {
    it('maps a 409 response with code and detail', () => {
      const httpError = {
        error: { code: 'EMAIL_TAKEN', detail: 'x' },
        status: 409,
      };

      const problem = toApiProblem(httpError);

      expect(problem.status).toBe(409);
      expect(problem.code).toBe('EMAIL_TAKEN');
      expect(problem.detail).toBe('x');
      expect(problem.errors).toEqual([]);
    });

    it('maps a network error (status 0) to NETWORK_ERROR code with connection message', () => {
      const httpError = {
        error: null,
        status: 0,
      };

      const problem = toApiProblem(httpError);

      expect(problem.status).toBe(0);
      expect(problem.code).toBe('NETWORK_ERROR');
      expect(problem.detail).toBe('Cannot reach the server. Check your connection.');
      expect(problem.errors).toEqual([]);
    });

    it('maps a non-HTTP error to UNKNOWN code', () => {
      const problem = toApiProblem(new Error('Some error'));

      expect(problem.status).toBe(0);
      expect(problem.code).toBe('UNKNOWN');
      expect(problem.detail).toBe('Something went wrong.');
      expect(problem.errors).toEqual([]);
    });

    it('maps an error response with validation errors', () => {
      const httpError = {
        error: {
          code: 'VALIDATION_FAILED',
          detail: 'Validation failed',
          errors: [
            { field: 'name', message: 'must not be blank' },
            { field: 'email', message: 'must be valid' },
          ],
        },
        status: 400,
      };

      const problem = toApiProblem(httpError);

      expect(problem.status).toBe(400);
      expect(problem.code).toBe('VALIDATION_FAILED');
      expect(problem.detail).toBe('Validation failed');
      expect(problem.errors).toHaveLength(2);
      expect(problem.errors[0]).toEqual({
        field: 'name',
        message: 'must not be blank',
      });
    });
  });
});
