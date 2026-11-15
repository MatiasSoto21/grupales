function decimal(value: number): [bigint, bigint] {
  const [coefficient, exponent = '0'] = value.toString().toLowerCase().split('e');
  const [whole, fraction = ''] = coefficient!.split('.');
  const scale = fraction.length - Number(exponent);
  const digits = BigInt(`${whole}${fraction}`);
  return scale >= 0 ? [digits, 10n ** BigInt(scale)] : [digits * 10n ** BigInt(-scale), 1n];
}

export function calculateLineAmounts(price: number, quantity: number, discount: number, comprobanteInterno = false): { neto: number; iva: number } {
  if (![price, quantity, discount].every(Number.isFinite) || price < 0 || quantity < 0 || discount < 0 || discount > 100) {
    return { neto: 0, iva: 0 };
  }
  const [p, pd] = decimal(price);
  const [q, qd] = decimal(quantity);
  const [d, dd] = decimal(discount);
  const numerator = p * q * (100n * dd - d) * 100n;
  const denominator = pd * qd * 100n * dd;
  const cents = (2n * numerator + denominator) / (2n * denominator);
  const taxCents = comprobanteInterno ? 0n : (cents * 21n + 50n) / 100n;
  return { neto: Number(cents) / 100, iva: Number(taxCents) / 100 };
}
