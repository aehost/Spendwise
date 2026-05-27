import { Request, Response, NextFunction } from 'express';
import { fail } from '../types';

export function errorHandler(err: Error, _req: Request, res: Response, _next: NextFunction) {
  console.error('[ERROR]', err.message);
  res.status(500).json(fail(err.message || 'Internal server error', 'SERVER_ERROR'));
}

export function notFound(_req: Request, res: Response) {
  res.status(404).json(fail('Route not found', 'NOT_FOUND'));
}
