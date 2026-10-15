export function parseNairaToKobo(value, { allowZero = false } = {}) {
  const raw = String(value ?? "").trim();
  if (!/^\d+(?:\.\d{1,2})?$/.test(raw)) {
    throw new Error("Enter a valid naira amount with up to 2 decimal places.");
  }

  const [naira, fraction = ""] = raw.split(".");
  const kobo = BigInt(naira) * 100n + BigInt((fraction + "00").slice(0, 2));
  if (kobo === 0n && !allowZero) {
    throw new Error("Enter an amount greater than ₦0.");
  }
  if (kobo > BigInt(Number.MAX_SAFE_INTEGER)) {
    throw new Error("That amount is too large to record safely.");
  }
  return Number(kobo);
}
