import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App.jsx";
import { ToastProvider } from "./components/ToastProvider.jsx";
import { initializeTheme } from "./utils/theme.js";
import "./styles.css";
import "./accessibility.css";

// index.html runs the external theme bootstrap in <head> before styles and the renderer module.
// Initialize the live system-preference listener and desktop bridge before React mounts.
initializeTheme();
ReactDOM.createRoot(document.getElementById("root")).render(<React.StrictMode><ToastProvider><App /></ToastProvider></React.StrictMode>);
