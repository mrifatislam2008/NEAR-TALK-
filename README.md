# NEAR TALK

**NEAR TALK** is a 100% offline, peer-to-peer Android communication application built with **Kotlin** and **Jetpack Compose**. It connects two nearby Android smartphones directly over **Bluetooth** without internet, mobile data, Wi-Fi Direct, or central servers.

## Core Capabilities

1. **One-to-One Offline Text Messaging**
   - Send and receive encrypted text messages directly over Bluetooth RFCOMM.
   - Local persistence using Room (`DeviceEntity`, `MessageEntity`, `CallHistoryEntity`) so conversations survive app restarts.
   - Accurate status tracking: `PENDING` (queued offline), `SENT` (written to RFCOMM stream), `DELIVERED` (updated only upon actual peer `MESSAGE_ACK`), and `READ` (on `MESSAGE_READ` receipt).
   - Automatic retry of `PENDING` messages when peers reconnect, plus message deduplication and ordering.

2. **One-to-One Real-Time Two-Way Audio Calling**
   - Full-duplex voice pipeline: `AudioRecord` (`VOICE_COMMUNICATION` + AEC/NoiseSuppressor/AGC) -> `OpusEncoder` (`audio/opus` 16 kHz mono 20 ms frames on API 29+, with IMA-ADPCM fallback on API 26–28) -> `AudioPacket` -> high-priority `BluetoothTransport` queue -> `OpusDecoder` -> `JitterBuffer` (sequence ordering + Packet Loss Concealment) -> `AudioTrack`.
   - Synchronized `CallState` machine (`IDLE`, `OUTGOING`, `INCOMING`, `CONNECTING`, `ACTIVE`, `MUTED`, `ENDING`, `ENDED`, `FAILED`), mute and speakerphone routing, live call timer, call quality indicator, and automatic microphone cleanup on disconnect or termination.

3. **Bluetooth & Application-Layer Security**
   - BLE scanning & advertising (`BleScanner`, `BleAdvertiser`) with custom 128-bit UUID filtering and battery-conscious scan timeouts.
   - Direct Bluetooth Classic RFCOMM sockets (`RfcommServer`, `RfcommClient`) with heartbeat watchdog and automatic reconnection.
   - Ephemeral ECDH (`secp256r1`) + HKDF-SHA256 per-session key derivation, AES-256-GCM authenticated encryption, HMAC-SHA256 peer proof, sliding-window replay protection, explicit connection approval, and trusted/blocked device management.

## Requirements

- **Minimum Android Version**: Android 8.0 (API 26)
- **Target SDK**: 36
- **JDK**: Java 11+ (tested with JDK 17/21)
- **Build System**: Gradle Kotlin DSL

## Build & APK Instructions

### Option A: Android Studio
1. Open the extracted project folder in **Android Studio** (Ladybug / Meerkat or newer).
2. Let Gradle sync dependencies.
3. Connect two physical Android phones (API 26+) with USB debugging enabled, or select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

### Option B: Command Line (Gradle)
From the project root:
- **Run Unit & Robolectric Tests**:
  ```bash
  gradle :app:testDebugUnitTest
  ```
- **Build Debug APK** (uses `debug.keystore` configured in `app/build.gradle.kts`):
  ```bash
  gradle :app:assembleDebug
  ```
  Output: `app/build/outputs/apk/debug/app-debug.apk`
- **Build Signed Release APK** (requires your upload keystore credentials):
  ```bash
  export KEYSTORE_PATH=/path/to/your-release-key.jks
  export STORE_PASSWORD=your_store_password
  export KEY_PASSWORD=your_key_password
  gradle :app:assembleRelease
  ```
  Output: `app/build/outputs/apk/release/app-release.apk`

## Installation & Two-Device Physical Testing Guide

1. Install `NEAR_TALK.apk` onto **two physical Android smartphones** (Phone A and Phone B).
2. Turn off Wi-Fi and mobile data (or enable Airplane Mode with Bluetooth ON) to verify pure offline operation.
3. Complete **Profile Setup** on both phones and grant **Bluetooth**, **Microphone**, and **Notification** permissions when prompted.
4. Open the **Nearby** tab on both devices and tap **Scan Nearby Devices**.
5. Tap **Connect** on Phone A and **Accept** on Phone B's **Connection Request** screen.
6. Test bidirectional text messaging, offline disconnect/reconnect with `PENDING` queue delivery, and two-way voice calling (Mute, Speaker, screen-off foreground service operation, and mid-call Bluetooth disconnect recovery).
