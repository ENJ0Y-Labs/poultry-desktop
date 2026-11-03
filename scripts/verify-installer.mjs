import { existsSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";
import packageJson from "../package.json" with { type: "json" };

const releaseDir = join(process.cwd(), "release");
const unpackedDir = join(releaseDir, "win-unpacked");
const errors = [];

function requireFile(path, description) {
  if (!existsSync(path)) errors.push(`- ${description} is missing: ${path}`);
}

function requireSetting(condition, description) {
  if (!condition) errors.push(`- ${description}`);
}

if (!existsSync(releaseDir)) {
  errors.push("- release directory is missing.");
} else {
  const installerPath = join(releaseDir, "PomasSetup.exe");
  const installers = readdirSync(releaseDir).filter(file => /^PomasSetup\.exe$/i.test(file));

  requireSetting(installers.includes("PomasSetup.exe"), "expected release/PomasSetup.exe.");
  if (existsSync(installerPath)) {
    const size = statSync(installerPath).size;
    requireSetting(size >= 1024 * 1024, `installer is unexpectedly small (${size} bytes).`);
  }
}

const nsis = packageJson.build?.nsis ?? {};
requireSetting(packageJson.version === "1.0.2", "package version must be 1.0.2.");
requireSetting(nsis.oneClick === false, "NSIS must use the multi-page wizard (oneClick: false).");
requireSetting(nsis.allowToChangeInstallationDirectory === true, "users must be able to choose the installation directory.");
requireSetting(nsis.createDesktopShortcut === true, "desktop shortcut creation must be enabled.");
requireSetting(nsis.createStartMenuShortcut === true, "Start Menu shortcut creation must be enabled.");
requireSetting(nsis.runAfterFinish === true, "launch-after-install must be enabled.");
requireSetting(nsis.deleteAppDataOnUninstall === false, "uninstall must preserve application data.");

const resources = packageJson.build?.extraResources ?? [];
requireSetting(
  resources.some(resource => resource.from === "runtime" && resource.to === "runtime"),
  "the bundled Java runtime must be included as an extra resource."
);
requireSetting(
  resources.some(resource => resource.from === "backend/target/poultry-backend.jar" && resource.to === "backend/poultry-backend.jar"),
  "the packaged Spring Boot backend JAR must be included as an extra resource."
);

if (existsSync(unpackedDir)) {
  requireFile(join(unpackedDir, "resources", "runtime", "bin", "java.exe"), "bundled Java runtime");
  requireFile(join(unpackedDir, "resources", "backend", "poultry-backend.jar"), "Spring Boot backend JAR");
} else {
  errors.push("- release/win-unpacked is missing; run the Windows packaging command before installer verification.");
}

if (errors.length) {
  console.error("Pomas installer verification failed:");
  for (const error of errors) console.error(error);
  process.exit(1);
}

console.log(`Pomas ${packageJson.version} installer verified: release/PomasSetup.exe`);
console.log("Verified NSIS wizard, install-directory selection, shortcuts, launch-after-finish, data preservation settings, bundled Java, and backend JAR.");
