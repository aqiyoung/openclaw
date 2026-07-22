import {
  Relay,
  finalizeEvent,
  type Event,
  type EventTemplate,
  type VerifiedEvent,
} from "nostr-tools";
import { createChannelReplayGuard } from "openclaw/plugin-sdk/persistent-dedupe";
import { decodeBuzzPrivateKey, resolveBuzzPublicKey } from "./types.js";

const MESSAGE_KIND = 9;
const AUTH_CHALLENGE_TIMEOUT_MS = 20_000;
const AUTH_CHALLENGE_POLL_MS = 25;
const REPLAY_TTL_MS = 30 * 24 * 60 * 60 * 1000;
const REPLAY_MAX_ENTRIES = 10_000;
const REPLAY_STATE_MAX_ENTRIES = 50_000;
const REPLAY_NAMESPACE_PREFIX = "buzz.inbound-dedupe";

export interface BuzzInboundMessage {
  id: string;
  senderPubkey: string;
  text: string;
  channelId: string;
  createdAt: number;
  threadId?: string;
  replyToId?: string;
  mentionedPubkeys: string[];
}

export interface BuzzBus {
  publicKey: string;
  sendText: (params: {
    channelId: string;
    text: string;
    threadId?: string;
    replyToId?: string;
  }) => Promise<string>;
  close: () => Promise<void>;
}

function tagValue(event: Event, name: string): string | undefined {
  return event.tags.find((tag) => tag[0] === name)?.[1];
}

function markerTagValue(event: Event, marker: string): string | undefined {
  return event.tags.find((tag) => tag[0] === "e" && tag[3] === marker)?.[1];
}

export function parseBuzzMessageEvent(event: Event): BuzzInboundMessage | null {
  if (event.kind !== MESSAGE_KIND || !event.content.trim()) {
    return null;
  }
  const channelId = tagValue(event, "h");
  if (!channelId) {
    return null;
  }
  const rootId = markerTagValue(event, "root");
  const replyToId = markerTagValue(event, "reply");
  return {
    id: event.id,
    senderPubkey: event.pubkey,
    text: event.content,
    channelId,
    createdAt: event.created_at,
    threadId: rootId ?? replyToId,
    replyToId,
    mentionedPubkeys: event.tags
      .filter((tag) => tag[0] === "p" && Boolean(tag[1]))
      .map((tag) => tag[1] as string),
  };
}

export function parseBuzzAuthTag(raw: string): string[] | undefined {
  if (!raw.trim()) {
    return undefined;
  }
  const parsed: unknown = JSON.parse(raw);
  if (
    !Array.isArray(parsed) ||
    parsed.length !== 4 ||
    parsed[0] !== "auth" ||
    parsed.some((value) => typeof value !== "string")
  ) {
    throw new Error('Buzz authTag must be ["auth","<pubkey>","<conditions>","<signature>"]');
  }
  return parsed;
}

export function buildBuzzMessageTags(params: {
  channelId: string;
  threadId?: string;
  replyToId?: string;
}): string[][] {
  const tags: string[][] = [["h", params.channelId]];
  const parentId = params.replyToId ?? params.threadId;
  if (params.threadId && parentId !== params.threadId) {
    tags.push(["e", params.threadId, "", "root"]);
  }
  if (parentId) {
    tags.push(["e", parentId, "", "reply"]);
  }
  return tags;
}

async function authenticateBuzzRelay(params: {
  relay: Relay;
  signAuth: (template: EventTemplate) => Promise<VerifiedEvent>;
  signal?: AbortSignal;
}): Promise<void> {
  const deadline = Date.now() + AUTH_CHALLENGE_TIMEOUT_MS;
  while (true) {
    params.signal?.throwIfAborted();
    try {
      await params.relay.auth(params.signAuth);
      return;
    } catch (error) {
      const awaitingChallenge =
        error instanceof Error && error.message === "can't perform auth, no challenge was received";
      if (!awaitingChallenge) {
        throw error;
      }
      if (Date.now() >= deadline) {
        throw new Error("Timed out waiting for Buzz NIP-42 authentication challenge", {
          cause: error,
        });
      }
      await new Promise<void>((resolve) => {
        setTimeout(resolve, AUTH_CHALLENGE_POLL_MS);
      });
    }
  }
}

export async function startBuzzBus(options: {
  accountId: string;
  relayUrl: string;
  privateKey: string;
  authTag?: string;
  channelIds: string[];
  since?: number;
  onMessage: (message: BuzzInboundMessage, bus: BuzzBus) => Promise<void>;
  onMessageError?: (error: Error) => void;
  onFatalError?: (error: Error) => void;
  onDedupeError?: (error: Error) => void;
  signal?: AbortSignal;
}): Promise<BuzzBus> {
  const secretKey = decodeBuzzPrivateKey(options.privateKey);
  const publicKey = resolveBuzzPublicKey(options.privateKey);
  const authTag = parseBuzzAuthTag(options.authTag ?? "");
  const relay = new Relay(options.relayUrl, { enableReconnect: false });
  const sessionStartedAt = Math.floor(Date.now() / 1000);
  const replayGuard = createChannelReplayGuard<Event>({
    dedupe: {
      pluginId: "buzz",
      namespacePrefix: REPLAY_NAMESPACE_PREFIX,
      ttlMs: REPLAY_TTL_MS,
      memoryMaxSize: REPLAY_MAX_ENTRIES,
      stateMaxEntries: REPLAY_STATE_MAX_ENTRIES,
      onDiskError: (error) => {
        options.onDedupeError?.(error instanceof Error ? error : new Error(String(error)));
      },
    },
    buildReplayKey: (event) => event.id,
    namespace: () => options.accountId,
  });
  const signAuth = async (template: EventTemplate) =>
    finalizeEvent(
      {
        ...template,
        tags: authTag ? [...template.tags, authTag] : template.tags,
      },
      secretKey,
    );
  let subscriptions: Array<ReturnType<Relay["subscribe"]>> = [];
  const bus: BuzzBus = {
    publicKey,
    sendText: async ({ channelId, text, threadId, replyToId }) => {
      const event = finalizeEvent(
        {
          kind: MESSAGE_KIND,
          content: text,
          created_at: Math.floor(Date.now() / 1000),
          tags: buildBuzzMessageTags({ channelId, threadId, replyToId }),
        },
        secretKey,
      );
      await relay.publish(event);
      return event.id;
    },
    close: async () => {
      for (const subscription of subscriptions) {
        subscription.close("shutdown");
      }
      replayGuard.clearMemory();
      relay.close();
    },
  };

  try {
    await relay.connect({ abort: options.signal });
    // Buzz rejects relay operations until its proactive NIP-42 challenge is signed.
    // Do not subscribe or publish before this account-level authentication completes.
    await authenticateBuzzRelay({ relay, signAuth, signal: options.signal });
    relay.onauth = signAuth;

    subscriptions = options.channelIds.map((channelId) =>
      relay.subscribe(
        [
          {
            kinds: [MESSAGE_KIND],
            "#h": [channelId],
            since: options.since ?? sessionStartedAt,
          },
        ],
        {
          onevent: (event) => {
            // Relay reconnects can replay signed events. Guard by immutable event id
            // before any authorization, command, or agent work can run twice.
            void replayGuard
              .processGuarded(event, async () => {
                if (event.pubkey === publicKey) {
                  return;
                }
                const message = parseBuzzMessageEvent(event);
                if (!message) {
                  return;
                }
                await options.onMessage(message, bus);
              })
              .catch((error: unknown) => {
                options.onMessageError?.(error instanceof Error ? error : new Error(String(error)));
              });
          },
          onclose: (reason) => {
            if (reason !== "shutdown" && reason !== "relay connection closed by us") {
              options.onFatalError?.(new Error(`Buzz subscription closed: ${reason}`));
            }
          },
        },
      ),
    );

    return bus;
  } catch (error) {
    // Every failed startup must release the socket before ownership returns to
    // the gateway-level reconnect loop.
    relay.close();
    throw error;
  }
}
