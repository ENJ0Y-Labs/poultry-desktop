import { existsSync, rmSync } from "node:fs";
import { join } from "node:path";
import { execFileSync } from "node:child_process";

const javaHome = process.env.JAVA_HOME;
if (!javaHome) throw new Error("JAVA_HOME must point to a JDK before packaging.");
const jlink = join(javaHome, "bin", process.platform === "win32" ? "jlink.exe" : "jlink");
if (!existsSync(jlink)) throw new Error("jlink was not found in JAVA_HOME.");

const output = join(process.cwd(), "runtime");
if (existsSync(output)) rmSync(output, { recursive: true, force: true });
const modules = [
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

execFileSync(jlink, [
  "--add-modules",
  modules.join(","),
  "--strip-debug",
  "--no-header-files",
  "--no-man-pages",
  "--compress=2",
  "--output",
  output
], { stdio: "inherit" });

console.log("Created packaged Java runtime at", output);
