import js from "@eslint/js";
import globals from "globals";

export default [
  { ignores: ["dist/**", "release/**", "out/**", "node_modules/**", "backend/target/**", "runtime/**"] },
  js.configs.recommended,
  {
    files: ["**/*.js", "**/*.jsx", "**/*.mjs"],
    languageOptions: {
      globals: { ...globals.browser, ...globals.node },
      parserOptions: {
        ecmaVersion: "latest",
        sourceType: "module",
        ecmaFeatures: { jsx: true }
      }
    },
    rules: {
      "no-unused-vars": ["error", { varsIgnorePattern: "^(App|React)$" }]
    }
  }
];
