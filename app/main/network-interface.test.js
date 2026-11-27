import { describe, expect, it } from "vitest";
import { discoverPrivateIPv4Interfaces, validatePrivateIPv4Interface } from "./network-interface.js";

const adapter = (address, netmask = "255.255.255.0", family = "IPv4", internal = false) => ({
  address, netmask, family, internal, mac: "00:11:22:33:44:55"
});

describe("private IPv4 interface discovery", () => {
  it("returns RFC1918 unicast addresses on configured external interfaces", () => {
    const result = discoverPrivateIPv4Interfaces({
      Ethernet: [adapter("192.168.1.20")],
      WiFi: [adapter("10.0.0.8", "255.0.0.0")],
      Loopback: [adapter("127.0.0.1", "255.0.0.0", "IPv4", true)]
    });
    expect(result.map(({ interfaceName, address }) => [interfaceName, address])).toEqual([
      ["Ethernet", "192.168.1.20"], ["WiFi", "10.0.0.8"]
    ]);
  });

  it.each([
    ["public IPv4", "8.8.8.8"], ["loopback", "127.0.0.1"],
    ["link-local", "169.254.10.2"], ["unspecified", "0.0.0.0"],
    ["multicast", "224.0.0.1"], ["carrier-grade NAT", "100.64.0.1"],
    ["broadcast", "255.255.255.255"], ["non-canonical octets", "010.0.0.1"]
  ])("rejects %s addresses", (_label, address) => {
    expect(discoverPrivateIPv4Interfaces({ Ethernet: [adapter(address)] })).toEqual([]);
  });

  it("rejects subnet network/broadcast addresses and invalid masks", () => {
    expect(discoverPrivateIPv4Interfaces({
      Ethernet: [
        adapter("192.168.1.0"), adapter("192.168.1.255"),
        adapter("192.168.1.20", "255.0.255.0"),
        adapter("192.168.1.20", "255.255.255.255")
      ]
    })).toEqual([]);
  });

  it("supports all three RFC1918 ranges", () => {
    expect(discoverPrivateIPv4Interfaces({
      a: [adapter("10.2.3.4", "255.0.0.0")],
      b: [adapter("172.20.3.4", "255.255.0.0")],
      c: [adapter("192.168.3.4")]
    })).toHaveLength(3);
  });

  it("revalidates the exact interface and address before use", () => {
    const interfaces = { WiFi: [adapter("192.168.1.20")] };
    expect(validatePrivateIPv4Interface({ interfaceName: "WiFi", address: "192.168.1.20" }, interfaces))
      .toMatchObject({ interfaceName: "WiFi", address: "192.168.1.20" });
    expect(() => validatePrivateIPv4Interface({ interfaceName: "Ethernet", address: "192.168.1.20" }, interfaces))
      .toThrow(/no longer available/i);
    expect(() => validatePrivateIPv4Interface({ interfaceName: "WiFi", address: "192.168.1.21" }, interfaces))
      .toThrow(/no longer available/i);
  });

  it("rejects malformed or incomplete selections", () => {
    expect(() => validatePrivateIPv4Interface(null, {})).toThrow(/select an available/i);
    expect(() => validatePrivateIPv4Interface({ address: "192.168.1.2" }, {}))
      .toThrow(/select an available/i);
  });
});
