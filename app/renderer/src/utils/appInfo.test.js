import { describe, expect, it } from "vitest";
import { appVersionLabel } from "./appInfo.js";

describe("appVersionLabel", () => {
  it("shows the packaged application version and actual architecture", () => {
    expect(appVersionLabel({ version: "1.0.3", architecture: "x64", isPackaged: true }))
      .toBe("POMAS v1.0.3 · x64");
  });

  it("marks an unpackaged development build", () => {
    expect(appVersionLabel({ version: "1.0.4-beta.1", architecture: "x64", isPackaged: false }))
      .toBe("POMAS v1.0.4-beta.1 · x64 · DEVELOPMENT");
  });

  it("does not invent a release version before Electron metadata loads", () => {
    expect(appVersionLabel({ version: null, architecture: null, isPackaged: true }))
      .toBe("POMAS · VERSION LOADING");
  });
});
