import { contextBridge } from "electron";
contextBridge.exposeInMainWorld("poultry",{version:"0.1.0"});