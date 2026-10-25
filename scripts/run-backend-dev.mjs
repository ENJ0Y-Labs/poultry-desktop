import { spawn } from "node:child_process";
import { mkdirSync } from "node:fs";
import { resolve } from "node:path";

const root = process.cwd();
const isWindows = process.platform === "win32";
const wrapper = resolve(root, isWindows ? "mvnw.cmd" : "mvnw");

mkdirSync(resolve(root, "data"), { recursive: true });
mkdirSync(resolve(root, "logs"), { recursive: true });

const child = spawn(
  wrapper,
  ["-f", "backend/pom.xml", "spring-boot:run"],
  {
    cwd: root,
    windowsHide: true,
    stdio: "inherit",
    shell: isWindows,
    env: {
      ...process.env,
      SERVER_PORT: process.env.SERVER_PORT || "18942",
      DATABASE_PATH: process.env.DATABASE_PATH || resolve(root, "data", "poultry.db"),
      LOG_FILE: process.env.LOG_FILE || resolve(root, "logs", "backend.log"),
    },
  }
);

child.once("error", (error) => {
  console.error("Could not start the Spring Boot development backend:", error);
  process.exitCode = 1;
});

child.once("exit", (code, signal) => {
  if (signal) {
    process.kill(process.pid, signal);
    return;
  }
  process.exitCode = code ?? 1;
});
