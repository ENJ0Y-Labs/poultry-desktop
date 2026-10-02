import { app, BrowserWindow, dialog } from "electron";
import { join } from "node:path";
import { BackendManager } from "./backend-manager.js";
let mainWindow;
let backendManager;
async function createWindow(){
  backendManager=new BackendManager({userDataPath:app.getPath("userData"),isPackaged:app.isPackaged});
  try{await backendManager.start();}catch(error){
    await dialog.showMessageBox({type:"error",title:"Backend startup failed",message:"Poultry Farm Manager could not start its local backend.",detail:error instanceof Error?error.message:String(error)});
    app.quit(); return;
  }
  mainWindow=new BrowserWindow({width:1366,height:768,minWidth:1100,minHeight:650,webPreferences:{preload:join(__dirname,"../preload/preload.js"),contextIsolation:true,nodeIntegration:false,sandbox:true}});
  if(process.env.ELECTRON_RENDERER_URL) await mainWindow.loadURL(process.env.ELECTRON_RENDERER_URL);
  else await mainWindow.loadFile(join(__dirname,"../renderer/index.html"));
}
app.whenReady().then(createWindow);
app.on("window-all-closed",()=>{if(process.platform!=="darwin")app.quit();});
app.on("before-quit",async()=>{if(backendManager)await backendManager.stop();});