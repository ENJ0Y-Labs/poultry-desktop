import { EventEmitter } from "node:events";
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { spawnMock } = vi.hoisted(() => ({ spawnMock: vi.fn() }));

import { BackendManager } from "./backend-manager.js";

function fakeChild() {
  const child = new EventEmitter();
  child.exitCode = null;
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

  beforeEach(() => {
    spawnMock.mockReset();
    globalThis.fetch = vi.fn(async () => ({ ok: true }));

    resourcesPath = mkdtempSync(join(tmpdir(), "poultry-electron-resources-"));
    mkdirSync(join(resourcesPath, "backend"), { recursive: true });
    mkdirSync(join(resourcesPath, "runtime", "bin"), { recursive: true });
    writeFileSync(join(resourcesPath, "backend", "poultry-backend.jar"), "");
    writeFileSync(join(resourcesPath, "runtime", "bin", "java.exe"), "");

  });

  afterEach(() => {
    rmSync(resourcesPath, { recursive: true, force: true });
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

  it("starts Spring Boot with the database path and waits for health", async () => {
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
        expect.objectContaining({ windowsHide: true, stdio: "ignore" })
      );
      expect(globalThis.fetch).toHaveBeenCalledWith("http://127.0.0.1:19002/api/v1/health");
    } finally {
      rmSync(userDataPath, { recursive: true, force: true });
    }
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
});
