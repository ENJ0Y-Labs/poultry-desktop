import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App.jsx";
import { initializeTheme } from "./utils/theme.js";
import "./styles.css";

// Apply the saved/system theme before React mounts to avoid a first-render flash.
initializeTheme();
ReactDOM.createRoot(document.getElementById("root")).render(<React.StrictMode><App/></React.StrictMode>);
