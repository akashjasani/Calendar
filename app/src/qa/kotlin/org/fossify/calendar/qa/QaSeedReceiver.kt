package org.fossify.calendar.qa

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.fossify.calendar.R
import org.fossify.calendar.extensions.calendarsDB
import org.fossify.calendar.extensions.config
import org.fossify.calendar.extensions.eventsDB
import org.fossify.calendar.extensions.eventsHelper
import org.fossify.calendar.extensions.seconds
import org.fossify.calendar.extensions.updateWidgets
import org.fossify.calendar.helpers.DAY
import org.fossify.calendar.helpers.FLAG_ALL_DAY
import org.fossify.calendar.helpers.LOCAL_CALENDAR_ID
import org.fossify.calendar.helpers.MONTHLY_VIEW
import org.fossify.calendar.helpers.TYPE_TASK
import org.fossify.calendar.models.CalendarEntity
import org.fossify.calendar.models.Event
import org.fossify.commons.extensions.getProperPrimaryColor
import org.joda.time.DateTime
import java.util.TimeZone

/**
 * mqa QA seeds (skill qa-seeds). Debug and uat only: this class and its manifest entry live in src/qa.
 *
 * `adb shell am broadcast -a org.fossify.calendar.QA_SEED -p <package> --es scenario calendar-mixed`
 *
 * Every scenario first removes all local events, tasks and extra local calendars (synced CalDAV calendars are
 * left alone), resets the default calendar and the start view, then writes its data. So loading it twice gives
 * the same state as loading it once.
 *
 * Dates: the calendar always opens on today, so the data is placed at fixed clock times on fixed day offsets
 * from today (local time). Texts, colours, times and offsets never change.
 */
class QaSeedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val scenario = intent.getStringExtra("scenario").orEmpty()
        val pending = goAsync()
        Thread {
            try {
                val loaded = load(context.applicationContext, scenario)
                pending.resultCode = if (loaded) Activity.RESULT_OK else Activity.RESULT_CANCELED
                pending.resultData = if (loaded) "seeded $scenario" else "unknown scenario $scenario"
            } catch (e: Exception) {
                pending.resultCode = Activity.RESULT_CANCELED
                pending.resultData = "seed $scenario failed: ${e.javaClass.simpleName}"
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun load(context: Context, scenario: String): Boolean {
        val write: ((Context) -> Unit) = when (scenario) {
            "calendar-mixed" -> ::writeMixed
            "calendar-empty" -> { _ -> }
            "calendar-long" -> ::writeLong
            else -> return false
        }
        clear(context)
        write(context)
        context.updateWidgets()
        return true
    }

    private fun clear(context: Context) {
        val helper = context.eventsHelper
        val local = helper.getCalendarsSync().filter { !it.isSyncedCalendar() }
        val localIds = local.mapNotNull { it.id }
        val eventIds = context.eventsDB.getEventAndTasksIdsByCalendar(localIds).toMutableList()
        helper.deleteEvents(eventIds, deleteFromCalDAV = false, updateWidgets = false)
        helper.deleteCalendars(ArrayList(local.filter { it.id != LOCAL_CALENDAR_ID }), deleteEvents = true)
        context.calendarsDB.insertOrUpdate(
            CalendarEntity(
                id = LOCAL_CALENDAR_ID,
                title = context.getString(R.string.regular_event),
                color = context.getProperPrimaryColor() // as a fresh install creates it
            )
        )
        context.config.apply {
            addDisplayCalendar(LOCAL_CALENDAR_ID.toString())
            storedView = MONTHLY_VIEW
        }
    }

    private fun writeMixed(context: Context) {
        val helper = context.eventsHelper
        val work = helper.insertOrUpdateCalendarSync(CalendarEntity(id = null, title = "Work", color = WORK_COLOR))
        val today = DateTime().withTimeAtStartOfDay()

        insert(context, event("Team standup", today.withHourOfDay(9), minutes = 15, calendarId = work).apply {
            repeatInterval = DAY
            description = "Daily sync of the team."
        })
        insert(context, event("Dentist appointment", today.withHourOfDay(14), minutes = 60).apply {
            location = "Main Street 1"
        })
        insert(context, event("Project review", today.plusDays(2).withHourOfDay(11), minutes = 90, calendarId = work))
        insert(context, event("Conference", today.plusDays(1), minutes = 0, calendarId = work).apply {
            flags = FLAG_ALL_DAY
            startTS = today.plusDays(1).seconds()
            endTS = today.plusDays(1).withHourOfDay(12).seconds()
        })

        val task = event("Buy groceries", today.withHourOfDay(18), minutes = 0).apply { type = TYPE_TASK }
        helper.insertTask(task, showToasts = false) {}
    }

    private fun writeLong(context: Context) {
        val today = DateTime().withTimeAtStartOfDay()
        for (day in 0 until LONG_COUNT) {
            insert(context, event("Seed event %02d".format(day + 1), today.plusDays(day).withHourOfDay(10), minutes = 30))
        }
    }

    private fun event(title: String, start: DateTime, minutes: Int, calendarId: Long = LOCAL_CALENDAR_ID) = Event(
        id = null,
        startTS = start.seconds(),
        endTS = start.plusMinutes(minutes).seconds(),
        title = title,
        timeZone = TimeZone.getDefault().id,
        calendarId = calendarId,
    )

    private fun insert(context: Context, event: Event) {
        context.eventsHelper.insertEvent(event, addToCalDAV = false, showToasts = false, updateWidgets = false)
    }

    private companion object {
        const val WORK_COLOR = 0xFF2E7D32.toInt()
        const val LONG_COUNT = 60
    }
}
