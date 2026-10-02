import { resolve } from "node:path";
import { defineConfig, externalizeDepsPlugin } from "electron-vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  main: {
    build: { rollupOptions: { input: resolve("app/main/main.js") } },
    plugins: [externalizeDepsPlugin()]
  },
  preload: {
    build: { rollupOptions: { input: resolve("app/preload/preload.js") } },
    plugins: [externalizeDepsPlugin()]
  },
  renderer: {
    root: resolve("app/renderer"),
    resolve: { alias: { "@": resolve("app/renderer/src") } },
    plugins: [react()]
  }
});