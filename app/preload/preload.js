const { contextBridge, ipcRenderer } = require("electron");

const channels = Object.freeze({
  RESTORE_BACKUP: "backup:restore",
  CHOOSE_BACKUP_DIRECTORY: "backup:choose-directory",
  SET_THEME: "theme:set",
  GET_BACKEND_BASE_URL: "backend:base-url",
});

contextBridge.exposeInMainWorld("poultryDesktop", Object.freeze({
  restoreBackup: () => ipcRenderer.invoke(channels.RESTORE_BACKUP),
  chooseBackupDirectory: () => ipcRenderer.invoke(channels.CHOOSE_BACKUP_DIRECTORY),
  setTheme: (theme) => ipcRenderer.invoke(channels.SET_THEME, theme),
  getApiBaseUrl: () => ipcRenderer.invoke(channels.GET_BACKEND_BASE_URL),
}));
