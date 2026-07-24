---
summary: "Buzz group rooms over NIP-29"
read_when:
  - You want OpenClaw in a hosted Buzz workspace
  - You want OpenClaw in a self-hosted Buzz workspace
  - You are configuring NIP-29 group messaging
title: "Buzz"
---

Buzz is an official channel plugin (`@openclaw/buzz`) for NIP-29 group rooms. OpenClaw signs in to one Buzz relay with a dedicated bot identity, listens only to configured room UUIDs, and replies in NIP-10 threads.

## Support status

| Supported                                                         | Not supported yet                                           |
| ----------------------------------------------------------------- | ----------------------------------------------------------- |
| NIP-42 relay authentication                                       | Direct messages                                             |
| NIP-29 `kind:9` text messages in configured rooms                 | Media and file upload or download                           |
| NIP-10 replies and threads                                        | Native NIP-25 `kind:7` reactions, including emoji reactions |
| Pubkey and text mention detection                                 | Creating or administering rooms                             |
| Sender allowlists and per-room mention gating                     | Automatic membership or role enrollment                     |
| Guided setup with authenticated room discovery                    | Guided key rotation                                         |
| Reconnect with a 24-hour lookback and durable event deduplication |                                                             |

Buzz itself supports more features than this OpenClaw plugin. The table describes the current plugin, not the Buzz platform.

A plain emoji sent as `kind:9` message text remains ordinary text. It is not a native reaction.

## Install

```bash
openclaw plugins install @openclaw/buzz
```

Restart the Gateway after installing or enabling the plugin.

## Choose a relay

OpenClaw can connect to hosted or self-hosted Buzz deployments:

| Deployment  | What you need                                                                                                                                                                                                                                                                         |
| ----------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Hosted      | Ask the operator for the workspace's `wss://` relay URL and whether relay membership or a NIP-OA authorization tag is required. A relay owner or admin performs enrollment through the hosted membership or invitation path; hosted users generally do not have `buzz-admin` access.  |
| Self-hosted | Deploy Buzz from the [upstream Buzz repository](https://github.com/block/buzz), expose its WebSocket relay, and use `wss://` in production. `ws://` is appropriate only for a trusted local development relay. The relay owner or admin can use the deployment's `buzz-admin` binary. |

A relay URL identifies one Buzz community. Connecting to the relay does not make the bot a member of any room.

## Guided setup

Run the channel setup flow:

```bash
openclaw channels add --channel buzz
```

The flow collects the hosted or self-hosted relay URL and offers two bot-key paths:

- Generate a dedicated Buzz identity (the default). OpenClaw writes the generated key as plaintext at `channels.buzz.privateKey`, following the current OpenClaw credential convention.
- Use an existing dedicated bot key as plaintext or an existing `env`, `file`, or `exec` SecretRef.

This does not introduce a new credential store or SecretRef provider. See [Secrets management](/gateway/secrets) if you want to configure a non-plaintext provider before setup.

After the bot has the external approvals described below, setup authenticates with NIP-42, discovers rooms where the bot is a member, and prompts for rooms, access policy, a default outbound target, and an optional post-write test message. You can enter a room UUID manually when discovery is unavailable.

Buzz identities are Nostr keypairs. Always use a dedicated identity for OpenClaw.

<Warning>
Never put a human Buzz owner's private key in OpenClaw. Give the bot's public key to operators for approval; keep both the human and bot private keys out of messages and tickets.
</Warning>

`buzz-admin generate-key` remains a manual bootstrap or recovery option for self-hosted operators:

```bash
buzz-admin generate-key
```

Record the printed public key for enrollment, then choose the existing-key path in guided setup or configure the key manually. Hosted users generally do not have this command; ask the hosted operator about its identity workflow instead.

## Enroll the bot

Buzz has two separate membership layers.

### Add relay membership when required

Relay membership requires a relay owner or admin action. Hosted deployments use their operator-provided invitation flow. On a self-hosted relay with membership enforcement enabled, the operator can use `buzz-admin`:

```bash
buzz-admin add-member --pubkey <BOT_PUBLIC_KEY> --role member
```

The relay operator runs this command on the relay host. Separately, the current Buzz relay accepts a privileged owner/admin-signed NIP-43 `kind:9030` add-member command from an authorized protocol client. Hosted users should ask their operator to use the hosted membership or invitation path instead. None of these paths requires, or should ever request, the human owner's private key from OpenClaw.

### Add the bot to each room

An authorized existing room member must add the bot public key with the Buzz-specific `bot` role. Buzz's current role model and CLI both include `bot`; this publishes a NIP-29 `kind:9000` add-user event with `role=bot`. A private room requires the actor to already be a member:

```bash
buzz channels add-member \
  --channel <ROOM_UUID> \
  --pubkey <BOT_PUBLIC_KEY> \
  --role bot
```

Run this through an already authorized Buzz identity. Do not copy that human identity's private key into the OpenClaw configuration.

Relay membership alone is insufficient for authorized OpenClaw participation. OpenClaw creates subscriptions for configured UUIDs without prevalidating room membership, but you should enroll the bot in every configured room before relying on received messages or sending a test message.

## Find room UUIDs

The current Buzz relay supports bot-centric room discovery in two steps: query relay-signed `kind:39002` membership events filtered by the bot's `#p`, then fetch matching relay-signed `kind:39000` room metadata by `#d`. After enrolling the bot, run the current Buzz CLI with the bot identity and copy the `channel_id` value:

```bash
buzz channels list --member
```

This proves that the bot is a room member; it does not prove the membership has the `bot` role. Confirm that role with the room administrator. Guided setup and the channel probe perform the same authenticated discovery. If discovery is unavailable, ask a room administrator for the UUID and enter it manually. Do not substitute the human-readable room name; OpenClaw targets require a UUID.

## Configure

Configure the relay, dedicated bot key, and every approved room UUID:

```json5
{
  channels: {
    buzz: {
      relayUrl: "wss://buzz.example.com",
      privateKey: "nsec1...",
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

`privateKey` accepts an `nsec` or 64-character hex private key. The generated-key setup path writes this field as plaintext, following the current OpenClaw convention; that leaves the key readable on disk. The existing-key path also accepts plaintext or existing `env`, `file`, and `exec` SecretRefs:

```json5
{
  channels: {
    buzz: {
      privateKey: { source: "env", provider: "default", id: "BUZZ_PRIVATE_KEY" },
      // Or a configured file provider:
      // privateKey: { source: "file", provider: "filemain", id: "/channels/buzz/privateKey" },
      // Or a configured exec provider:
      // privateKey: { source: "exec", provider: "vault", id: "channels/buzz/privateKey" },
    },
  },
}
```

Define file and exec providers under `secrets.providers`; see [Secrets management](/gateway/secrets). The default account can also read raw values directly from the Gateway environment:

```bash
export BUZZ_RELAY_URL="wss://buzz.example.com"
export BUZZ_PRIVATE_KEY="nsec1..."
```

If the Buzz workspace uses NIP-OA delegated authorization, set `authTag` or `BUZZ_AUTH_TAG` to the JSON tag issued for this bot identity. `authTag` accepts the same plaintext and SecretRef forms as `privateKey`:

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

`groupAllowFrom` is an OpenClaw ingress policy in addition to Buzz room membership. A sender must pass both layers before their message reaches the agent.

## Targets and threads

Outbound targets use a Buzz channel UUID, with an optional `buzz:` prefix:

```bash
openclaw message send \
  --channel buzz \
  --target buzz:7c4a6d2a-2ed9-4b4e-a5e2-4d705ee9b34c \
  --message "Hello from OpenClaw"
```

Direct replies use one NIP-10 `reply` event tag. Nested replies include both the thread `root` and immediate `reply` parent.

## Test the connection

Start with runtime status:

```bash
openclaw channels status --channel buzz --probe
```

The Buzz account probe opens an authenticated WebSocket/NIP-42 session and performs membership discovery. A successful probe proves relay authentication and reports the rooms whose membership events include the bot. It does not prove that the bot has the Buzz `bot` role; confirm that separately with a room administrator. An HTTP health response only proves that a service answered and is not an authorization check.

Send a real room message for a live outbound check:

```bash
openclaw message send \
  --channel buzz \
  --target buzz:<ROOM_UUID> \
  --message "OpenClaw Buzz test"
```

Complete relay enrollment when required and room enrollment before running the test. A successful send proves only that the authenticated relay accepted the outbound `kind:9` publish; it does not prove room membership, the `bot` role, inbound routing, or agent replies. Verify membership through the `kind:39002` discovery step, confirm the role with a room administrator, and observe the message in Buzz. For an end-to-end check, have a pubkey listed in `groupAllowFrom` mention the bot in the room and confirm OpenClaw replies in the thread.

## Rotate the bot key

Rotation is non-atomic and changes the Buzz identity, so the new public key must be enrolled again before cutover.

1. Generate a new Gateway-owned keypair dedicated to OpenClaw.
2. Add the new public key to the relay when relay membership is enforced.
3. Add the new public key to every configured room with `--role bot`.
4. Replace `privateKey` and any identity-bound `authTag`, then restart or reload the Gateway.
5. Test an outbound message and an inbound mention.
6. Remove the old public key from each room:

   ```bash
   buzz channels remove-member --channel <ROOM_UUID> --pubkey <OLD_PUBLIC_KEY>
   ```

7. Have the relay operator remove the old relay membership:

   ```bash
   buzz-admin remove-member --pubkey <OLD_PUBLIC_KEY>
   ```

8. Remove or archive the old identity through the Buzz operator workflow when required.

Enroll the new key before switching OpenClaw to minimize downtime. There is no atomic handoff. Guided rotation and automatic re-enrollment are not implemented.

## Protocol follow-ups

The unsupported surfaces map to existing Buzz protocol families, but the OpenClaw plugin does not implement them yet:

- DMs: Buzz lifecycle kinds `41010`, `41011`, `41012`, and `41001`
- Media and files: Blossom storage plus NIP-92 `imeta` metadata
- Reactions: NIP-25 `kind:7`

Adding those surfaces requires explicit plugin work; configuring the current group-text channel does not enable them.

## Troubleshooting

| Symptom                                                  | Check                                                                                                                                                              |
| -------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `Buzz requires at least one channels.buzz.groups entry`  | Add at least one room UUID under `channels.buzz.groups`. Names such as `general` are not accepted.                                                                 |
| `Buzz bus not running`                                   | The account has not connected yet or is reconnecting. Run `openclaw channels status --channel buzz` and inspect Gateway logs.                                      |
| NIP-42 authentication timeout                            | Confirm `relayUrl` is the Buzz WebSocket endpoint and that the host is reachable. Use `wss://` outside local development.                                          |
| Authentication rejected                                  | Check the dedicated bot key, relay membership, and any identity-bound NIP-OA `authTag`.                                                                            |
| Message publish rejected                                 | Confirm the bot has relay membership when required and room membership with the `bot` role.                                                                        |
| Connected but inbound messages do not activate the agent | Confirm the UUID is under `groups`, the sender pubkey passes `groupPolicy` and `groupAllowFrom`, and the message mentions the bot when `requireMention` is `true`. |
| Test message sends but no agent reply appears            | The outbound test does not exercise inbound routing. Send an allowed inbound mention from another Buzz identity.                                                   |
