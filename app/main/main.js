import { app, BrowserWindow, dialog, ipcMain } from "electron";
import { copyFileSync, existsSync, renameSync, rmSync } from "node:fs";
import { join } from "node:path";
import { pathToFileURL } from "node:url";
import { BackendManager } from "./backend-manager.js";

let mainWindow;
let backendManager;
let shutdownInProgress = false;

const log = (event, details = {}) => {
  console.info(JSON.stringify({
    timestamp: new Date().toISOString(),
    event,
    ...details
  }));
};

const backupFilename = (file) => file.split(/[\\/]/).pop();

function friendlyFileError(error, fallback) {
  const message = error instanceof Error ? error.message : String(error || "");
  const lower = message.toLowerCase();
  if (lower.includes("enospc") || lower.includes("disk full") || lower.includes("not enough space")) {
    return "There is not enough disk space to complete the operation. Free some space and try again.";
  }
  if (lower.includes("eacces") || lower.includes("eperm") || lower.includes("access is denied") || lower.includes("permission denied")) {
    return "The application does not have permission to access the required database file or folder.";
  }
  if (lower.includes("enoent") || lower.includes("no such file")) {
    return "The selected backup file could not be found. Select an existing backup and try again.";
  }
  return fallback;
}

async function validateBackup(file) {
  const response = await fetch("http://127.0.0.1:" + backendManager.port + "/api/v1/backup/validate", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ file })
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok || !body?.data?.valid) {
    throw new Error(body?.error?.message || "The selected backup is invalid.");
  }
  return body.data;
}

function schemaVersionNumber(version) {
  const match = String(version || "").match(/^\d+/);
  return match ? Number.parseInt(match[0], 10) : 0;
}

async function waitForBackendExit(timeoutMs = 5_000) {
  const started = Date.now();
  while (backendManager?.process && Date.now() - started < timeoutMs) {
    await new Promise(resolve => setTimeout(resolve, 50));
  }
  if (backendManager?.process) throw new Error("The local backend did not stop cleanly.");
}

ipcMain.handle("backup:choose-directory", async (_event, ...args) => {
  if (args.length !== 0) throw new Error("Invalid IPC message.");
  const result = await dialog.showOpenDialog({ properties: ["openDirectory", "createDirectory"] });
  return result.canceled ? null : result.filePaths[0];
});

ipcMain.handle("backup:restore", async (_event, ...args) => {
  if (args.length !== 0) throw new Error("Invalid IPC message.");
  if (!backendManager) throw new Error("Backend is not running.");

  const picked = await dialog.showOpenDialog({
    title: "Restore poultry database backup",
    properties: ["openFile"],
    filters: [{ name: "Poultry database", extensions: ["db"] }]
  });
  if (picked.canceled) return { canceled: true };

  const backup = picked.filePaths[0];
  const database = backendManager.databasePath;
  log("restore_started", { filename: backupFilename(backup) });

  try {
    if (backup === database) throw new Error("The active database cannot be restored over itself.");

    const validation = await validateBackup(backup);
    const currentHealthResponse = await fetch(
      "http://127.0.0.1:" + backendManager.port + "/api/v1/health"
    );
    const currentHealth = await currentHealthResponse.json().catch(() => ({}));
    if (!currentHealthResponse.ok || currentHealth.schema !== "VALID") {
      throw new Error("The current database is not healthy enough to perform a restore.");
    }

    const currentVersion = schemaVersionNumber(currentHealth.schemaVersion);
    const backupVersion = schemaVersionNumber(validation.schemaVersion);
    if (!backupVersion) throw new Error("The selected backup has no usable schema version.");
    if (backupVersion > currentVersion) {
      throw new Error("This backup was created by a newer application version.");
    }

    const confirmation = await dialog.showMessageBox({
      type: "warning",
      buttons: ["Restore backup", "Cancel"],
      defaultId: 1,
      cancelId: 1,
      title: "Confirm database restore",
      message: "Restore this database backup?",
      detail: "The current database will be replaced. A safety backup will be kept before replacement."
    });
    if (confirmation.response !== 0) {
      log("restore_cancelled", { filename: backupFilename(backup) });
      return { canceled: true };
    }

    const safety = database.replace(/\.db$/, "") +
      "-pre-restore-" + new Date().toISOString().replace(/[:.]/g, "-") + ".db";
    const temporary = database + ".restore-" + Date.now() + ".tmp";
    const wal = database + "-wal";
    const shm = database + "-shm";

    await backendManager.stop();
    await waitForBackendExit();

    let replacementStarted = false;
    try {
      if (existsSync(database)) copyFileSync(database, safety);

      copyFileSync(backup, temporary);
      replacementStarted = true;
      rmSync(database, { force: true });
      rmSync(wal, { force: true });
      rmSync(shm, { force: true });
      renameSync(temporary, database);

      await backendManager.start();
      log("restore_completed", {
        filename: backupFilename(backup),
        schemaVersion: validation.schemaVersion
      });
      return {
        canceled: false,
        restored: true,
        safetyBackup: safety,
        schemaVersion: validation.schemaVersion
      };
    } catch (error) {
      log("restore_failed", {
        filename: backupFilename(backup),
        error: error instanceof Error ? error.message : String(error)
      });

      rmSync(temporary, { force: true });
      if (replacementStarted) {
        rmSync(database, { force: true });
        if (existsSync(safety)) copyFileSync(safety, database);
      }

      try {
        await backendManager.start();
      } catch (restartError) {
        log("restore_recovery_failed", {
          error: restartError instanceof Error ? restartError.message : String(restartError)
        });
      }
      throw error;
    }
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    log("restore_rejected", {
      filename: backupFilename(backup),
      error: message
    });
    const friendly = message.includes("newer application version") || message.includes("cannot be restored")
      || message.includes("not healthy enough") || message.includes("valid schema version")
      || message.includes("backup is invalid")
      ? message
      : friendlyFileError(error, "The database restore could not be completed. The original database was kept safe where possible.");
    throw new Error(friendly);
  }
});

async function createWindow() {
  backendManager = new BackendManager({
    userDataPath: app.getPath("userData"),
    appPath: app.getAppPath(),
    isPackaged: app.isPackaged
  });

  log("application_starting", { packaged: app.isPackaged });

  try {
    await backendManager.start();
    log("backend_started", { port: backendManager.port });
  } catch (error) {
    log("backend_start_failed", {
      error: error instanceof Error ? error.message : String(error)
    });
    await dialog.showMessageBox({
      type: "error",
      title: "Backend startup failed",
      message: "Poultry Farm Manager could not start its local backend.",
      detail: friendlyFileError(error, "The local backend could not start. Check the backend log for the database or migration error.")
    });
    app.quit();
    return;
  }

  mainWindow = new BrowserWindow({
    width: 1366,
    height: 768,
    minWidth: 1100,
    minHeight: 650,
    webPreferences: {
      preload: join(__dirname, "../preload/preload.js"),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  });

  mainWindow.webContents.setWindowOpenHandler(() => ({ action: "deny" }));
  mainWindow.webContents.on("will-navigate", (event, url) => {
    const rendererFile = pathToFileURL(join(__dirname, "../renderer/index.html")).href;
    let allowed = url === rendererFile;
    if (process.env.ELECTRON_RENDERER_URL) {
      try {
        allowed = allowed || new URL(url).origin === new URL(process.env.ELECTRON_RENDERER_URL).origin;
      } catch {
        allowed = false;
      }
    }
    if (!allowed) event.preventDefault();
  });

  mainWindow.webContents.on("will-redirect", (event) => event.preventDefault());

  if (process.env.ELECTRON_RENDERER_URL) {
    const rendererUrl = new URL(process.env.ELECTRON_RENDERER_URL);
    if (rendererUrl.protocol !== "http:" || !["localhost", "127.0.0.1"].includes(rendererUrl.hostname)) {
      throw new Error("The Electron renderer must use a local development URL.");
    }
    await mainWindow.loadURL(rendererUrl.href);
  } else {
    await mainWindow.loadFile(join(__dirname, "../renderer/index.html"));
  }
}

async function shutdown() {
  if (shutdownInProgress) return;
  shutdownInProgress = true;
  log("application_shutdown_started");

  try {
    if (mainWindow && !mainWindow.isDestroyed()) mainWindow.destroy();
    if (backendManager) await backendManager.stop();
  } finally {
    log("application_shutdown_completed");
    app.quit();
  }
}

app.whenReady().then(createWindow);
app.on("window-all-closed", () => {
  if (process.platform !== "darwin") app.quit();
});
app.on("before-quit", (event) => {
  if (shutdownInProgress) return;
  event.preventDefault();
  void shutdown();
});
