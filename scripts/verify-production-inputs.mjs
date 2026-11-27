import { existsSync, statSync } from "node:fs";
import { join } from "node:path";
import { execFileSync } from "node:child_process";

const runtimeModules = [
  "java.base",
  "java.desktop",
  "java.instrument",
  "java.logging",
  "java.management",
  "java.naming",
  "java.net.http",
  "java.security.jgss",
  "java.sql",
  "java.xml",
  "jdk.crypto.ec",
  "jdk.unsupported"
];

const required = [
  ["renderer build", join("dist", "renderer", "index.html")],
  ["main process build", join("dist", "main", "main.js")],
  ["preload build", join("dist", "preload", "preload.cjs")],
  ["Spring Boot backend", join("backend", "target", "poultry-backend.jar")],
  [
    "bundled Java runtime",
    join("runtime", process.platform === "win32" ? "bin/java.exe" : "bin/java")
  ]
];

const missing = required
  .filter(([, path]) => !existsSync(path) || !statSync(path).isFile())
  .map(([name, path]) => name + ": " + path);

const java = join("runtime", process.platform === "win32" ? "bin/java.exe" : "bin/java");
if (existsSync(java) && statSync(java).isFile()) {
  const listedModules = execFileSync(join(process.cwd(), java), ["--list-modules"], {
    encoding: "utf8"
  })
    .split(/\r?\n/)
    .map(line => line.split("@")[0]);
  const missingModules = runtimeModules.filter(module => !listedModules.includes(module));
  if (missingModules.length) {
    console.error("Bundled Java runtime is missing required modules:");
    for (const module of missingModules) console.error("- " + module);
    process.exit(1);
  }
} else if (!missing.length) {
  missing.push("bundled Java runtime executable: " + java);
}

const backendJarPath = join("backend", "target", "poultry-backend.jar");
if (existsSync(backendJarPath) && statSync(backendJarPath).isFile()) {
  const jarExecutable = process.env.JAVA_HOME\n    ? join(process.env.JAVA_HOME, "bin", process.platform === "win32" ? "jar.exe" : "jar")\n    : "jar";\n  const entries = execFileSync(jarExecutable, ["tf", backendJarPath], { encoding: "utf8" }).split(/\r?\n/);
  const requiredBrowserEntries = [
    "BOOT-INF/classes/static/index.html",
    "BOOT-INF/classes/static/theme-init.js"
  ];
  const missingBrowserEntries = requiredBrowserEntries.filter(entry => !entries.includes(entry));
  if (missingBrowserEntries.length) {
    console.error("Packaged backend is missing production browser assets:");
    for (const entry of missingBrowserEntries) console.error("- " + entry);
    process.exit(1);
  }
  if (!entries.some(entry => entry.startsWith("BOOT-INF/classes/static/assets/") && entry.endsWith(".js"))) {
    console.error("Packaged backend is missing the production renderer JavaScript bundle.");
    process.exit(1);
  }
}

if (missing.length) {
  console.error("Production package inputs are incomplete:");
  for (const item of missing) console.error("- " + item);
  process.exit(1);
}

console.log("Production package inputs verified:");
for (const [name, path] of required) {
  console.log("- " + name + ": " + path);
}
