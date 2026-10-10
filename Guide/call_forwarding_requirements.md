# Call Forwarding & Voice Bridge Requirements

**Owner:** Ismail  
**Status:** Approved Specification  
**Architecture Model:** Interactive Bot Relay + Discord Voice Bridge  
**Platforms Supported:** Phone B on iOS (iPhone) and Android via official Discord & Telegram apps.

---

## 1. System Overview

```
Phone A (PTA Android - Real SIM)
   │
   ├─► Detects Incoming Call (< 500ms)
   │
   ▼
Cloud Relay / Discord Bot & Telegram Bot
   │
   ├─► Posts interactive card with [ ✅ Answer ] and [ ❌ Reject ]
   │
Phone B (iOS / Android)
   │
   ├─► Tap [ ❌ Reject ] ────────► Phone A hangs up call ──► Logs "❌ Call Rejected"
   │
   └─► Tap [ ✅ Answer ] ────────► Phone A auto-answers ──► Joins Discord Voice Channel
                                                           (2-Way Real-Time Voice Audio)
```

---

## 2. Interactive Call Flow

### 2.1 The Incoming Call Notification
When Phone A receives an incoming cellular call, the bot immediately posts a message with interactive buttons in Discord (`#calls` channel) and Telegram:

```
┌────────────────────────────────────────────────────────┐
│ 📞 INCOMING CALL: Ali (+92 300 1234567)                │
│ SIM 1 (Jazz) • Status: Ringing...                      │
├────────────────────────────────────────────────────────┤
│           [ ✅ Answer & Join Voice ]                   │
│           [ ❌ Reject Call         ]                   │
└────────────────────────────────────────────────────────┘
```

### 2.2 Action A: Reject Call (`❌`)
1. User taps **`[ ❌ Reject Call ]`** (or reacts with `❌`) on Phone B.
2. Relay sends reject command to Phone A.
3. Phone A terminates the cellular call immediately (`TelecomManager.endCall()`).
4. The message card updates to:
   ```
   ❌ Call Rejected: Ali (+92 300 1234567)
   Time: 20:15:42 • Duration: 0s
   ```
5. An audit log entry is saved.

### 2.3 Action B: Answer Call & Voice Bridge (`✅`)
1. User taps **`[ ✅ Answer & Join Voice ]`** on Phone B.
2. Phone A automatically answers the cellular call (`TelecomManager.acceptRingingCall()`).
3. **Voice Bridge Activation:**
   * The Discord Bot connects to a designated **`🔊 Active Call`** voice channel.
   * User on Phone B connects to the voice channel via the standard Discord app (using earphones/AirPods).
   * Two-way audio streams in real time between the Discord voice channel and Phone A's cellular call.
4. **Active Call Card:** Message updates to live duration:
   ```
   🟢 In Call: Ali (+92 300 1234567)
   Duration: 01:23 • Connected [ 🔴 End Call ]
   ```
5. **Call Termination:**
   * When either party hangs up, the bot leaves the voice channel.
   * The message card finalizes with duration and timestamp:
     ```
     🏁 Call Ended: Ali (+92 300 1234567)
     Total Duration: 2 mins 45 secs • Ended at 20:18:27
     ```

### 2.4 Missed Call Handling
* If the caller hangs up before the user responds, Phone A detects the `IDLE` state.
* The message card automatically updates to:
  ```
  🔴 Missed Call: Ali (+92 300 1234567)
  Timestamp: 20:14:10 • SIM 1 (Jazz)
  ```

---

## 3. Technical Implementation Details

### 3.1 Phone A (Android Client)
* **Required Permissions:**
  * `READ_PHONE_STATE` & `READ_CALL_LOG` (To identify caller name/number in real time).
  * `ANSWER_PHONE_CALLS` (Android 8.0+ API to answer programmatically).
  * `FOREGROUND_SERVICE_PHONE_CALL` (Foreground service to ensure zero delay).
* **Telephony Listeners:**
  * Uses `TelephonyCallback` / `PhoneStateListener` to observe call state changes (`RINGING`, `OFFHOOK`, `IDLE`).
* **Audio Bridge Connection:**
  * Connects audio stream through Bluetooth Hands-Free Profile (HFP) or system audio sink to the relay daemon.

### 3.2 Discord Voice & Bot Integration
* **Bot Framework:** Discord bot with `@discordjs/voice` or Rust (`songbird`).
* **Voice Channel:** Dedicated `🔊 Active Call` room.
* **Audio Codec:** Low-latency Opus encoding native to Discord.

---

## 4. Key Advantages
1. **Universal Compatibility:** Works seamlessly whether Phone B is an iPhone or an Android phone.
2. **Zero Extra Apps on Phone B:** Audio works directly inside the official Discord app already on Phone B.
3. **Full Audit Trail:** Every incoming call, whether answered, missed, or rejected, has a persistent record in your Discord/Telegram channel.
