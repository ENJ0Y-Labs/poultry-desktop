import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const rendererHtmlPath = resolve("dist", "renderer", "index.html");
const html = readFileSync(rendererHtmlPath, "utf8");
const match = html.match(/<meta\s+http-equiv="Content-Security-Policy"\s+content="([^"]+)"\s*\/?>/i);

assert.ok(match, `No renderer Content-Security-Policy meta tag found in ${rendererHtmlPath}`);

const directives = match[1]
  .split(";")
  .map((directive) => directive.trim())
  .filter(Boolean)
  .sort();

const expectedDirectives = [
  "default-src 'self'",
  "style-src 'self'",
  "script-src 'self'",
  "connect-src 'self' http://127.0.0.1:18942",
].sort();

assert.deepEqual(
  directives,
  expectedDirectives,
  "Packaged renderer CSP must allow only bundled resources and the intended local backend origin/port.",
);

assert.doesNotMatch(
  match[1],
  /unsafe-inline|unsafe-eval|ws:|wss:|vite|localhost:5173|127\.0\.0\.1:\*/i,
  "Development-only or unsafe CSP sources must not appear in the packaged renderer.",
);

console.log("Packaged renderer CSP verified:");
for (const directive of directives) console.log(`- ${directive}`);
