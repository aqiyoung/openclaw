import {
  defineChannelSetupContract,
  type ChannelSetupAdapter,
  type ChannelSetupInput,
} from "openclaw/plugin-sdk/channel-setup";
import type { OpenClawConfig } from "openclaw/plugin-sdk/config-contracts";
import { DEFAULT_ACCOUNT_ID } from "openclaw/plugin-sdk/setup-runtime";
import { decodeBuzzPrivateKey } from "./types.js";

type BuzzSetupInput = ChannelSetupInput & {
  relayUrl?: string;
  privateKey?: string;
};

function validRelayUrl(value: string | undefined): boolean {
  try {
    const url = new URL(value ?? "");
    return url.protocol === "ws:" || url.protocol === "wss:";
  } catch {
    return false;
  }
}

export const buzzSetupAdapter: ChannelSetupAdapter<BuzzSetupInput> = {
  resolveAccountId: () => DEFAULT_ACCOUNT_ID,
  validateInput: ({ accountId, input }) => {
    if (accountId !== DEFAULT_ACCOUNT_ID) {
      return "Buzz currently supports only the default account.";
    }
    if (!validRelayUrl(input.relayUrl)) {
      return "Buzz requires --relay-url with a ws:// or wss:// URL.";
    }
    if (input.useEnv) {
      return null;
    }
    if (!input.privateKey?.trim()) {
      return "Buzz requires --private-key or --use-env.";
    }
    try {
      decodeBuzzPrivateKey(input.privateKey);
      return null;
    } catch (error) {
      return error instanceof Error ? error.message : "Invalid Buzz private key.";
    }
  },
  applyAccountConfig: ({ cfg, input }) => {
    const { privateKey: _privateKey, ...existing } = cfg.channels?.buzz ?? {};
    return {
      ...cfg,
      channels: {
        ...cfg.channels,
        buzz: {
          ...existing,
          enabled: true,
          relayUrl: input.relayUrl?.trim(),
          ...(input.useEnv ? {} : { privateKey: input.privateKey?.trim() }),
        },
      },
    } as OpenClawConfig;
  },
};

export const buzzSetupContract = defineChannelSetupContract({
  fields: {
    relayUrl: {
      kind: "string",
      cli: { flags: "--relay-url <url>", description: "Buzz relay WebSocket URL" },
    },
    privateKey: {
      kind: "string",
      sensitive: true,
      cli: { flags: "--private-key <key>", description: "Buzz bot Nostr private key" },
    },
    useEnv: {
      kind: "boolean",
      cli: {
        flags: "--use-env",
        description: "Use BUZZ_PRIVATE_KEY with the supplied relay URL",
      },
    },
  },
  adapter: buzzSetupAdapter,
});
