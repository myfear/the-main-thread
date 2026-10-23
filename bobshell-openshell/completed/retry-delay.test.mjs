import test from 'node:test';
import assert from 'node:assert/strict';
import { retryDelay } from './retry-delay.mjs';

test('the first attempt uses the base delay', () => {
  assert.equal(retryDelay(0), 100);
});

test('the delay doubles before reaching the cap', () => {
  assert.equal(retryDelay(3), 800);
});

test('the delay stops at the cap', () => {
  assert.equal(retryDelay(4), 1000);
  assert.equal(retryDelay(20), 1000);
});

test('callers can choose the base and cap', () => {
  assert.equal(retryDelay(3, 50, 250), 250);
});
