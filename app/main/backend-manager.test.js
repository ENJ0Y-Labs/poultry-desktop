import { EventEmitter } from "node:events";
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { spawnMock } = vi.hoisted(() => ({ spawnMock: vi.fn() }));

import { BackendManager } from "./backend-manager.js";

function fakeChild() {
  const child = new EventEmitter();
  child.exitCode = null;
  child.signalCode = null;
  child.killed = false;
  child.kill = vi.fn(() => {
    child.killed = true;
    child.exitCode = 0;
    queueMicrotask(() => child.emit("exit", 0));
  });
  return child;
}

function createTempUserDataPath() {
  return mkdtempSync(join(tmpdir(), "poultry-backend-manager-"));
}

describe("BackendManager", () => {
  let resourcesPath;
  let javaExecutable;

  beforeEach(() => {
    spawnMock.mockReset();
    globalThis.fetch = vi.fn(async () => ({
      ok: true,
      json: async () => ({
        status: "UP",
        instanceId: spawnMock.mock.calls.at(-1)?.[2]?.env?.POULTRY_INSTANCE_ID
      })
    }));

    resourcesPath = mkdtempSync(join(tmpdir(), "poultry-electron-resources-"));
    javaExecutable = process.platform === "win32" ? "java.exe" : "java";
    mkdirSync(join(resourcesPath, "backend"), { recursive: true });
    mkdirSync(join(resourcesPath, "runtime", "bin"), { recursive: true });
    writeFileSync(join(resourcesPath, "backend", "poultry-backend.jar"), "");
    writeFileSync(join(resourcesPath, "runtime", "bin", javaExecutable), "");
  });

  afterEach(() => {
    rmSync(resourcesPath, { recursive: true, force: true });
  });

  it("refuses to fall back to a system Java runtime in packaged builds", () => {
    rmSync(join(resourcesPath, "runtime", "bin", javaExecutable), { force: true });

    const manager = new BackendManager({
      userDataPath: "C:/Users/test/AppData/Roaming/Poultry Farm Manager",
      appPath: "C:/project",
      isPackaged: true,
      resourcesPath,
    });

    expect(() => manager.javaPath).toThrow(/bundled Java runtime is missing/i);
  });

  it("does not pin the packaged backend to the development port", () => {
    const previousPort = process.env.POULTRY_BACKEND_PORT;
    process.env.POULTRY_BACKEND_PORT = "19003";

    try {
      const manager = new BackendManager({
        userDataPath: "C:/Users/test/AppData/Roaming/Poultry Farm Manager",
        appPath: "C:/project",
        isPackaged: true,
      });

      expect(manager.port).toBe(0);
    } finally {
      if (previousPort === undefined) delete process.env.POULTRY_BACKEND_PORT;
      else process.env.POULTRY_BACKEND_PORT = previousPort;
    }
  });

  it("uses the project data directory for the development database", () => {
    const manager = new BackendManager({
      userDataPath: "C:/Users/test/AppData/Roaming/Poultry Farm Manager",
      appPath: "C:/project",
      isPackaged: false,
    });

    expect(manager.databasePath).toMatch(/C:[\\/]project[\\/]data[\\/]poultry\.db$/);
  });

  it("uses the Electron application-data directory for the production database", () => {
    const manager = new BackendManager({
      userDataPath: "C:/Users/test/AppData/Roaming/Poultry Farm Manager",
      appPath: "C:/project",
      isPackaged: true,
      port: 19001,
    });

    expect(manager.databasePath).toContain("Poultry Farm Manager");
    expect(manager.databasePath).toMatch(/data[\\/]poultry\.db$/);
  });

  it("starts Spring Boot with a unique instance ID and waits for its health response", async () => {
    const child = fakeChild();
    spawnMock.mockReturnValue(child);
    const userDataPath = createTempUserDataPath();

    try {
      const manager = new BackendManager({
        userDataPath,
        appPath: "C:/project",
        isPackaged: true,
        port: 19002,
        resourcesPath,
        spawnProcess: spawnMock,
      });

      await manager.start();

      expect(spawnMock).toHaveBeenCalledWith(
        expect.any(String),
        expect.arrayContaining([
          "-jar",
          expect.stringContaining("poultry-backend.jar"),
          "--server.address=127.0.0.1",
          "--server.port=19002",
          expect.stringContaining("--poultry.database-path="),
          "--spring.profiles.active=prod",
        ]),
        expect.objectContaining({
          windowsHide: true,
          stdio: ["ignore", "pipe", "pipe"],
          env: expect.objectContaining({ POULTRY_INSTANCE_ID: manager.instanceId })
        })
      );
      expect(manager.instanceId).toMatch(/^[0-9a-f-]{36}$/i);
      expect(globalThis.fetch).toHaveBeenCalledWith("http://127.0.0.1:19002/api/v1/health");
      expect(existsSync(join(userDataPath, "logs", "backend-process.log"))).toBe(true);
      expect(readFileSync(join(userDataPath, "logs", "backend-process.log"), "utf8")).toContain("Backend health check passed");
    } finally {
      rmSync(userDataPath, { recursive: true, force: true });
    }
  });

  it("chooses an available non-development port for a packaged backend", async () => {
    const child = fakeChild();
    spawnMock.mockReturnValue(child);
    const userDataPath = createTempUserDataPath();

    try {
      const manager = new BackendManager({
        userDataPath,
        appPath: "C:/project",
        isPackaged: true,
        resourcesPath,
        spawnProcess: spawnMock,
      });

      await manager.start();

      expect(manager.port).toBeGreaterThan(0);
      expect(manager.port).not.toBe(18942);
      expect(spawnMock.mock.calls[0][1]).toContain(`--server.port=${manager.port}`);
      expect(globalThis.fetch).toHaveBeenCalledWith(`http://127.0.0.1:${manager.port}/api/v1/health`);
    } finally {
      rmSync(userDataPath, { recursive: true, force: true });
    }
  });

  it("rejects a successful health response from a different backend instance", async () => {
    globalThis.fetch.mockResolvedValue({
      ok: true,
      json: async () => ({ status: "UP", instanceId: "some-other-instance" })
    });
    const child = fakeChild();
    const manager = new BackendManager({
      userDataPath: createTempUserDataPath(),
      appPath: "C:/project",
      isPackaged: true,
      port: 19124,
      resourcesPath,
      spawnProcess: spawnMock,
    });
    manager.process = child;
    manager.instanceId = "expected-instance";

    await expect(manager.waitForHealth({
      child,
      instanceId: "expected-instance",
      timeoutMs: 1,
      intervalMs: 0
    })).rejects.toThrow(/did not prove its identity/i);
  });

  it("stops the Spring Boot process during shutdown", async () => {
    const child = fakeChild();
    spawnMock.mockReturnValue(child);
    const userDataPath = createTempUserDataPath();

    try {
      const manager = new BackendManager({
        userDataPath,
        appPath: "C:/project",
        isPackaged: true,
        resourcesPath,
        spawnProcess: spawnMock,
      });

      await manager.start();
      await manager.stop();

      expect(child.kill).toHaveBeenCalledTimes(1);
    } finally {
      rmSync(userDataPath, { recursive: true, force: true });
    }
  });

  it("reports actionable startup recovery guidance when health never becomes ready", async () => {
    globalThis.fetch.mockRejectedValue(new Error("connection refused"));
    const child = fakeChild();
    const manager = new BackendManager({
      userDataPath: createTempUserDataPath(),
      appPath: "C:/project",
      isPackaged: true,
      port: 19123,
      resourcesPath,
      spawnProcess: spawnMock,
    });
    manager.process = child;

    await expect(manager.waitForHealth({ child, timeoutMs: 1, intervalMs: 0 }))
      .rejects.toThrow(/did not prove its identity.*database may be unavailable.*migration may have failed/i);
  });
});
