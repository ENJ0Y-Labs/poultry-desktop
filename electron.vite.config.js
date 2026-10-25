import { resolve } from "node:path";
import { defineConfig, externalizeDepsPlugin } from "electron-vite";
import react from "@vitejs/plugin-react";

const rendererDevelopmentCsp = {
  name: "pomas-electron-development-csp",
  apply: "serve",
  transformIndexHtml(html) {
    // Vite injects development CSS and opens an HMR websocket. These sources
    // exist only while serving the renderer and must not enter the packaged HTML.
    return html.replace(
      /<meta http-equiv="Content-Security-Policy" content="[^"]*"\s*\/?>/i,
      `<meta http-equiv="Content-Security-Policy" content="default-src 'self'; style-src 'self' 'unsafe-inline'; script-src 'self'; connect-src 'self' http://127.0.0.1:18942 ws://127.0.0.1:5173 ws://localhost:5173;"/>`
    );
  }
};

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
      rollupOptions: {
        input: resolve("app/preload/preload.js"),
        output: {
          format: "cjs",
          entryFileNames: "preload.cjs"
        }
      }
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
    plugins: [react(), rendererDevelopmentCsp]
  }
});
