package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateTimeUtils {
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    private val fullDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    fun formatMessageTime(timestampMs: Long): String = timeFormat.format(Date(timestampMs))

    fun formatConversationTime(timestampMs: Long): String {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = timestampMs }
        return if (
            now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
        ) {
            timeFormat.format(Date(timestampMs))
        } else if (now.get(Calendar.YEAR) == target.get(Calendar.YEAR)) {
            dateTimeFormat.format(Date(timestampMs))
        } else {
            fullDateFormat.format(Date(timestampMs))
        }
    }

    fun formatCallDuration(durationSeconds: Long): String {
        val hours = durationSeconds / 3600
        val minutes = (durationSeconds % 3600) / 60
        val seconds = durationSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }
}
