import { existsSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";

const releaseDir = join(process.cwd(), "release");

if (!existsSync(releaseDir)) {
  console.error("Installer verification failed: release directory is missing.");
  process.exit(1);
}

const expected = `PoultrySetup.exe`;
const installers = readdirSync(releaseDir).filter(file => /^PoultrySetup\.exe$/i.test(file));

if (!installers.includes(expected)) {
  console.error("Installer verification failed:");
  console.error(`- expected: release/${expected}`);
  console.error(`- found: ${installers.length ? installers.join(", ") : "PoultrySetup.exe not found"}`);
  process.exit(1);
}

const installerPath = join(releaseDir, expected);
const size = statSync(installerPath).size;
if (size < 1024 * 1024) {
  console.error(`Installer verification failed: ${expected} is unexpectedly small (${size} bytes).`);
  process.exit(1);
}

console.log(`Installer verified: release/${expected} (${size} bytes)`);
