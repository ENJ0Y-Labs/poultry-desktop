import { resolve } from "node:path";
import { defineConfig, externalizeDepsPlugin } from "electron-vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  main: {
    build: {
      outDir: "dist/main",
      rollupOptions: { input: resolve("app/main/main.js") }
    },
    plugins: [externalizeDepsPlugin()]
  },
  preload: {
    build: {
      outDir: "dist/preload",
      rollupOptions: { input: resolve("app/preload/preload.js") }
    },
    plugins: [externalizeDepsPlugin()]
  },
  renderer: {
    root: resolve("app/renderer"),
    build: {
      outDir: resolve("dist/renderer"),
      rollupOptions: { input: resolve("app/renderer/index.html") }
    },
    resolve: { alias: { "@": resolve("app/renderer/src") } },
    plugins: [react()]
  }
});
