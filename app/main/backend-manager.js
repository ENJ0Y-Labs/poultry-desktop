import { spawn } from "node:child_process";
import { existsSync, mkdirSync } from "node:fs";
import { join } from "node:path";

const DEFAULT_PORT = 18942;
const STARTUP_TIMEOUT_MS = 30_000;
const HEALTH_INTERVAL_MS = 250;
const STOP_TIMEOUT_MS = 5_000;

export class BackendManager {
  constructor({ userDataPath, appPath, isPackaged, port } = {}) {
    this.userDataPath = userDataPath;
    this.appPath = appPath;
    this.isPackaged = isPackaged;
    this.process = null;
    this.port = Number(port || process.env.POULTRY_BACKEND_PORT || DEFAULT_PORT);
    this.lastError = null;
  }

  get dataDirectory() {
    return join(this.userDataPath, "data");
  }

  get databasePath() {
    return join(this.dataDirectory, "poultry.db");
  }

  get javaPath() {
    const bundled = join(process.resourcesPath, "runtime", process.platform === "win32" ? "bin/java.exe" : "bin/java");
    return this.isPackaged && existsSync(bundled) ? bundled : "java";
  }

  get jarPath() {
    return join(process.resourcesPath, "backend", "poultry-backend.jar");
  }

  get backendArguments() {
    return [
      "--server.address=127.0.0.1",
      `--server.port=${this.port}`,
      `--poultry.database-path=${this.databasePath}`,
      `--spring.profiles.active=${this.isPackaged ? "prod" : "dev"}`
    ];
  }

  async start() {
    if (this.process) return;

    mkdirSync(this.dataDirectory, { recursive: true });
    this.lastError = null;

    if (!this.isPackaged) {
      const command = process.platform === "win32" ? "mvn.cmd" : "mvn";
      this.process = spawn(command, ["-f", join(this.appPath, "backend", "pom.xml"), "spring-boot:run", ...this.backendArguments], {
        cwd: this.appPath,
        windowsHide: true,
        stdio: "ignore"
      });
    } else {
      if (!existsSync(this.jarPath)) throw new Error(`Packaged backend JAR not found: ${this.jarPath}`);
      this.process = spawn(this.javaPath, ["-jar", this.jarPath, ...this.backendArguments], {
        windowsHide: true,
        stdio: "ignore"
      });
    }

    const child = this.process;
    child.once("error", (error) => { this.lastError = error; });
    child.once("exit", (code) => {
      if (code !== 0 && this.process === child) this.lastError = new Error(`Spring Boot exited with code ${code}.`);
      if (this.process === child) this.process = null;
    });

    try {
      await this.waitForHealth();
    } catch (error) {
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

    throw new Error(`Local Spring Boot backend did not become ready on port ${this.port} within ${timeoutMs}ms.`);
  }

  async stop() {
    const child = this.process;
    if (!child) return;

    this.process = null;
    this.lastError = null;

    if (child.exitCode !== null || child.killed) return;

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
