import { Request, Response, NextFunction } from 'express';
import { verifyAccessToken } from '../jwt';
import { fail } from '../types';

declare global {
  namespace Express {
    interface Request {
      user?: { userId: string; email: string; role: string };
    }
  }
}

export function authMiddleware(req: Request, res: Response, next: NextFunction) {
  const header = req.headers.authorization;
  if (!header?.startsWith('Bearer ')) {
    return res.status(401).json(fail('Missing or invalid Authorization header', 'UNAUTHORIZED'));
  }
  const token = header.slice(7);
  try {
    const payload = verifyAccessToken(token);
    req.user = { userId: payload.userId, email: payload.email, role: payload.role };
    next();
  } catch {
    return res.status(401).json(fail('Invalid or expired token', 'TOKEN_EXPIRED'));
  }
}

export function requireRole(...roles: string[]) {
  return (req: Request, res: Response, next: NextFunction) => {
    if (!req.user || !roles.includes(req.user.role)) {
      return res.status(403).json(fail('Insufficient permissions', 'FORBIDDEN'));
    }
    next();
  };
}
