import assert from "node:assert/strict";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { extname, join, relative, resolve, sep } from "node:path";

const rendererDir = resolve("dist", "renderer");
const htmlPath = join(rendererDir, "index.html");

assert.ok(
  statSync(rendererDir).isDirectory(),
  `Production renderer directory is missing: ${rendererDir}`,
);
assert.ok(
  statSync(htmlPath).isFile(),
  `Production renderer HTML is missing: ${htmlPath}`,
);

function collectFiles(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) return collectFiles(path);
    return entry.isFile() ? [path] : [];
  });
}

const files = collectFiles(rendererDir);
const textExtensions = new Set([".html", ".js", ".mjs", ".cjs", ".css", ".json", ".map"]);
const forbiddenReferences = [
  /\/@vite\/client/i,
  /@vite\/client/i,
  /\b__vite_ping\b/i,
  /\bvite\/client\b/i,
  /(?:https?:\/\/)?(?:127\.0\.0\.1|localhost):5173/i,
  /\bws:\/\//i,
  /\bwss:\/\//i,
];

const violations = [];
for (const file of files) {
  if (!textExtensions.has(extname(file).toLowerCase())) continue;
  const content = readFileSync(file, "utf8");
  for (const pattern of forbiddenReferences) {
    if (pattern.test(content)) {
      violations.push(`${relative(rendererDir, file).split(sep).join("/")}: matched ${pattern}`);
    }
  }
}

assert.deepEqual(
  violations,
  [],
  "Production renderer contains a Vite development server or HMR reference.",
);

const html = readFileSync(htmlPath, "utf8");
assert.doesNotMatch(
  html,
  /<script[^>]+src=["']\/src\/main\.jsx["']/i,
  "Production HTML must reference built assets, not the renderer source entry.",
);

const assetReferences = [
  ...html.matchAll(/(?:src|href)=["']([^"']+)["']/gi),
]
  .map((match) => match[1])
  .filter((reference) => /\.(?:js|css)(?:\?.*)?$/i.test(reference));

assert.ok(assetReferences.some((reference) => /\.js(?:\?.*)?$/i.test(reference)),
  "Production HTML must reference a bundled JavaScript asset.");

for (const reference of assetReferences) {
  if (/^(?:[a-z]+:)?\/\//i.test(reference) || reference.startsWith("data:")) continue;
  const cleanReference = reference.split("?")[0].split("#")[0];
  const assetPath = resolve(rendererDir, cleanReference.replace(/^\//, ""));
  assert.ok(
    assetPath.startsWith(rendererDir + sep) || assetPath === rendererDir,
    `Renderer asset reference escapes production renderer directory: ${reference}`,
  );
  assert.ok(
    files.includes(assetPath),
    `Production HTML references a missing bundled asset: ${reference}`,
  );
}

console.log("Production renderer verified:");
console.log("- bundled HTML and JavaScript assets are present");
console.log("- no Vite client, HMR, WebSocket, or port 5173 references found");
