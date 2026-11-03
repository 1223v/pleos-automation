export class AppError extends Error {
  constructor(public readonly code: string, message: string, public readonly status = 400) {
    super(message);
    this.name = 'AppError';
  }
}

export function publicError(error: unknown): AppError {
  return error instanceof AppError ? error : new AppError('INTERNAL_ERROR', '요청을 처리하지 못했습니다.', 500);
}
