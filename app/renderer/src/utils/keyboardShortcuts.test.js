import { describe, expect, it, vi, afterEach } from "vitest";
import { isTypingTarget, NAVIGATION_SHORTCUTS, registerGlobalShortcuts } from "./keyboardShortcuts.js";

describe("central keyboard shortcuts", () => {
  afterEach(() => { window.dispatchEvent(new KeyboardEvent("keyup")); });
  it("maps the ten navigation shortcuts without using Alt combinations", () => {
    expect(NAVIGATION_SHORTCUTS["1"]).toBe("dashboard");
    expect(NAVIGATION_SHORTCUTS["9"]).toBe("import-export");
    expect(NAVIGATION_SHORTCUTS["0"]).toBe("settings");
  });
  it("ignores editable controls and contenteditable regions", () => {
    const input = document.createElement("input");
    const editor = document.createElement("div");
    editor.setAttribute("contenteditable", "true");
    const plainTextEditor = document.createElement("div");
    plainTextEditor.setAttribute("contenteditable", "plaintext-only");
    const lockedEditor = document.createElement("div");
    lockedEditor.setAttribute("contenteditable", "false");
    const nestedLockedEditor = document.createElement("span");
    editor.append(nestedLockedEditor);
    nestedLockedEditor.setAttribute("contenteditable", "false");

    expect(isTypingTarget(input)).toBe(true);
    expect(isTypingTarget(editor)).toBe(true);
    expect(isTypingTarget(plainTextEditor)).toBe(true);
    expect(isTypingTarget(lockedEditor)).toBe(false);
    expect(isTypingTarget(nestedLockedEditor)).toBe(false);
    expect(isTypingTarget(document.body)).toBe(false);
  });
  it("routes navigation and dispatches quick actions from one listener", () => {
    const navigate = vi.fn(), openQuickActions = vi.fn(), focusPageSearch = vi.fn(() => true);
    const cleanup = registerGlobalShortcuts({ navigate, openQuickActions, focusPageSearch });
    const nav = new KeyboardEvent("keydown", { key: "2", ctrlKey: true, bubbles: true, cancelable: true });
    window.dispatchEvent(nav);
    expect(nav.defaultPrevented).toBe(true);
    expect(navigate).toHaveBeenCalledWith("flocks");
    const quick = new KeyboardEvent("keydown", { key: "k", ctrlKey: true, bubbles: true, cancelable: true });
    window.dispatchEvent(quick);
    expect(quick.defaultPrevented).toBe(true);
    expect(openQuickActions).toHaveBeenCalledTimes(1);
    cleanup();
  });
  it("does not consume Ctrl+F when the page has no search target", () => {
    const cleanup = registerGlobalShortcuts({ navigate: vi.fn(), focusPageSearch: () => false });
    const event = new KeyboardEvent("keydown", { key: "f", ctrlKey: true, bubbles: true, cancelable: true });
    window.dispatchEvent(event);
    expect(event.defaultPrevented).toBe(false);
    cleanup();
  });
  it("does not override native Ctrl+N or Ctrl+S", () => {
    const cleanup = registerGlobalShortcuts({ navigate: vi.fn(), openQuickActions: vi.fn() });
    for (const key of ["n", "s"]) {
      const event = new KeyboardEvent("keydown", { key, ctrlKey: true, bubbles: true, cancelable: true });
      window.dispatchEvent(event);
      expect(event.defaultPrevented).toBe(false);
    }
    cleanup();
  });
});
