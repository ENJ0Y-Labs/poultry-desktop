import { app, BrowserWindow, dialog, ipcMain, nativeTheme } from "electron";
import { copyFileSync, existsSync, mkdirSync, readFileSync, renameSync, rmSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { pathToFileURL } from "node:url";
import { BackendManager } from "./backend-manager.js";

// The product is branded "Pomas", but installed copies keep their farm data in the
// folder created under the original product name. Pin the packaged data folder so
// the rename can never make existing records appear to vanish.
const LEGACY_USER_DATA_FOLDER = "Poultry Farm Manager";
if (app.isPackaged) {
  app.setPath("userData", join(app.getPath("appData"), LEGACY_USER_DATA_FOLDER));
}

let mainWindow;
let backendManager;
let shutdownInProgress = false;

ipcMain.handle("app:info", async (_event, ...args) => {
  if (args.length !== 0) throw new Error("Invalid IPC message.");
  return Object.freeze({ version: app.getVersion(), architecture: process.arch, isPackaged: app.isPackaged, customTitleBar: process.platform === "win32" });
});

ipcMain.handle("app:quit", async (event, ...args) => {
  if (args.length !== 0 || event.sender !== mainWindow?.webContents) throw new Error("Invalid IPC message.");
  app.quit();
  return true;
});

ipcMain.handle("backend:base-url", async (_event, ...args) => {
  if (args.length !== 0) throw new Error("Invalid IPC message.");
  if (!backendManager?.port) throw new Error("The local backend is not ready.");
  return backendManager.baseUrl;
});


async function loadLocalDesktopRenderer() {
  if (process.env.ELECTRON_RENDERER_URL) {
    await mainWindow.loadURL(process.env.ELECTRON_RENDERER_URL);
  } else {
    await mainWindow.loadFile(join(__dirname, "../renderer/index.html"));
  }
}

async function requireManagerSession(event) {
  if (event.sender !== mainWindow?.webContents || !backendManager?.process) {
    throw new Error("This desktop action is not available from this window.");
  }
  const baseUrl = backendManager.baseUrl;
  const cookies = await mainWindow.webContents.session.cookies.get({
    url: baseUrl,
    name: "poultry_session"
  });
  const sessionCookie = cookies.find(cookie => cookie.name === "poultry_session");
  if (!sessionCookie?.value) throw new Error("Sign in with a Manager account before changing LAN access.");
  const response = await fetch(baseUrl + "/api/v1/auth/me", {
    headers: { Cookie: "poultry_session=" + sessionCookie.value }
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok || payload?.data?.role !== "MANAGER") {
    throw new Error("Only an authenticated Manager can change LAN access.");
  }
  return sessionCookie.value;
}

async function moveDesktopSessionCookie(url, token, lanMode) {
  await mainWindow.webContents.session.cookies.set({
    url,
    name: "poultry_session",
    value: token,
    path: "/",
    httpOnly: true,
    secure: !lanMode,
    sameSite: lanMode ? "lax" : "no_restriction"
  });
}

ipcMain.handle("lan-access:info", async (event, ...args) => {
  if (args.length !== 0) throw new Error("Invalid IPC message.");
  await requireManagerSession(event);
  return Object.freeze({
    interfaces: backendManager.getLanInterfaces(),
    status: backendManager.lanStatus
  });
});

ipcMain.handle("lan-access:enable", async (event, selection, ...args) => {
  if (args.length !== 0 || !selection || typeof selection !== "object"
      || typeof selection.interfaceName !== "string" || typeof selection.address !== "string") {
    throw new Error("Invalid LAN interface selection.");
  }
  const token = await requireManagerSession(event);
  const confirmation = await dialog.showMessageBox(mainWindow, {
    type: "warning",
    buttons: ["Enable LAN access", "Cancel"],
    defaultId: 1,
    cancelId: 1,
    title: "Enable network access",
    message: "Allow other devices on this private network to access Pomas?",
    detail: "Anyone on the selected network who can reach this computer will see the Pomas sign-in page. Only enable this on a trusted private network."
  });
  if (confirmation.response !== 0) return { canceled: true };
  const status = await backendManager.enableLanAccess(selection);
  try {
    const browserResponse = await fetch(status.url + "/", { redirect: "manual" });
    const browserHtml = browserResponse.ok && (browserResponse.headers.get("content-type") || "").includes("text/html")
      ? await browserResponse.text() : "";
    if (!browserHtml.includes('<div id="root">') || !browserHtml.includes("theme-init.js")) {
      throw new Error("The production browser workspace is missing or incomplete. Build the renderer before enabling LAN access.");
    }
    await moveDesktopSessionCookie(status.url, token, true);
    await mainWindow.loadURL(status.url);
    return status;
  } catch (error) {
    await backendManager.disableLanAccess();
    await loadLocalDesktopRenderer();
    throw new Error("LAN access was rolled back because the browser workspace could not be opened. " + error.message);
  }
});

ipcMain.handle("lan-access:disable", async (event, ...args) => {
  if (args.length !== 0) throw new Error("Invalid IPC message.");
  const token = await requireManagerSession(event);
  const confirmation = await dialog.showMessageBox(mainWindow, {
    type: "warning",
    buttons: ["Disable LAN access", "Cancel"],
    defaultId: 1,
    cancelId: 1,
    title: "Disable network access",
    message: "Stop access from other devices?",
    detail: "Pomas will return to localhost-only mode. Other devices will be disconnected."
  });
  if (confirmation.response !== 0) return { canceled: true };
  await backendManager.disableLanAccess();
  await moveDesktopSessionCookie(backendManager.baseUrl, token, false);
  await loadLocalDesktopRenderer();
  return backendManager.lanStatus;
});

const DRAFT_IDS = new Set([
  "batch", "feedPurchase", "expense", "health", "drug", "vaccination",
  "birdSale", "eggSale", "eggCollection", "weight"
]);
const draftsPath = () => join(app.getPath("userData"), "pomas-drafts.json");

function readDrafts() {
  try {
    const parsed = JSON.parse(readFileSync(draftsPath(), "utf8"));
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) return {};
    return Object.fromEntries(Object.entries(parsed).filter(([id, draft]) =>
      DRAFT_IDS.has(id) && draft && typeof draft === "object" &&
      draft.form && typeof draft.form === "object" && !Array.isArray(draft.form)
    ));
  } catch {
    return {};
  }
}

function writeDrafts(drafts) {
  const target = draftsPath();
  const temporary = target + ".tmp";
  mkdirSync(app.getPath("userData"), { recursive: true });
  writeFileSync(temporary, JSON.stringify(drafts), { encoding: "utf8", mode: 0o600 });
  renameSync(temporary, target);
}

ipcMain.handle("drafts:list", async (_event, ...args) => {
  if (args.length !== 0) throw new Error("Invalid IPC message.");
  return readDrafts();
});

ipcMain.handle("drafts:save", async (_event, payload, ...args) => {
  if (args.length !== 0 || !payload || typeof payload !== "object" || Array.isArray(payload) ||
      !DRAFT_IDS.has(payload.id) || !payload.form || typeof payload.form !== "object" ||
      Array.isArray(payload.form) || !payload.references || typeof payload.references !== "object" ||
      Array.isArray(payload.references) || (payload.posted !== undefined && typeof payload.posted !== "boolean")) {
    throw new Error("Invalid draft data.");
  }
  const serialized = JSON.stringify({ form: payload.form, references: payload.references, posted: payload.posted === true });
  if (Buffer.byteLength(serialized, "utf8") > 65536) throw new Error("This draft is too large to save.");
  const drafts = readDrafts();
  const previous = drafts[payload.id];
  const draft = {
    form: payload.form,
    references: payload.references,
    posted: payload.posted === true,
    createdAt: previous?.createdAt || new Date().toISOString(),
    savedAt: new Date().toISOString()
  };
  drafts[payload.id] = draft;
  writeDrafts(drafts);
  return draft;
});

ipcMain.handle("drafts:discard", async (_event, id, ...args) => {
  if (args.length !== 0 || typeof id !== "string" || !DRAFT_IDS.has(id)) {
    throw new Error("Invalid draft identifier.");
  }
  const drafts = readDrafts();
  delete drafts[id];
  writeDrafts(drafts);
  return { discarded: true };
});

const themePreferencePath = () => join(app.getPath("userData"), "theme-preference.json");

function readThemePreference() {
  try {
    const saved = JSON.parse(readFileSync(themePreferencePath(), "utf8"))?.theme;
    return ["auto", "light", "dark"].includes(saved) ? saved : "auto";
  } catch {
    return "auto";
  }
}

function applyNativeTheme(theme) {
  nativeTheme.themeSource = theme === "auto" ? "system" : theme;
  return nativeTheme.shouldUseDarkColors ? "#0b0f14" : "#f5f7fa";
}

const themeBackground = () => nativeTheme.shouldUseDarkColors ? "#0b0f14" : "#f5f7fa";
const titleBarOverlay = () => ({
  color: nativeTheme.shouldUseDarkColors ? "#16161a" : "#ffffff",
  symbolColor: nativeTheme.shouldUseDarkColors ? "#e4e4e7" : "#27272a",
  height: 40,
});

ipcMain.handle("theme:set", async (_event, theme) => {
  if (!["auto", "light", "dark"].includes(theme)) throw new Error("Invalid theme preference.");
  writeFileSync(themePreferencePath(), JSON.stringify({ theme }, null, 2), "utf8");
  const backgroundColor = applyNativeTheme(theme);
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.setBackgroundColor(backgroundColor);
    if (process.platform === "win32") mainWindow.setTitleBarOverlay(titleBarOverlay());
  }
  return { theme, backgroundColor };
});

nativeTheme.on("updated", () => {
  if (mainWindow && !mainWindow.isDestroyed() && nativeTheme.themeSource === "system") {
    mainWindow.setBackgroundColor(themeBackground());
    if (process.platform === "win32") mainWindow.setTitleBarOverlay(titleBarOverlay());
  }
});

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
  const response = await fetch(backendManager.baseUrl + "/api/v1/backup/validate", {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Requested-With": "XMLHttpRequest" },
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
    title: "Restore Pomas database backup",
    properties: ["openFile"],
    filters: [{ name: "Pomas database", extensions: ["db"] }]
  });
  if (picked.canceled) return { canceled: true };

  const backup = picked.filePaths[0];
  const database = backendManager.databasePath;
  log("restore_started", { filename: backupFilename(backup) });

  try {
    if (backup === database) throw new Error("The active database cannot be restored over itself.");

    const validation = await validateBackup(backup);
    const currentHealthResponse = await fetch(
      backendManager.baseUrl + "/api/v1/health"
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

    const restoreToken = Date.now() + "-" + Math.random().toString(36).slice(2);
    const temporary = database + ".restore-" + restoreToken + ".tmp";
    const rollback = database + ".pre-restore-" + restoreToken + ".db";
    const wal = database + "-wal";
    const shm = database + "-shm";

    // The backend owns SQLite. Create and validate a consistent pre-restore
    // snapshot before stopping it; never raw-copy a live WAL database.
    const safetyResponse = await fetch(
      backendManager.baseUrl + "/api/v1/backup",
      {
        method: "POST",
        headers: { "Content-Type": "application/json", "X-Requested-With": "XMLHttpRequest" },
        body: JSON.stringify({ directory: backendManager.dataDirectory })
      }
    );
    const safetyBody = await safetyResponse.json().catch(() => ({}));
    if (!safetyResponse.ok || !safetyBody?.data?.path) {
      rmSync(temporary, { force: true });
      throw new Error(safetyBody?.error?.message || "The pre-restore safety backup could not be created.");
    }
    const safety = safetyBody.data.path;
    await validateBackup(safety);
    log("restore_safety_backup_created", { filename: backupFilename(safety) });

    // Stage and validate the exact bytes that will be installed while the current
    // backend is still available to inspect the SQLite file.
    try {
      copyFileSync(backup, temporary);
      await validateBackup(temporary);
    } catch (error) {
      rmSync(temporary, { force: true });
      throw error;
    }

    try {
      await backendManager.stop();
      await waitForBackendExit();
    } catch (error) {
      rmSync(temporary, { force: true });
      throw error;
    }

    let originalMoved = false;
    let replacementPublished = false;
    try {
      // Keep the original database recoverable until the replacement has passed
      // backend startup and schema/readiness checks.
      renameSync(database, rollback);
      originalMoved = true;
      rmSync(wal, { force: true });
      rmSync(shm, { force: true });
      renameSync(temporary, database);
      replacementPublished = true;

      await backendManager.start();
      log("restore_completed", {
        filename: backupFilename(backup),
        schemaVersion: validation.schemaVersion
      });
      try { rmSync(rollback, { force: true }); }
      catch { log("restore_rollback_cleanup_deferred", { filename: backupFilename(rollback) }); }
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
      if (replacementPublished) {
        rmSync(database, { force: true });
        rmSync(wal, { force: true });
        rmSync(shm, { force: true });
      }
      if (originalMoved) {
        // Same-volume rename restores the exact stopped database file without
        // another copy operation. The validated VACUUM snapshot remains the
        // fallback if the rollback file cannot be recovered.
        try {
          renameSync(rollback, database);
        } catch (rollbackError) {
          log("restore_rollback_rename_failed", {
            error: rollbackError instanceof Error ? rollbackError.message : String(rollbackError)
          });
          if (!existsSync(safety)) {
            throw new Error("The pre-restore safety backup is missing; automatic recovery is impossible.");
          }
          copyFileSync(safety, database);
        }
      } else if (replacementPublished) {
        if (!existsSync(safety)) {
          throw new Error("The pre-restore safety backup is missing; automatic recovery is impossible.");
        }
        copyFileSync(safety, database);
      }
      rmSync(wal, { force: true });
      rmSync(shm, { force: true });

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
  const initialTheme = readThemePreference();
  const initialBackground = applyNativeTheme(initialTheme);

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
    const startupError = error instanceof Error ? error.message : String(error || "");
    await dialog.showMessageBox({
      type: "error",
      title: "Backend startup failed",
      message: "Pomas could not start its local backend.",
      detail: [
        startupError || "The local backend could not start.",
        "",
        `Spring Boot log: ${backendManager.logFilePath}`,
        `Process startup log: ${backendManager.processLogFilePath}`
      ].join("\n")
    });
    app.quit();
    return;
  }

  const iconPath = join(app.getAppPath(), "app", "assets", "icon.png");
  mainWindow = new BrowserWindow({
    ...(existsSync(iconPath) ? { icon: iconPath } : {}),
    backgroundColor: initialBackground,
    ...(process.platform === "win32" ? { titleBarStyle: "hidden", titleBarOverlay: titleBarOverlay() } : {}),
    width: 1366,
    height: 768,
    minWidth: 1100,
    minHeight: 650,
    webPreferences: {
      preload: join(__dirname, "../preload/preload.cjs"),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  });

  mainWindow.webContents.setWindowOpenHandler(() => ({ action: "deny" }));
  mainWindow.webContents.on("will-navigate", (event, url) => {
    const rendererFile = pathToFileURL(join(__dirname, "../renderer/index.html")).href;
    let allowed = url === rendererFile;\n    if (backendManager?.lanStatus?.url) {\n      try { allowed = allowed || new URL(url).origin === new URL(backendManager.lanStatus.url).origin; }\n      catch { allowed = false; }\n    }
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
