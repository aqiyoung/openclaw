import { validateJsonSchemaValue } from "openclaw/plugin-sdk/json-schema-runtime";
import { describe, expect, it } from "vitest";
import { BuzzConfigSchema } from "./config-schema.js";

function expectRelayUrlValidity(relayUrl: string, valid: boolean) {
  const config = { relayUrl, groupPolicy: "allowlist" };
  const jsonSchemaResult = validateJsonSchemaValue({
    cacheKey: "buzz.config-schema.test",
    schema: BuzzConfigSchema.schema,
    value: config,
  });

  expect(BuzzConfigSchema.runtime.safeParse(config).success).toBe(valid);
  expect(jsonSchemaResult.ok).toBe(valid);
}

describe("BuzzConfigSchema", () => {
  it.each([
    "ws://localhost:3000",
    "wss://buzz.example.com/relay",
    "Ws://localhost:3000",
    "WSS://buzz.example.com/relay",
  ])("accepts WebSocket relay URL %s", (relayUrl) => {
    expectRelayUrlValidity(relayUrl, true);
  });

  it.each(["http://localhost:3000", "https://buzz.example.com/relay", "ws://", "ws:// bad"])(
    "rejects non-WebSocket relay URL %s",
    (relayUrl) => {
      expectRelayUrlValidity(relayUrl, false);
    },
  );
});
