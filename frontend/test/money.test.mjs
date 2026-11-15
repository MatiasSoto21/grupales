import { test } from 'node:test';
import assert from 'node:assert/strict';
import { calculateLineAmounts } from '../dist/money.js';

test('uses decimal half-up rounding for prices and discounts', () => {
  assert.deepEqual(calculateLineAmounts(1.005, 1, 0), { neto: 1.01, iva: 0.21 });
  assert.deepEqual(calculateLineAmounts(33.33, 3, 9.99), { neto: 90, iva: 18.9 });
  assert.deepEqual(calculateLineAmounts(100, 2, 100), { neto: 0, iva: 0 });
});

test('VAT is rounded per line before totals are added', () => {
  const lines = Array.from({ length: 6 }, () => calculateLineAmounts(0.01, 1, 0));
  assert.equal(lines.reduce((sum, line) => sum + line.iva, 0), 0);
  assert.equal(lines.reduce((sum, line) => sum + Math.round(line.neto * 100), 0), 6);
});

test('invalid inputs cannot introduce NaN or negative totals', () => {
  for (const args of [[NaN, 1, 0], [1, Infinity, 0], [100, 1, 101]]) {
    assert.deepEqual(calculateLineAmounts(...args), { neto: 0, iva: 0 });
  }
});


test('X documents use internal amounts without adding fiscal VAT', () => {
  assert.deepEqual(calculateLineAmounts(100, 2, 10, true), { neto: 180, iva: 0 });
  assert.deepEqual(calculateLineAmounts(100, 2, 10, false), { neto: 180, iva: 37.8 });
  assert.deepEqual(calculateLineAmounts(1.005, 1, 0, true), { neto: 1.01, iva: 0 });
});
