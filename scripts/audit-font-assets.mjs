import assert from "node:assert/strict";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { extname, join, relative, resolve, sep } from "node:path";

const sourceDir = resolve("app", "renderer");
const outputDir = resolve("dist", "renderer");
const fontExtensions = new Set([".woff", ".woff2", ".ttf", ".otf", ".eot"]);
const textExtensions = new Set([".html", ".js", ".mjs", ".cjs", ".css", ".json", ".map"]);

function collectFiles(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) return collectFiles(path);
    return entry.isFile() ? [path] : [];
  });
}

function inspectDirectory(directory) {
  assert.ok(statSync(directory).isDirectory(), `Directory is missing: ${directory}`);
  const files = collectFiles(directory);
  const fontFiles = files.filter((file) => fontExtensions.has(extname(file).toLowerCase()));
  const violations = [];

  for (const file of files) {
    if (!textExtensions.has(extname(file).toLowerCase())) continue;
    const content = readFileSync(file, "utf8");
    const label = relative(directory, file).split(sep).join("/");

    if (/@font-face\\b/i.test(content)) {
      violations.push(`${label}: contains an @font-face declaration; document and review the font before shipping it`);
    }
    if (/@import\\s+(?:url\\()?\\s*["']?https?:/i.test(content)
      || /@import\\s+/i.test(content)) {
      violations.push(`${label}: contains a stylesheet import; review it for hidden font/network dependencies`);
    }
    if (/fonts\\.googleapis\\.com|fonts\\.gstatic\\.com|use\\.typekit\\.net/i.test(content)) {
      violations.push(`${label}: references a remote web-font provider`);
    }
    if (/url\\(\\s*["']?[^)'"]+\\.(?:woff2?|ttf|otf|eot)(?:[?#][^)'"]*)?["']?\\s*\\)/i.test(content)) {
      violations.push(`${label}: references a font file through CSS url()`);
    }
  }

  return { files, fontFiles, violations };
}

const source = inspectDirectory(sourceDir);
const output = inspectDirectory(outputDir);
const allViolations = [
  ...source.violations.map((item) => `source: ${item}`),
  ...output.violations.map((item) => `built renderer: ${item}`),
];

assert.deepEqual(allViolations, [], allViolations.join("\\n"));
assert.equal(source.fontFiles.length, 0, "Renderer source must not contain unreviewed font files.");
assert.equal(output.fontFiles.length, 0, "Built renderer must not contain unreviewed font files.");

const css = readFileSync(resolve(sourceDir, "src", "styles.css"), "utf8");
assert.match(
  css,
  /font-family:\\s*["']?Segoe UI["']?,\\s*system-ui,\\s*-apple-system,\\s*BlinkMacSystemFont,\\s*sans-serif/i,
  "The UI must use the documented system-font fallback stack.",
);

console.log("Renderer font audit passed.");
console.log(`Source: ${source.fontFiles.length} font files, ${source.fontFiles.reduce((n, file) => n + statSync(file).size, 0)} bytes.`);
console.log(`Built renderer: ${output.fontFiles.length} font files, ${output.fontFiles.reduce((n, file) => n + statSync(file).size, 0)} bytes.`);
console.log("No @font-face declarations, stylesheet imports, remote font providers, or CSS font-file URLs found.");
console.log("The UI uses local system fonts; no web-font download or font-display rule is needed.");
