import browserConfig from "./vite.browser.config.js";

export default {
  ...browserConfig,
  plugins: [
    ...browserConfig.plugins,
    {
      name: "pomas-ui-audit-loopback-csp",
      transformIndexHtml: {
        order: "post",
        handler(html) {
          return html
            .replace("http://127.0.0.1:18942", "http://127.0.0.1:18950")
            .replace("ws://127.0.0.1:5173", "ws://127.0.0.1:5175");
        },
      },
    },
  ],
  server: {
    ...browserConfig.server,
    port: 5175,
    strictPort: true,
  },
};
