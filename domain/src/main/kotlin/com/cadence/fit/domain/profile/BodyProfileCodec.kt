package com.cadence.fit.domain.profile

import com.cadence.fit.domain.json.Json
import com.cadence.fit.domain.json.JsonValue
import com.cadence.fit.domain.model.BodyProfile
import com.cadence.fit.domain.model.WeightEntry

/**
 * Local, compact JSON for optional body details.
 *
 * The codec is deliberately forgiving: a corrupt preference falls back to an
 * empty profile instead of crashing the app, and malformed entries are dropped
 * rather than guessed at.
 */
object BodyProfileCodec {

    fun encode(profile: BodyProfile): String {
        val sanitized = profile.sanitized()
        return Json.write(
            JsonValue.ofObj(
                "ageYears" to sanitized.ageYears?.let { JsonValue.of(it) },
                "heightCm" to sanitized.heightCm?.let { JsonValue.of(it) },
                "weights" to JsonValue.ofArray(
                    sanitized.weightEntries.map { entry ->
                        JsonValue.ofObj(
                            "atMs" to JsonValue.of(entry.atMs),
                            "weightKg" to JsonValue.of(entry.weightKg),
                        )
                    },
                ),
            ),
            pretty = false,
        )
    }

    fun decode(raw: String?): BodyProfile {
        if (raw.isNullOrBlank()) return BodyProfile()
        val root = runCatching { Json.parse(raw) as? JsonValue.Obj }.getOrNull() ?: return BodyProfile()
        val weights = root.array("weights")?.objects.orEmpty().mapNotNull { entry ->
            val atMs = entry.long("atMs") ?: return@mapNotNull null
            val weightKg = entry.double("weightKg") ?: return@mapNotNull null
            if (!weightKg.isFinite() || weightKg <= 0.0) return@mapNotNull null
            WeightEntry(atMs = atMs, weightKg = weightKg)
        }
        return BodyProfile(
            ageYears = root.int("ageYears"),
            heightCm = root.double("heightCm"),
            weightEntries = weights,
        ).sanitized()
    }
}
