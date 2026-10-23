export function retryDelay(attempt, baseMs = 100, capMs = 1000) {
  return baseMs * 2 ** attempt;
}
