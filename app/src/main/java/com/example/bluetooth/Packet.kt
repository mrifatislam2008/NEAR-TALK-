package com.example.bluetooth

import com.example.util.Constants

enum class PacketType(val code: Byte) {
    HANDSHAKE(0x01),
    AUTH_REQUEST(0x02),
    AUTH_RESPONSE(0x03),
    CONNECTION_READY(0x04),
    TEXT_MESSAGE(0x10),
    MESSAGE_ACK(0x11),
    MESSAGE_READ(0x12),
    CALL_REQUEST(0x20),
    CALL_ACCEPT(0x21),
    CALL_REJECT(0x22),
    CALL_END(0x23),
    AUDIO_PACKET(0x30),
    HEARTBEAT(0x40),
    DISCONNECT(0x41),
    PROFILE_SYNC(0x42);

    companion object {
        fun fromCode(code: Byte): PacketType? = entries.firstOrNull { it.code == code }
    }
}

data class Packet(
    val version: Byte = Constants.PROTOCOL_VERSION,
    val type: PacketType,
    val isEncrypted: Boolean = false,
    val packetId: Long,
    val senderId: String,
    val receiverId: String,
    val sequenceNumber: Int,
    val payload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Packet) return false
        return version == other.version &&
            type == other.type &&
            isEncrypted == other.isEncrypted &&
            packetId == other.packetId &&
            senderId == other.senderId &&
            receiverId == other.receiverId &&
            sequenceNumber == other.sequenceNumber &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = version.toInt()
        result = 31 * result + type.hashCode()
        result = 31 * result + isEncrypted.hashCode()
        result = 31 * result + packetId.hashCode()
        result = 31 * result + senderId.hashCode()
        result = 31 * result + receiverId.hashCode()
        result = 31 * result + sequenceNumber
        result = 31 * result + payload.contentHashCode()
        return result
    }
}
