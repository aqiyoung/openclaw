# @openclaw/buzz

Official OpenClaw channel plugin for Buzz group rooms over NIP-29.

## What it supports

- `kind:9` text messages in configured Buzz rooms
- NIP-42 relay authentication
- NIP-10 replies and threads
- Guided setup with authenticated room discovery and probe
- Mention gating and sender allowlists
- Reconnect recovery and durable event deduplication

The plugin is group-only. It does not currently support DMs, media or files,
native NIP-25 `kind:7` reactions (including emoji reactions), room creation,
automatic membership or role enrollment, or guided key rotation. A plain emoji
sent as message text remains ordinary text, not a reaction.

## Install

```sh
openclaw plugins install @openclaw/buzz
```

Restart the Gateway after installing or enabling the plugin.

## Guided setup

```sh
openclaw channels add --channel buzz
```

The flow collects the relay URL and defaults to generating a dedicated Buzz bot
identity. OpenClaw stores that generated key as plaintext at
`channels.buzz.privateKey`, following the current credential convention. You
can instead provide an existing dedicated bot key as plaintext or an existing
`env`, `file`, or `exec` SecretRef; setup adds no new credential architecture.

After external enrollment, setup authenticates with NIP-42, discovers the bot's
rooms, and prompts for room selection or a manual UUID, access policy, the
default outbound target, and an optional post-write test message.

## Bot identity and enrollment

Buzz identities are Nostr keypairs. Create a dedicated identity for OpenClaw;
never give OpenClaw a human workspace owner's private key.

`buzz-admin generate-key` remains a manual bootstrap or recovery tool for
self-hosted operators; it is not the normal guided default:

```sh
buzz-admin generate-key
```

Keep the secret key private. Give only the printed public key to the Buzz relay
operator and room administrator.

If a self-hosted relay enforces relay membership, its owner or admin can add the
bot identity with the operator-only admin CLI:

```sh
buzz-admin add-member --pubkey <BOT_PUBLIC_KEY> --role member
```

Separately, the current Buzz relay accepts a privileged owner/admin-signed
NIP-43 `kind:9030` add-member command from an authorized protocol client.

For hosted Buzz, ask the relay owner or admin for the supported hosted invitation
flow. Hosted users generally do not have `buzz-admin` access and should not
attempt privileged protocol admin commands.

An authorized existing room member must also add the identity to every target
room with the Buzz `bot` role. This publishes a `kind:9000` event with
`role=bot`:

```sh
buzz channels add-member \
  --channel <ROOM_UUID> \
  --pubkey <BOT_PUBLIC_KEY> \
  --role bot
```

These are separate approvals. Relay membership alone does not grant room
access.

## Configure OpenClaw

```json5
{
  channels: {
    buzz: {
      relayUrl: "wss://buzz.example.com",
      privateKey: {
        source: "env",
        provider: "default",
        id: "BUZZ_PRIVATE_KEY",
      },
      groupPolicy: "allowlist",
      groupAllowFrom: ["<64-character-hex-human-pubkey>"],
      groups: {
        "<ROOM_UUID>": {
          requireMention: true,
        },
      },
    },
  },
}
```

```sh
export BUZZ_PRIVATE_KEY="nsec1..."
```

`privateKey` accepts an `nsec` or 64-character hex key. The generated-key setup
path writes a plaintext string. An existing key may be plaintext or an existing
OpenClaw SecretRef from an `env`, `file`, or `exec` provider; `authTag` supports
the same forms.

The default account also reads `BUZZ_RELAY_URL`, `BUZZ_PRIVATE_KEY`, and the
optional `BUZZ_AUTH_TAG` directly from the Gateway environment.

## Verify

`channels status` runs the Buzz account probe:

```sh
openclaw channels status --channel buzz --probe
```

The probe opens an authenticated WebSocket/NIP-42 session and discovers rooms
whose membership events include the bot. Success proves relay authentication
and discovered membership, not the Buzz `bot` role. HTTP health is not an
authorization check. Follow with a real test message:

```sh
openclaw message send \
  --channel buzz \
  --target buzz:<ROOM_UUID> \
  --message "OpenClaw Buzz test"
```

Relay acceptance proves only the outbound publish path. Confirm room membership
and the `bot` role separately, then observe the message in Buzz.

For an inbound test, have an allowed human sender mention the bot in that room
and confirm OpenClaw replies.

## Key rotation

Rotation is non-atomic. Generate a new Gateway-owned key, enroll its public key
at the relay and in every room, then change `privateKey`. Restart or reload the
Gateway, test both outbound and inbound messages, then remove or archive the old
identity externally. Reissue `authTag` too when it was issued for the old
identity.

Rotation and re-enrollment are manual today.

## Documentation

See the [Buzz channel guide](https://docs.openclaw.ai/channels/buzz) for hosted
and self-hosted setup, room UUID discovery, configuration, and troubleshooting.
