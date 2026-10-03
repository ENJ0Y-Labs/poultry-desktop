import { contextBridge, ipcRenderer } from "electron";

const channels = Object.freeze({
  RESTORE_BACKUP: "backup:restore",
  CHOOSE_BACKUP_DIRECTORY: "backup:choose-directory",
});

contextBridge.exposeInMainWorld("poultryDesktop", Object.freeze({
  restoreBackup: () => ipcRenderer.invoke(channels.RESTORE_BACKUP),
  chooseBackupDirectory: () => ipcRenderer.invoke(channels.CHOOSE_BACKUP_DIRECTORY),
}));
