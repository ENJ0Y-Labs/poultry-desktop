import { existsSync, statSync } from "node:fs";
import { join } from "node:path";

const required = [
  ["renderer build", join("dist", "renderer", "index.html")],
  ["main process build", join("dist", "main", "main.js")],
  ["preload build", join("dist", "preload", "preload.js")],
  ["Spring Boot backend", join("backend", "target", "poultry-backend.jar")],
  ["bundled Java runtime", join("runtime", process.platform === "win32" ? "bin", "java.exe" : "bin", "java")]
];

const missing = required
  .filter(([, path]) => !existsSync(path) || !statSync(path).isFile())
  .map(([name, path]) => `${name}: ${path}`);

if (missing.length) {
  console.error("Production package inputs are incomplete:");
  for (const item of missing) console.error(`- ${item}`);
  process.exit(1);
}

console.log("Production package inputs verified:");
for (const [name, path] of required) {
  console.log(`- ${name}: ${path}`);
}
