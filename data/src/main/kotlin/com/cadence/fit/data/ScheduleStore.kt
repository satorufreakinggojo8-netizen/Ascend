package com.cadence.fit.data

import android.content.ContentValues
import com.cadence.fit.data.CadenceDatabase.Companion.COL_ID
import com.cadence.fit.data.CadenceDatabase.Companion.TABLE_SCHEDULE
import com.cadence.fit.domain.model.ScheduleStatus
import com.cadence.fit.domain.model.ScheduledWorkout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Persistence for the training calendar.
 *
 * Dates are stored as epoch days, which keeps them independent of the device
 * timezone: a workout planned for the 13th is still the 13th after travelling.
 */
class ScheduleStore(private val database: CadenceDatabase) {

    suspend fun loadSlots(): List<ScheduledWorkout> = withContext(Dispatchers.IO) {
        val slots = mutableListOf<ScheduledWorkout>()
        database.readableDatabase.query(
            TABLE_SCHEDULE,
            null,
            null,
            null,
            null,
            null,
            "date_epoch_day ASC, id ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                slots += ScheduledWorkout(
                    id = cursor.text(COL_ID),
                    date = LocalDate.ofEpochDay(cursor.long("date_epoch_day")),
                    routineId = cursor.textOrNull("routine_id"),
                    routineName = cursor.text("routine_name"),
                    status = enumValueOrNull<ScheduleStatus>(cursor.text("status"))
                        ?: ScheduleStatus.PLANNED,
                    sessionId = cursor.textOrNull("session_id"),
                    completedAtMs = cursor.longOrNull("completed_at"),
                )
            }
        }
        slots
    }

    suspend fun upsert(slot: ScheduledWorkout) = withContext(Dispatchers.IO) {
        database.writableDatabase.insertWithOnConflict(
            TABLE_SCHEDULE,
            null,
            slot.toValues(),
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
        )
        Unit
    }

    /** Writes several changed days at once, in one transaction. */
    suspend fun upsertAll(slots: List<ScheduledWorkout>) = withContext(Dispatchers.IO) {
        database.writableDatabase.inTransaction {
            slots.forEach { slot ->
                insertWithOnConflict(
                    TABLE_SCHEDULE,
                    null,
                    slot.toValues(),
                    android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
        }
        Unit
    }

    suspend fun deleteSlot(id: String) = withContext(Dispatchers.IO) {
        database.writableDatabase.delete(TABLE_SCHEDULE, "$COL_ID = ?", arrayOf(id))
        Unit
    }

    /** Removes every planned day, keeping completed ones so history stays linked. */
    suspend fun deletePlannedSlots() = withContext(Dispatchers.IO) {
        database.writableDatabase.delete(
            TABLE_SCHEDULE,
            "status != ?",
            arrayOf(ScheduleStatus.COMPLETED.name),
        )
        Unit
    }

    suspend fun deleteAllSlots() = withContext(Dispatchers.IO) {
        database.writableDatabase.delete(TABLE_SCHEDULE, null, null)
        Unit
    }

    private fun ScheduledWorkout.toValues(): ContentValues = ContentValues().apply {
        put(COL_ID, id)
        put("date_epoch_day", date.toEpochDay())
        put("routine_id", routineId)
        put("routine_name", routineName)
        put("status", status.name)
        put("session_id", sessionId)
        put("completed_at", completedAtMs)
    }
}
