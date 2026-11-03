import "@testing-library/jest-dom/vitest";

if (!Element.prototype.scrollIntoView) {
  Element.prototype.scrollIntoView = () => {};
}

// Provide localStorage and sessionStorage in the test environment.
// Node.js 26 exposes an experimental global `localStorage` that requires
// --localstorage-file and is undefined otherwise. Create a reliable
// in-memory storage implementation that matches the Web Storage API.
function createStorage() {
  const store = new Map();
  return {
    get length() { return store.size; },
    clear() { store.clear(); },
    getItem(key) { return store.has(String(key)) ? store.get(String(key)) : null; },
    key(index) {
      const keys = [...store.keys()];
      return index >= 0 && index < keys.length ? keys[index] : null;
    },
    removeItem(key) { store.delete(String(key)); },
    setItem(key, value) { store.set(String(key), String(value)); },
  };
}

if (typeof globalThis.localStorage === "undefined" || globalThis.localStorage === null) {
  globalThis.localStorage = createStorage();
}
if (typeof globalThis.sessionStorage === "undefined" || globalThis.sessionStorage === null) {
  globalThis.sessionStorage = createStorage();
}
