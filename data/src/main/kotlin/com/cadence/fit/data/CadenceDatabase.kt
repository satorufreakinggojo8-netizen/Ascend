package com.cadence.fit.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Local-first storage. Workout history lives here and nothing is sent anywhere:
 * the app has no network code at all.
 *
 * Schema history:
 * - v1: routines, routine slots, sessions, logged sets.
 * - v2: the training calendar (`schedule`).
 *
 * Upgrades are additive so an existing user's history is never dropped.
 */
class CadenceDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    NAME,
    null,
    VERSION,
) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        SCHEMA.forEach(db::execSQL)
        SCHEDULE_SCHEMA.forEach(db::execSQL)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Additive migrations only: dropping tables here would erase real training
        // history for anyone who updates the app.
        if (oldVersion < 2) {
            SCHEDULE_SCHEMA.forEach(db::execSQL)
        }
    }

    companion object {
        const val NAME = "cadence.db"
        const val VERSION = 2

        const val TABLE_ROUTINES = "routines"
        const val TABLE_ROUTINE_EXERCISES = "routine_exercises"
        const val TABLE_SESSIONS = "sessions"
        const val TABLE_LOGGED_SETS = "logged_sets"
        const val TABLE_SCHEDULE = "schedule"

        const val COL_ID = "id"

        val TABLES = listOf(
            TABLE_LOGGED_SETS,
            TABLE_SESSIONS,
            TABLE_ROUTINE_EXERCISES,
            TABLE_ROUTINES,
            TABLE_SCHEDULE,
        )

        private val SCHEMA = listOf(
            """
            CREATE TABLE $TABLE_ROUTINES (
                $COL_ID TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                focus TEXT NOT NULL DEFAULT '',
                goal TEXT NOT NULL,
                location TEXT NOT NULL,
                is_starter INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                position INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
            """
            CREATE TABLE $TABLE_ROUTINE_EXERCISES (
                $COL_ID TEXT PRIMARY KEY NOT NULL,
                routine_id TEXT NOT NULL,
                position INTEGER NOT NULL,
                exercise_id TEXT NOT NULL,
                target_sets INTEGER NOT NULL,
                rep_min INTEGER NOT NULL,
                rep_max INTEGER NOT NULL,
                rest_seconds INTEGER NOT NULL,
                warmup_sets INTEGER NOT NULL DEFAULT 0,
                superset_group INTEGER,
                note TEXT NOT NULL DEFAULT '',
                FOREIGN KEY (routine_id) REFERENCES $TABLE_ROUTINES($COL_ID) ON DELETE CASCADE
            )
            """.trimIndent(),
            "CREATE INDEX idx_routine_exercises_routine ON $TABLE_ROUTINE_EXERCISES(routine_id, position)",
            """
            CREATE TABLE $TABLE_SESSIONS (
                $COL_ID TEXT PRIMARY KEY NOT NULL,
                routine_id TEXT,
                routine_name TEXT NOT NULL,
                started_at INTEGER NOT NULL,
                ended_at INTEGER,
                note TEXT NOT NULL DEFAULT '',
                location TEXT NOT NULL
            )
            """.trimIndent(),
            "CREATE INDEX idx_sessions_ended ON $TABLE_SESSIONS(ended_at)",
            """
            CREATE TABLE $TABLE_LOGGED_SETS (
                $COL_ID TEXT PRIMARY KEY NOT NULL,
                session_id TEXT NOT NULL,
                exercise_id TEXT NOT NULL,
                set_index INTEGER NOT NULL,
                weight_kg REAL NOT NULL,
                reps INTEGER NOT NULL,
                rpe REAL,
                is_warmup INTEGER NOT NULL DEFAULT 0,
                completed_at INTEGER NOT NULL,
                superset_group INTEGER,
                note TEXT NOT NULL DEFAULT '',
                FOREIGN KEY (session_id) REFERENCES $TABLE_SESSIONS($COL_ID) ON DELETE CASCADE
            )
            """.trimIndent(),
            "CREATE INDEX idx_logged_sets_session ON $TABLE_LOGGED_SETS(session_id, set_index)",
            "CREATE INDEX idx_logged_sets_exercise ON $TABLE_LOGGED_SETS(exercise_id)",
        )

        /** Added in schema v2; also applied by the v1 to v2 upgrade. */
        private val SCHEDULE_SCHEMA = listOf(
            """
            CREATE TABLE $TABLE_SCHEDULE (
                $COL_ID TEXT PRIMARY KEY NOT NULL,
                date_epoch_day INTEGER NOT NULL,
                routine_id TEXT,
                routine_name TEXT NOT NULL DEFAULT '',
                status TEXT NOT NULL DEFAULT 'PLANNED',
                session_id TEXT,
                completed_at INTEGER
            )
            """.trimIndent(),
            "CREATE INDEX idx_schedule_date ON $TABLE_SCHEDULE(date_epoch_day)",
        )
    }
}
