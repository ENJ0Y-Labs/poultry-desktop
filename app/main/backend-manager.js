import { spawn } from "node:child_process";
import { randomUUID } from "node:crypto";
import { createServer } from "node:net";
import { appendFileSync, existsSync, mkdirSync } from "node:fs";
import { join } from "node:path";

const DEFAULT_DEV_PORT = 18942;
const STARTUP_TIMEOUT_MS = 30_000;
const HEALTH_INTERVAL_MS = 250;
const STOP_TIMEOUT_MS = 5_000;
const PACKAGED_PORT_ATTEMPTS = 3;

async function findAvailablePort() {
  const server = createServer();
  await new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(0, "127.0.0.1", resolve);
  });

  const address = server.address();
  if (!address || typeof address === "string") {
    await new Promise((resolve) => server.close(resolve));
    throw new Error("Could not determine an available local backend port.");
  }

  const { port } = address;
  await new Promise((resolve, reject) => server.close((error) => error ? reject(error) : resolve()));
  return port;
}

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
    this.explicitPort = Number(port) > 0;
    this.resourcesPath = resourcesPath;
    this.spawnProcess = spawnProcess;
    this.process = null;
    this.port = Number(port ?? ((!isPackaged && process.env.POULTRY_BACKEND_PORT) || (isPackaged ? 0 : DEFAULT_DEV_PORT)));
    this.instanceId = null;
    this.lastError = null;
  }

  get dataDirectory() {
    return this.isPackaged ? join(this.userDataPath, "data") : join(this.appPath, "data");
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
    const bundled = join(this.resourcesPath, "runtime", "bin", process.platform === "win32" ? "java.exe" : "java");
    if (!this.isPackaged) return "java";
    if (!existsSync(bundled)) {
      throw new Error(`The bundled Java runtime is missing: ${bundled}. Rebuild the Pomas installer with the packaged runtime; do not require customers to install Java.`);
    }
    return bundled;
  }

  /**
   * Returns true when JAVA_HOME points to a directory containing a usable java executable.
   * Used to validate the environment before launching the Maven wrapper in development mode.
   * Never hardcodes a developer-specific JDK path.
   */
  javaHomePointsToValidJava() {
    const javaHome = process.env.JAVA_HOME;
    if (!javaHome) return false;
    const javaExecutable = join(javaHome, "bin", process.platform === "win32" ? "java.exe" : "java");
    return existsSync(javaExecutable);
  }

  get jarPath() {
    return join(this.resourcesPath, "backend", "poultry-backend.jar");
  }

  get backendArguments() {
    return [
      "127.0.0.1",
      this.port,
      this.databasePath,
      this.isPackaged ? "prod" : "dev",
      this.logFilePath
    ];
  }

  get backendArgumentsForMaven() {
    const [, , databasePath, profile, logName] = this.backendArguments;
    return [
      `-Dpoultry.database-path=${databasePath}`,
      `-Dspring.profiles.active=${profile}`,
      `-Dlogging.file.name=${logName}`
    ];
  }

  get backendArgumentsForJava() {
    const [address, port, databasePath, profile, logName] = this.backendArguments;
    return [
      "-jar",
      this.jarPath,
      `--server.address=${address}`,
      `--server.port=${port}`,
      `--poultry.database-path=${databasePath}`,
      `--spring.profiles.active=${profile}`,
      `--logging.file.name=${logName}`
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
    const attempts = this.isPackaged ? PACKAGED_PORT_ATTEMPTS : 1;
    let lastStartupError;

    for (let attempt = 1; attempt <= attempts; attempt += 1) {
      if (this.isPackaged && !(attempt === 1 && this.explicitPort)) this.port = await findAvailablePort();
      this.instanceId = randomUUID();

      try {
        await this.startOnce();
        return;
      } catch (error) {
        lastStartupError = error;
        const processExited = /backend process exited before becoming healthy/i.test(String(error?.message || error));
        if (!this.isPackaged || !processExited || attempt === attempts) throw error;
        this.writeLog(`Backend exited during startup on port ${this.port}; retrying with a new port (attempt ${attempt + 1}/${attempts}).`);
      }
    }

    throw lastStartupError || new Error("Pomas could not start its local backend.");
  }

  async startOnce() {
    mkdirSync(this.dataDirectory, { recursive: true });
    mkdirSync(join(this.isPackaged ? this.userDataPath : this.appPath, "logs"), { recursive: true });
    this.lastError = null;
    this.writeLog(`Starting backend; packaged=${this.isPackaged}; database=${this.databasePath}; port=${this.port}; instanceId=${this.instanceId}`);

    try {
      if (!this.isPackaged) {
        const isWin = process.platform === "win32";
        const wrapper = join(this.appPath, isWin ? "mvnw.cmd" : "mvnw");
        // Pass a copy of the environment so we can drop a broken JAVA_HOME without
        // mutating the Electron process environment. The Maven wrapper validates
        // JAVA_HOME and exits with code 1 when it points to a missing java/javac.
        // Dropping an invalid JAVA_HOME lets the wrapper resolve Java from PATH instead.
        const env = { ...process.env };
        if (process.env.JAVA_HOME && !this.javaHomePointsToValidJava()) {
          this.writeLog(
            `Warning: JAVA_HOME="${process.env.JAVA_HOME}" does not point to a valid Java executable. ` +
              `Removing JAVA_HOME so the Maven wrapper can resolve Java from PATH.`
          );
          delete env.JAVA_HOME;
        }
        this.process = this.spawnProcess(
          wrapper,
          ["-f", join(this.appPath, "backend", "pom.xml"), "spring-boot:run"],
          {
            cwd: this.appPath,
            windowsHide: true,
            stdio: ["ignore", "pipe", "pipe"],
            shell: isWin,
            env: {
              ...env,
              SERVER_PORT: String(this.port),
              DATABASE_PATH: this.databasePath,
              LOG_FILE: this.logFilePath,
              POULTRY_INSTANCE_ID: this.instanceId
            }
          }
        );
      } else {
        if (!existsSync(this.jarPath)) throw new Error(`Packaged backend JAR not found: ${this.jarPath}`);
        this.process = this.spawnProcess(this.javaPath, this.backendArgumentsForJava, {
          windowsHide: true,
          stdio: ["ignore", "pipe", "pipe"],
          env: { ...process.env, POULTRY_INSTANCE_ID: this.instanceId }
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
      await this.waitForHealth({ child, instanceId: this.instanceId });
      this.writeLog(`Backend health check passed for instance ${this.instanceId} on port ${this.port}.`);
    } catch (error) {
      this.writeLog(`Backend startup failed: ${error.stack || error.message || error}`);
      await this.stop();
      throw error;
    }
  }

  async waitForHealth({
    child = this.process,
    instanceId = this.instanceId,
    timeoutMs = STARTUP_TIMEOUT_MS,
    intervalMs = HEALTH_INTERVAL_MS
  } = {}) {
    const started = Date.now();
    while (Date.now() - started < timeoutMs) {
      if (this.lastError) throw this.lastError;
      if (!child || child.exitCode !== null || child.signalCode !== null || this.process !== child) {
        throw this.lastError || new Error("Backend process exited before becoming healthy.");
      }

      try {
        const response = await fetch(`http://127.0.0.1:${this.port}/api/v1/health`);
        const body = response.ok ? await response.json().catch(() => null) : null;
        if (body?.instanceId === instanceId && body?.status === "UP") return true;
      } catch {
        // Spring Boot may still be starting. Poll until the startup deadline.
      }

      await new Promise((resolve) => setTimeout(resolve, intervalMs));
    }

    throw new Error(`The local backend did not prove its identity on port ${this.port}. Another application may be using that port, the database may be unavailable, or a migration may have failed. Check ${this.logFilePath} for Spring Boot errors and ${this.processLogFilePath} for process startup output.`);
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
