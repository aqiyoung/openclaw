import { describe, expect, it, vi } from "vitest";
import { createOpenClawTestState } from "../test-utils/openclaw-test-state.js";
import { listCoreGatewayMethodNames } from "./methods/core-method-policy.js";
import { createGatewayAuxHandlers } from "./server-aux-handlers.js";
import { coreGatewayHandlers } from "./server-methods/core-handlers.js";
import type { GatewayRequestHandlers } from "./server-methods/types.js";

/**
 * Core descriptors whose handler family is not ported into this fork yet.
 * `CORE_GATEWAY_HANDLER_MODULES` in ./server-methods/core-handlers.ts has no
 * loader for those families, so their dispatch stays deliberately unregistered.
 * Shrink this list as the families land.
 */
const UNPORTED_CORE_GATEWAY_METHODS = [
  "claws.packages.remove",
  "computer.invoke",
  "computer.status",
  "models.authLogin",
  "sessions.activitySummary.ensure",
];

describe("core and auxiliary method handler parity", () => {
  it("wires a dispatchable core or auxiliary handler for every core descriptor", async () => {
    const fixture = await createOpenClawTestState({ label: "gateway-aux-methods" });
    const aux = createGatewayAuxHandlers({
      log: {},
      activateRuntimeSecrets: async () => {
        throw new Error("unexpected secrets reload");
      },
      sharedGatewaySessionGenerationState: { current: undefined, required: null },
      resolveSharedGatewaySessionGenerationForConfig: () => undefined,
      clients: [],
      channelManager: {
        startChannel: async () => new Map(),
        stopChannel: async () => {},
        isManuallyStopped: () => false,
        resolveRuntimeAccountId: (_channel: string, accountId: string) => accountId,
      },
      logChannels: { info: vi.fn() },
    });
    try {
      // Assert against the real construction maps rather than an auxiliary
      // exemption list: assistant media is served by the separate Control UI
      // handler, and unported handler families are never registered at all.
      const handlers: GatewayRequestHandlers = { ...coreGatewayHandlers, ...aux.extraHandlers };
      const missing = listCoreGatewayMethodNames()
        .filter((method) => method !== "assistant.media.get")
        .filter((method) => typeof handlers[method] !== "function")
        .toSorted();
      expect(missing).toEqual(UNPORTED_CORE_GATEWAY_METHODS.toSorted());
    } finally {
      await aux.stopOperatorInteractions();
      await fixture.cleanup();
    }
  });
});
