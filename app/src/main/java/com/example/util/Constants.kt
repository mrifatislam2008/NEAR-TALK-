package com.example.util

import java.util.UUID

object Constants {
    const val APP_NAME = "NEAR TALK"
    const val APP_VERSION = "1.0.0"

    // Custom 128-bit UUIDs exclusively for NEAR TALK peer discovery and RFCOMM channels
    val BLE_SERVICE_UUID: UUID = UUID.fromString("7F9B4E10-8A2C-4D6E-9B1F-3E5A7C9D0123")
    val RFCOMM_SERVICE_UUID: UUID = UUID.fromString("8A1C5F20-9B3D-4E7F-AC20-4F6B8D0E1234")
    const val RFCOMM_SERVICE_NAME = "NearTalkRFCOMM"

    // Versioned Binary Packet Protocol
    const val PROTOCOL_MAGIC_0: Byte = 0x4E // 'N'
    const val PROTOCOL_MAGIC_1: Byte = 0x54 // 'T'
    const val PROTOCOL_VERSION: Byte = 1
    const val MAX_PACKET_PAYLOAD_BYTES = 16 * 1024
    const val HEADER_SIZE_BYTES = 49 // 2 magic + 1 ver + 1 type + 1 flags + 8 pktId + 16 sender + 16 receiver + 4 seq + 4 len = 53; handled via PacketCodec constants

    // Transport Queue Capacities
    const val AUDIO_QUEUE_CAPACITY = 32
    const val MESSAGE_QUEUE_CAPACITY = 128

    // Timing & Watchdogs
    const val BLE_SCAN_TIMEOUT_MS = 25_000L
    const val CONNECTION_TIMEOUT_MS = 15_000L
    const val AUTH_TIMEOUT_MS = 10_000L
    const val HEARTBEAT_INTERVAL_MS = 5_000L
    const val HEARTBEAT_TIMEOUT_MS = 15_000L
    const val RECONNECT_BASE_DELAY_MS = 2_000L
    const val MAX_RECONNECT_ATTEMPTS = 3

    // Voice Audio Parameters (16 kHz Mono Speech, 20ms frames)
    const val AUDIO_SAMPLE_RATE = 16_000
    const val AUDIO_FRAME_DURATION_MS = 20
    const val AUDIO_FRAME_SAMPLES = (AUDIO_SAMPLE_RATE * AUDIO_FRAME_DURATION_MS) / 1000 // 320 samples
    const val AUDIO_FRAME_PCM_BYTES = AUDIO_FRAME_SAMPLES * 2 // 640 bytes 16-bit PCM
    const val OPUS_DEFAULT_BITRATE_BPS = 24_000
    const val OPUS_MIN_BITRATE_BPS = 12_000
    const val OPUS_MAX_BITRATE_BPS = 32_000

    // Foreground Service Notifications
    const val CHANNEL_BLUETOOTH_ID = "neartalk_bluetooth_channel"
    const val CHANNEL_CALLS_ID = "neartalk_calls_channel"
    const val NOTIFICATION_ID_BLUETOOTH = 1001
    const val NOTIFICATION_ID_ACTIVE_CALL = 1002
    const val NOTIFICATION_ID_INCOMING_CALL = 1003
}
