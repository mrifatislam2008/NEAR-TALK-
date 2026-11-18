package com.example.bluetooth

import com.example.util.Constants
import com.example.util.Logger
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32

/**
 * Stateful streaming packet decoder handling:
 * - Partial packet reads across arbitrary InputStream chunks
 * - Multiple concatenated packets in a single read
 * - Resynchronization on corrupted magic bytes or bad CRC32
 * - Strict payload length validation
 */
class PacketDecoder {
    private val buffer = ByteArrayOutputStream(4096)

    fun feed(chunk: ByteArray, length: Int = chunk.size): List<Packet> {
        if (length <= 0) return emptyList()
        buffer.write(chunk, 0, length)
        return drainAvailablePackets()
    }

    fun reset() {
        buffer.reset()
    }

    private fun drainAvailablePackets(): List<Packet> {
        val packets = mutableListOf<Packet>()
        var raw = buffer.toByteArray()
        var offset = 0

        while (raw.size - offset >= PacketEncoder.FIXED_HEADER_SIZE + PacketEncoder.CRC_SIZE) {
            // 1. Sync on magic bytes 'N', 'T'
            if (raw[offset] != Constants.PROTOCOL_MAGIC_0 || raw[offset + 1] != Constants.PROTOCOL_MAGIC_1) {
                offset++
                continue
            }

            val headerBuf = ByteBuffer.wrap(raw, offset + 2, PacketEncoder.FIXED_HEADER_SIZE - 2)
            val version = headerBuf.get()
            val typeCode = headerBuf.get()
            val flags = headerBuf.get()
            val packetId = headerBuf.getLong()
            val senderId = PacketEncoder.bytesToUuidString(raw, offset + 13)
            val receiverId = PacketEncoder.bytesToUuidString(raw, offset + 29)
            headerBuf.position(headerBuf.position() + 32) // skip sender + receiver read directly
            val sequenceNumber = headerBuf.getInt()
            val payloadLength = headerBuf.getInt()

            val packetType = PacketType.fromCode(typeCode)
            if (version != Constants.PROTOCOL_VERSION ||
                packetType == null ||
                payloadLength < 0 ||
                payloadLength > Constants.MAX_PACKET_PAYLOAD_BYTES
            ) {
                Logger.w(TAG, "Corrupted packet header (ver=$version, type=$typeCode, len=$payloadLength), resyncing")
                offset++
                continue
            }

            val totalPacketLen = PacketEncoder.FIXED_HEADER_SIZE + payloadLength + PacketEncoder.CRC_SIZE
            if (raw.size - offset < totalPacketLen) {
                // Wait for remaining bytes in next InputStream read
                break
            }

            // 2. Verify CRC32 integrity
            val expectedCrc = ByteBuffer.wrap(raw, offset + PacketEncoder.FIXED_HEADER_SIZE + payloadLength, PacketEncoder.CRC_SIZE).getInt()
            val actualCrc = CRC32().apply {
                update(raw, offset + 2, PacketEncoder.FIXED_HEADER_SIZE - 2 + payloadLength)
            }.value.toInt()

            if (expectedCrc != actualCrc) {
                Logger.w(TAG, "Packet CRC32 mismatch for type=$packetType seq=$sequenceNumber, discarding frame")
                offset += 2 // advance past magic to resync
                continue
            }

            val payload = raw.copyOfRange(
                offset + PacketEncoder.FIXED_HEADER_SIZE,
                offset + PacketEncoder.FIXED_HEADER_SIZE + payloadLength
            )
            packets.add(
                Packet(
                    version = version,
                    type = packetType,
                    isEncrypted = (flags.toInt() and PacketEncoder.FLAG_ENCRYPTED.toInt()) != 0,
                    packetId = packetId,
                    senderId = senderId,
                    receiverId = receiverId,
                    sequenceNumber = sequenceNumber,
                    payload = payload
                )
            )
            offset += totalPacketLen
        }

        // Retain unconsumed trailing bytes
        buffer.reset()
        if (offset < raw.size) {
            // Prevent unbounded memory growth if garbage fills buffer without magic sync
            val remaining = raw.size - offset
            if (remaining <= Constants.MAX_PACKET_PAYLOAD_BYTES + PacketEncoder.FIXED_HEADER_SIZE + PacketEncoder.CRC_SIZE) {
                buffer.write(raw, offset, remaining)
            } else {
                Logger.w(TAG, "Packet buffer overflow guard triggered, trimming stale bytes")
            }
        }
        return packets
    }

    companion object {
        private const val TAG = "PacketDecoder"
    }
}
