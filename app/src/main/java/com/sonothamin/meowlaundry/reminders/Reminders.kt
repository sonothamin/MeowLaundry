package com.sonothamin.meowlaundry.reminders

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sonothamin.meowlaundry.MainActivity
import com.sonothamin.meowlaundry.MeowLaundryApp
import com.sonothamin.meowlaundry.R
import com.sonothamin.meowlaundry.data.DueDates
import com.sonothamin.meowlaundry.data.DueState
import com.sonothamin.meowlaundry.data.LaundryTicket
import com.sonothamin.meowlaundry.ui.components.serviceLabel
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * "Laundry is due" reminders. Once a day, around the chosen hour, a background check looks at every
 * ticket that still has garments out and a due date, and posts a notification for the ones due
 * tomorrow, due today, or overdue (overdue ones repeat daily until they're sorted).
 */
object Reminders {
    const val EXTRA_TICKET_ID = "open_ticket_id"

    private const val CHANNEL_ID = "laundry_due"
    private const val UNIQUE_WORK = "laundry_due_check"
    private const val TEST_NOTIFICATION_ID = -1

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Laundry reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Tickets that are due back or overdue"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Applies the saved settings: schedules the daily check, or cancels it when reminders are off. */
    suspend fun sync(app: MeowLaundryApp) {
        if (app.appPreferences.remindersEnabled.first()) {
            schedule(app, app.appPreferences.reminderHour.first(), app.appPreferences.reminderMinute.first(), replace = false)
        } else {
            cancel(app)
        }
    }

    /** [replace] restarts the schedule (needed when the time changes); otherwise an existing one is kept. */
    fun schedule(context: Context, hour: Int, minute: Int, replace: Boolean) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(hour, minute)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK,
            if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
    }

    /** One pass of the daily check. Safe to call more than once a day: it only notifies once per day. */
    suspend fun runCheck(app: MeowLaundryApp) {
        val prefs = app.appPreferences
        if (!prefs.remindersEnabled.first()) return
        val today = DueDates.today()
        if (prefs.lastReminderDay.first() == today.toEpochDay()) return

        app.repository.getOpenTicketsWithDueDate().forEach { ticket ->
            val info = DueDates.info(ticket, today) ?: return@forEach
            if (info.state == DueState.LATER) return@forEach
            if (info.state == DueState.SOON && info.daysUntil > 1) return@forEach // only "tomorrow"
            val stillOut = app.repository.getItemsForTicket(ticket.id).count { !it.returned && !it.lost }
            val title = when (info.state) {
                DueState.OVERDUE -> "Ticket #${ticket.id} is overdue"
                DueState.TODAY -> "Ticket #${ticket.id} is due back today"
                else -> "Ticket #${ticket.id} is due back tomorrow"
            }
            notify(app, ticket.id.toInt(), title, describe(ticket, stillOut, info.label), ticket.id)
        }
        prefs.setLastReminderDay(today.toEpochDay())
    }

    /** A sample notification so the person can check that reminders reach them. */
    fun postTest(context: Context) {
        notify(context, TEST_NOTIFICATION_ID, "Reminders are on", "You'll get a note like this when laundry is due back.", null)
    }

    private fun describe(ticket: LaundryTicket, stillOut: Int, dueLabel: String): String {
        val parts = listOfNotNull(
            serviceLabel(ticket.serviceType),
            ticket.providerName?.takeIf { it.isNotBlank() },
            "$stillOut ${if (stillOut == 1) "garment" else "garments"} still out",
            dueLabel.takeIf { it.startsWith("Overdue") },
        )
        return parts.joinToString(" · ")
    }

    @SuppressLint("MissingPermission") // checked via areNotificationsEnabled(); the permission is requested in the UI
    private fun notify(context: Context, id: Int, title: String, text: String, ticketId: Long?) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (ticketId != null) putExtra(EXTRA_TICKET_ID, ticketId)
        }
        val pending = PendingIntent.getActivity(
            context, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(id, notification) }
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? MeowLaundryApp ?: return Result.success()
        Reminders.runCheck(app)
        return Result.success()
    }
}
