export function retryDelay(attempt, baseMs = 100, capMs = 1000) {
  return Math.min(baseMs * 2 ** attempt, capMs);
}
