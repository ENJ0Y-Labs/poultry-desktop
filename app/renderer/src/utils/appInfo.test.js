import { describe, expect, it } from "vitest";
import { appVersionLabel } from "./appInfo.js";

describe("appVersionLabel", () => {
  it("shows the packaged application version without its runtime architecture", () => {
    expect(appVersionLabel({ version: "1.0.4", architecture: "x64", isPackaged: true }))
      .toBe("POMAS v1.0.4");
  });

  it("does not expose architecture in development version labels", () => {
    expect(appVersionLabel({ version: "1.0.4-beta.1", architecture: "x64", isPackaged: false }))
      .toBe("POMAS v1.0.4-beta.1 · DEVELOPMENT");
  });

  it("does not invent a release version before Electron metadata loads", () => {
    expect(appVersionLabel({ version: null, architecture: "x64", isPackaged: true }))
      .toBe("POMAS · VERSION LOADING");
  });

  it("keeps the development loading state independent of architecture", () => {
    expect(appVersionLabel({ version: null, architecture: "arm64", isPackaged: false }))
      .toBe("POMAS · DEVELOPMENT BUILD");
  });
});
