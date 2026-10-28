package com.cadence.fit.domain.backup

import com.cadence.fit.domain.json.Json
import com.cadence.fit.domain.json.JsonParseException
import com.cadence.fit.domain.json.JsonValue
import com.cadence.fit.domain.model.AppSettings
import com.cadence.fit.domain.model.Difficulty
import com.cadence.fit.domain.model.Equipment
import com.cadence.fit.domain.model.ExperienceLevel
import com.cadence.fit.domain.model.LoggedSet
import com.cadence.fit.domain.model.Routine
import com.cadence.fit.domain.model.RoutineExercise
import com.cadence.fit.domain.model.TrainingGoal
import com.cadence.fit.domain.model.TrainingLocation
import com.cadence.fit.domain.model.TrainingProfile
import com.cadence.fit.domain.model.WorkoutSession
import com.cadence.fit.domain.units.WeightUnit

class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Everything Cadence stores locally, in one portable document.
 *
 * Sleep sessions are deliberately absent: they are read from Health Connect on
 * demand and are never copied into the app or into an export.
 */
data class BackupDocument(
    val formatVersion: Int = BackupCodec.CURRENT_FORMAT_VERSION,
    val exportedAtMs: Long,
    val appVersion: String,
    val profile: TrainingProfile,
    val settings: AppSettings,
    val routines: List<Routine>,
    val sessions: List<WorkoutSession>,
)

object BackupCodec {

    const val CURRENT_FORMAT_VERSION = 1
    const val FORMAT_TAG = "cadence-backup"
    const val FILE_PREFIX = "cadence-backup"

    const val NOTE: String =
        "This file contains your Cadence routines, workout history and preferences. " +
            "It contains no sleep data, no account details and no identifiers from other apps."

    fun encode(document: BackupDocument, pretty: Boolean = true): String =
        Json.write(toJson(document), pretty)

    fun decode(text: String): BackupDocument {
        val root = try {
            Json.parse(text)
        } catch (parseError: JsonParseException) {
            throw BackupException("This file is not valid JSON.", parseError)
        }
        val rootObject = root as? JsonValue.Obj
            ?: throw BackupException("This file does not contain a Cadence backup.")
        val tag = rootObject.string("format")
        if (tag != FORMAT_TAG) {
            throw BackupException("This file is not a Cadence backup (expected format \"$FORMAT_TAG\").")
        }
        val version = rootObject.int("formatVersion") ?: 0
        if (version > CURRENT_FORMAT_VERSION) {
            throw BackupException(
                "This backup was written by a newer version of Cadence (format $version).",
            )
        }

        return BackupDocument(
            formatVersion = version,
            exportedAtMs = rootObject.long("exportedAtMs") ?: 0L,
            appVersion = rootObject.string("appVersion").orEmpty(),
            profile = readProfile(rootObject.obj("profile")),
            settings = readSettings(rootObject.obj("settings")),
            routines = rootObject.array("routines")?.objects.orEmpty().map { readRoutine(it) },
            sessions = rootObject.array("sessions")?.objects.orEmpty().map { readSession(it) },
        )
    }

    fun decodeOrNull(text: String): BackupDocument? = try {
        decode(text)
    } catch (error: BackupException) {
        null
    }

    fun suggestedFileName(exportedAtMs: Long): String = "$FILE_PREFIX-$exportedAtMs.json"

    private fun toJson(document: BackupDocument): JsonValue.Obj = JsonValue.ofObj(
        "format" to JsonValue.of(FORMAT_TAG),
        "formatVersion" to JsonValue.of(document.formatVersion),
        "exportedAtMs" to JsonValue.of(document.exportedAtMs),
        "appVersion" to JsonValue.of(document.appVersion),
        "note" to JsonValue.of(NOTE),
        "profile" to profileJson(document.profile),
        "settings" to settingsJson(document.settings),
        "routines" to JsonValue.ofArray(document.routines.map { routineJson(it) }),
        "sessions" to JsonValue.ofArray(document.sessions.map { sessionJson(it) }),
    )

    private fun profileJson(profile: TrainingProfile): JsonValue.Obj = JsonValue.ofObj(
        "onboardingComplete" to JsonValue.of(profile.onboardingComplete),
        "location" to JsonValue.of(profile.location.name),
        "equipment" to JsonValue.ofStrings(profile.equipment.map { it.name }.sorted()),
        "experience" to JsonValue.of(profile.experience.name),
        "goal" to JsonValue.of(profile.goal.name),
        "daysPerWeek" to JsonValue.of(profile.daysPerWeek),
        "sessionMinutes" to JsonValue.of(profile.sessionMinutes),
    )

    private fun settingsJson(settings: AppSettings): JsonValue.Obj = JsonValue.ofObj(
        "weightUnit" to JsonValue.of(settings.weightUnit.name),
        "showProgressLevel" to JsonValue.of(settings.showProgressLevel),
        "sleepInsightsEnabled" to JsonValue.of(settings.sleepInsightsEnabled),
        "restTimerSoundEnabled" to JsonValue.of(settings.restTimerSoundEnabled),
        "keepScreenOnDuringWorkout" to JsonValue.of(settings.keepScreenOnDuringWorkout),
    )

    private fun routineJson(routine: Routine): JsonValue.Obj = JsonValue.ofObj(
        "id" to JsonValue.of(routine.id),
        "name" to JsonValue.of(routine.name),
        "focus" to JsonValue.of(routine.focus),
        "goal" to JsonValue.of(routine.goal.name),
        "location" to JsonValue.of(routine.location.name),
        "isStarterPlan" to JsonValue.of(routine.isStarterPlan),
        "createdAtMs" to JsonValue.of(routine.createdAtMs),
        "exercises" to JsonValue.ofArray(routine.exercises.map { routineExerciseJson(it) }),
    )

    private fun routineExerciseJson(item: RoutineExercise): JsonValue.Obj = JsonValue.ofObj(
        "id" to JsonValue.of(item.id),
        "exerciseId" to JsonValue.of(item.exerciseId),
        "targetSets" to JsonValue.of(item.targetSets),
        "repMin" to JsonValue.of(item.repRange.first),
        "repMax" to JsonValue.of(item.repRange.last),
        "restSeconds" to JsonValue.of(item.restSeconds),
        "warmupSets" to JsonValue.of(item.warmupSets),
        "supersetGroup" to item.supersetGroup?.let { JsonValue.of(it) },
        "note" to JsonValue.of(item.note),
    )

    private fun sessionJson(session: WorkoutSession): JsonValue.Obj = JsonValue.ofObj(
        "id" to JsonValue.of(session.id),
        "routineId" to session.routineId?.let { JsonValue.of(it) },
        "routineName" to JsonValue.of(session.routineName),
        "startedAtMs" to JsonValue.of(session.startedAtMs),
        "endedAtMs" to session.endedAtMs?.let { JsonValue.of(it) },
        "note" to JsonValue.of(session.note),
        "location" to JsonValue.of(session.location.name),
        "sets" to JsonValue.ofArray(session.sets.map { setJson(it) }),
    )

    private fun setJson(set: LoggedSet): JsonValue.Obj = JsonValue.ofObj(
        "id" to JsonValue.of(set.id),
        "exerciseId" to JsonValue.of(set.exerciseId),
        "setIndex" to JsonValue.of(set.setIndex),
        "weightKg" to JsonValue.of(set.weightKg),
        "reps" to JsonValue.of(set.reps),
        "rpe" to set.rpe?.let { JsonValue.of(it) },
        "isWarmup" to JsonValue.of(set.isWarmup),
        "completedAtMs" to JsonValue.of(set.completedAtMs),
        "supersetGroup" to set.supersetGroup?.let { JsonValue.of(it) },
        "note" to JsonValue.of(set.note),
    )

    private fun readProfile(json: JsonValue.Obj?): TrainingProfile {
        if (json == null) return TrainingProfile()
        return TrainingProfile(
            onboardingComplete = json.bool("onboardingComplete") ?: false,
            location = enumOrNull<TrainingLocation>(json.string("location")) ?: TrainingLocation.GYM,
            equipment = json.array("equipment")?.strings.orEmpty()
                .mapNotNull { enumOrNull<Equipment>(it) }
                .toSet(),
            experience = enumOrNull<ExperienceLevel>(json.string("experience")) ?: ExperienceLevel.BEGINNER,
            goal = enumOrNull<TrainingGoal>(json.string("goal")) ?: TrainingGoal.GENERAL_FITNESS,
            daysPerWeek = json.int("daysPerWeek") ?: 3,
            sessionMinutes = json.int("sessionMinutes") ?: 45,
        )
    }

    private fun readSettings(json: JsonValue.Obj?): AppSettings {
        if (json == null) return AppSettings()
        return AppSettings(
            weightUnit = enumOrNull<WeightUnit>(json.string("weightUnit")) ?: WeightUnit.default,
            showProgressLevel = json.bool("showProgressLevel") ?: true,
            sleepInsightsEnabled = json.bool("sleepInsightsEnabled") ?: false,
            restTimerSoundEnabled = json.bool("restTimerSoundEnabled") ?: true,
            keepScreenOnDuringWorkout = json.bool("keepScreenOnDuringWorkout") ?: true,
        )
    }

    private fun readRoutine(json: JsonValue.Obj): Routine = Routine(
        id = json.string("id").orEmpty().ifEmpty { "routine-${json.long("createdAtMs") ?: 0L}" },
        name = json.string("name") ?: "Routine",
        focus = json.string("focus") ?: "",
        goal = enumOrNull<TrainingGoal>(json.string("goal")) ?: TrainingGoal.GENERAL_FITNESS,
        location = enumOrNull<TrainingLocation>(json.string("location")) ?: TrainingLocation.GYM,
        exercises = json.array("exercises")?.objects.orEmpty().mapIndexed { index, item ->
            readRoutineExercise(item, index)
        },
        isStarterPlan = json.bool("isStarterPlan") ?: false,
        createdAtMs = json.long("createdAtMs") ?: 0L,
    )

    private fun readRoutineExercise(json: JsonValue.Obj, index: Int): RoutineExercise {
        val min = json.int("repMin") ?: 8
        val max = json.int("repMax") ?: 12
        return RoutineExercise(
            id = json.string("id").orEmpty().ifEmpty { "slot-$index" },
            exerciseId = json.string("exerciseId").orEmpty(),
            targetSets = json.int("targetSets") ?: 3,
            repRange = if (max >= min) min..max else min..min,
            restSeconds = json.int("restSeconds") ?: 90,
            warmupSets = json.int("warmupSets") ?: 0,
            supersetGroup = json.int("supersetGroup"),
            note = json.string("note").orEmpty(),
        )
    }

    private fun readSession(json: JsonValue.Obj): WorkoutSession {
        val id = json.string("id").orEmpty().ifEmpty { "session-${json.long("startedAtMs") ?: 0L}" }
        return WorkoutSession(
            id = id,
            routineId = json.string("routineId"),
            routineName = json.string("routineName") ?: "Workout",
            startedAtMs = json.long("startedAtMs") ?: 0L,
            endedAtMs = json.long("endedAtMs"),
            sets = json.array("sets")?.objects.orEmpty().mapIndexed { index, item ->
                readSet(item, id, index)
            },
            note = json.string("note").orEmpty(),
            location = enumOrNull<TrainingLocation>(json.string("location")) ?: TrainingLocation.GYM,
        )
    }

    private fun readSet(json: JsonValue.Obj, sessionId: String, index: Int): LoggedSet = LoggedSet(
        id = json.string("id").orEmpty().ifEmpty { "$sessionId-set-$index" },
        sessionId = sessionId,
        exerciseId = json.string("exerciseId").orEmpty(),
        setIndex = json.int("setIndex") ?: (index + 1),
        weightKg = json.double("weightKg") ?: 0.0,
        reps = json.int("reps") ?: 0,
        rpe = json.double("rpe"),
        isWarmup = json.bool("isWarmup") ?: false,
        completedAtMs = json.long("completedAtMs") ?: 0L,
        supersetGroup = json.int("supersetGroup"),
        note = json.string("note").orEmpty(),
    )

    private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? {
        if (name == null) return null
        return enumValues<T>().firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

/** Legacy difficulty names are accepted even though difficulty is not exported yet. */
internal fun difficultyFromTag(tag: String?): Difficulty? =
    Difficulty.entries.firstOrNull { it.name.equals(tag, ignoreCase = true) }
