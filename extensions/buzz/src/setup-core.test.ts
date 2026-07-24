import type { OpenClawConfig } from "openclaw/plugin-sdk/config-contracts";
import { afterEach, describe, expect, it, vi } from "vitest";
import { buzzSetupAdapter } from "./setup-core.js";

describe("buzzSetupAdapter", () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it("removes a stored private key when switching to BUZZ_PRIVATE_KEY", () => {
    vi.stubEnv("BUZZ_PRIVATE_KEY", "22".repeat(32));
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

  it("rejects --use-env when BUZZ_PRIVATE_KEY is unset", () => {
    vi.stubEnv("BUZZ_PRIVATE_KEY", "");

    expect(
      buzzSetupAdapter.validateInput({
        cfg: {} as OpenClawConfig,
        accountId: "default",
        input: { relayUrl: "wss://buzz.example.com", useEnv: true },
      }),
    ).toBe("BUZZ_PRIVATE_KEY is not set.");
  });

  it("clears an identity-bound auth tag when changing the private key", () => {
    const cfg = {
      channels: {
        buzz: {
          relayUrl: "wss://buzz.example.com",
          privateKey: "11".repeat(32),
          authTag: '["auth","owner","kind=9","signature"]',
        },
      },
    } as OpenClawConfig;

    const result = buzzSetupAdapter.applyAccountConfig({
      cfg,
      accountId: "default",
      input: { relayUrl: "wss://buzz.example.com", privateKey: "22".repeat(32) },
    });

    expect(result.channels?.buzz?.privateKey).toBe("22".repeat(32));
    expect(result.channels?.buzz?.authTag).toBeUndefined();
  });
});
