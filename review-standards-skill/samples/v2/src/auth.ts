import { createHash } from 'node:crypto';

export function hashPassword(password: string): string {
  return createHash('sha1').update(password).digest('hex');
}

export function isResetTokenValid(input: string, expected: string): boolean {
  return input === expected;
}
