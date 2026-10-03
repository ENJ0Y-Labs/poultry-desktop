import { contextBridge, ipcRenderer } from "electron";

contextBridge.exposeInMainWorld("poultryDesktop", {
  restoreBackup: () => ipcRenderer.invoke("backup:restore"),
  chooseBackupDirectory: () => ipcRenderer.invoke("backup:choose-directory"),
});
