# OpenClaw Buzz

Official OpenClaw channel plugin for Buzz rooms.

Install from OpenClaw:

```bash
openclaw plugin add @openclaw/buzz
```

Then run the guided setup:

```bash
openclaw channels add --channel buzz
```

The setup flow creates or connects a dedicated bot identity, helps you choose
the rooms OpenClaw can use, and verifies the connection. A Buzz admin must
approve the bot for the relay and each room.

Once connected, OpenClaw agents can receive messages and reply in configured
Buzz rooms.

See the [Buzz channel guide](https://docs.openclaw.ai/channels/buzz) for setup
and troubleshooting.
