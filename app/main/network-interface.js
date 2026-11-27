import { networkInterfaces } from "node:os";

function parseIPv4(value) {
  if (typeof value !== "string") return null;
  const parts = value.split(".");
  if (parts.length !== 4 || parts.some(part => !/^\d{1,3}$/.test(part))) return null;
  const octets = parts.map(Number);
  if (octets.some(value => value < 0 || value > 255)) return null;
  if (parts.some((part, index) => String(octets[index]) !== part)) return null;
  return octets;
}

function toInteger(octets) {
  return (((octets[0] << 24) >>> 0) + (octets[1] << 16) + (octets[2] << 8) + octets[3]) >>> 0;
}

function prefixLength(netmask) {
  const octets = parseIPv4(netmask);
  if (!octets) return null;
  const bits = octets.map(value => value.toString(2).padStart(8, "0")).join("");
  if (!/^1*0*$/.test(bits)) return null;
  return bits.indexOf("0") === -1 ? 32 : bits.indexOf("0");
}

function isPrivateUnicast(octets) {
  const [a, b] = octets;
  return a === 10 || (a === 172 && b >= 16 && b <= 31) || (a === 192 && b === 168);
}

function isUsableHostAddress(address, netmask) {
  const ip = parseIPv4(address);
  const mask = parseIPv4(netmask);
  const prefix = prefixLength(netmask);
  if (!ip || !mask || !isPrivateUnicast(ip) || prefix === null || prefix < 8 || prefix > 30) return false;
  const ipNumber = toInteger(ip);
  const maskNumber = toInteger(mask);
  const network = (ipNumber & maskNumber) >>> 0;
  const broadcast = (network | (~maskNumber >>> 0)) >>> 0;
  return ipNumber !== network && ipNumber !== broadcast;
}

/**
 * Returns configured RFC1918 IPv4 host addresses with a usable subnet.
 * Node does not expose adapter operational state consistently on all supported
 * platforms, so callers must still attempt the exact bind and fail closed.
 */
export function discoverPrivateIPv4Interfaces(interfaces = networkInterfaces()) {
  const discovered = [];
  for (const [interfaceName, addresses] of Object.entries(interfaces || {})) {
    if (!Array.isArray(addresses)) continue;
    for (const entry of addresses) {
      const family = entry?.family;
      if (entry?.internal === true || !(family === "IPv4" || family === 4)) continue;
      if (!isUsableHostAddress(entry.address, entry.netmask)) continue;
      discovered.push(Object.freeze({
        interfaceName,
        address: entry.address,
        netmask: entry.netmask,
        mac: typeof entry.mac === "string" ? entry.mac : ""
      }));
    }
  }
  return discovered.sort((left, right) =>
    left.interfaceName.localeCompare(right.interfaceName) || left.address.localeCompare(right.address)
  );
}

/** Revalidates the exact interface/address pair against the current OS snapshot. */
export function validatePrivateIPv4Interface(selection, interfaces = networkInterfaces()) {
  if (!selection || typeof selection !== "object"
      || typeof selection.interfaceName !== "string" || !selection.interfaceName
      || typeof selection.address !== "string") {
    throw new Error("Select an available private IPv4 network interface.");
  }
  const match = discoverPrivateIPv4Interfaces(interfaces).find(entry =>
    entry.interfaceName === selection.interfaceName && entry.address === selection.address
  );
  if (!match) {
    throw new Error("The selected private IPv4 address is no longer available. Choose an active network interface and try again.");
  }
  return match;
}
