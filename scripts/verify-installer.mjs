import { existsSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";
import { readFileSync } from "node:fs";

const projectDir = process.cwd();
const packageJson = JSON.parse(readFileSync(join(projectDir, "package.json"), "utf8"));

const releaseDir = join(projectDir, "release");
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
  const expectedInstaller = `POMAS-v${packageJson.version}-Setup-x64.exe`;
  const installerPath = join(releaseDir, expectedInstaller);
  const installers = readdirSync(releaseDir).filter(file => /^POMAS-v.+-Setup-x64\.exe$/i.test(file));

  requireSetting(installers.includes(expectedInstaller), `expected release/${expectedInstaller}.`);
  if (existsSync(installerPath)) {
    const size = statSync(installerPath).size;
    requireSetting(size >= 1024 * 1024, `installer is unexpectedly small (${size} bytes).`);
  }
}

const nsis = packageJson.build?.nsis ?? {};
requireSetting(/^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?$/.test(packageJson.version), "package version must be a valid release version.");
requireSetting(packageJson.author?.name === "Grantino Farms", "Windows product metadata must use the approved Grantino Farms identity via package author metadata.");
requireSetting(packageJson.build?.artifactName === "POMAS-v${version}-Setup-${arch}.${ext}", "installer artifact name must include product, version, setup type, and architecture.");
const windowsTargets = packageJson.build?.win?.target ?? [];
requireSetting(windowsTargets.some(target => target.target === "nsis" && target.arch?.includes("x64")), "Windows NSIS builds must explicitly target x64.");
requireSetting(nsis.oneClick === false, "NSIS must use the multi-page wizard (oneClick: false).");
requireSetting(nsis.allowToChangeInstallationDirectory === true, "new users must be able to choose the installation directory.");
requireSetting(nsis.include === "build/installer.nsh", "NSIS must load the Pomas maintenance-mode include.");
requireSetting(nsis.createDesktopShortcut === true, "desktop shortcut creation must be enabled.");
requireSetting(nsis.createStartMenuShortcut === true, "Start Menu shortcut creation must be enabled.");
requireSetting(nsis.runAfterFinish === true, "launch-after-install must be enabled.");
requireSetting(nsis.deleteAppDataOnUninstall === false, "uninstall must preserve application data.");

const installerScriptPath = join(projectDir, "build", "installer.nsh");
requireFile(installerScriptPath, "NSIS maintenance-mode script");
if (existsSync(installerScriptPath)) {
  const installerScript = readFileSync(installerScriptPath, "utf8");
  requireSetting(installerScript.includes("!macro customInit"), "NSIS must detect existing per-user/per-machine installations.");
  requireSetting(installerScript.includes("!macro customWelcomePage"), "NSIS must register the maintenance page.");
  requireSetting(installerScript.includes('Page custom PomasMaintenancePage'), "NSIS must show the Pomas maintenance page.");
  requireSetting(installerScript.includes("!ifndef BUILD_UNINSTALLER"), "the installer-only maintenance page must not be compiled into the uninstaller.");
  requireSetting(/!ifndef BUILD_UNINSTALLER\s+Var PomasExistingInstall\s+!endif/.test(installerScript), "the maintenance-state variable must not be compiled into the uninstaller.");
  requireSetting(installerScript.includes('Modify the existing Pomas installation'), "the only maintenance action must be Modify.");
  requireSetting(installerScript.includes("StrCpy $INSTDIR"), "maintenance mode must retain the detected installation directory.");
  requireSetting(installerScript.includes("farm database, settings, and backups"), "the maintenance page must explain that farm data is preserved.");
}

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

console.log(`Pomas ${packageJson.version} x64 installer verified: release/POMAS-v${packageJson.version}-Setup-x64.exe`);
console.log("Verified NSIS first-install/Modify flow, install-directory selection, shortcuts, launch-after-finish, data preservation settings, bundled Java, and backend JAR.");
