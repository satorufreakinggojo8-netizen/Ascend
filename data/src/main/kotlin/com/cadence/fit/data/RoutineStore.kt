package com.cadence.fit.data

import android.content.ContentValues
import com.cadence.fit.data.CadenceDatabase.Companion.COL_ID
import com.cadence.fit.data.CadenceDatabase.Companion.TABLE_ROUTINES
import com.cadence.fit.data.CadenceDatabase.Companion.TABLE_ROUTINE_EXERCISES
import com.cadence.fit.domain.model.Routine
import com.cadence.fit.domain.model.RoutineExercise
import com.cadence.fit.domain.model.TrainingGoal
import com.cadence.fit.domain.model.TrainingLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Persistence for the user's routines. */
class RoutineStore(private val database: CadenceDatabase) {

    suspend fun loadRoutines(): List<Routine> = withContext(Dispatchers.IO) {
        val db = database.readableDatabase
        val slots = mutableMapOf<String, MutableList<Pair<Int, RoutineExercise>>>()

        db.query(TABLE_ROUTINE_EXERCISES, null, null, null, null, null, "position ASC").use { cursor ->
            while (cursor.moveToNext()) {
                val routineId = cursor.text("routine_id")
                val slot = RoutineExercise(
                    id = cursor.text(COL_ID),
                    exerciseId = cursor.text("exercise_id"),
                    targetSets = cursor.int("target_sets"),
                    repRange = cursor.int("rep_min")..cursor.int("rep_max"),
                    restSeconds = cursor.int("rest_seconds"),
                    warmupSets = cursor.int("warmup_sets"),
                    supersetGroup = cursor.intOrNull("superset_group"),
                    note = cursor.text("note"),
                )
                slots.getOrPut(routineId) { mutableListOf() } += cursor.int("position") to slot
            }
        }

        val routines = mutableListOf<Routine>()
        db.query(TABLE_ROUTINES, null, null, null, null, null, "position ASC").use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.text(COL_ID)
                routines += Routine(
                    id = id,
                    name = cursor.text("name"),
                    focus = cursor.text("focus"),
                    goal = enumValueOrNull<TrainingGoal>(cursor.text("goal")) ?: TrainingGoal.GENERAL_FITNESS,
                    location = enumValueOrNull<TrainingLocation>(cursor.text("location"))
                        ?: TrainingLocation.GYM,
                    exercises = slots[id].orEmpty().sortedBy { it.first }.map { it.second },
                    isStarterPlan = cursor.bool("is_starter"),
                    createdAtMs = cursor.long("created_at"),
                )
            }
        }
        routines
    }

    suspend fun upsertRoutine(routine: Routine, position: Int = 0) = withContext(Dispatchers.IO) {
        database.writableDatabase.inTransaction {
            delete(TABLE_ROUTINE_EXERCISES, "routine_id = ?", arrayOf(routine.id))
            delete(TABLE_ROUTINES, "$COL_ID = ?", arrayOf(routine.id))
            insertOrThrow(TABLE_ROUTINES, null, routine.toValues(position))
            routine.exercises.forEachIndexed { index, slot ->
                insertOrThrow(TABLE_ROUTINE_EXERCISES, null, slot.toValues(routine.id, index))
            }
        }
        Unit
    }

    suspend fun saveRoutineOrder(routines: List<Routine>) = withContext(Dispatchers.IO) {
        database.writableDatabase.inTransaction {
            routines.forEachIndexed { index, routine ->
                val values = ContentValues().apply { put("position", index) }
                update(TABLE_ROUTINES, values, "$COL_ID = ?", arrayOf(routine.id))
            }
        }
        Unit
    }

    suspend fun deleteRoutine(id: String) = withContext(Dispatchers.IO) {
        database.writableDatabase.inTransaction {
            delete(TABLE_ROUTINE_EXERCISES, "routine_id = ?", arrayOf(id))
            delete(TABLE_ROUTINES, "$COL_ID = ?", arrayOf(id))
        }
        Unit
    }

    suspend fun deleteAllRoutines() = withContext(Dispatchers.IO) {
        database.writableDatabase.inTransaction {
            delete(TABLE_ROUTINE_EXERCISES, null, null)
            delete(TABLE_ROUTINES, null, null)
        }
        Unit
    }

    private fun Routine.toValues(position: Int): ContentValues = ContentValues().apply {
        put(COL_ID, id)
        put("name", name)
        put("focus", focus)
        put("goal", goal.name)
        put("location", location.name)
        put("is_starter", if (isStarterPlan) 1 else 0)
        put("created_at", createdAtMs)
        put("position", position)
    }

    private fun RoutineExercise.toValues(routineId: String, position: Int): ContentValues =
        ContentValues().apply {
            put(COL_ID, id)
            put("routine_id", routineId)
            put("position", position)
            put("exercise_id", exerciseId)
            put("target_sets", targetSets)
            put("rep_min", repRange.first)
            put("rep_max", repRange.last)
            put("rest_seconds", restSeconds)
            put("warmup_sets", warmupSets)
            put("superset_group", supersetGroup)
            put("note", note)
        }
}
