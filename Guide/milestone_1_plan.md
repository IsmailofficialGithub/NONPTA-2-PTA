# Milestone 1 Plan: Unified Android Forwarder App

**Target:** Single Kotlin APK supporting **Sender Mode** (Phone A) and **Receiver Mode** (Phone B) with direct dispatch to Telegram & Discord.

---

## Phase 1: Project Scaffolding & Setup UI

- [x] **Task 1.1: Project Skeleton & Build Setup**
  - Initialize minimal Gradle Android project structure (`build.gradle.kts`, `app/build.gradle.kts`, `gradle.properties`).
  - Configure target SDK 34, min SDK 26, Kotlin 1.9+.
  - Add core dependencies: `androidx.core-ktx`, `androidx.appcompat`, `material`, `okhttp`.

- [x] **Task 1.2: App Configuration Store (`AppConfig`)**
  - Create `AppConfig.kt` to persist settings using Android `SharedPreferences`:
    - Role: `SENDER` vs `RECEIVER`.
    - Telegram: Bot Token, Target Chat ID, Enabled toggle.
    - Discord: Webhook URL, Enabled toggle.
    - Service Active state.

- [x] **Task 1.3: Single-Screen Setup UI (`MainActivity`)**
  - Design layout with clean Material components:
    - Role toggle radio buttons: `Sender (PTA)` / `Receiver (Non-PTA)`.
    - Checkboxes for `Telegram` and `Discord` with collapsible credential fields.
    - Permission status cards with 1-tap grant buttons:
      - SMS & Phone State permissions (Sender).
      - Battery Optimization Exemption dialog (Sender).
      - Notification Access Settings shortcut (Receiver).
    - Big toggle action button: `[ START SERVICE ]` / `[ STOP SERVICE ]`.

---

## Phase 2: Sender Engine (SMS, Calls & Cloud Dispatch)

- [x] **Task 2.1: Background Persistence (`ForwarderService`)**
  - Implement `ForegroundService` with a low-priority ongoing notification.
  - Use `START_STICKY` to ensure automatic OS resurrection.
  - Acquire partial `WakeLock` during active event dispatch to prevent sleep dropouts.

- [x] **Task 2.2: Device Boot Auto-Start (`BootReceiver`)**
  - Implement `BroadcastReceiver` for `ACTION_BOOT_COMPLETED` and `ACTION_MY_PACKAGE_REPLACED`.
  - Auto-launch `ForwarderService` on device boot if service was previously active.

- [x] **Task 2.3: SMS Interception (`SmsReceiver`)**
  - Implement `BroadcastReceiver` for `android.provider.Telephony.SMS_RECEIVED`.
  - Parse `SmsMessage` PDUs to extract sender number, timestamp, and message body.
  - Detect dual-SIM subscription ID if available.

- [x] **Task 2.4: Phone Call Interception (`CallListener`)**
  - Implement `TelephonyCallback.CallStateListener` (Android 12+) with `PhoneStateListener` fallback.
  - Intercept states:
    - `CALL_STATE_RINGING`: Read incoming number, resolve contact name via `ContactsContract`.
    - `CALL_STATE_OFFHOOK`: Call answered.
    - `CALL_STATE_IDLE`: Detect missed calls (if ringing stopped without being answered).

- [x] **Task 2.5: Cloud Dispatcher (`CloudDispatcher`)**
  - Build lightweight `OkHttp` network client:
    - `sendTelegramMessage(text)`: `POST https://api.telegram.org/bot<TOKEN>/sendMessage` (HTML format).
    - `sendDiscordWebhook(embed)`: `POST <WEBHOOK_URL>` with rich embeds (bold OTP, timestamps, colors).
  - Include local in-memory/file queue with retry for temporary network drops.

---

## Phase 3: Receiver Engine (Banking OTP Auto-Capture)

- [x] **Task 3.1: Notification Interceptor (`OtpNotificationService`)**
  - Subclass `NotificationListenerService`.
  - Filter notifications strictly from:
    - Telegram (`org.telegram.messenger`, `org.thunderdog.challegram`).
    - Discord (`com.discord`).

- [x] **Task 3.2: Regex OTP Extraction (`OtpParser`)**
  - Build regex engine to isolate 4-to-8 digit verification codes from notification text.
  - Detect known banking senders (UBL, JazzCash, Easypaisa, SadaPay, NayaPay, Meezan, HBL).

- [x] **Task 3.3: Auto-Copy & Haptic Feedback**
  - Programmatically set extracted OTP into system `ClipboardManager`.
  - Fire subtle haptic buzz (`Vibrator`).
  - Display non-intrusive Toast: `[🏦 UBL OTP 839201 copied to clipboard]`.

---

## Phase 4: Self-Check & Build Validation

- [x] **Task 4.1: Unit Tests for Parsers**
  - Write test cases for `OtpParser` using 10+ real Pakistani banking SMS formats.
  - Write test cases for Telegram/Discord payload formatters.

- [x] **Task 4.2: Build & Package Validation**
  - Run `./gradlew assembleDebug` to verify clean compilation.
  - Verify APK package size (target: under 5 MB).

- [ ] **Task 4.3: End-to-End Simulation**
  - Test simulated SMS receipt via ADB:
    ```bash
    adb emu sms send 03001234567 "Your UBL Digital OTP is 839201"
    ```
  - Verify instant receipt in Telegram and Discord.
  - Verify auto-clipboard capture on Receiver device.

---

## Phase 5: Interactive Call Control & Discord Voice Relay (Milestone 2)

- [x] **Task 5.1: Android Telecom Call Control**
  - Added `ANSWER_PHONE_CALLS` permission to `AndroidManifest.xml`.
  - Implemented `acceptCall()` (`TelecomManager.acceptRingingCall()`) and `endCall()` in `CallListener.kt`.
  - Added permission launcher in `MainActivity.kt`.

- [x] **Task 5.2: Bidirectional WebSocket Signaling (`RelayClient`)**
  - Created persistent OkHttp WebSocket client (`RelayClient.kt`) with auto-reconnect.
  - Wired into `ForwarderService.kt` to stream `CALL_STATE` events and receive remote `ANSWER` / `REJECT` commands.
  - Added Relay Server UI controls in `MainActivity.kt` and `activity_main.xml`.

- [x] **Task 5.3: Discord Voice & Control Relay Daemon (`relay/`)**
  - Implemented Node.js daemon (`relay/src/index.js`) with WebSocket server and Discord Bot.
  - Added interactive buttons (`[ ✅ Answer & Join Voice ]`, `[ ❌ Reject Call ]`, `[ 🔴 End Call ]`).
  - Added Telegram Bot inline keyboard support.
  - Created low-latency 2-way audio bridge (`relay/src/audio_bridge.js`) interfacing system Bluetooth HFP with `@discordjs/voice`.
  - Added Linux Bluetooth HFP pairing helper (`relay/setup_hfp_linux.sh`) and setup documentation.

