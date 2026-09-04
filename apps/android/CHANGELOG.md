# OpenClaw Android Changelog

## Unreleased

## 2026.9.4.7 - 2026-09-04

Polishes the v4.6 thinking control to track the upstream web mobile design more closely:

- Adds a 10dp fast-mode lightning badge in the bottom-right of the collapsed gauge trigger, matching the upstream `.chat-controls__effort-fast-badge`.
- Switches the bolt icon, the selected effort value, and the fast-mode toggle fill to the theme `accent` color (the same role the upstream uses for `var(--accent)`).
- Adds a switch role and state description to the fast-mode toggle, with a "Fast responses: ${state}" aria-label template translated to "快速响应：%1$s" / "快速回應：%1$s".
- Refines the effort slider: the track background uses text at 7% alpha (matching the upstream `--text 7%`), the thumb gets a soft drop shadow like the web's 0 1px 4px rgba(0,0,0,0.35) shadow, and the thumb is centered with 3dp vertical margin in the 26dp track.
- The toggle thumb is now positioned with 3dp padding and an explicit 14dp translate, matching the upstream `top:3, left:3, transform: translateX(14px)`.

## 2026.9.4.6 - 2026-09-04

Replaces the thinking-level trigger with a half-circle dial gauge that matches the upstream web mobile control. The expanded panel is rewritten to the web effort picker: an "Effort" slider with stop dots, Faster / Smarter scale labels, and a Fast mode toggle row with the lightning icon and helper text.

Localizes the new composer strings (Effort, Fast mode, Faster, Smarter, fast mode help) into Simplified and Traditional Chinese.

## 2026.9.4.5 - 2026-09-04

Fixes the chat composer send button overflowing the right edge of the input pill on narrow screens.

Replaces the inline thinking-level chip in the composer footer with a circular button (matching the web mobile control) that opens a bottom sheet panel with the thinking level selector and faster/smarter orientation labels.

## 2026.7.4 - 2026-07-30

Adds inline audio/video playback and uploads, session dashboards, run telemetry, chat rewind/fork, a Settings repair assistant, and Wear instant Talk.

Improves the working claw, collapsible details, Skill Workshop flows, and generated images.

Fixes reconnect/session state, Talk transcripts, manual gateway ports, large-text onboarding, reduced motion, and Wear pairing/reply reliability.

Thanks @IWhatsskill, @NianJiuZst, @masatohoshino, @cygnostik, @licheer-zte, and @metaforismo.

## 2026.7.3 - 2026-07-20

Adds a Wear OS companion for sessions, transcripts, text and voice replies, realtime Talk, Gateway controls, notifications, settings, and a launch Tile.

Adds foreground, on-device Voice Wake with editable Gateway-synced wake words, plus copy and save-as-PNG actions for rendered chat widgets.

Fixes composer media leaking across chats and malformed agent or profile initials when display names begin with emoji.

Thanks @sibbl, @IWhatsskill, and @Leon-SK668.

## 2026.7.2 - 2026-07-13

Adds Automations and Skills management with search, filters, editing, run tracking, install safety, and ClawHub risk review.

Improves chat with per-device history, durable approval status, session search, sharing, and agent avatars.

Adds provider model details, build identity, safer permission recovery, fresh Installed Apps consent, and Gateway protocol v3/v4 support.

Thanks @snowzlmbot, @IWhatsskill, @NianJiuZst, and @guarismo.

## 2026.7.1 - 2026-07-08

Adds multi-gateway switching with isolated credentials, history, queues, and notification routing.

Upgrades chat with offline recovery, session search and groups, model and agent pickers, voice notes, actions, link previews, code and math rendering.

Adds workspace files, Cron details, terminal access, and Listen playback.

Improves onboarding, reconnects, keyboards, notification filtering, location, canvas safety, and voice reliability.

Thanks @IWhatsskill, @ioridev, and @narcissus0702.

## 2026.6.11 - 2026-07-01

Improves Android gateway setup with localized onboarding, QR pairing fixes, and support for local mDNS gateway hosts.

Adds clearer recovery guidance for TLS fingerprint timeouts, mobile protocol mismatches, and gateway auth states.

Refreshes native Android localization coverage, including Swedish app naming and localized gateway trust flows.

## 2026.6.2 - 2026-06-02

OpenClaw is now available on Android.

Connect to your OpenClaw Gateway to chat with your assistant, use realtime Talk mode, review approvals, and bring Android device capabilities like camera, location, screen, and notifications into your private automation workflows.
