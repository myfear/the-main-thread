// A paste uses the minimum pause. Continuous keystrokes wait longer than their cadence.
export class TypingPause {
  constructor() { this.reset(); }

  reset() { this.previousText = ''; this.previousTime = 0; this.intervals = []; }

  edit(text, now) {
    const interval = now - this.previousTime;
    if (text && this.previousText && Math.abs(text.length - this.previousText.length) === 1 && interval > 0 && interval < 1500) {
      this.intervals.push(interval);
      if (this.intervals.length > 6) this.intervals.shift();
    } else {
      this.intervals = [];
    }
    this.previousText = text; this.previousTime = now;
  }

  delay(config) {
    if (!config.adaptiveDebounce || !this.intervals.length) return config.debounceMs;
    const sorted = [...this.intervals].sort((a, b) => a - b);
    const cadence = sorted[Math.floor(sorted.length / 2)];
    return Math.round(Math.max(config.debounceMs, Math.min(config.maximumDebounceMs, cadence * 1.5 + 50)));
  }
}

export function waitingReason(text, rules = []) {
  const trimmed = text.trim();
  if (trimmed.length < 2) return 'Keep typing.';
  for (const rule of rules) {
    if (new RegExp(rule.pattern, 'iu').test(trimmed) && (!rule.exception || !new RegExp(rule.exception, 'iu').test(trimmed))) return rule.reason;
  }
  return null;
}
