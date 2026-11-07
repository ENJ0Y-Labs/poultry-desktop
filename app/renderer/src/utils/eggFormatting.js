export function formatEggQuantity(totalEggs, crateSize) {
  if (totalEggs == null || !Number.isSafeInteger(Number(totalEggs)) || Number(totalEggs) < 0) {
    return "—";
  }

  const eggs = Number(totalEggs);
  const size = Number(crateSize);
  if (!Number.isSafeInteger(size) || size <= 0) {
    return `${eggs.toLocaleString("en-NG")} ${eggs === 1 ? "egg" : "eggs"}`;
  }

  if (eggs === 0) return "0 eggs";

  const crates = Math.floor(eggs / size);
  const remainder = eggs % size;
  const parts = [];

  if (crates > 0) {
    parts.push(`${crates.toLocaleString("en-NG")} ${crates === 1 ? "crate" : "crates"}`);
  }
  if (remainder > 0 || crates === 0) {
    parts.push(`${remainder.toLocaleString("en-NG")} ${remainder === 1 ? "egg" : "eggs"}`);
  }

  return parts.join(", ");
}
