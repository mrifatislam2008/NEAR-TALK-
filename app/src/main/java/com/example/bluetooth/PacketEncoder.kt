package com.example.bluetooth

import com.example.util.Constants
import java.nio.ByteBuffer
import java.util.UUID
import java.util.zip.CRC32

object PacketEncoder {
    const val FIXED_HEADER_SIZE = 53 // 2 magic + 1 ver + 1 type + 1 flags + 8 pktId + 16 sender + 16 receiver + 4 seq + 4 payloadLen
    const val CRC_SIZE = 4
    const val FLAG_ENCRYPTED: Byte = 0x01

    fun encode(packet: Packet): ByteArray {
        require(packet.payload.size <= Constants.MAX_PACKET_PAYLOAD_BYTES) {
            "Payload exceeds max length (${packet.payload.size} > ${Constants.MAX_PACKET_PAYLOAD_BYTES})"
        }
        val totalSize = FIXED_HEADER_SIZE + packet.payload.size + CRC_SIZE
        val buffer = ByteBuffer.allocate(totalSize)

        buffer.put(Constants.PROTOCOL_MAGIC_0)
        buffer.put(Constants.PROTOCOL_MAGIC_1)
        buffer.put(packet.version)
        buffer.put(packet.type.code)
        buffer.put(if (packet.isEncrypted) FLAG_ENCRYPTED else 0x00)
        buffer.putLong(packet.packetId)
        putUuidBytes(buffer, packet.senderId)
        putUuidBytes(buffer, packet.receiverId)
        buffer.putInt(packet.sequenceNumber)
        buffer.putInt(packet.payload.size)
        buffer.put(packet.payload)

        val frameBytesWithoutCrc = buffer.array()
        val crc = CRC32().apply {
            // Integrity verification covers version through end of payload
            update(frameBytesWithoutCrc, 2, FIXED_HEADER_SIZE - 2 + packet.payload.size)
        }.value.toInt()
        buffer.putInt(crc)

        return buffer.array()
    }

    fun buildAad(
        version: Byte,
        type: PacketType,
        packetId: Long,
        senderId: String,
        receiverId: String,
        sequenceNumber: Int
    ): ByteArray {
        val buf = ByteBuffer.allocate(46)
        buf.put(version)
        buf.put(type.code)
        buf.putLong(packetId)
        putUuidBytes(buf, senderId)
        putUuidBytes(buf, receiverId)
        buf.putInt(sequenceNumber)
        return buf.array()
    }

    fun uuidToBytes(id: String): ByteArray {
        val uuid = parseUuidOrDeterministic(id)
        return ByteBuffer.allocate(16)
            .putLong(uuid.mostSignificantBits)
            .putLong(uuid.leastSignificantBits)
            .array()
    }

    fun bytesToUuidString(bytes: ByteArray, offset: Int): String {
        val buf = ByteBuffer.wrap(bytes, offset, 16)
        val msb = buf.getLong()
        val lsb = buf.getLong()
        return if (msb == 0L && lsb == 0L) "" else UUID(msb, lsb).toString()
    }

    private fun putUuidBytes(buffer: ByteBuffer, id: String) {
        if (id.isBlank()) {
            buffer.putLong(0L)
            buffer.putLong(0L)
        } else {
            val uuid = parseUuidOrDeterministic(id)
            buffer.putLong(uuid.mostSignificantBits)
            buffer.putLong(uuid.leastSignificantBits)
        }
    }

    private fun parseUuidOrDeterministic(id: String): UUID {
        return try {
            UUID.fromString(id)
        } catch (_: IllegalArgumentException) {
            UUID.nameUUIDFromBytes(id.toByteArray(Charsets.UTF_8))
        }
    }
}
