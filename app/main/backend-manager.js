import { spawn } from "node:child_process";
import { appendFileSync, existsSync, mkdirSync } from "node:fs";
import { join } from "node:path";

const DEFAULT_PORT = 18942;
const STARTUP_TIMEOUT_MS = 30_000;
const HEALTH_INTERVAL_MS = 250;
const STOP_TIMEOUT_MS = 5_000;

export class BackendManager {
  constructor({
    userDataPath,
    appPath,
    isPackaged,
    port,
    resourcesPath = process.resourcesPath,
    spawnProcess = spawn,
  } = {}) {
    this.userDataPath = userDataPath;
    this.appPath = appPath;
    this.isPackaged = isPackaged;
    this.resourcesPath = resourcesPath;
    this.spawnProcess = spawnProcess;
    this.process = null;
    this.port = Number(port || process.env.POULTRY_BACKEND_PORT || DEFAULT_PORT);
    this.lastError = null;
  }

  get dataDirectory() {
    return this.isPackaged
      ? join(this.userDataPath, "data")
      : join(this.appPath, "data");
  }

  get databasePath() {
    return join(this.dataDirectory, "poultry.db");
  }

  get logDirectory() {
    return join(this.isPackaged ? this.userDataPath : this.appPath, "logs");
  }

  get logFilePath() {
    return join(this.logDirectory, "backend.log");
  }

  get processLogFilePath() {
    return join(this.logDirectory, "backend-process.log");
  }

  get javaPath() {
    const bundled = join(this.resourcesPath, "runtime", process.platform === "win32" ? "bin/java.exe" : "bin/java");
    return this.isPackaged && existsSync(bundled) ? bundled : "java";
  }

  get jarPath() {
    return join(this.resourcesPath, "backend", "poultry-backend.jar");
  }

  get backendArguments() {
    return [
      "--server.address=127.0.0.1",
      `--server.port=${this.port}`,
      `--poultry.database-path=${this.databasePath}`,
      `--spring.profiles.active=${this.isPackaged ? "prod" : "dev"}`,
      `--logging.file.name=${this.logFilePath}`
    ];
  }

  writeLog(message) {
    try {
      appendFileSync(this.processLogFilePath, `[${new Date().toISOString()}] ${message}\n`);
    } catch {
      // Logging must never prevent the backend from starting or shutting down.
    }
  }

  async start() {
    if (this.process) return;

    mkdirSync(this.dataDirectory, { recursive: true });
    mkdirSync(join(this.isPackaged ? this.userDataPath : this.appPath, "logs"), { recursive: true });
    this.lastError = null;
    this.writeLog(`Starting backend; packaged=${this.isPackaged}; database=${this.databasePath}; port=${this.port}`);

    try {
      if (!this.isPackaged) {
        const isWin = process.platform === "win32";
        const wrapper = join(this.appPath, isWin ? "mvnw.cmd" : "mvnw");
        this.process = this.spawnProcess(
          wrapper,
          ["-f", join(this.appPath, "backend", "pom.xml"), "spring-boot:run", ...this.backendArguments],
          { cwd: this.appPath, windowsHide: true, stdio: ["ignore", "pipe", "pipe"], shell: isWin }
        );
      } else {
        if (!existsSync(this.jarPath)) throw new Error(`Packaged backend JAR not found: ${this.jarPath}`);
        this.process = this.spawnProcess(this.javaPath, ["-jar", this.jarPath, ...this.backendArguments], {
          windowsHide: true,
          stdio: ["ignore", "pipe", "pipe"]
        });
      }
    } catch (error) {
      this.writeLog(`Backend process could not be launched: ${error.stack || error.message || error}`);
      throw new Error(`Pomas could not launch the local backend. Check ${this.processLogFilePath}. Details: ${error.message || error}`);
    }

    const child = this.process;
    child.stdout?.on("data", (chunk) => this.writeLog(`[stdout] ${String(chunk).trimEnd()}`));
    child.stderr?.on("data", (chunk) => this.writeLog(`[stderr] ${String(chunk).trimEnd()}`));
    child.once("error", (error) => {
      this.lastError = new Error(`Backend process error: ${error.message}. See ${this.processLogFilePath}`);
      this.writeLog(`Backend process error: ${error.stack || error.message}`);
    });
    child.once("exit", (code, signal) => {
      this.writeLog(`Backend process exited; code=${code}; signal=${signal || "none"}`);
      if (this.process === child) {
        this.lastError = new Error(`Backend process exited before becoming healthy (code ${code}${signal ? `, signal ${signal}` : ""}). Check ${this.processLogFilePath} and ${this.logFilePath}`);
        this.process = null;
      }
    });

    try {
      await this.waitForHealth();
      this.writeLog("Backend health check passed.");
    } catch (error) {
      this.writeLog(`Backend startup failed: ${error.stack || error.message || error}`);
      await this.stop();
      throw error;
    }
  }

  async waitForHealth({ timeoutMs = STARTUP_TIMEOUT_MS, intervalMs = HEALTH_INTERVAL_MS } = {}) {
    const started = Date.now();

    while (Date.now() - started < timeoutMs) {
      if (this.lastError) throw this.lastError;

      try {
        const response = await fetch(`http://127.0.0.1:${this.port}/api/v1/health`);
        if (response.ok) return true;
      } catch {
        // Spring Boot may still be starting. Poll until the startup deadline.
      }

      await new Promise((resolve) => setTimeout(resolve, intervalMs));
    }

    throw new Error(
      `The local backend could not become ready on port ${this.port}. Another application may be using that port, the database may be unavailable, or a migration may have failed. Check ${this.logFilePath} for Spring Boot errors and ${this.processLogFilePath} for process startup output.`
    );
  }

  async stop() {
    const child = this.process;
    if (!child) return;

    this.process = null;
    this.lastError = null;

    if (child.exitCode !== null || child.killed) return;

    this.writeLog("Stopping backend process.");
    child.kill();

    await new Promise((resolve) => {
      const timer = setTimeout(resolve, STOP_TIMEOUT_MS);
      child.once("exit", () => {
        clearTimeout(timer);
        resolve();
      });
    });
  }
}
