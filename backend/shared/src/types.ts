export interface JwtPayload {
  userId: string;
  email: string;
  role: string;
}

export interface ApiResponse<T = unknown> {
  success: boolean;
  data?: T;
  error?: string;
  code?: string;
}

export function ok<T>(data: T): ApiResponse<T> {
  return { success: true, data };
}

export function fail(error: string, code?: string): ApiResponse {
  return { success: false, error, code };
}
