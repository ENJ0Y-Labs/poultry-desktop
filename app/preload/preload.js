const { contextBridge, ipcRenderer } = require("electron");

const channels = Object.freeze({
  RESTORE_BACKUP: "backup:restore",
  CHOOSE_BACKUP_DIRECTORY: "backup:choose-directory",
  SET_THEME: "theme:set",
  GET_BACKEND_BASE_URL: "backend:base-url",
  GET_APP_INFO: "app:info",
  QUIT_APP: "app:quit",
});

contextBridge.exposeInMainWorld("poultryDesktop", Object.freeze({
  supportsCustomTitleBar: process.platform === "win32",
  quit: () => ipcRenderer.invoke(channels.QUIT_APP),
  restoreBackup: () => ipcRenderer.invoke(channels.RESTORE_BACKUP),
  chooseBackupDirectory: () => ipcRenderer.invoke(channels.CHOOSE_BACKUP_DIRECTORY),
  setTheme: (theme) => ipcRenderer.invoke(channels.SET_THEME, theme),
  getApiBaseUrl: () => ipcRenderer.invoke(channels.GET_BACKEND_BASE_URL),
  getAppInfo: () => ipcRenderer.invoke(channels.GET_APP_INFO),
  listDrafts: () => ipcRenderer.invoke(channels.LIST_DRAFTS),
  saveDraft: (draft) => ipcRenderer.invoke(channels.SAVE_DRAFT, draft),
  discardDraft: (id) => ipcRenderer.invoke(channels.DISCARD_DRAFT, id),
}));
