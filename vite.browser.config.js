import { resolve } from "node:path";
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

const browserDevelopmentCsp = {
  name: "pomas-browser-development-csp",
  transformIndexHtml(html) {
    // The desktop CSP is intentionally strict. Browser development additionally
    // needs Vite's HMR websocket and injected development CSS.
    return html.replace(
      /<meta http-equiv="Content-Security-Policy" content="[^"]*"\s*\/?>/i,
      `<meta http-equiv="Content-Security-Policy" content="default-src 'self'; style-src 'self' 'unsafe-inline'; script-src 'self'; connect-src 'self' http://127.0.0.1:18942 ws://127.0.0.1:5173;"/>`
    );
  },
};

export default defineConfig({
  root: resolve("app/renderer"),
  plugins: [react(), browserDevelopmentCsp],
  resolve: {
    alias: {
      "@": resolve("app/renderer/src"),
    },
  },
  server: {
    host: "127.0.0.1",
    port: 5173,
    strictPort: true,
  },
});
