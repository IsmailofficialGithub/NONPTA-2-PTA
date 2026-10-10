# Message Forwarding Requirements (SMS & OTP)

**Owner:** Ismail  
**Status:** Approved Specification  
**Architecture Model:** Serverless / Broker Architecture (via Telegram Bot API & Discord Webhook)  
**Apps Required:** Single Unified Android App (Kotlin) with Sender & Receiver modes.

---

## 1. System Overview

```
Phone A (PTA Android - SENDER)
   │
   │  Direct HTTPS POST (OkHttp)
   ▼
Telegram Bot API & Discord Webhooks (Zero-cost Cloud Broker)
   │
   ├─► Phone B is iOS (iPhone):
   │     • Receives push notifications via official Telegram / Discord iOS apps.
   │     • Lockscreen banner with OTP / SMS details; manual copy/paste into banking apps.
   │
   └─► Phone B is Android (RECEIVER):
         • Runs the same Unified APK in "Receiver Mode".
         • NotificationListenerService catches Telegram/Discord alerts locally.
         • Extracts OTP and injects/writes to local storage for banking apps.
```

---

## 2. Phone A: Sender Configuration & Behavior

### 2.1 First-Time Setup & Onboarding
1. **Mode Selection:** Select `[•] SENDER (PTA Phone)`.
2. **Permission Setup:**
   * `RECEIVE_SMS` & `READ_SMS` (Capture incoming SMS).
   * `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (Survive OEM battery killers).
   * `RECEIVE_BOOT_COMPLETED` (Auto-restart service on reboot).
3. **Delivery Channel Configuration:**
   * `[ ] Discord` checkbox:
     * Webhook URL input field.
   * `[ ] Telegram` checkbox:
     * Bot Token input field.
     * Target Chat ID input field.
   * `[ Save & Start Service ]` button.

### 2.2 Background Execution & Reliability
* **Foreground Service:** Runs with a persistent system notification (`START_STICKY`) so the OS does not terminate it.
* **SMS Interception:** `BroadcastReceiver` captures incoming SMS via `android.provider.Telephony.SMS_RECEIVED`.
* **Payload Formatting:**
  * Clean markdown for Telegram (HTML/MarkdownV2).
  * Rich embed for Discord (highlighting OTP in bold, sender name, and timestamp).
* **Offline Handling:** Local SQLite queue. If the internet drops, unsent messages are queued and flushed as soon as network connectivity is restored.

---

## 3. Phone B: Receiver Behavior

### 3.1 Case 1: Phone B is iOS (iPhone)
* **Zero custom app required.**
* User installs official Telegram and/or Discord from the Apple App Store.
* Receives instant push notifications via Apple's native APNs pipeline with 0% battery impact.
* User copies OTP directly from the notification banner to use in iOS banking apps (which allow manual OTP input).

### 3.2 Case 2: Phone B is Android
* **Runs the same Unified APK.**
* **Mode Selection:** Select `[•] RECEIVER (Non-PTA Phone)`.
* **Required Permission:** Grant system `Notification Access` (`NotificationListenerService`).

#### The Android Auto-Fill Pipeline for Banking Apps:
1. Phone B receives a Telegram or Discord notification sent by Phone A.
2. `NotificationListenerService` in the app detects notifications from `org.telegram.messenger` or `com.discord`.
3. The app parses the notification text:
   * Identifies the sender (e.g. `UBL`, `JazzCash`, `Meezan`).
   * Extracts the OTP code via regex (4 to 8 digit sequence).
4. **Action executed:**
   * **Clipboard Auto-Copy:** Instantly copies the extracted OTP to the system clipboard and triggers a subtle haptic vibration.
   * **Default SMS App Insertion (Optional / Configurable):** If the app is set as Default SMS Handler, it inserts the message into `content://sms/inbox` so bank `ContentObserver` hooks detect it.
   * **Floating OTP Pill:** Displays a transient 1-tap overlay bubble (`[ 🏦 UBL OTP: 839210 | Tap to Copy/Paste ]`).

---

## 4. Security & Privacy
* **Direct Communication:** Phone A talks directly to official Telegram (`api.telegram.org`) and Discord (`discord.com/api`) over TLS 1.3.
* **Zero Intermediary Server:** No custom VPS or third-party database stores SMS content.
* **Credentials:** Tokens and webhook URLs are stored strictly in Android's encrypted private app storage (`EncryptedSharedPreferences`).
