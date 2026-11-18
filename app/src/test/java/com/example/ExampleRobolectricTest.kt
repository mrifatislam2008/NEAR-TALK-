package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.NearTalkDatabase
import com.example.data.local.entities.CallHistoryEntity
import com.example.data.local.entities.DeviceEntity
import com.example.messaging.Message
import com.example.messaging.MessageRepository
import com.example.messaging.MessageStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var database: NearTalkDatabase
    private lateinit var messageRepository: MessageRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NearTalkDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        messageRepository = MessageRepository(database.messageDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `app name matches NEAR TALK`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("NEAR TALK", appName)
    }

    @Test
    fun `message deduplication and monotonic delivery status progression`() = runTest {
        val msg = Message(
            id = "msg-1",
            conversationId = "peer-a",
            senderId = "local",
            receiverId = "peer-a",
            content = "Hello offline",
            timestamp = 1000L,
            status = MessageStatus.PENDING,
            sequenceNumber = 1,
            isIncoming = false,
            isReadLocally = true
        )

        assertTrue(messageRepository.saveMessageIfNew(msg))
        assertFalse(messageRepository.saveMessageIfNew(msg)) // deduplicated

        messageRepository.updateStatus("msg-1", MessageStatus.SENT)
        messageRepository.updateStatus("msg-1", MessageStatus.DELIVERED)
        messageRepository.updateStatus("msg-1", MessageStatus.READ)
        // Attempted downgrade should be ignored
        messageRepository.updateStatus("msg-1", MessageStatus.DELIVERED)

        val stored = messageRepository.observeMessages("peer-a").first()
        assertEquals(1, stored.size)
        assertEquals(MessageStatus.READ, stored.first().status)
    }

    @Test
    fun `device trust block and call history persist cleanly`() = runTest {
        database.deviceDao().upsertDevice(
            DeviceEntity(
                id = "peer-1",
                displayName = "Pixel Peer",
                deviceNickname = "Pixel 9",
                isTrusted = true
            )
        )
        database.callHistoryDao().insertCallRecord(
            CallHistoryEntity(
                id = "call-1",
                remoteDeviceId = "peer-1",
                remoteDisplayName = "Pixel Peer",
                callType = "OUTGOING",
                startTime = 1000L,
                endTime = 6000L,
                duration = 5L,
                callStatus = "COMPLETED"
            )
        )

        val trusted = database.deviceDao().observeTrustedDevices().first()
        val calls = database.callHistoryDao().observeAllCalls().first()
        assertEquals(1, trusted.size)
        assertEquals(1, calls.size)
        assertEquals(5L, calls.first().duration)
    }
}
