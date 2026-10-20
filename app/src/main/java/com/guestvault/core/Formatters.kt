package com.guestvault.core

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Small formatting helpers shared by the owner and guest screens. */
object Formatters {

    fun duration(millis: Long): String {
        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(millis).coerceAtLeast(0)
        return when {
            totalMinutes < 1 -> "Less than a minute"
            totalMinutes < 60 -> "$totalMinutes min"
            else -> {
                val hours = totalMinutes / 60
                val minutes = totalMinutes % 60
                if (minutes == 0) "$hours h" else "$hours h ${minutes.toString().padStart(2, '0')} min"
            }
        }
    }

    /** mm:ss below one hour, h:mm:ss above. */
    fun countdown(millis: Long): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun sessionTime(timestamp: Long): String =
        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(timestamp))

    fun dayLabel(timestamp: Long): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = timestamp }
        val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
        val dayDiff = now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR)
        return when {
            sameYear && dayDiff == 0 -> "Today"
            sameYear && dayDiff == 1 -> "Yesterday"
            else -> SimpleDateFormat(if (sameYear) "MMM d" else "MMM d, yyyy", Locale.getDefault())
                .format(Date(timestamp))
        }
    }

    fun timeOnly(timestamp: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

    fun minutesLabel(minutes: Int): String = when {
        minutes <= 0 -> "No limit"
        minutes < 60 -> "$minutes min"
        minutes == 60 -> "1 hour"
        else -> "$minutes min"
    }
}
