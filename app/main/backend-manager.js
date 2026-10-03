import { spawn } from "node:child_process";
import { existsSync, mkdirSync } from "node:fs";
import { join } from "node:path";

export class BackendManager {
  constructor({ userDataPath, appPath, isPackaged }) {
    this.userDataPath = userDataPath;
    this.appPath = appPath;
    this.isPackaged = isPackaged;
    this.process = null;
    this.port = Number(process.env.POULTRY_BACKEND_PORT || 18942);
    this.lastError = null;
  }

  get databasePath() {
    const root = this.isPackaged ? this.userDataPath : this.appPath;
    return join(root, "data", "poultry.db");
  }

  get javaPath() {
    const bundled = join(
      process.resourcesPath,
      "runtime",
      process.platform === "win32" ? "bin/java.exe" : "bin/java"
    );
    return this.isPackaged && existsSync(bundled) ? bundled : "java";
  }

  get jarPath() {
    return join(process.resourcesPath, "backend", "poultry-backend.jar");
  }

  async start() {
    mkdirSync(join(this.userDataPath, "data"), { recursive: true });

    if (!this.isPackaged) {
      const command = process.platform === "win32" ? "mvn.cmd" : "mvn";
      this.process = spawn(command, [
        "-f", join(this.appPath, "backend", "pom.xml"),
        "spring-boot:run",
        "--server.port=" + this.port,
        "--poultry.database-path=" + this.databasePath,
        "--spring.profiles.active=dev"
      ], { cwd: this.appPath, windowsHide: true, stdio: "ignore" });
      this.process.on("error", (error) => { this.lastError = error; });
      this.process.on("exit", (code) => {
        if (code !== 0 && this.process) this.lastError = new Error("Spring Boot exited with code " + code + ".");
      });
    } else {
      if (!existsSync(this.jarPath)) {
        throw new Error(`Packaged backend JAR not found: ${this.jarPath}`);
      }

      this.process = spawn(
        this.javaPath,
        [
          "-jar",
          this.jarPath,
          `--server.port=${this.port}`,
          `--poultry.database-path=${this.databasePath}`,
          "--spring.profiles.active=prod"
        ],
        { windowsHide: true, stdio: "ignore" }
      );

      this.process.on("error", (error) => {
        this.lastError = error;
      });
      this.process.on("exit", (code) => {
        if (code !== 0 && this.process) {
          this.lastError = new Error(`Spring Boot exited with code ${code}.`);
        }
      });
    }

    await this.waitForHealth();
  }

  async waitForHealth({ timeoutMs = 30000, intervalMs = 250 } = {}) {
    const started = Date.now();

    while (Date.now() - started < timeoutMs) {
      if (this.lastError) throw this.lastError;
      try {
        const response = await fetch(`http://127.0.0.1:${this.port}/api/v1/health`);
        if (response.ok) return;
      } catch {
        this.lastError = null;
      }

      await new Promise((resolve) => setTimeout(resolve, intervalMs));
    }

    throw new Error(`Local Spring Boot backend did not become ready on port ${this.port}.`);
  }

  async stop() {
    if (!this.process) return;

    const child = this.process;
    this.process = null;

    if (!child.killed) {
      child.kill();
      await new Promise((resolve) => {
        const timer = setTimeout(resolve, 5000);
        child.once("exit", () => {
          clearTimeout(timer);
          resolve();
        });
      });
    }
  }
}
