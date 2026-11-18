package com.example.audio

import java.nio.ByteBuffer

enum class AudioCodecType(val code: Byte) {
    OPUS_16K(0x01),
    ADPCM_16K(0x02);

    companion object {
        fun fromCode(code: Byte): AudioCodecType = entries.firstOrNull { it.code == code } ?: OPUS_16K
    }
}

/**
 * Audio packetizer / depacketizer format carried inside PacketType.AUDIO_PACKET payload:
 * [1 byte codecType][4 bytes audioSeq][8 bytes timestampMs][2 bytes frameDurationMs][encoded audio bytes]
 */
data class AudioPacket(
    val codecType: AudioCodecType,
    val sequenceNumber: Int,
    val timestampMs: Long,
    val frameDurationMs: Short = 20,
    val encodedPayload: ByteArray
) {
    fun toWireBytes(): ByteArray {
        val buf = ByteBuffer.allocate(HEADER_SIZE + encodedPayload.size)
        buf.put(codecType.code)
        buf.putInt(sequenceNumber)
        buf.putLong(timestampMs)
        buf.putShort(frameDurationMs)
        buf.put(encodedPayload)
        return buf.array()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioPacket) return false
        return codecType == other.codecType &&
            sequenceNumber == other.sequenceNumber &&
            timestampMs == other.timestampMs &&
            frameDurationMs == other.frameDurationMs &&
            encodedPayload.contentEquals(other.encodedPayload)
    }

    override fun hashCode(): Int {
        var result = codecType.hashCode()
        result = 31 * result + sequenceNumber
        result = 31 * result + timestampMs.hashCode()
        result = 31 * result + frameDurationMs
        result = 31 * result + encodedPayload.contentHashCode()
        return result
    }

    companion object {
        private const val HEADER_SIZE = 15

        fun fromWireBytes(bytes: ByteArray): AudioPacket? {
            if (bytes.size <= HEADER_SIZE) return null
            val buf = ByteBuffer.wrap(bytes)
            val codec = AudioCodecType.fromCode(buf.get())
            val seq = buf.getInt()
            val ts = buf.getLong()
            val duration = buf.getShort()
            val payload = ByteArray(bytes.size - HEADER_SIZE)
            buf.get(payload)
            return AudioPacket(
                codecType = codec,
                sequenceNumber = seq,
                timestampMs = ts,
                frameDurationMs = duration,
                encodedPayload = payload
            )
        }
    }
}
