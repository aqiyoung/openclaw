---
summary: "Buzz group rooms over NIP-29"
read_when:
  - You want OpenClaw in a self-hosted Buzz workspace
  - You are configuring NIP-29 group messaging
title: "Buzz"
---

Buzz is an official channel plugin (`@openclaw/buzz`) for self-hosted NIP-29 group rooms. OpenClaw completes Buzz's required NIP-42 relay authentication, receives `kind:9` messages, and replies in NIP-10 threads.

## Install

```bash
openclaw plugins install @openclaw/buzz
```

Restart the Gateway after installing or enabling the plugin.

## Configure

Create or choose a Buzz channel, generate a Nostr private key for OpenClaw, and configure the relay URL plus the channel UUID:

```json5
{
  channels: {
    buzz: {
      relayUrl: "wss://buzz.example.com",
      privateKey: "${BUZZ_PRIVATE_KEY}",
      groupPolicy: "allowlist",
      groupAllowFrom: ["<64-character-hex-pubkey>"],
      groups: {
        "7c4a6d2a-2ed9-4b4e-a5e2-4d705ee9b34c": {
          requireMention: true,
        },
      },
    },
  },
}
```

The default account can read credentials from the environment:

```bash
export BUZZ_RELAY_URL="wss://buzz.example.com"
export BUZZ_PRIVATE_KEY="nsec1..."
```

If the Buzz workspace uses NIP-OA delegated authorization, set `authTag` or `BUZZ_AUTH_TAG` to the JSON tag issued by Buzz:

```json
["auth", "<owner-pubkey>", "kind=9", "<signature>"]
```

## Access control

Buzz is group-only. Each enabled entry under `groups` subscribes OpenClaw to one channel UUID.

- `requireMention` defaults to `true`.
- `groupPolicy` defaults to `"allowlist"`.
- `groupPolicy: "open"` allows any authenticated sender in a configured room.
- `groupPolicy: "allowlist"` limits activation to pubkeys in `groupAllowFrom`.
- Set a group's `enabled` field to `false` to keep it configured but unsubscribed.

Mentions can be a Nostr `p` tag naming OpenClaw's pubkey or a configured text mention.

## Targets and threads

Outbound targets use a Buzz channel UUID, with an optional `buzz:` prefix:

```bash
openclaw message send \
  --channel buzz \
  --target buzz:7c4a6d2a-2ed9-4b4e-a5e2-4d705ee9b34c \
  --message "Hello from OpenClaw"
```

Direct replies use one NIP-10 `reply` event tag. Nested replies include both the thread `root` and immediate `reply` parent.

## Current scope

Supported:

- NIP-42 relay authentication
- NIP-29 `kind:9` group messages
- NIP-10 threaded replies
- Mention gating and sender allowlists
- Reconnect recovery with a 24-hour replay window and durable event deduplication

DMs, media uploads, reactions, and channel creation are not yet exposed by the OpenClaw plugin.

## Troubleshooting

- `Buzz requires at least one channels.buzz.groups entry`: add at least one channel UUID.
- `Buzz bus not running`: restart the Gateway and check channel status.
- NIP-42 auth timeout: confirm the URL points at the Buzz WebSocket relay.
- Auth rejection: verify the private key is permitted and the optional NIP-OA tag is current.
