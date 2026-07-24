import type { OpenClawConfig } from "openclaw/plugin-sdk/config-contracts";
import { describe, expect, it } from "vitest";
import { buzzSetupAdapter } from "./setup-core.js";

describe("buzzSetupAdapter", () => {
  it("removes a stored private key when switching to BUZZ_PRIVATE_KEY", () => {
    const cfg = {
      channels: {
        buzz: {
          enabled: true,
          relayUrl: "wss://old.example.com",
          privateKey: "11".repeat(32),
        },
      },
    } as OpenClawConfig;

    const result = buzzSetupAdapter.applyAccountConfig({
      cfg,
      accountId: "default",
      input: { relayUrl: "wss://buzz.example.com", useEnv: true },
    });

    expect(result.channels?.buzz).toEqual({
      enabled: true,
      relayUrl: "wss://buzz.example.com",
    });
  });
});
