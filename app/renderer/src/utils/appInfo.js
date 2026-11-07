export function appVersionLabel(info) {
  if (!info?.version) {
    return info?.isPackaged === false ? "POMAS · DEVELOPMENT BUILD" : "POMAS · VERSION LOADING";
  }
  const buildType = info.isPackaged ? "" : " · DEVELOPMENT";
  return `POMAS v${info.version}${buildType}`;
}
