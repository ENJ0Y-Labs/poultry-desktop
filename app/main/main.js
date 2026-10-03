import { app, BrowserWindow, dialog, ipcMain } from "electron";
import { copyFileSync, existsSync } from "node:fs";
import { join } from "node:path";
import { BackendManager } from "./backend-manager.js";

let mainWindow;
let backendManager;
let shutdownInProgress = false;

async function validateBackup(file) {
  const response = await fetch("http://127.0.0.1:" + backendManager.port + "/api/v1/backup/validate", {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ file })
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok || !body?.data?.valid) throw new Error(body?.error?.message || "The selected backup is invalid.");
  return body.data;
}

ipcMain.handle("backup:choose-directory", async () => {
  const result = await dialog.showOpenDialog({ properties: ["openDirectory", "createDirectory"] });
  return result.canceled ? null : result.filePaths[0];
});

ipcMain.handle("backup:restore", async () => {
  if (!backendManager) throw new Error("Backend is not running.");
  const picked = await dialog.showOpenDialog({
    title: "Restore poultry database backup",
    properties: ["openFile"],
    filters: [{ name: "Poultry database", extensions: ["db"] }]
  });
  if (picked.canceled) return { canceled: true };
  const backup = picked.filePaths[0];
  const validation = await validateBackup(backup);
  const currentHealth = await fetch("http://127.0.0.1:" + backendManager.port + "/api/v1/health").then(r => r.json());
  const currentVersion = Number.parseInt(String(currentHealth.schemaVersion || "0").replace(/[^0-9].*$/, ""), 10) || 0;
  const backupVersion = Number.parseInt(String(validation.schemaVersion || "0").replace(/[^0-9].*$/, ""), 10) || 0;
  if (backupVersion > currentVersion) throw new Error("This backup was created by a newer application version.");

  const database = backendManager.databasePath;
  const safety = database.replace(/\\.db$/, "") + "-pre-restore-" + new Date().toISOString().replace(/[:.]/g, "-") + ".db";
  await backendManager.stop();
  try {
    if (existsSync(database)) copyFileSync(database, safety);
    copyFileSync(backup, database);
    await backendManager.start();
    return { canceled: false, restored: true, safetyBackup: safety };
  } catch (error) {
    console.error("Database restore failed; attempting safety restore:", error);
    if (existsSync(safety)) copyFileSync(safety, database);
    try { await backendManager.start(); } catch (restartError) { console.error("Backend restart after restore failure failed:", restartError); }
    throw error;
  }
});

async function createWindow() {
  backendManager = new BackendManager({
    userDataPath: app.getPath("userData"),
    appPath: app.getAppPath(),
    isPackaged: app.isPackaged
  });

  try {
    await backendManager.start();
  } catch (error) {
    await dialog.showMessageBox({
      type: "error",
      title: "Backend startup failed",
      message: "Poultry Farm Manager could not start its local backend.",
      detail: error instanceof Error ? error.message : String(error)
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
    const allowed = url.startsWith("file://") || url.startsWith("http://localhost:") || url.startsWith("http://127.0.0.1:");
    if (!allowed) event.preventDefault();
  });

  if (process.env.ELECTRON_RENDERER_URL) await mainWindow.loadURL(process.env.ELECTRON_RENDERER_URL);
  else await mainWindow.loadFile(join(__dirname, "../renderer/index.html"));
}

async function shutdown() {
  if (shutdownInProgress) return;
  shutdownInProgress = true;

  try {
    if (mainWindow && !mainWindow.isDestroyed()) mainWindow.destroy();
    if (backendManager) await backendManager.stop();
  } finally {
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
