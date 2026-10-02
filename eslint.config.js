import js from "@eslint/js";
import globals from "globals";
export default [
  { ignores: ["dist/**","release/**","node_modules/**","backend/target/**"] },
  js.configs.recommended,
  { files: ["**/*.js","**/*.jsx"], languageOptions: { globals: { ...globals.browser, ...globals.node } } }
];