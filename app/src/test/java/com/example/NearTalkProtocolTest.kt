package com.example

import com.example.audio.AudioCodecType
import com.example.audio.AudioPacket
import com.example.audio.JitterBuffer
import com.example.bluetooth.Packet
import com.example.bluetooth.PacketDecoder
import com.example.bluetooth.PacketEncoder
import com.example.bluetooth.PacketType
import com.example.core.SecurityManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class NearTalkProtocolTest {

    @Test
    fun `packet encoder and decoder handle partial reads, multiple packets, and corruption recovery`() {
        val sender = UUID.randomUUID().toString()
        val receiver = UUID.randomUUID().toString()

        val p1 = Packet(
            type = PacketType.TEXT_MESSAGE,
            isEncrypted = true,
            packetId = 101L,
            senderId = sender,
            receiverId = receiver,
            sequenceNumber = 1,
            payload = "First packet".toByteArray()
        )
        val p2 = Packet(
            type = PacketType.AUDIO_PACKET,
            isEncrypted = true,
            packetId = 102L,
            senderId = sender,
            receiverId = receiver,
            sequenceNumber = 2,
            payload = byteArrayOf(1, 2, 3, 4, 5)
        )

        val streamBytes = byteArrayOf(0x00, 0x7F) + // Leading noise
            PacketEncoder.encode(p1) +
            PacketEncoder.encode(p2)

        val decoder = PacketDecoder()
        val decoded = mutableListOf<Packet>()

        // Feed in tiny 7-byte chunks to simulate fragmented RFCOMM reads
        var offset = 0
        while (offset < streamBytes.size) {
            val len = minOf(7, streamBytes.size - offset)
            decoded += decoder.feed(streamBytes.copyOfRange(offset, offset + len), len)
            offset += len
        }

        assertEquals(2, decoded.size)
        assertEquals(p1, decoded[0])
        assertEquals(p2, decoded[1])

        // Corrupt a payload byte and verify CRC rejects it
        val corrupted = PacketEncoder.encode(p1)
        corrupted[PacketEncoder.FIXED_HEADER_SIZE] = (corrupted[PacketEncoder.FIXED_HEADER_SIZE].toInt() xor 0xFF).toByte()
        decoder.reset()
        assertTrue(decoder.feed(corrupted, corrupted.size).isEmpty())
    }

    @Test
    fun `ecdh session derivation, aes-gcm encryption, and replay protection work end to end`() {
        val aliceSec = SecurityManager()
        val bobSec = SecurityManager()

        val aliceKp = aliceSec.generateEphemeralKeyPair()
        val bobKp = bobSec.generateEphemeralKeyPair()
        val aliceNonce = aliceSec.generateRandomBytes(32)
        val bobNonce = bobSec.generateRandomBytes(32)

        assertTrue(aliceSec.deriveSessionKeys(aliceKp, bobKp.public.encoded, aliceNonce, bobNonce, isInitiator = true))
        assertTrue(bobSec.deriveSessionKeys(bobKp, aliceKp.public.encoded, bobNonce, aliceNonce, isInitiator = false))

        val proof = aliceSec.computeAuthProof(aliceNonce, bobNonce, "alice-id")
        assertTrue(bobSec.verifyAuthProof(proof, aliceNonce, bobNonce, "alice-id"))

        val aad = PacketEncoder.buildAad(1, PacketType.TEXT_MESSAGE, 1L, "alice", "bob", 1)
        val plaintext = "Confidential offline message".toByteArray()
        val encrypted = aliceSec.encryptPayload(plaintext, aad)
        val decrypted = bobSec.decryptPayload(encrypted, aad)

        assertArrayEquals(plaintext, decrypted)

        // Replay window rejects duplicate sequence numbers
        assertTrue(bobSec.checkAndAcceptSequence(1))
        assertFalse(bobSec.checkAndAcceptSequence(1))
        assertTrue(bobSec.checkAndAcceptSequence(3))
        assertTrue(bobSec.checkAndAcceptSequence(2))
        assertFalse(bobSec.checkAndAcceptSequence(2))
    }

    @Test
    fun `jitter buffer reorders frames and conceals missing packets`() {
        val jb = JitterBuffer(targetDelayFrames = 2, maxBufferedFrames = 8)
        jb.push(1, ShortArray(320) { 100 })
        assertNull(jb.popNextFrame()) // still priming

        jb.push(3, ShortArray(320) { 300 }) // seq 2 missing!
        val f1 = jb.popNextFrame()
        assertNotNull(f1)
        assertEquals(100.toShort(), f1!![0])

        val concealed = jb.popNextFrame() // PLC for seq 2
        assertNotNull(concealed)
        assertEquals(50.toShort(), concealed!![0])

        val f3 = jb.popNextFrame()
        assertNotNull(f3)
        assertEquals(300.toShort(), f3!![0])
    }

    @Test
    fun `audio packet wire serialization roundtrips cleanly`() {
        val pkt = AudioPacket(
            codecType = AudioCodecType.OPUS_16K,
            sequenceNumber = 42,
            timestampMs = 123456789L,
            frameDurationMs = 20,
            encodedPayload = byteArrayOf(10, 20, 30, 40)
        )
        val restored = AudioPacket.fromWireBytes(pkt.toWireBytes())
        assertEquals(pkt, restored)
    }
}
