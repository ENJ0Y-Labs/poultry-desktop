import { spawn } from "node:child_process";
import { mkdirSync } from "node:fs";
import { createConnection } from "node:net";
import { resolve } from "node:path";

const root = process.cwd();
const isWindows = process.platform === "win32";
const wrapper = resolve(root, isWindows ? "mvnw.cmd" : "mvnw");
const port = process.env.SERVER_PORT || "18942";
const backendUrl = `http://127.0.0.1:${port}`;

async function hasHealthyBackend() {
  try {
    const response = await fetch(`${backendUrl}/api/v1/health`, {
      signal: AbortSignal.timeout(2000),
    });
    const health = await response.json();
    return response.ok
      && health.ok === true
      && health.status === "UP"
      && health.database === "UP"
      && health.schema === "VALID";
  } catch {
    return false;
  }
}

function isPortListening() {
  return new Promise((resolvePortState) => {
    const socket = createConnection({ host: "127.0.0.1", port: Number(port) });
    socket.setTimeout(1000);
    socket.once("connect", () => {
      socket.destroy();
      resolvePortState(true);
    });
    socket.once("error", () => resolvePortState(false));
    socket.once("timeout", () => {
      socket.destroy();
      resolvePortState(false);
    });
  });
}

function waitForShutdownSignal() {
  return new Promise((resolveSignal) => {
    const keepAlive = setInterval(() => {}, 60_000);
    const shutdown = () => {
      clearInterval(keepAlive);
      process.removeListener("SIGINT", shutdown);
      process.removeListener("SIGTERM", shutdown);
      resolveSignal();
    };
    process.once("SIGINT", shutdown);
    process.once("SIGTERM", shutdown);
  });
}

if (await hasHealthyBackend()) {
  console.info(`Reusing healthy development backend at ${backendUrl}.`);
  await waitForShutdownSignal();
} else if (await isPortListening()) {
  console.error(
    `A service is already listening at ${backendUrl}, but it did not pass the backend health check. `
    + "Not starting a second backend on the same port."
  );
  process.exitCode = 1;
} else {
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
        SERVER_PORT: port,
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
}
