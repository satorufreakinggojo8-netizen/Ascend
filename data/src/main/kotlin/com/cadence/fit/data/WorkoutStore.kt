package com.cadence.fit.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.cadence.fit.data.CadenceDatabase.Companion.COL_ID
import com.cadence.fit.data.CadenceDatabase.Companion.TABLE_LOGGED_SETS
import com.cadence.fit.data.CadenceDatabase.Companion.TABLE_SESSIONS
import com.cadence.fit.domain.model.LoggedSet
import com.cadence.fit.domain.model.TrainingLocation
import com.cadence.fit.domain.model.WorkoutSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Persistence for workout history. Every session is written with its sets atomically. */
class WorkoutStore(private val database: CadenceDatabase) {

    suspend fun loadSessions(): List<WorkoutSession> = withContext(Dispatchers.IO) {
        val db = database.readableDatabase
        val setsBySession = loadSetsBySession(db)
        val sessions = mutableListOf<WorkoutSession>()

        db.query(TABLE_SESSIONS, null, null, null, null, null, "started_at DESC").use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.text(COL_ID)
                sessions += WorkoutSession(
                    id = id,
                    routineId = cursor.textOrNull("routine_id"),
                    routineName = cursor.text("routine_name"),
                    startedAtMs = cursor.long("started_at"),
                    endedAtMs = cursor.longOrNull("ended_at"),
                    sets = setsBySession[id].orEmpty(),
                    note = cursor.text("note"),
                    location = enumValueOrNull<TrainingLocation>(cursor.text("location"))
                        ?: TrainingLocation.GYM,
                )
            }
        }
        sessions
    }

    suspend fun loadSession(id: String): WorkoutSession? = withContext(Dispatchers.IO) {
        val db = database.readableDatabase
        db.query(TABLE_SESSIONS, null, "$COL_ID = ?", arrayOf(id), null, null, null).use { cursor ->
            if (!cursor.moveToFirst()) return@withContext null
            WorkoutSession(
                id = cursor.text(COL_ID),
                routineId = cursor.textOrNull("routine_id"),
                routineName = cursor.text("routine_name"),
                startedAtMs = cursor.long("started_at"),
                endedAtMs = cursor.longOrNull("ended_at"),
                sets = loadSets(db, id),
                note = cursor.text("note"),
                location = enumValueOrNull<TrainingLocation>(cursor.text("location"))
                    ?: TrainingLocation.GYM,
            )
        }
    }

    /** Replaces the session and all of its sets in one transaction. */
    suspend fun upsertSession(session: WorkoutSession) = withContext(Dispatchers.IO) {
        database.writableDatabase.inTransaction {
            delete(TABLE_LOGGED_SETS, "session_id = ?", arrayOf(session.id))
            delete(TABLE_SESSIONS, "$COL_ID = ?", arrayOf(session.id))
            insertOrThrow(TABLE_SESSIONS, null, session.toValues())
            session.sets.forEach { set ->
                insertOrThrow(TABLE_LOGGED_SETS, null, set.toValues(session.id))
            }
        }
        Unit
    }

    suspend fun deleteSession(id: String) = withContext(Dispatchers.IO) {
        database.writableDatabase.inTransaction {
            delete(TABLE_LOGGED_SETS, "session_id = ?", arrayOf(id))
            delete(TABLE_SESSIONS, "$COL_ID = ?", arrayOf(id))
        }
        Unit
    }

    suspend fun deleteAllSessions() = withContext(Dispatchers.IO) {
        database.writableDatabase.inTransaction {
            delete(TABLE_LOGGED_SETS, null, null)
            delete(TABLE_SESSIONS, null, null)
        }
        Unit
    }

    private fun loadSetsBySession(db: SQLiteDatabase): Map<String, List<LoggedSet>> {
        val result = mutableMapOf<String, MutableList<LoggedSet>>()
        db.query(TABLE_LOGGED_SETS, null, null, null, null, null, "set_index ASC").use { cursor ->
            while (cursor.moveToNext()) {
                val set = cursor.toLoggedSet()
                result.getOrPut(set.sessionId) { mutableListOf() } += set
            }
        }
        return result
    }

    private fun loadSets(db: SQLiteDatabase, sessionId: String): List<LoggedSet> {
        val sets = mutableListOf<LoggedSet>()
        db.query(
            TABLE_LOGGED_SETS,
            null,
            "session_id = ?",
            arrayOf(sessionId),
            null,
            null,
            "set_index ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) sets += cursor.toLoggedSet()
        }
        return sets
    }

    private fun android.database.Cursor.toLoggedSet(): LoggedSet = LoggedSet(
        id = text(COL_ID),
        sessionId = text("session_id"),
        exerciseId = text("exercise_id"),
        setIndex = int("set_index"),
        weightKg = double("weight_kg"),
        reps = int("reps"),
        rpe = doubleOrNull("rpe"),
        isWarmup = bool("is_warmup"),
        completedAtMs = long("completed_at"),
        supersetGroup = intOrNull("superset_group"),
        note = text("note"),
    )

    private fun WorkoutSession.toValues(): ContentValues = ContentValues().apply {
        put(COL_ID, id)
        put("routine_id", routineId)
        put("routine_name", routineName)
        put("started_at", startedAtMs)
        put("ended_at", endedAtMs)
        put("note", note)
        put("location", location.name)
    }

    private fun LoggedSet.toValues(sessionId: String): ContentValues = ContentValues().apply {
        put(COL_ID, id)
        put("session_id", sessionId)
        put("exercise_id", exerciseId)
        put("set_index", setIndex)
        put("weight_kg", weightKg)
        put("reps", reps)
        put("rpe", rpe)
        put("is_warmup", if (isWarmup) 1 else 0)
        put("completed_at", completedAtMs)
        put("superset_group", supersetGroup)
        put("note", note)
    }
}
