# PTA Phone → Non-PTA Phone Forwarder — Requirements

**Owner:** Ismail
**Status:** Draft wishlist (what is wanted, not what has been validated as possible)
**Devices:** Phone A = PTA-registered phone (has active SIM, receives real SMS/calls). Phone B = Non-PTA phone (the one Ismail actually carries/uses day to day).

---

## 1. Core Goal

Everything that happens on Phone A (SMS, calls) should reach Phone B in real time, automatically, with zero manual steps and zero ongoing cost.

---

## 2. Non-Negotiable Constraints

- [ ] **100% free, forever** — no per-message fees, no monthly subscription, no paid API tier, no paid phone number. If a feature can only be done with a paid service, it is not an acceptable final solution — find a free path.
- [ ] **Fully automatic** — no manual "open the app and press forward," no manual QR scans on a daily basis, no manual window-refreshing.
- [ ] **Always running** — the background service on Phone A must survive reboots, must survive aggressive OEM battery killers (Xiaomi/Oppo/Vivo/Samsung-style), and must not require Phone A to be plugged in or actively used.
- [ ] **Real-time** — delivery to Phone B should happen within seconds of the event on Phone A, not batched/delayed.
- [ ] **Self-hosted, under Ismail's control** — no third-party SaaS holding the data; backend lives on infrastructure Ismail owns/controls.

---

## 3. SMS Forwarding

### 3.1 Must-have
- [ ] Every incoming SMS on Phone A is forwarded to Phone B automatically.
- [ ] Forwarded message shows: sender number, full message text, timestamp received.
- [ ] Delivery channel: Telegram Bot API and Discord Webhook.
- [ ] Works even if Phone A's screen is off / app is not open.
- [ ] Detailed specifications tracked in `message_forwarding_requirements.md`.

### 3.2 Filtering & smart handling
- [ ] OTP / bank / verification codes are visually highlighted or pulled to the top of the forwarded message.
- [ ] Ability to mute/block specific senders (promotional SMS, spam) so they are *not* forwarded.
- [ ] Ability to whitelist specific senders/keywords that always get forwarded even if general filtering is on.
- [ ] Option to tag which SIM (if Phone A is dual-SIM) a message came from.

### 3.3 Reliability for SMS
- [ ] If Phone A has no internet at the moment an SMS arrives, the message is queued locally and sent as soon as connectivity returns — nothing is silently lost.
- [ ] A local history/log of forwarded messages is kept on Phone A as a backup, in case a forward fails.

---

## 4. Call Handling

### 4.1 Interactive Call Alerts & Management
- [ ] Incoming calls trigger an interactive card in Discord (`#calls`) and Telegram with `[ ✅ Answer ]` and `[ ❌ Reject ]`.
- [ ] Tapping `[ ❌ Reject ]` sends command to Phone A to immediately terminate the call, recording timestamp and duration log.
- [ ] Missed calls automatically update the message card to a tagged missed call with timestamp.
- [ ] Detailed specifications tracked in `call_forwarding_requirements.md`.

### 4.2 Discord Voice Bridge (2-Way Audio)
- [ ] Tapping `[ ✅ Answer ]` auto-answers the cellular call on Phone A and connects a live 2-way audio stream to Discord's `🔊 Active Call` voice channel.
- [ ] Ismail connects to the voice channel from Phone B (iOS or Android) using the official Discord app to speak and listen in real time.
- [ ] Works over WiFi/mobile data, 100% free, zero root required on Phone A.
- [ ] Audio bridge cleanly disconnects when call ends, logging total call duration.

---

## 5. Delivery Channel Preferences

- [ ] Telegram Bot API is the primary channel (fast, reliable, free APNs/FCM delivery to iOS and Android).
- [ ] Discord Webhook is the secondary channel (clean embed formatting).
- [ ] WhatsApp is explicitly removed to avoid session dropouts and unofficial API bans.

---

## 6. Reliability & Monitoring

- [ ] Heartbeat/health check: periodic ping or free healthcheck monitor (e.g. healthchecks.io) to alert if Phone A goes silent.
- [ ] Dual-delivery fallback: forward to both Telegram and Discord to guarantee zero missed alerts.

---

## 7. Security & Privacy

- [ ] Communication between Phone A and Telegram/Discord is encrypted (TLS 1.3).
- [ ] Tokens and webhook URLs stored securely in Android's EncryptedSharedPreferences.
- [ ] Zero intermediary servers storing SMS data.

---

## 8. Technical Preferences (how it should be built)

- [ ] Single Unified Android app written natively in Kotlin.
- [ ] Mode toggle in app: `Sender` (Phone A) and `Receiver` (Phone B).
- [ ] Receiver mode on Android utilizes `NotificationListenerService` to intercept Telegram/Discord alerts and auto-fill banking apps.
- [ ] Sideloaded APK for both devices.
- [ ] Code style: `//` comments only, no block comments.

---

## 9. Nice-to-Have / Future

- [ ] Auto-reply from Phone B's side back through to the original SMS sender or caller.
- [ ] Support for multiple PTA phones/numbers feeding into one Phone B.
- [ ] Web-based dashboard to review full SMS/call history, searchable.
- [ ] "Do Not Disturb" hours — pause forwarding (or route silently without notification sound) during set hours.

---

## 10. Explicitly Out of Scope (for now)

- Anything requiring a paid WhatsApp Business template tier.
- Anything requiring Phone A or Phone B to be rooted (unless Ismail later decides rooting is acceptable).
- Anything requiring a paid SIP/VoIP number subscription.